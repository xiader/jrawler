package com.jobcrawler.company;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService service;

    @GetMapping
    public List<CompanyDto.Response> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CompanyDto.Response getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<CompanyDto.Response> create(@Valid @RequestBody CompanyDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public CompanyDto.Response update(@PathVariable UUID id,
                                      @Valid @RequestBody CompanyDto.Request request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    public CompanyDto.Response toggle(@PathVariable UUID id) {
        return service.toggleActive(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
