package com.jobcrawler.scheduler;

import com.jobcrawler.adapter.AdapterRegistry;
import com.jobcrawler.adapter.JobSearchAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.crawl.CrawlLog;
import com.jobcrawler.crawl.CrawlLogRepository;
import com.jobcrawler.processing.ProcessingPipeline;
import com.jobcrawler.profile.SearchProfileRepository;
import com.jobcrawler.source.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final AdapterRegistry adapterRegistry;
    private final SearchProfileRepository profileRepository;
    private final SourceRepository sourceRepository;
    private final CrawlLogRepository crawlLogRepository;
    private final ProcessingPipeline processingPipeline;

    @Value("${crawler.enabled:true}")
    private boolean crawlerEnabled;

    @Scheduled(cron = "${crawler.schedule-cron:0 0 * * * *}")
    public void runCrawl() {
        if (!crawlerEnabled) {
            log.info("Crawler is disabled, skipping scheduled run");
            return;
        }
        runAll(false);
    }

    /**
     * Runs all enabled adapters in parallel using Virtual Threads (Java 21).
     * Can be called manually via REST API.
     * @param force if true, clears all cached ETags to force a full rescan
     */
    public void runAll(boolean force) {
        if (force) {
            sourceRepository.findAll().forEach(source -> {
                if (source.getLastEtag() != null) {
                    source.setLastEtag(null);
                    sourceRepository.save(source);
                }
            });
            log.info("Force mode: cleared all cached ETags");
        }

        List<JobSearchAdapter> enabled = adapterRegistry.getEnabled();
        if (enabled.isEmpty()) {
            log.info("No enabled adapters found, skipping crawl");
            return;
        }

        SearchCriteria criteria = buildSearchCriteria();
        log.info("Starting crawl: {} adapters, {} keywords",
                enabled.size(), criteria.keywords().size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AdapterResult>> futures = enabled.stream()
                    .map(adapter -> executor.submit(() -> runAdapter(adapter, criteria)))
                    .toList();

            int totalFound = 0;
            for (Future<AdapterResult> future : futures) {
                try {
                    AdapterResult result = future.get(5, TimeUnit.MINUTES);
                    totalFound += result.vacanciesFound();
                } catch (Exception e) {
                    log.error("Adapter task failed: {}", e.getMessage());
                }
            }

            log.info("Crawl complete: {} total vacancies found across {} adapters",
                    totalFound, enabled.size());
        }
    }

    private AdapterResult runAdapter(JobSearchAdapter adapter, SearchCriteria criteria) {
        String sourceId = adapter.getSourceId();
        CrawlLog crawlLog = CrawlLog.start(sourceId);

        try {
            log.debug("[{}] Starting fetch", sourceId);
            List<RawVacancy> vacancies = adapter.fetchJobs(criteria);
            int found = vacancies.size();
            int saved = processingPipeline.process(vacancies);

            // Update last_crawled_at for source
            sourceRepository.findById(sourceId).ifPresent(source -> {
                source.setLastCrawledAt(Instant.now());
                sourceRepository.save(source);
            });

            crawlLog.finish(found, saved);
            crawlLogRepository.save(crawlLog);

            log.info("[{}] Fetched {}, saved {} new vacancies", sourceId, found, saved);
            return new AdapterResult(sourceId, found);

        } catch (Exception e) {
            log.error("[{}] Adapter failed: {}", sourceId, e.getMessage());
            crawlLog.fail(e.getMessage());
            crawlLogRepository.save(crawlLog);
            return new AdapterResult(sourceId, 0);
        }
    }

    private SearchCriteria buildSearchCriteria() {
        var profiles = profileRepository.findByActiveTrue();
        if (profiles.isEmpty()) {
            log.warn("No active search profiles found, using empty criteria");
            return new SearchCriteria(List.of(), List.of(), List.of());
        }
        return SearchCriteria.fromProfiles(profiles);
    }

    record AdapterResult(String sourceId, int vacanciesFound) {}
}
