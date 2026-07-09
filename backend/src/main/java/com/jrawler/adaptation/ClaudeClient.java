package com.jrawler.adaptation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaudeClient {

    private static final String SYSTEM_PROMPT = """
            You help a candidate adapt their resume to a specific vacancy so it passes both literal ATS \
            keyword screening and a human recruiter's read.

            Ground truth:
            - The resume paragraphs in <resume> and the entries in <candidate_skills> are the only facts \
            you may state. Never invent experience, technologies, employers, dates, or numbers beyond them.
            - <candidate_skills> lists skills the candidate asserts they actually have but that may be \
            missing from the resume (each entry: a term, optionally with a context note).

            Rewriting rules:
            - ATS keyword matching is literal. Where the resume describes the same technology the vacancy \
            asks for under a different name or spelling, rewrite it to the vacancy's exact spelling. For \
            well-known abbreviations use the "Full Name (ABBR)" form once, e.g. "Amazon Web Services (AWS)".
            - Weave <candidate_skills> terms in where the vacancy asks for them: into the resume's skills \
            section verbatim (in the vacancy's spelling), and into experience bullets only where the entry's \
            context note supports it, phrased at honest scale — never inflate scope, duration, or seniority.
            - Where the candidate's experience is genuinely adjacent to a vacancy requirement, you may add \
            truthful umbrella phrasing next to the concrete technology (e.g. "container orchestration", \
            "JVM backend frameworks") — but never name a specific technology the candidate has not used.
            - Do not stuff the skills section with every vacancy keyword; recruiters discard resumes whose \
            skill list mirrors the job description 1:1. Only add what the resume or <candidate_skills> supports.
            - Only rewrite paragraphs that are genuinely worth strengthening for this vacancy. \
            Leave everything else untouched — do not return edits whose text is unchanged.
            - Write rewritten paragraphs in the same language as the resume.

            Suggestions:
            - Separately, list vacancy requirements covered by neither the resume nor <candidate_skills>: \
            keyword = the requirement as a short canonical term, text = one-sentence advice \
            ("The vacancy asks for X — add it if you actually have that experience."). Write them in the resume's language.

            Resume paragraphs are numbered with [N] markers; use those numbers as paragraphIndex.
            """;

    private final AdaptationProperties props;
    private volatile AnthropicClient client;

    public ClaudeClient(AdaptationProperties props) {
        this.props = props;
    }

    public LlmAdaptation adapt(String vacancyText, List<DocxService.DocxParagraph> paragraphs,
                               List<CandidateSkill> skills) {
        StringBuilder resume = new StringBuilder();
        for (DocxService.DocxParagraph p : paragraphs) {
            resume.append('[').append(p.index()).append("] ").append(p.text()).append('\n');
        }
        StringBuilder skillsBlock = new StringBuilder();
        if (skills.isEmpty()) {
            skillsBlock.append("(none)\n");
        } else {
            for (CandidateSkill skill : skills) {
                skillsBlock.append("- ").append(skill.getTerm());
                if (skill.getNote() != null && !skill.getNote().isBlank()) {
                    skillsBlock.append(" — ").append(skill.getNote());
                }
                skillsBlock.append('\n');
            }
        }
        String userMessage = "<vacancy>\n" + vacancyText + "\n</vacancy>\n\n"
                + "<candidate_skills>\n" + skillsBlock + "</candidate_skills>\n\n"
                + "<resume>\n" + resume + "</resume>";

        StructuredMessageCreateParams<LlmAdaptation> params = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(LlmAdaptation.class)
                .addUserMessage(userMessage)
                .build();

        return getClient().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Empty LLM response"));
    }

    private AnthropicClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build();
                }
            }
        }
        return client;
    }
}
