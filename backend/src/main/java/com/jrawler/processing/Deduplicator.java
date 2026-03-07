package com.jrawler.processing;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.vacancy.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Detects duplicate vacancies.
 *
 * Strategy:
 *  1. URL uniqueness — vacancies table has UNIQUE constraint on url.
 *  2. Description hash — normalized hash of first 500 chars of description.
 */
@Component
@RequiredArgsConstructor
public class Deduplicator {

    private final VacancyRepository vacancyRepository;
    private final Normalizer normalizer;

    /**
     * Returns true if this vacancy already exists in DB (by URL or description hash).
     */
    public boolean isDuplicate(RawVacancy raw) {
        if (raw.url() != null && vacancyRepository.existsByUrl(raw.url())) {
            return true;
        }
        if (raw.description() != null && !raw.description().isBlank()) {
            String hash = normalizer.descriptionHash(raw.description());
            if (hash != null && vacancyRepository.existsByDescriptionHash(hash)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a stable external ID from company + title + location when the
     * adapter didn't provide one.
     */
    public String deriveExternalId(RawVacancy raw) {
        if (raw.externalId() != null && !raw.externalId().isBlank()) {
            return raw.externalId();
        }
        String company = raw.companyName() != null ? raw.companyName().toLowerCase(Locale.ROOT) : "";
        String title   = raw.title() != null ? raw.title().toLowerCase(Locale.ROOT) : "";
        String loc     = raw.location() != null ? raw.location().toLowerCase(Locale.ROOT) : "";
        return (raw.sourceId() + "-" + (company + title + loc).hashCode());
    }
}
