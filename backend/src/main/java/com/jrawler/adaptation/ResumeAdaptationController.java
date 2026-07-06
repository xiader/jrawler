package com.jrawler.adaptation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/resume-adaptation")
@RequiredArgsConstructor
public class ResumeAdaptationController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AdaptationService adaptationService;
    private final VacancyTextExtractor vacancyTextExtractor;

    public record FetchVacancyRequest(@NotBlank String url) {}
    public record FetchVacancyResponse(String text) {}
    public record DownloadRequest(List<Integer> acceptedIndexes) {}

    @PostMapping("/fetch-vacancy")
    public FetchVacancyResponse fetchVacancy(@Valid @RequestBody FetchVacancyRequest request) {
        return new FetchVacancyResponse(vacancyTextExtractor.extract(request.url()));
    }

    @PostMapping
    public AdaptationService.AdaptationResponse create(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("vacancyText") String vacancyText) {
        if (vacancyText == null || vacancyText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vacancy text is required");
        }
        try {
            return adaptationService.createAdaptation(resume.getBytes(), vacancyText);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
    }

    @PostMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, @RequestBody DownloadRequest request) {
        byte[] docx = adaptationService.buildDocx(id,
                request.acceptedIndexes() == null ? List.of() : request.acceptedIndexes());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resume-adapted.docx\"")
                .contentType(DOCX)
                .body(docx);
    }
}
