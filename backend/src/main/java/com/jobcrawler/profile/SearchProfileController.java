package com.jobcrawler.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class SearchProfileController {

    private final SearchProfileService profileService;

    @GetMapping
    public List<SearchProfileDto> list() {
        return profileService.findAll();
    }

    @GetMapping("/{id}")
    public SearchProfileDto get(@PathVariable UUID id) {
        return profileService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SearchProfileDto create(@RequestBody SearchProfileRequest request) {
        return profileService.create(request);
    }

    @PutMapping("/{id}")
    public SearchProfileDto update(@PathVariable UUID id, @RequestBody SearchProfileRequest request) {
        return profileService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    public SearchProfileDto toggle(@PathVariable UUID id) {
        return profileService.toggle(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        profileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
