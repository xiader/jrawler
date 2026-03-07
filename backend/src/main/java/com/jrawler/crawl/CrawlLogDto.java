package com.jrawler.crawl;

import java.time.Instant;
import java.util.UUID;

public record CrawlLogDto(
        UUID id,
        String sourceId,
        Instant startedAt,
        Instant finishedAt,
        int vacanciesFound,
        int vacanciesNew,
        String error
) {
    public static CrawlLogDto from(CrawlLog c) {
        return new CrawlLogDto(
                c.getId(), c.getSourceId(), c.getStartedAt(), c.getFinishedAt(),
                c.getVacanciesFound(), c.getVacanciesNew(), c.getError()
        );
    }
}
