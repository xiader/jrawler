package com.jrawler.crawl;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crawl_logs")
@Getter
@Setter
public class CrawlLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "vacancies_found", nullable = false)
    private int vacanciesFound;

    @Column(name = "vacancies_new", nullable = false)
    private int vacanciesNew;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    public static CrawlLog start(String sourceId) {
        CrawlLog log = new CrawlLog();
        log.sourceId = sourceId;
        log.startedAt = Instant.now();
        log.vacanciesFound = 0;
        log.vacanciesNew = 0;
        return log;
    }

    public void finish(int found, int newCount) {
        this.finishedAt = Instant.now();
        this.vacanciesFound = found;
        this.vacanciesNew = newCount;
    }

    public void fail(String errorMessage) {
        this.finishedAt = Instant.now();
        this.error = errorMessage;
    }
}
