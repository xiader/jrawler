package com.jrawler.processing;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.profile.SearchProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores a RawVacancy against a SearchProfile.
 *
 * Scoring rules:
 *   = 0  if any exclude keyword found in title or description
 *   +30  per must-have keyword found in title
 *   +10  per must-have keyword found in description only
 *   +5   per nice-to-have keyword found in title or description
 *   +15  if remote type matches profile's remote_types list
 *   +10  if location matches profile's locations list (or profile has no location filter)
 *
 * Score is capped at 100.
 */
@Component
public class RelevanceScorer {

    public record ScoringResult(int score, List<String> matchedKeywords) {}

    public ScoringResult score(RawVacancy vacancy, SearchProfile profile) {
        String titleLower = lower(vacancy.title());
        String descLower  = lower(vacancy.description());

        List<String> matched = new ArrayList<>();

        // Exclude check — score = 0 immediately
        for (String kw : safe(profile.getExcludeKeywords())) {
            if (titleLower.contains(kw.toLowerCase(Locale.ROOT))
                    || descLower.contains(kw.toLowerCase(Locale.ROOT))) {
                return new ScoringResult(0, List.of());
            }
        }

        int score = 0;

        // Must-have keywords
        for (String kw : safe(profile.getMustHaveKeywords())) {
            String kwLow = kw.toLowerCase(Locale.ROOT);
            if (titleLower.contains(kwLow)) {
                score += 30;
                matched.add(kw);
            } else if (descLower.contains(kwLow)) {
                score += 10;
                matched.add(kw);
            }
        }

        // Nice-to-have keywords
        for (String kw : safe(profile.getNiceToHaveKeywords())) {
            String kwLow = kw.toLowerCase(Locale.ROOT);
            if (titleLower.contains(kwLow) || descLower.contains(kwLow)) {
                score += 5;
                matched.add(kw);
            }
        }

        // Remote type match — hard filter if profile specifies remote types
        List<String> profileRemoteTypes = safe(profile.getRemoteTypes());
        if (!profileRemoteTypes.isEmpty()) {
            if (vacancy.remoteTypeRaw() == null) {
                score += 5; // unknown remote type — small bonus, not excluded
            } else {
                String remoteNorm = vacancy.remoteTypeRaw().toUpperCase(Locale.ROOT);
                boolean remoteMatch = profileRemoteTypes.stream()
                        .anyMatch(r -> r.equalsIgnoreCase(remoteNorm));
                if (remoteMatch) {
                    score += 15;
                } else {
                    return new ScoringResult(0, List.of()); // wrong remote type — hard exclude
                }
            }
        }

        // Location match
        List<String> profileLocations = safe(profile.getLocations());
        if (profileLocations.isEmpty()) {
            score += 10; // no location filter = always matches
        } else if (vacancy.location() != null) {
            String locLow = vacancy.location().toLowerCase(Locale.ROOT);
            boolean locationMatch = profileLocations.stream()
                    .anyMatch(l -> locLow.contains(l.toLowerCase(Locale.ROOT)));
            if (locationMatch) score += 10;
        }

        return new ScoringResult(Math.min(score, 100), matched);
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
