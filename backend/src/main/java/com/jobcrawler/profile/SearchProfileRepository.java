package com.jobcrawler.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SearchProfileRepository extends JpaRepository<SearchProfile, UUID> {

    List<SearchProfile> findByActiveTrue();
}
