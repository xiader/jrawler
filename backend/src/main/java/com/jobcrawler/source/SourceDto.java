package com.jobcrawler.source;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SourceDto(
        String id,
        String name,
        int priority,
        @JsonProperty("isEnabled") boolean enabled,
        Instant lastCrawledAt
) {
    public static SourceDto from(Source s) {
        return new SourceDto(s.getId(), s.getName(), s.getPriority(), s.isEnabled(), s.getLastCrawledAt());
    }
}
