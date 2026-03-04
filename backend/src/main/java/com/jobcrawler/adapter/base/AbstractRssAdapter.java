package com.jobcrawler.adapter.base;

import com.jobcrawler.adapter.JobSearchAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.Source;
import com.jobcrawler.source.SourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;

public abstract class AbstractRssAdapter implements JobSearchAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final OkHttpClient httpClient;
    protected final RedisTemplate<String, String> redisTemplate;
    protected final SourceRepository sourceRepository;

    protected AbstractRssAdapter(OkHttpClient httpClient,
                                  RedisTemplate<String, String> redisTemplate,
                                  SourceRepository sourceRepository) {
        this.httpClient = httpClient;
        this.redisTemplate = redisTemplate;
        this.sourceRepository = sourceRepository;
    }

    protected abstract String getRssFeedUrl(SearchCriteria criteria);

    protected abstract RawVacancy mapEntry(SyndEntry entry);

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        String url = getRssFeedUrl(criteria);

        Optional<Source> sourceOpt = sourceRepository.findById(getSourceId());
        String cachedEtag = sourceOpt.map(Source::getLastEtag).orElse(null);

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", "JobCrawler/1.0 (RSS Reader)");

        if (cachedEtag != null) {
            reqBuilder.header("If-None-Match", cachedEtag);
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (response.code() == 304) {
                log.debug("[{}] Feed not modified (304), skipping", getSourceId());
                return List.of();
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("[{}] RSS fetch failed: HTTP {}", getSourceId(), response.code());
                return List.of();
            }

            // Persist new ETag to DB for next request's If-None-Match header
            String newEtag = response.header("ETag");
            if (newEtag != null) {
                sourceOpt.ifPresent(source -> {
                    source.setLastEtag(newEtag);
                    sourceRepository.save(source);
                });
            }

            SyndFeed feed = new SyndFeedInput().build(new XmlReader(response.body().byteStream()));
            List<RawVacancy> result = feed.getEntries().stream()
                    .map(entry -> {
                        try { return mapEntry(entry); }
                        catch (Exception e) {
                            log.debug("[{}] Failed to map entry: {}", getSourceId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(v -> v != null)
                    .toList();

            log.info("[{}] Fetched {} vacancies from RSS", getSourceId(), result.size());
            return result;

        } catch (Exception e) {
            log.error("[{}] RSS fetch error: {}", getSourceId(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isEnabled() {
        return sourceRepository.findById(getSourceId())
                .map(Source::isEnabled)
                .orElse(false);
    }
}
