# Resume Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** New dashboard page where the user pastes a vacancy (URL or text), uploads a `.docx` resume, gets LLM-proposed paragraph rewrites, reviews a per-edit diff, and downloads the adapted `.docx`.

**Architecture:** Backend package `com.jrawler.adaptation` — POI parses docx paragraphs by index, Claude API (official Java SDK, structured output) returns `{paragraphIndex, newText}` edits + suggestions, state lives in Redis (TTL 1h), accepted edits are substituted back into the original docx. Frontend: one new route `/adapt` with a form → diff → download flow.

**Tech Stack:** Spring Boot 4.1 / Java 25, Apache POI (`poi-ooxml`), Anthropic Java SDK (`com.anthropic:anthropic-java`), Jsoup + Playwright (already in project), Redis via existing `RedisTemplate<String,String>`, React 19 + TypeScript + Tailwind 4 + TanStack Query.

Spec: `docs/superpowers/specs/2026-07-06-resume-adaptation-design.md`

## Global Constraints

- No PostgreSQL changes, no Flyway migrations. Ephemeral state in Redis only (`adaptation:{uuid}`, TTL 1 hour).
- Config via env vars with defaults in `application.yml`: `ANTHROPIC_API_KEY` (no default), `ADAPTATION_MODEL` (default `claude-opus-4-8`).
- Missing API key → endpoints return 503; rest of the app unaffected.
- No new frontend npm dependencies (word diff is written in-house).
- Backend error responses use Spring `ProblemDetail` (existing `GlobalExceptionHandler` pattern); services throw `ResponseStatusException`.
- Controllers live in the feature package (`com.jrawler.adaptation`), matching existing convention (`SourceController` lives in `source/`, etc.) — this supersedes the spec's "in `api/`" note.
- Resume upload limit 2 MB.
- All backend tests run with `mvn test` from `backend/`. Frontend is verified with `npm run build` and `npm run lint` from `frontend/`.

---

### Task 1: Backend scaffolding — dependencies, config, error handling

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/jrawler/adaptation/AdaptationProperties.java`
- Create: `backend/src/main/java/com/jrawler/adaptation/AdaptationConfig.java`
- Modify: `backend/src/main/java/com/jrawler/api/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `AdaptationProperties` record — `apiKey(): String`, `model(): String`, `fetchTimeoutSeconds(): int` — injected as a Spring bean into later tasks. `ResponseStatusException` thrown anywhere now maps to a `ProblemDetail` with the exception's status and reason.

- [ ] **Step 1: Add dependencies to `backend/pom.xml`**

In `<properties>` add:

```xml
<poi.version>5.4.1</poi.version>
<anthropic.version>2.34.0</anthropic.version>
```

In `<dependencies>` (after the Playwright dependency) add:

```xml
<!-- DOCX парсинг/редактирование -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>${poi.version}</version>
</dependency>

<!-- Anthropic Claude API -->
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>${anthropic.version}</version>
</dependency>
```

- [ ] **Step 2: Add config to `application.yml`**

Under the existing `spring:` key add (sibling of `data:`):

```yaml
  servlet:
    multipart:
      max-file-size: 2MB
      max-request-size: 3MB
```

At the top level (after the `crawler:` block) add:

```yaml
adaptation:
  api-key: ${ANTHROPIC_API_KEY:}
  model: ${ADAPTATION_MODEL:claude-opus-4-8}
  fetch-timeout-seconds: 30
```

- [ ] **Step 3: Create `AdaptationProperties.java`**

```java
package com.jrawler.adaptation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adaptation")
public record AdaptationProperties(String apiKey, String model, int fetchTimeoutSeconds) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
```

- [ ] **Step 4: Create `AdaptationConfig.java`**

```java
package com.jrawler.adaptation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdaptationProperties.class)
public class AdaptationConfig {
}
```

- [ ] **Step 5: Extend `GlobalExceptionHandler`**

Add two handlers to `backend/src/main/java/com/jrawler/api/GlobalExceptionHandler.java` (above the generic `Exception` handler — Spring picks the most specific, but keep them together for readability). New imports: `org.springframework.web.server.ResponseStatusException`, `org.springframework.web.multipart.MaxUploadSizeExceededException`.

```java
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(),
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "File too large (max 2 MB)");
    }
```

- [ ] **Step 6: Verify it compiles**

Run from `backend/`: `mvn compile -q`
Expected: BUILD SUCCESS (first run downloads POI + Anthropic SDK jars).

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/jrawler/adaptation backend/src/main/java/com/jrawler/api/GlobalExceptionHandler.java
git commit -m "feat(adaptation): add deps, config properties, error handlers"
```

---

### Task 2: DocxService — parse and rewrite docx paragraphs

**Files:**
- Create: `backend/src/main/java/com/jrawler/adaptation/DocxService.java`
- Test: `backend/src/test/java/com/jrawler/adaptation/DocxServiceTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `record DocxService.DocxParagraph(int index, String text)` — `index` is the position in `XWPFDocument.getParagraphs()` (empty paragraphs keep their slot but are not returned).
  - `List<DocxParagraph> extractParagraphs(byte[] docxBytes)` — throws `ResponseStatusException(400)` if the bytes are not a readable docx.
  - `byte[] applyEdits(byte[] docxBytes, Map<Integer, String> editsByIndex)` — returns a new docx with each indexed paragraph's text replaced, preserving the formatting of the paragraph's first run; indexes out of range are silently skipped (validated upstream).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/jrawler/adaptation/DocxServiceTest.java`. No fixture file — the test builds a docx in memory with POI.

```java
package com.jrawler.adaptation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxServiceTest {

    private final DocxService docxService = new DocxService();

    private byte[] sampleDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p0 = doc.createParagraph();
            XWPFRun r0 = p0.createRun();
            r0.setText("Ivan Ivanov");
            r0.setBold(true);

            doc.createParagraph(); // index 1: empty

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("Built microservices with Spring Boot");

            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsNonEmptyParagraphsWithOriginalIndexes() throws Exception {
        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(sampleDocx());

        assertThat(paragraphs).containsExactly(
                new DocxService.DocxParagraph(0, "Ivan Ivanov"),
                new DocxService.DocxParagraph(2, "Built microservices with Spring Boot"));
    }

    @Test
    void appliesEditsAndPreservesFirstRunFormatting() throws Exception {
        byte[] result = docxService.applyEdits(sampleDocx(),
                Map.of(2, "Designed and built Kafka-based microservices with Spring Boot"));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("Ivan Ivanov");
            assertThat(doc.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(doc.getParagraphs().get(2).getText())
                    .isEqualTo("Designed and built Kafka-based microservices with Spring Boot");
        }
    }

    @Test
    void roundTripAfterEditYieldsUpdatedParagraphList() throws Exception {
        byte[] result = docxService.applyEdits(sampleDocx(), Map.of(0, "IVAN IVANOV"));

        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(result);
        assertThat(paragraphs.get(0)).isEqualTo(new DocxService.DocxParagraph(0, "IVAN IVANOV"));
    }

    @Test
    void rejectsNonDocxBytes() {
        assertThatThrownBy(() -> docxService.extractParagraphs("not a docx".getBytes()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run from `backend/`: `mvn test -q -Dtest=DocxServiceTest`
Expected: COMPILATION ERROR — `DocxService` does not exist.

- [ ] **Step 3: Implement `DocxService.java`**

```java
package com.jrawler.adaptation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocxService {

    public record DocxParagraph(int index, String text) {}

    public List<DocxParagraph> extractParagraphs(byte[] docxBytes) {
        try (XWPFDocument doc = openDocx(docxBytes)) {
            List<DocxParagraph> result = new ArrayList<>();
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText();
                if (text != null && !text.isBlank()) {
                    result.add(new DocxParagraph(i, text));
                }
            }
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid .docx file");
        }
    }

    public byte[] applyEdits(byte[] docxBytes, Map<Integer, String> editsByIndex) {
        try (XWPFDocument doc = openDocx(docxBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (Map.Entry<Integer, String> edit : editsByIndex.entrySet()) {
                int index = edit.getKey();
                if (index < 0 || index >= paragraphs.size()) {
                    continue;
                }
                replaceText(paragraphs.get(index), edit.getValue());
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid .docx file");
        }
    }

    // Keep the first run (and its formatting), drop the rest, set the new text on it.
    private void replaceText(XWPFParagraph paragraph, String newText) {
        for (int i = paragraph.getRuns().size() - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        run.setText(newText, 0);
    }

    private XWPFDocument openDocx(byte[] docxBytes) throws IOException {
        try {
            return new XWPFDocument(new ByteArrayInputStream(docxBytes));
        } catch (Exception e) {
            throw new IOException("Not a docx", e);
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `mvn test -q -Dtest=DocxServiceTest`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/jrawler/adaptation/DocxService.java backend/src/test/java/com/jrawler/adaptation/DocxServiceTest.java
git commit -m "feat(adaptation): DocxService — paragraph extraction and edit application"
```

---

### Task 3: AdaptationStore — Redis storage for in-flight adaptations

**Files:**
- Create: `backend/src/main/java/com/jrawler/adaptation/AdaptationStore.java`
- Test: `backend/src/test/java/com/jrawler/adaptation/AdaptationStoreTest.java`

**Interfaces:**
- Consumes: existing beans `RedisTemplate<String, String>` (from `RedisConfig`) and `ObjectMapper` (Spring Boot auto-config).
- Produces:
  - `record AdaptationStore.StoredAdaptation(String docxBase64, List<EditDto> edits)`
  - `record EditDto(int paragraphIndex, String original, String proposed)` — **top-level class** `com.jrawler.adaptation.EditDto`, reused by the API response in Task 4.
  - `String save(byte[] docxBytes, List<EditDto> edits)` — returns the generated adaptation id (UUID string), stores under key `adaptation:{id}` with TTL 1 hour.
  - `Optional<StoredAdaptation> find(String id)`.

- [ ] **Step 1: Create `EditDto.java`**

```java
package com.jrawler.adaptation;

public record EditDto(int paragraphIndex, String original, String proposed) {}
```

- [ ] **Step 2: Write the failing test**

`backend/src/test/java/com/jrawler/adaptation/AdaptationStoreTest.java` — mocks `RedisTemplate` so no Redis instance is required.

```java
package com.jrawler.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationStoreTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private AdaptationStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new AdaptationStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void savesWithTtlAndRoundTrips() {
        byte[] docx = {1, 2, 3};
        List<EditDto> edits = List.of(new EditDto(2, "old text", "new text"));

        String id = store.save(docx, edits);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("adaptation:" + id), json.capture(), eq(Duration.ofHours(1)));

        when(valueOps.get("adaptation:" + id)).thenReturn(json.getValue());
        Optional<AdaptationStore.StoredAdaptation> loaded = store.find(id);

        assertThat(loaded).isPresent();
        assertThat(Base64.getDecoder().decode(loaded.get().docxBase64())).isEqualTo(docx);
        assertThat(loaded.get().edits()).containsExactly(new EditDto(2, "old text", "new text"));
    }

    @Test
    void findReturnsEmptyWhenKeyMissing() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertThat(store.find("nope")).isEmpty();
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `mvn test -q -Dtest=AdaptationStoreTest`
Expected: COMPILATION ERROR — `AdaptationStore` does not exist.

- [ ] **Step 4: Implement `AdaptationStore.java`**

```java
package com.jrawler.adaptation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdaptationStore {

    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public record StoredAdaptation(String docxBase64, List<EditDto> edits) {}

    public String save(byte[] docxBytes, List<EditDto> edits) {
        String id = UUID.randomUUID().toString();
        StoredAdaptation stored = new StoredAdaptation(
                Base64.getEncoder().encodeToString(docxBytes), edits);
        try {
            redisTemplate.opsForValue().set(key(id), objectMapper.writeValueAsString(stored), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize adaptation", e);
        }
        return id;
    }

    public Optional<StoredAdaptation> find(String id) {
        String json = redisTemplate.opsForValue().get(key(id));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, StoredAdaptation.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String key(String id) {
        return "adaptation:" + id;
    }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `mvn test -q -Dtest=AdaptationStoreTest`
Expected: 2 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/jrawler/adaptation/EditDto.java backend/src/main/java/com/jrawler/adaptation/AdaptationStore.java backend/src/test/java/com/jrawler/adaptation/AdaptationStoreTest.java
git commit -m "feat(adaptation): Redis store for in-flight adaptations (TTL 1h)"
```

---

### Task 4: ClaudeClient + AdaptationService — LLM call, validation, orchestration

**Files:**
- Create: `backend/src/main/java/com/jrawler/adaptation/LlmAdaptation.java`
- Create: `backend/src/main/java/com/jrawler/adaptation/ClaudeClient.java`
- Create: `backend/src/main/java/com/jrawler/adaptation/AdaptationService.java`
- Test: `backend/src/test/java/com/jrawler/adaptation/AdaptationServiceTest.java`

**Interfaces:**
- Consumes: `DocxService` (Task 2), `AdaptationStore` + `EditDto` (Task 3), `AdaptationProperties` (Task 1).
- Produces:
  - `record LlmAdaptation(List<LlmEdit> edits, List<String> suggestions)` with nested `record LlmEdit(int paragraphIndex, String newText)` — the structured-output schema for the LLM.
  - `ClaudeClient.adapt(String vacancyText, List<DocxService.DocxParagraph> paragraphs): LlmAdaptation`.
  - `record AdaptationService.AdaptationResponse(String adaptationId, List<EditDto> edits, List<String> suggestions)`.
  - `AdaptationService.createAdaptation(byte[] docxBytes, String vacancyText): AdaptationResponse` — 503 if API key missing, 400 if docx has no paragraphs, 502 if LLM call fails.
  - `AdaptationService.buildDocx(String id, List<Integer> acceptedIndexes): byte[]` — 410 if the adaptation expired.

- [ ] **Step 1: Create `LlmAdaptation.java`**

```java
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
```

- [ ] **Step 2: Write the failing test for `AdaptationService`**

`ClaudeClient` is mocked — no live API calls in tests.

```java
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
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `mvn test -q -Dtest=AdaptationServiceTest`
Expected: COMPILATION ERROR — `ClaudeClient`, `AdaptationService` do not exist.

- [ ] **Step 4: Implement `ClaudeClient.java`**

The Anthropic client is built lazily so the app boots without an API key. Structured output via the SDK's typed `outputConfig(Class)`.

```java
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
```

Note for the implementer: if the SDK's builder method names differ in the installed version (e.g. `.model(String)` overload), compile and fix from the compiler error — do not guess alternative APIs; the shape above follows the official SDK docs for `anthropic-java` 2.x structured outputs.

- [ ] **Step 5: Implement `AdaptationService.java`**

```java
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

    public record AdaptationResponse(String adaptationId, List<EditDto> edits, List<String> suggestions) {}

    public AdaptationResponse createAdaptation(byte[] docxBytes, String vacancyText) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adaptation is not configured: set ANTHROPIC_API_KEY");
        }
        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(docxBytes);
        if (paragraphs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume contains no text");
        }

        LlmAdaptation llm;
        try {
            llm = claudeClient.adapt(vacancyText, paragraphs);
        } catch (Exception e) {
            log.error("LLM adaptation call failed: {}", e.getMessage());
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

        List<String> suggestions = llm.suggestions() == null ? List.of() : llm.suggestions();
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
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `mvn test -q -Dtest=AdaptationServiceTest`
Expected: 6 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/jrawler/adaptation/LlmAdaptation.java backend/src/main/java/com/jrawler/adaptation/ClaudeClient.java backend/src/main/java/com/jrawler/adaptation/AdaptationService.java backend/src/test/java/com/jrawler/adaptation/AdaptationServiceTest.java
git commit -m "feat(adaptation): ClaudeClient with structured output + AdaptationService validation"
```

---

### Task 5: VacancyTextExtractor — URL to clean text

**Files:**
- Create: `backend/src/main/java/com/jrawler/adaptation/VacancyTextExtractor.java`
- Test: `backend/src/test/java/com/jrawler/adaptation/VacancyTextExtractorTest.java`

**Interfaces:**
- Consumes: existing `OkHttpClient` bean (from `OkHttpConfig`), `AdaptationProperties` (Task 1).
- Produces: `String extract(String url)` — fetches with Jsoup-over-OkHttp; if the extracted text is shorter than 300 chars, retries with Playwright (JS rendering); if still short or the fetch fails, throws `ResponseStatusException(422)`. Static helper `String extractText(Document doc)` is package-private for testing.

- [ ] **Step 1: Write the failing test**

Only the pure text-extraction logic is unit-tested; network paths are covered by manual E2E (Task 8).

```java
package com.jrawler.adaptation;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyTextExtractorTest {

    @Test
    void stripsScriptsStylesAndChrome() {
        String html = """
                <html><head><style>body{color:red}</style></head>
                <body>
                <nav>Home | Jobs | About</nav>
                <script>console.log('tracking')</script>
                <h1>Senior Java Developer</h1>
                <p>We need Spring Boot and Kafka experience.</p>
                <footer>© 2026 Acme</footer>
                </body></html>
                """;

        String text = VacancyTextExtractor.extractText(Jsoup.parse(html));

        assertThat(text).contains("Senior Java Developer");
        assertThat(text).contains("Spring Boot and Kafka");
        assertThat(text).doesNotContain("console.log");
        assertThat(text).doesNotContain("color:red");
        assertThat(text).doesNotContain("Home | Jobs");
        assertThat(text).doesNotContain("© 2026");
    }

    @Test
    void returnsEmptyForBodylessDocument() {
        String text = VacancyTextExtractor.extractText(Jsoup.parse(""));
        assertThat(text).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `mvn test -q -Dtest=VacancyTextExtractorTest`
Expected: COMPILATION ERROR — `VacancyTextExtractor` does not exist.

- [ ] **Step 3: Implement `VacancyTextExtractor.java`**

Fetch helpers follow the pattern in `AbstractWebCrawlerAdapter` (`fetchWithJsoup` / `fetchWithPlaywright`); they are private there, so the ~25 lines are duplicated rather than refactoring the adapter hierarchy.

```java
package com.jrawler.adaptation;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VacancyTextExtractor {

    private static final int MIN_TEXT_LENGTH = 300;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Logger log = LoggerFactory.getLogger(VacancyTextExtractor.class);

    private final OkHttpClient httpClient;

    public VacancyTextExtractor(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String extract(String url) {
        String text;
        try {
            text = extractText(fetchWithJsoup(url));
            if (text.length() < MIN_TEXT_LENGTH) {
                log.info("Static fetch of {} too short ({} chars), retrying with Playwright", url, text.length());
                text = extractText(fetchWithPlaywright(url));
            }
        } catch (Exception e) {
            log.warn("Vacancy fetch failed for {}: {}", url, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not fetch the vacancy page — paste the text manually");
        }
        if (text.length() < MIN_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The page contains too little text — paste the vacancy manually");
        }
        return text;
    }

    static String extractText(Document doc) {
        doc.select("script, style, noscript, svg, nav, footer, header, iframe").remove();
        return doc.body() != null ? doc.body().text().strip() : "";
    }

    private Document fetchWithJsoup(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return Jsoup.parse(response.body().string(), url);
        }
    }

    private Document fetchWithPlaywright(String url) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
            try (Browser browser = playwright.chromium().launch(opts)) {
                Page page = browser.newPage();
                page.setExtraHTTPHeaders(java.util.Map.of("User-Agent", USER_AGENT));
                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return Jsoup.parse(page.content(), url);
            }
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `mvn test -q -Dtest=VacancyTextExtractorTest`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/jrawler/adaptation/VacancyTextExtractor.java backend/src/test/java/com/jrawler/adaptation/VacancyTextExtractorTest.java
git commit -m "feat(adaptation): vacancy text extractor with Playwright fallback"
```

---

### Task 6: ResumeAdaptationController — REST endpoints

**Files:**
- Create: `backend/src/main/java/com/jrawler/adaptation/ResumeAdaptationController.java`
- Test: `backend/src/test/java/com/jrawler/adaptation/ResumeAdaptationControllerTest.java`

**Interfaces:**
- Consumes: `AdaptationService` (Task 4), `VacancyTextExtractor` (Task 5).
- Produces the HTTP API the frontend (Task 7) calls:
  - `POST /api/v1/resume-adaptation/fetch-vacancy` body `{"url": "..."}` → `{"text": "..."}`
  - `POST /api/v1/resume-adaptation` multipart (`resume` file, `vacancyText` param) → `{"adaptationId", "edits": [{"paragraphIndex","original","proposed"}], "suggestions": [...]}`
  - `POST /api/v1/resume-adaptation/{id}/download` body `{"acceptedIndexes": [2, 5]}` → docx bytes, `Content-Disposition: attachment; filename="resume-adapted.docx"`

- [ ] **Step 1: Write the failing MockMvc test**

Spring Boot 4 uses `@MockitoBean` (not the removed `@MockBean`).

```java
package com.jrawler.adaptation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeAdaptationController.class)
class ResumeAdaptationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdaptationService adaptationService;

    @MockitoBean
    private VacancyTextExtractor vacancyTextExtractor;

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
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `mvn test -q -Dtest=ResumeAdaptationControllerTest`
Expected: COMPILATION ERROR — `ResumeAdaptationController` does not exist.

- [ ] **Step 3: Implement `ResumeAdaptationController.java`**

```java
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
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `mvn test -q -Dtest=ResumeAdaptationControllerTest`
Expected: 5 tests PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `mvn test -q`
Expected: all tests PASS (DocxServiceTest, AdaptationStoreTest, AdaptationServiceTest, VacancyTextExtractorTest, ResumeAdaptationControllerTest).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/jrawler/adaptation/ResumeAdaptationController.java backend/src/test/java/com/jrawler/adaptation/ResumeAdaptationControllerTest.java
git commit -m "feat(adaptation): REST endpoints for resume adaptation"
```

---

### Task 7: Frontend — API client and word-diff helper

**Files:**
- Create: `frontend/src/api/adaptation.ts`
- Create: `frontend/src/lib/wordDiff.ts`

**Interfaces:**
- Consumes: existing axios instance `frontend/src/api/client.ts` (baseURL `/api/v1`).
- Produces (used by Task 8):
  - `interface AdaptationEdit { paragraphIndex: number; original: string; proposed: string }`
  - `interface AdaptationResponse { adaptationId: string; edits: AdaptationEdit[]; suggestions: string[] }`
  - `fetchVacancyText(url: string): Promise<string>`
  - `createAdaptation(resume: File, vacancyText: string): Promise<AdaptationResponse>`
  - `downloadAdapted(id: string, acceptedIndexes: number[]): Promise<Blob>`
  - `interface DiffPart { text: string; changed: boolean }`
  - `diffWords(original: string, proposed: string): { originalParts: DiffPart[]; proposedParts: DiffPart[] }`

- [ ] **Step 1: Create `frontend/src/api/adaptation.ts`**

```typescript
import api from './client';

export interface AdaptationEdit {
  paragraphIndex: number;
  original: string;
  proposed: string;
}

export interface AdaptationResponse {
  adaptationId: string;
  edits: AdaptationEdit[];
  suggestions: string[];
}

export const fetchVacancyText = async (url: string): Promise<string> => {
  const { data } = await api.post<{ text: string }>('/resume-adaptation/fetch-vacancy', { url });
  return data.text;
};

export const createAdaptation = async (
  resume: File,
  vacancyText: string,
): Promise<AdaptationResponse> => {
  const form = new FormData();
  form.append('resume', resume);
  form.append('vacancyText', vacancyText);
  const { data } = await api.post<AdaptationResponse>('/resume-adaptation', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
};

export const downloadAdapted = async (id: string, acceptedIndexes: number[]): Promise<Blob> => {
  const { data } = await api.post<Blob>(
    `/resume-adaptation/${id}/download`,
    { acceptedIndexes },
    { responseType: 'blob' },
  );
  return data;
};
```

- [ ] **Step 2: Create `frontend/src/lib/wordDiff.ts`**

Word-level LCS diff, no dependency. Consecutive parts of the same kind are merged so rendering produces few spans.

```typescript
export interface DiffPart {
  text: string;
  changed: boolean;
}

interface SplitDiff {
  originalParts: DiffPart[];
  proposedParts: DiffPart[];
}

/** Word-level diff via longest common subsequence. */
export function diffWords(original: string, proposed: string): SplitDiff {
  const a = original.split(/\s+/).filter(Boolean);
  const b = proposed.split(/\s+/).filter(Boolean);

  // LCS table
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () =>
    new Array<number>(b.length + 1).fill(0),
  );
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const originalParts: DiffPart[] = [];
  const proposedParts: DiffPart[] = [];
  const push = (parts: DiffPart[], word: string, changed: boolean) => {
    const last = parts[parts.length - 1];
    if (last && last.changed === changed) {
      last.text += ` ${word}`;
    } else {
      parts.push({ text: word, changed });
    }
  };

  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      push(originalParts, a[i], false);
      push(proposedParts, b[j], false);
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      push(originalParts, a[i], true);
      i++;
    } else {
      push(proposedParts, b[j], true);
      j++;
    }
  }
  while (i < a.length) push(originalParts, a[i++], true);
  while (j < b.length) push(proposedParts, b[j++], true);

  return { originalParts, proposedParts };
}
```

- [ ] **Step 3: Verify build and lint**

Run from `frontend/`: `npm run build && npm run lint`
Expected: both succeed (files compile; unused-export warnings are acceptable until Task 8 consumes them — if eslint flags unused exports as errors, proceed to Task 8 before committing lint-clean state; otherwise commit now).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/adaptation.ts frontend/src/lib/wordDiff.ts
git commit -m "feat(adaptation): frontend API client and word-diff helper"
```

---

### Task 8: Frontend — Adapt page, diff view, route, nav

**Files:**
- Create: `frontend/src/pages/Adapt.tsx`
- Create: `frontend/src/components/AdaptationDiff.tsx`
- Modify: `frontend/src/App.tsx` (add route)
- Modify: `frontend/src/components/Layout.tsx` (add nav item)

**Interfaces:**
- Consumes: everything from Task 7.
- Produces: route `/adapt` reachable from the nav bar.

- [ ] **Step 1: Create `frontend/src/components/AdaptationDiff.tsx`**

```tsx
import { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { AdaptationResponse } from '../api/adaptation';
import { downloadAdapted } from '../api/adaptation';
import { diffWords, type DiffPart } from '../lib/wordDiff';

interface Props {
  adaptation: AdaptationResponse;
  onReset: () => void;
}

function DiffText({ parts, changedClass }: { parts: DiffPart[]; changedClass: string }) {
  return (
    <p className="text-sm leading-relaxed whitespace-pre-wrap">
      {parts.map((part, i) => (
        <span key={i} className={part.changed ? changedClass : undefined}>
          {i > 0 ? ' ' : ''}
          {part.text}
        </span>
      ))}
    </p>
  );
}

export default function AdaptationDiff({ adaptation, onReset }: Props) {
  const [accepted, setAccepted] = useState<Set<number>>(
    () => new Set(adaptation.edits.map(e => e.paragraphIndex)),
  );

  const diffs = useMemo(
    () => adaptation.edits.map(e => ({ edit: e, diff: diffWords(e.original, e.proposed) })),
    [adaptation.edits],
  );

  const downloadMutation = useMutation({
    mutationFn: () => downloadAdapted(adaptation.adaptationId, [...accepted]),
    onSuccess: blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'resume-adapted.docx';
      a.click();
      URL.revokeObjectURL(url);
    },
  });

  const toggle = (index: number) => {
    setAccepted(prev => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">
          Правки <span className="text-gray-400 text-sm">принято {accepted.size} из {adaptation.edits.length}</span>
        </h2>
        <div className="flex gap-2">
          <button
            onClick={onReset}
            className="px-4 py-1.5 text-sm rounded border border-gray-700 text-gray-300 hover:bg-gray-800 transition-colors"
          >
            Начать заново
          </button>
          <button
            onClick={() => downloadMutation.mutate()}
            disabled={downloadMutation.isPending}
            className="px-4 py-1.5 text-sm rounded bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-medium transition-colors"
          >
            {downloadMutation.isPending ? 'Готовим…' : 'Скачать docx'}
          </button>
        </div>
      </div>

      {downloadMutation.isError && (
        <p className="text-sm text-red-400">
          Не удалось скачать: {(downloadMutation.error as Error).message}. Возможно, сессия истекла — начни заново.
        </p>
      )}

      {adaptation.edits.length === 0 && (
        <p className="text-gray-400">LLM не предложил правок — резюме уже хорошо подходит под вакансию.</p>
      )}

      <div className="space-y-4">
        {diffs.map(({ edit, diff }) => (
          <label
            key={edit.paragraphIndex}
            className={`block rounded-lg border p-4 cursor-pointer transition-colors ${
              accepted.has(edit.paragraphIndex)
                ? 'border-blue-600 bg-gray-900'
                : 'border-gray-800 bg-gray-900/50 opacity-60'
            }`}
          >
            <div className="flex items-start gap-3">
              <input
                type="checkbox"
                checked={accepted.has(edit.paragraphIndex)}
                onChange={() => toggle(edit.paragraphIndex)}
                className="mt-1 accent-blue-600"
              />
              <div className="grid md:grid-cols-2 gap-4 flex-1">
                <div>
                  <div className="text-xs uppercase text-gray-500 mb-1">Было</div>
                  <DiffText parts={diff.originalParts} changedClass="bg-red-900/60 text-red-200 rounded px-0.5" />
                </div>
                <div>
                  <div className="text-xs uppercase text-gray-500 mb-1">Стало</div>
                  <DiffText parts={diff.proposedParts} changedClass="bg-green-900/60 text-green-200 rounded px-0.5" />
                </div>
              </div>
            </div>
          </label>
        ))}
      </div>

      {adaptation.suggestions.length > 0 && (
        <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h3 className="text-sm font-semibold text-gray-300 mb-2">Подсказки — чего не хватает в резюме</h3>
          <ul className="list-disc list-inside space-y-1 text-sm text-gray-400">
            {adaptation.suggestions.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create `frontend/src/pages/Adapt.tsx`**

```tsx
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import {
  createAdaptation,
  fetchVacancyText,
  type AdaptationResponse,
} from '../api/adaptation';
import AdaptationDiff from '../components/AdaptationDiff';

function errorDetail(error: unknown): string {
  if (isAxiosError(error) && error.response?.data?.detail) {
    return error.response.data.detail as string;
  }
  return error instanceof Error ? error.message : 'Unknown error';
}

export default function Adapt() {
  const [url, setUrl] = useState('');
  const [vacancyText, setVacancyText] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [adaptation, setAdaptation] = useState<AdaptationResponse | null>(null);

  const fetchMutation = useMutation({
    mutationFn: () => fetchVacancyText(url),
    onSuccess: text => setVacancyText(text),
  });

  const adaptMutation = useMutation({
    mutationFn: () => createAdaptation(file!, vacancyText),
    onSuccess: setAdaptation,
  });

  const reset = () => {
    setAdaptation(null);
    adaptMutation.reset();
  };

  if (adaptation) {
    return <AdaptationDiff adaptation={adaptation} onReset={reset} />;
  }

  return (
    <div className="max-w-3xl space-y-6">
      <h1 className="text-xl font-semibold">Адаптация резюме</h1>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Ссылка на вакансию</label>
        <div className="flex gap-2">
          <input
            type="url"
            value={url}
            onChange={e => setUrl(e.target.value)}
            placeholder="https://..."
            className="flex-1 bg-gray-900 border border-gray-800 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-600"
          />
          <button
            onClick={() => fetchMutation.mutate()}
            disabled={!url || fetchMutation.isPending}
            className="px-4 py-2 text-sm rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-50 transition-colors"
          >
            {fetchMutation.isPending ? 'Загружаем…' : 'Загрузить'}
          </button>
        </div>
        {fetchMutation.isError && (
          <p className="text-sm text-yellow-400">
            {errorDetail(fetchMutation.error)}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Текст вакансии</label>
        <textarea
          value={vacancyText}
          onChange={e => setVacancyText(e.target.value)}
          rows={10}
          placeholder="Вставь описание вакансии или загрузи по ссылке выше"
          className="w-full bg-gray-900 border border-gray-800 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-600"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm text-gray-400">Резюме (.docx)</label>
        <input
          type="file"
          accept=".docx"
          onChange={e => setFile(e.target.files?.[0] ?? null)}
          className="block text-sm text-gray-400 file:mr-3 file:px-4 file:py-1.5 file:rounded file:border-0 file:bg-gray-800 file:text-gray-200 file:text-sm hover:file:bg-gray-700"
        />
      </div>

      <div className="space-y-2">
        <button
          onClick={() => adaptMutation.mutate()}
          disabled={!file || !vacancyText.trim() || adaptMutation.isPending}
          className="px-6 py-2 rounded bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-medium transition-colors"
        >
          {adaptMutation.isPending ? 'Адаптируем… (~30 сек)' : 'Адаптировать резюме'}
        </button>
        {adaptMutation.isError && (
          <p className="text-sm text-red-400">{errorDetail(adaptMutation.error)}</p>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Add the route in `frontend/src/App.tsx`**

Add the import and route:

```tsx
import Adapt from './pages/Adapt';
```

Inside `<Route element={<Layout />}>` after the `/settings` route:

```tsx
            <Route path="/adapt" element={<Adapt />} />
```

- [ ] **Step 4: Add the nav item in `frontend/src/components/Layout.tsx`**

In the `nav` array, after `Profiles`:

```tsx
  { to: '/adapt', label: 'Adapt CV' },
```

- [ ] **Step 5: Verify build and lint**

Run from `frontend/`: `npm run build && npm run lint`
Expected: both succeed with no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Adapt.tsx frontend/src/components/AdaptationDiff.tsx frontend/src/App.tsx frontend/src/components/Layout.tsx
git commit -m "feat(adaptation): adapt page with diff review and docx download"
```

---

### Task 9: End-to-end verification

**Files:**
- None created; manual verification.

**Interfaces:**
- Consumes: the full stack from Tasks 1–8.

- [ ] **Step 1: Start the stack**

Use the `running-jrawler` skill (project skill) to start infra + backend + frontend. `ANTHROPIC_API_KEY` must be set in the environment (or `.env`) for the backend.

- [ ] **Step 2: Verify the 503 path first (optional but cheap)**

With `ANTHROPIC_API_KEY` unset, POST to the endpoint:

```bash
curl -s -X POST http://localhost:8080/api/v1/resume-adaptation -F "resume=@somefile.docx" -F "vacancyText=test" | head -c 300
```

Expected: JSON ProblemDetail with status 503 and the "set ANTHROPIC_API_KEY" message. Restart backend with the key set afterwards.

- [ ] **Step 3: Verify the happy path in the browser**

1. Open http://localhost:5173/adapt.
2. Paste a real vacancy URL (e.g. a justjoin.it offer), click «Загрузить» — textarea fills with the vacancy text. If the site blocks fetching, paste the text manually — that's the designed fallback.
3. Upload a real `.docx` resume, click «Адаптировать резюме».
4. Expect a diff page: edit cards with word-level highlights, all checkboxes on, suggestions list at the bottom.
5. Uncheck one edit, click «Скачать docx».
6. Open the downloaded `resume-adapted.docx` in Word/LibreOffice: accepted edits applied, unchecked paragraph unchanged, document formatting (headings, bold, layout) intact.

- [ ] **Step 4: Verify expiry path**

Delete the Redis key (`docker exec -it <redis-container> redis-cli KEYS 'adaptation:*'` then `DEL <key>`), click «Скачать docx» again — expect the "сессия истекла" error message on the diff page.

- [ ] **Step 5: Full test suites one last time**

```bash
cd backend && mvn test -q
cd ../frontend && npm run build && npm run lint
```

Expected: everything green.

- [ ] **Step 6: Final commit (if any fixes were made during E2E)**

```bash
git add -A
git commit -m "fix(adaptation): E2E verification fixes"
```
