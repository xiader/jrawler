package com.jrawler.adaptation;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdaptationService {

    private static final Logger log = LoggerFactory.getLogger(AdaptationService.class);

    private final AdaptationProperties props;
    private final DocxService docxService;
    private final ClaudeClient claudeClient;
    private final AdaptationStore store;
    private final CandidateSkillRepository skillRepository;

    public record SuggestionDto(String keyword, String text) {}
    public record AdaptationResponse(String adaptationId, List<EditDto> edits, List<SuggestionDto> suggestions) {}

    public AdaptationResponse createAdaptation(byte[] docxBytes, String vacancyText) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adaptation is not configured: set ANTHROPIC_API_KEY");
        }
        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(docxBytes);
        if (paragraphs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume contains no text");
        }

        List<CandidateSkill> skills = skillRepository.findAll();
        LlmAdaptation llm;
        try {
            llm = claudeClient.adapt(vacancyText, paragraphs, skills);
        } catch (Exception e) {
            log.error("LLM adaptation call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM service unavailable, try again");
        }

        Map<Integer, String> originalByIndex = paragraphs.stream()
                .collect(Collectors.toMap(DocxService.DocxParagraph::index, DocxService.DocxParagraph::text));
        List<EditDto> edits = llm.edits() == null ? List.of() : llm.edits().stream()
                .filter(e -> originalByIndex.containsKey(e.paragraphIndex()))
                .filter(e -> e.newText() != null && !e.newText().isBlank())
                .filter(e -> !e.newText().strip().equals(originalByIndex.get(e.paragraphIndex()).strip()))
                .map(e -> new EditDto(e.paragraphIndex(),
                        originalByIndex.get(e.paragraphIndex()), e.newText().strip()))
                .toList();

        // Safety net: the prompt already excludes known skills from suggestions, but filter anyway.
        Set<String> knownTerms = skills.stream()
                .map(s -> s.getTerm().toLowerCase())
                .collect(Collectors.toSet());
        List<SuggestionDto> suggestions = llm.suggestions() == null ? List.of() : llm.suggestions().stream()
                .filter(s -> s.keyword() != null && !s.keyword().isBlank())
                .filter(s -> !knownTerms.contains(s.keyword().strip().toLowerCase()))
                .map(s -> new SuggestionDto(s.keyword().strip(), s.text()))
                .toList();
        String id = store.save(docxBytes, edits);
        log.info("Adaptation {} created: {} edits, {} suggestions", id, edits.size(), suggestions.size());
        return new AdaptationResponse(id, edits, suggestions);
    }

    public byte[] buildDocx(String id, List<Integer> acceptedIndexes) {
        AdaptationStore.StoredAdaptation stored = store.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Adaptation session expired, start over"));
        Set<Integer> accepted = Set.copyOf(acceptedIndexes);
        Map<Integer, String> editsByIndex = new HashMap<>();
        for (EditDto edit : stored.edits()) {
            if (accepted.contains(edit.paragraphIndex())) {
                editsByIndex.put(edit.paragraphIndex(), edit.proposed());
            }
        }
        return docxService.applyEdits(Base64.getDecoder().decode(stored.docxBase64()), editsByIndex);
    }
}
