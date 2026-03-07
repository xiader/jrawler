package com.jrawler.processing;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.profile.SearchProfile;
import com.jrawler.profile.SearchProfileRepository;
import com.jrawler.vacancy.Vacancy;
import com.jrawler.vacancy.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the full RawVacancy → Vacancy pipeline:
 *   1. Deduplication (skip if URL or description hash already exists)
 *   2. Normalization (remoteType, salary, descriptionHash)
 *   3. Relevance scoring against all active profiles
 *   4. Persist with the best-matching profile
 */
@Component
@RequiredArgsConstructor
public class ProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(ProcessingPipeline.class);

    private final VacancyRepository vacancyRepository;
    private final SearchProfileRepository profileRepository;
    private final Normalizer normalizer;
    private final RelevanceScorer scorer;
    private final Deduplicator deduplicator;

    /**
     * Processes a list of raw vacancies and returns the number actually saved.
     */
    @Transactional
    public int process(List<RawVacancy> rawVacancies) {
        List<SearchProfile> activeProfiles = profileRepository.findByActiveTrue();
        Map<UUID, Integer> profileMinScores = activeProfiles.stream()
                .collect(Collectors.toMap(SearchProfile::getId, SearchProfile::getMinRelevanceScore));
        int saved = 0;

        for (RawVacancy raw : rawVacancies) {
            try {
                if (deduplicator.isDuplicate(raw)) {
                    log.debug("[pipeline] Duplicate skipped: {}", raw.url());
                    continue;
                }

                Vacancy vacancy = toVacancy(raw, activeProfiles);

                // Skip vacancies that don't meet the minimum score of the matched profile
                int minScore = vacancy.getProfileId() != null
                        ? profileMinScores.getOrDefault(vacancy.getProfileId(), 1)
                        : 1;
                if (activeProfiles.isEmpty() || vacancy.getRelevanceScore() >= minScore) {
                    vacancyRepository.save(vacancy);
                    saved++;
                } else {
                    log.debug("[pipeline] Below min score ({}<{}), skipped: {}", vacancy.getRelevanceScore(), minScore, raw.title());
                }
            } catch (Exception e) {
                log.error("[pipeline] Failed to process vacancy '{}': {}", raw.title(), e.getMessage());
            }
        }

        log.info("[pipeline] Processed {}/{} vacancies (saved/total)", saved, rawVacancies.size());
        return saved;
    }

    private Vacancy toVacancy(RawVacancy raw, List<SearchProfile> profiles) {
        Vacancy v = new Vacancy();
        v.setSourceId(raw.sourceId());
        v.setExternalId(deduplicator.deriveExternalId(raw));
        v.setTitle(raw.title());
        v.setCompanyName(raw.companyName());
        v.setUrl(raw.url());
        v.setLocation(raw.location());
        v.setSalaryRaw(normalizer.normalizeSalary(raw.salaryRaw()));
        v.setRemoteType(normalizer.normalizeRemoteType(raw.remoteTypeRaw()));
        v.setDescription(raw.description());
        v.setDescriptionHash(normalizer.descriptionHash(raw.description()));
        v.setFoundAt(raw.fetchedAt() != null ? raw.fetchedAt() : Instant.now());
        v.setCreatedAt(Instant.now());

        // Score against all active profiles, pick the best match
        int bestScore = 0;
        List<String> bestMatched = List.of();
        SearchProfile bestProfile = null;

        for (SearchProfile profile : profiles) {
            RelevanceScorer.ScoringResult result = scorer.score(raw, profile);
            if (result.score() > bestScore) {
                bestScore = result.score();
                bestMatched = result.matchedKeywords();
                bestProfile = profile;
            }
        }

        v.setRelevanceScore(bestScore);
        v.setMatchedKeywords(bestMatched);
        v.setProfileId(bestProfile != null ? bestProfile.getId() : null);

        return v;
    }
}
