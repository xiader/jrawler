package com.jrawler.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
