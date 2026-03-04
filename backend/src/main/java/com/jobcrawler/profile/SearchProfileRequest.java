package com.jobcrawler.profile;

import java.util.List;

public record SearchProfileRequest(
        String name,
        Boolean active,
        List<String> mustHaveKeywords,
        List<String> niceToHaveKeywords,
        List<String> excludeKeywords,
        List<String> locations,
        List<String> remoteTypes,
        int minRelevanceScore
) {}
