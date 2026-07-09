package com.jrawler.adaptation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A skill the candidate asserts they actually have but that may be missing from the
 * uploaded resume (side work, omitted tasks). Fed to the LLM as ground truth it may
 * weave into adaptations — never a licence to invent experience.
 */
@Entity
@Table(name = "candidate_skills")
@Getter
@Setter
public class CandidateSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "term", nullable = false)
    private String term;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
