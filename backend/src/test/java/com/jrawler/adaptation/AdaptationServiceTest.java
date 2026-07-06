package com.jrawler.adaptation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptationServiceTest {

    private final DocxService docxService = mock(DocxService.class);
    private final ClaudeClient claudeClient = mock(ClaudeClient.class);
    private final AdaptationStore store = mock(AdaptationStore.class);
    private AdaptationService service;

    private final byte[] docx = {1, 2, 3};
    private final List<DocxService.DocxParagraph> paragraphs = List.of(
            new DocxService.DocxParagraph(0, "Ivan Ivanov"),
            new DocxService.DocxParagraph(2, "Built microservices"));

    @BeforeEach
    void setUp() {
        AdaptationProperties props = new AdaptationProperties("test-key", "claude-opus-4-8", 30);
        service = new AdaptationService(props, docxService, claudeClient, store);
        when(docxService.extractParagraphs(docx)).thenReturn(paragraphs);
        when(store.save(any(), anyList())).thenReturn("id-123");
    }

    @Test
    void keepsValidEditsAndDropsInvalidOnes() {
        when(claudeClient.adapt(anyString(), anyList())).thenReturn(new LlmAdaptation(List.of(
                new LlmAdaptation.LlmEdit(2, "Designed Kafka microservices"), // valid
                new LlmAdaptation.LlmEdit(99, "index not in resume"),          // dropped: unknown index
                new LlmAdaptation.LlmEdit(0, "   "),                            // dropped: blank
                new LlmAdaptation.LlmEdit(0, "Ivan Ivanov")                     // dropped: no-op
        ), List.of("Add Kafka experience if you have it")));

        AdaptationService.AdaptationResponse response = service.createAdaptation(docx, "vacancy text");

        assertThat(response.adaptationId()).isEqualTo("id-123");
        assertThat(response.edits()).containsExactly(
                new EditDto(2, "Built microservices", "Designed Kafka microservices"));
        assertThat(response.suggestions()).containsExactly("Add Kafka experience if you have it");
    }

    @Test
    void returns503WhenApiKeyMissing() {
        AdaptationProperties noKey = new AdaptationProperties("", "claude-opus-4-8", 30);
        AdaptationService unconfigured = new AdaptationService(noKey, docxService, claudeClient, store);

        assertThatThrownBy(() -> unconfigured.createAdaptation(docx, "vacancy"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void returns400WhenDocxHasNoParagraphs() {
        when(docxService.extractParagraphs(docx)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAdaptation(docx, "vacancy"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void returns502WhenLlmFails() {
        when(claudeClient.adapt(anyString(), anyList())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.createAdaptation(docx, "vacancy"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
    }

    @Test
    void buildDocxAppliesOnlyAcceptedEdits() {
        String docxBase64 = java.util.Base64.getEncoder().encodeToString(docx);
        when(store.find("id-123")).thenReturn(Optional.of(new AdaptationStore.StoredAdaptation(
                docxBase64, List.of(
                        new EditDto(0, "Ivan Ivanov", "IVAN IVANOV"),
                        new EditDto(2, "Built microservices", "Designed Kafka microservices")))));
        byte[] adapted = {9, 9, 9};
        when(docxService.applyEdits(any(), any())).thenReturn(adapted);

        byte[] result = service.buildDocx("id-123", List.of(2));

        assertThat(result).isEqualTo(adapted);
        org.mockito.Mockito.verify(docxService).applyEdits(docx,
                java.util.Map.of(2, "Designed Kafka microservices"));
    }

    @Test
    void buildDocxReturns410WhenExpired() {
        when(store.find("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildDocx("gone", List.of(0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410");
    }
}
