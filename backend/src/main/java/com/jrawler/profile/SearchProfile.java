package com.jrawler.profile;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "search_profiles")
@Getter
@Setter
public class SearchProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Type(JsonBinaryType.class)
    @Column(name = "must_have_keywords", columnDefinition = "jsonb")
    private List<String> mustHaveKeywords;

    @Type(JsonBinaryType.class)
    @Column(name = "nice_to_have_keywords", columnDefinition = "jsonb")
    private List<String> niceToHaveKeywords;

    @Type(JsonBinaryType.class)
    @Column(name = "exclude_keywords", columnDefinition = "jsonb")
    private List<String> excludeKeywords;

    @Type(JsonBinaryType.class)
    @Column(name = "locations", columnDefinition = "jsonb")
    private List<String> locations;

    @Type(JsonBinaryType.class)
    @Column(name = "remote_types", columnDefinition = "jsonb")
    private List<String> remoteTypes;

    @Column(name = "min_relevance_score", nullable = false)
    private int minRelevanceScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
