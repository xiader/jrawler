package com.jrawler.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchProfileService {

    private final SearchProfileRepository profileRepository;

    public List<SearchProfileDto> findAll() {
        return profileRepository.findAll().stream().map(SearchProfileDto::from).toList();
    }

    public SearchProfileDto findById(UUID id) {
        return profileRepository.findById(id)
                .map(SearchProfileDto::from)
                .orElseThrow(() -> new SearchProfileNotFoundException(id));
    }

    @Transactional
    public SearchProfileDto create(SearchProfileRequest req) {
        SearchProfile profile = new SearchProfile();
        apply(profile, req);
        profile.setCreatedAt(Instant.now());
        return SearchProfileDto.from(profileRepository.save(profile));
    }

    @Transactional
    public SearchProfileDto update(UUID id, SearchProfileRequest req) {
        SearchProfile profile = profileRepository.findById(id)
                .orElseThrow(() -> new SearchProfileNotFoundException(id));
        apply(profile, req);
        return SearchProfileDto.from(profileRepository.save(profile));
    }

    @Transactional
    public SearchProfileDto toggle(UUID id) {
        SearchProfile profile = profileRepository.findById(id)
                .orElseThrow(() -> new SearchProfileNotFoundException(id));
        profile.setActive(!profile.isActive());
        return SearchProfileDto.from(profileRepository.save(profile));
    }

    @Transactional
    public void delete(UUID id) {
        if (!profileRepository.existsById(id)) {
            throw new SearchProfileNotFoundException(id);
        }
        profileRepository.deleteById(id);
    }

    private void apply(SearchProfile profile, SearchProfileRequest req) {
        profile.setName(req.name());
        if (req.active() != null) profile.setActive(req.active());
        profile.setMustHaveKeywords(req.mustHaveKeywords());
        profile.setNiceToHaveKeywords(req.niceToHaveKeywords());
        profile.setExcludeKeywords(req.excludeKeywords());
        profile.setLocations(req.locations());
        profile.setRemoteTypes(req.remoteTypes());
        profile.setMinRelevanceScore(req.minRelevanceScore());
    }
}
