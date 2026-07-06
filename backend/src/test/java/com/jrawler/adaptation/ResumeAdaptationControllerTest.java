package com.jrawler.adaptation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.jrawler.api.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeAdaptationControllerTest {

    private MockMvc mockMvc;
    private AdaptationService adaptationService;
    private VacancyTextExtractor vacancyTextExtractor;

    @BeforeEach
    void setup() {
        adaptationService = mock(AdaptationService.class);
        vacancyTextExtractor = mock(VacancyTextExtractor.class);
        ResumeAdaptationController controller = new ResumeAdaptationController(
                adaptationService, vacancyTextExtractor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void fetchVacancyReturnsText() throws Exception {
        when(vacancyTextExtractor.extract("https://example.com/job")).thenReturn("Java job text");

        mockMvc.perform(post("/api/v1/resume-adaptation/fetch-vacancy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/job\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Java job text"));
    }

    @Test
    void createAdaptationReturnsEditsAndSuggestions() throws Exception {
        when(adaptationService.createAdaptation(any(), anyString()))
                .thenReturn(new AdaptationService.AdaptationResponse("id-123",
                        List.of(new EditDto(2, "old", "new")),
                        List.of("Add Kafka")));

        MockMultipartFile resume = new MockMultipartFile("resume", "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/resume-adaptation")
                        .file(resume)
                        .param("vacancyText", "We need Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptationId").value("id-123"))
                .andExpect(jsonPath("$.edits[0].paragraphIndex").value(2))
                .andExpect(jsonPath("$.edits[0].original").value("old"))
                .andExpect(jsonPath("$.edits[0].proposed").value("new"))
                .andExpect(jsonPath("$.suggestions[0]").value("Add Kafka"));
    }

    @Test
    void serviceUnavailableMapsTo503ProblemDetail() throws Exception {
        when(adaptationService.createAdaptation(any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Adaptation is not configured: set ANTHROPIC_API_KEY"));

        MockMultipartFile resume = new MockMultipartFile("resume", "cv.docx",
                "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/resume-adaptation")
                        .file(resume)
                        .param("vacancyText", "text"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Adaptation is not configured: set ANTHROPIC_API_KEY"));
    }

    @Test
    void downloadStreamsDocxAttachment() throws Exception {
        when(adaptationService.buildDocx(eq("id-123"), anyList())).thenReturn(new byte[]{7, 7});

        mockMvc.perform(post("/api/v1/resume-adaptation/id-123/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedIndexes\": [2, 5]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"resume-adapted.docx\""))
                .andExpect(content().bytes(new byte[]{7, 7}));
    }

    @Test
    void downloadOfExpiredAdaptationReturns410() throws Exception {
        when(adaptationService.buildDocx(eq("gone"), anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE,
                        "Adaptation session expired, start over"));

        mockMvc.perform(post("/api/v1/resume-adaptation/gone/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedIndexes\": []}"))
                .andExpect(status().isGone());
    }
}
