package com.jobcrawler.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class CompanyDto {

    public record Request(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 512) String careerPageUrl,
            @Size(max = 64) String atsType,
            @Size(max = 128) String atsCompanyId,
            Map<String, String> customSelectors,
            boolean active
    ) {}

    public record Response(
            UUID id,
            String name,
            String careerPageUrl,
            String atsType,
            String atsCompanyId,
            Map<String, String> customSelectors,
            boolean active,
            Instant lastCrawledAt,
            Instant createdAt
    ) {}

    static Response toResponse(Company c) {
        return new Response(
                c.getId(),
                c.getName(),
                c.getCareerPageUrl(),
                c.getAtsType(),
                c.getAtsCompanyId(),
                c.getCustomSelectors(),
                c.isActive(),
                c.getLastCrawledAt(),
                c.getCreatedAt()
        );
    }
}
