package com.jrawler.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchProfileDto(
        UUID id,
        String name,
        @JsonProperty("isActive") boolean active,
        List<String> mustHaveKeywords,
        List<String> niceToHaveKeywords,
        List<String> excludeKeywords,
        List<String> locations,
        List<String> remoteTypes,
        int minRelevanceScore,
        Instant createdAt
) {
    public static SearchProfileDto from(SearchProfile p) {
        return new SearchProfileDto(
                p.getId(), p.getName(), p.isActive(),
                p.getMustHaveKeywords(), p.getNiceToHaveKeywords(), p.getExcludeKeywords(),
                p.getLocations(), p.getRemoteTypes(), p.getMinRelevanceScore(), p.getCreatedAt()
        );
    }
}
