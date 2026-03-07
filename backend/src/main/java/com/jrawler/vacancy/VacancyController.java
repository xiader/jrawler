package com.jrawler.vacancy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vacancies")
@RequiredArgsConstructor
public class VacancyController {

    private final VacancyService vacancyService;

    @GetMapping
    public Page<VacancyDto> list(
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(required = false) VacancyStatus status,
            @RequestParam(required = false) Integer minScore,
            @PageableDefault(size = 20, sort = "relevanceScore") Pageable pageable
    ) {
        return vacancyService.findAll(sourceId, profileId, status, minScore, pageable);
    }

    @GetMapping("/{id}")
    public VacancyDto get(@PathVariable UUID id) {
        return vacancyService.findById(id);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<VacancyDto> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(vacancyService.updateStatus(id, request.status()));
    }

    record StatusUpdateRequest(VacancyStatus status) {}
}
