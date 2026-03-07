package com.jrawler.source;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceRepository sourceRepository;

    @GetMapping
    public List<SourceDto> list() {
        return sourceRepository.findAll().stream().map(SourceDto::from).toList();
    }

    @PatchMapping("/{id}/toggle")
    @Transactional
    public ResponseEntity<SourceDto> toggle(@PathVariable String id) {
        return sourceRepository.findById(id)
                .map(source -> {
                    source.setEnabled(!source.isEnabled());
                    return ResponseEntity.ok(SourceDto.from(sourceRepository.save(source)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
