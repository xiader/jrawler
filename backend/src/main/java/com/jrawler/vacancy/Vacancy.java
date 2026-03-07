package com.jrawler.vacancy;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vacancies")
@Getter
@Setter
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "url", nullable = false, unique = true)
    private String url;

    @Column(name = "location")
    private String location;

    @Column(name = "salary_raw")
    private String salaryRaw;

    @Column(name = "remote_type")
    private String remoteType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_hash")
    private String descriptionHash;

    @Column(name = "relevance_score", nullable = false)
    private int relevanceScore;

    @Type(JsonBinaryType.class)
    @Column(name = "matched_keywords", columnDefinition = "jsonb")
    private List<String> matchedKeywords;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private VacancyStatus status = VacancyStatus.NEW;

    @Column(name = "found_at", nullable = false, updatable = false)
    private Instant foundAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
