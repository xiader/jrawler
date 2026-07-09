package com.jrawler.adaptation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record LlmAdaptation(
        @JsonPropertyDescription("Paragraph rewrites tailoring the resume to the vacancy")
        List<LlmEdit> edits,
        @JsonPropertyDescription("Vacancy requirements covered by neither the resume nor the candidate's skill list")
        List<LlmSuggestion> suggestions) {

    public record LlmEdit(
            @JsonPropertyDescription("Index of the resume paragraph being rewritten, from the [N] markers")
            int paragraphIndex,
            @JsonPropertyDescription("The rewritten paragraph text")
            String newText) {}

    public record LlmSuggestion(
            @JsonPropertyDescription("The missing requirement as a short canonical term, e.g. \"Kubernetes\"")
            String keyword,
            @JsonPropertyDescription("One-sentence advice to the candidate about this requirement, in English")
            String text) {}
}
