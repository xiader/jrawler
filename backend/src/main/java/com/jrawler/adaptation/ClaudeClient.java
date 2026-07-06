package com.jrawler.adaptation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaudeClient {

    private static final String SYSTEM_PROMPT = """
            You help a candidate adapt their resume to a specific vacancy.

            Rules:
            - You may only REPHRASE existing resume content: strengthen wording, mirror the vacancy's \
            terminology, shift emphasis. Never invent experience, technologies, employers, dates, or numbers \
            that are not already in the resume.
            - Only rewrite paragraphs that are genuinely worth strengthening for this vacancy. \
            Leave everything else untouched — do not return edits whose text is unchanged.
            - Write rewritten paragraphs in the same language as the resume.
            - Separately, list vacancy requirements that the resume does not mention, as short suggestions \
            the candidate can act on ("The vacancy asks for X — add it if you actually have that experience"). \
            Write suggestions in the resume's language.
            - Resume paragraphs are numbered with [N] markers; use those numbers as paragraphIndex.
            """;

    private final AdaptationProperties props;
    private volatile AnthropicClient client;

    public ClaudeClient(AdaptationProperties props) {
        this.props = props;
    }

    public LlmAdaptation adapt(String vacancyText, List<DocxService.DocxParagraph> paragraphs) {
        StringBuilder resume = new StringBuilder();
        for (DocxService.DocxParagraph p : paragraphs) {
            resume.append('[').append(p.index()).append("] ").append(p.text()).append('\n');
        }
        String userMessage = "<vacancy>\n" + vacancyText + "\n</vacancy>\n\n<resume>\n" + resume + "</resume>";

        StructuredMessageCreateParams<LlmAdaptation> params = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(LlmAdaptation.class)
                .addUserMessage(userMessage)
                .build();

        return getClient().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
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
