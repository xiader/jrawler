package com.jrawler.vacancy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VacancyService {

    private final VacancyRepository vacancyRepository;

    public Page<VacancyDto> findAll(String sourceId, UUID profileId, VacancyStatus status,
                                    Integer minScore, Pageable pageable) {
        return vacancyRepository
                .findWithFilters(sourceId, profileId, status, minScore, pageable)
                .map(VacancyDto::from);
    }

    public VacancyDto findById(UUID id) {
        return vacancyRepository.findById(id)
                .map(VacancyDto::from)
                .orElseThrow(() -> new VacancyNotFoundException(id));
    }

    @Transactional
    public VacancyDto updateStatus(UUID id, VacancyStatus newStatus) {
        Vacancy vacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new VacancyNotFoundException(id));
        vacancy.setStatus(newStatus);
        return VacancyDto.from(vacancyRepository.save(vacancy));
    }
}
