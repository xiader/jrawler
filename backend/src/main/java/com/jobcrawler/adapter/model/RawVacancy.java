package com.jobcrawler.adapter.model;

import java.time.Instant;

public record RawVacancy(
        String sourceId,
        String externalId,
        String title,
        String companyName,
        String url,
        String location,
        String description,
        String salaryRaw,
        String remoteTypeRaw,
        Instant fetchedAt
) {
    public static Builder builder(String sourceId) {
        return new Builder(sourceId);
    }

    public static final class Builder {
        private final String sourceId;
        private String externalId;
        private String title;
        private String companyName;
        private String url;
        private String location;
        private String description;
        private String salaryRaw;
        private String remoteTypeRaw;
        private Instant fetchedAt = Instant.now();

        private Builder(String sourceId) {
            this.sourceId = sourceId;
        }

        public Builder externalId(String v) { externalId = v; return this; }
        public Builder title(String v) { title = v; return this; }
        public Builder companyName(String v) { companyName = v; return this; }
        public Builder url(String v) { url = v; return this; }
        public Builder location(String v) { location = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder salaryRaw(String v) { salaryRaw = v; return this; }
        public Builder remoteTypeRaw(String v) { remoteTypeRaw = v; return this; }
        public Builder fetchedAt(Instant v) { fetchedAt = v; return this; }

        public RawVacancy build() {
            return new RawVacancy(sourceId, externalId, title, companyName,
                    url, location, description, salaryRaw, remoteTypeRaw, fetchedAt);
        }
    }
}
