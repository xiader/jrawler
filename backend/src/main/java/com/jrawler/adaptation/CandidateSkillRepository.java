package com.jrawler.adaptation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {

    Optional<CandidateSkill> findByTermIgnoreCase(String term);
}
