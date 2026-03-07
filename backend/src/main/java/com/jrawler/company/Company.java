package com.jrawler.company;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "career_page_url", length = 512)
    private String careerPageUrl;

    @Column(name = "ats_type", length = 64)
    private String atsType;

    @Column(name = "ats_company_id", length = 128)
    private String atsCompanyId;

    @Type(JsonBinaryType.class)
    @Column(name = "custom_selectors", columnDefinition = "jsonb")
    private Map<String, String> customSelectors;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_crawled_at")
    private Instant lastCrawledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
