package com.jrawler.vacancy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    boolean existsByUrl(String url);

    boolean existsByDescriptionHash(String hash);

    Optional<Vacancy> findByUrl(String url);

    @Query("""
            SELECT v FROM Vacancy v
            WHERE (:sourceId IS NULL OR v.sourceId = :sourceId)
              AND (:profileId IS NULL OR v.profileId = :profileId)
              AND (:status IS NULL OR v.status = :status)
              AND (:minScore IS NULL OR v.relevanceScore >= :minScore)
            ORDER BY v.relevanceScore DESC, v.foundAt DESC
            """)
    Page<Vacancy> findWithFilters(
            @Param("sourceId") String sourceId,
            @Param("profileId") UUID profileId,
            @Param("status") VacancyStatus status,
            @Param("minScore") Integer minScore,
            Pageable pageable
    );
}
