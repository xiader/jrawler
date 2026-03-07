package com.jobcrawler.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchProfileRequest(
        String name,
        @JsonProperty("isActive") Boolean active,
        List<String> mustHaveKeywords,
        List<String> niceToHaveKeywords,
        List<String> excludeKeywords,
        List<String> locations,
        List<String> remoteTypes,
        int minRelevanceScore
) {}
