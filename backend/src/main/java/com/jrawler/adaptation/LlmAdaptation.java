package com.jrawler.adaptation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record LlmAdaptation(
        @JsonPropertyDescription("Paragraph rewrites tailoring the resume to the vacancy")
        List<LlmEdit> edits,
        @JsonPropertyDescription("Vacancy requirements not mentioned in the resume, phrased as advice to the candidate, in the resume's language")
        List<String> suggestions) {

    public record LlmEdit(
            @JsonPropertyDescription("Index of the resume paragraph being rewritten, from the [N] markers")
            int paragraphIndex,
            @JsonPropertyDescription("The rewritten paragraph text")
            String newText) {}
}
