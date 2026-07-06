package com.jrawler.adapter.model;

import com.jrawler.profile.SearchProfile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record SearchCriteria(
        List<String> keywords,
        List<String> locations,
        List<RemoteType> remoteTypes
) {
    /**
     * Builds a wide "union" criteria from all active profiles.
     * Adapters use this to make broad requests; filtering happens in the pipeline.
     */
    public static SearchCriteria fromProfiles(List<SearchProfile> profiles) {
        List<String> keywords = profiles.stream()
                .flatMap(p -> Stream.concat(
                        p.getMustHaveKeywords().stream(),
                        p.getNiceToHaveKeywords().stream()
                ))
                .map(String::toLowerCase)
                .distinct()
                .toList();

        List<String> locations = profiles.stream()
                .flatMap(p -> p.getLocations().stream())
                .distinct()
                .toList();

        List<RemoteType> remoteTypes = profiles.stream()
                .flatMap(p -> p.getRemoteTypes().stream())
                .map(s -> {
                    try { return RemoteType.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException _) { return null; }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new SearchCriteria(keywords, locations, remoteTypes);
    }
}
