package com.jrawler.adapter.model;

import com.jrawler.profile.SearchProfile;

import java.util.List;
import java.util.stream.Collectors;
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
                .collect(Collectors.toList());

        List<String> locations = profiles.stream()
                .flatMap(p -> p.getLocations().stream())
                .distinct()
                .collect(Collectors.toList());

        List<RemoteType> remoteTypes = profiles.stream()
                .flatMap(p -> p.getRemoteTypes().stream())
                .map(s -> {
                    try { return RemoteType.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(r -> r != null)
                .distinct()
                .collect(Collectors.toList());

        return new SearchCriteria(keywords, locations, remoteTypes);
    }
}
