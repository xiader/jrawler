package com.jobcrawler.scheduler;

import com.jobcrawler.crawl.CrawlLogDto;
import com.jobcrawler.crawl.CrawlLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlScheduler crawlScheduler;
    private final CrawlLogRepository crawlLogRepository;

    /**
     * Triggers a manual crawl run asynchronously.
     * Returns immediately with 202 Accepted.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run() {
        CompletableFuture.runAsync(crawlScheduler::runAll);
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Returns the latest crawl logs across all sources.
     */
    @GetMapping("/logs")
    public List<CrawlLogDto> logs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String sourceId
    ) {
        if (sourceId != null && !sourceId.isBlank()) {
            return crawlLogRepository
                    .findBySourceIdOrderByStartedAtDesc(sourceId, PageRequest.of(0, limit))
                    .stream().map(CrawlLogDto::from).toList();
        }
        return crawlLogRepository
                .findAll(PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("startedAt").descending()))
                .stream().map(CrawlLogDto::from).toList();
    }
}
