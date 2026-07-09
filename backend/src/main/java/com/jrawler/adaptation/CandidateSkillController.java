package com.jrawler.adaptation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidate-skills")
@RequiredArgsConstructor
public class CandidateSkillController {

    private final CandidateSkillRepository repository;

    public record SkillRequest(@NotBlank String term, String note) {}
    public record SkillDto(UUID id, String term, String note, Instant createdAt) {

        static SkillDto from(CandidateSkill skill) {
            return new SkillDto(skill.getId(), skill.getTerm(), skill.getNote(), skill.getCreatedAt());
        }
    }

    @GetMapping
    public List<SkillDto> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(CandidateSkill::getTerm, String.CASE_INSENSITIVE_ORDER))
                .map(SkillDto::from)
                .toList();
    }

    /** Upsert by term (case-insensitive) so "I have this" on a suggestion is idempotent. */
    @PostMapping
    public SkillDto create(@Valid @RequestBody SkillRequest request) {
        String term = request.term().strip();
        CandidateSkill skill = repository.findByTermIgnoreCase(term).orElseGet(() -> {
            CandidateSkill created = new CandidateSkill();
            created.setTerm(term);
            created.setCreatedAt(Instant.now());
            return created;
        });
        if (request.note() != null && !request.note().isBlank()) {
            skill.setNote(request.note().strip());
        }
        return SkillDto.from(repository.save(skill));
    }

    @PutMapping("/{id}")
    public SkillDto update(@PathVariable UUID id, @Valid @RequestBody SkillRequest request) {
        CandidateSkill skill = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        skill.setTerm(request.term().strip());
        skill.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        return SkillDto.from(repository.save(skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
