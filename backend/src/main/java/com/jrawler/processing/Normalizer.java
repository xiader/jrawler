package com.jrawler.processing;

import com.jrawler.adapter.model.RemoteType;
import org.springframework.stereotype.Component;

@Component
public class Normalizer {

    /**
     * Normalizes raw remote type string to enum string value.
     * Returns null if not recognized.
     */
    public String normalizeRemoteType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RemoteType.fromRaw(raw).name();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Normalizes salary string by trimming whitespace and collapsing internal spaces.
     * Returns null for blank input.
     */
    public String normalizeSalary(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().replaceAll("\\s+", " ");
    }

    /**
     * Computes a short hash of the description for deduplication.
     * Uses first 500 chars, lowercased and stripped of extra whitespace.
     */
    public String descriptionHash(String description) {
        if (description == null || description.isBlank()) return null;
        String normalized = description.toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
        String sample = normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
        return String.valueOf(sample.hashCode());
    }
}
