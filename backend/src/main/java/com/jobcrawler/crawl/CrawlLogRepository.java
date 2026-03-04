package com.jobcrawler.crawl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrawlLogRepository extends JpaRepository<CrawlLog, UUID> {

    List<CrawlLog> findBySourceIdOrderByStartedAtDesc(String sourceId, Pageable pageable);
}
