package com.jrawler.vacancy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VacancyDto(
        UUID id,
        String sourceId,
        String externalId,
        String title,
        String companyName,
        String url,
        String location,
        String salaryRaw,
        String remoteType,
        String description,
        int relevanceScore,
        List<String> matchedKeywords,
        UUID profileId,
        VacancyStatus status,
        Instant foundAt,
        Instant createdAt
) {
    public static VacancyDto from(Vacancy v) {
        return new VacancyDto(
                v.getId(), v.getSourceId(), v.getExternalId(),
                v.getTitle(), v.getCompanyName(), v.getUrl(),
                v.getLocation(), v.getSalaryRaw(), v.getRemoteType(),
                v.getDescription(), v.getRelevanceScore(), v.getMatchedKeywords(),
                v.getProfileId(), v.getStatus(), v.getFoundAt(), v.getCreatedAt()
        );
    }
}
