package com.jobcrawler.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sources")
@Getter
@Setter
public class Source {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_crawled_at")
    private Instant lastCrawledAt;

    @Column(name = "last_etag")
    private String lastEtag;
}
