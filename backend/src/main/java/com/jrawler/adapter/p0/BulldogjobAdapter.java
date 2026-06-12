package com.jrawler.adapter.p0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrawler.adapter.JobSearchAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulldogjob.pl public GraphQL API: POST https://bulldogjob.pl/graphql
 * Query: searchJobs(page, perPage, filters: {skills: [...]}).
 * No free-text search — keyword maps to the skills filter.
 * One query per keyword, dedup by job id (id is a slug usable in the job URL).
 */
@Component
public class BulldogjobAdapter implements JobSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(BulldogjobAdapter.class);
    private static final String SOURCE_ID = "bulldogjob";
    private static final String GRAPHQL_URL = "https://bulldogjob.pl/graphql";
    private static final String JOB_BASE_URL = "https://bulldogjob.pl/companies/jobs/";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int PER_PAGE = 100;
    private static final int MAX_KEYWORDS = 3;

    private static final String QUERY = """
            query($page: Int, $perPage: Int, $skills: [String!]) {
              searchJobs(page: $page, perPage: $perPage, filters: {skills: $skills}) {
                totalCount
                nodes {
                  id
                  position
                  company { name }
                  city
                  remote
                  technologyTags
                  experienceLevel
                  publishedAt
                }
              }
            }""";

    private final OkHttpClient httpClient;
    private final SourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public BulldogjobAdapter(OkHttpClient httpClient,
                             SourceRepository sourceRepository,
                             ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public boolean isEnabled() {
        return sourceRepository.findById(SOURCE_ID).map(s -> s.isEnabled()).orElse(false);
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        List<String> keywords = criteria == null || criteria.keywords().isEmpty()
                ? List.of("java")
                : criteria.keywords().stream().limit(MAX_KEYWORDS).toList();

        Map<String, RawVacancy> byExternalId = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (RawVacancy v : fetchForSkill(keyword)) {
                byExternalId.putIfAbsent(v.externalId(), v);
            }
        }
        log.info("[{}] Fetched {} unique vacancies for {} keywords",
                SOURCE_ID, byExternalId.size(), keywords.size());
        return new ArrayList<>(byExternalId.values());
    }

    private List<RawVacancy> fetchForSkill(String skill) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "query", QUERY,
                    "variables", Map.of("page", 1, "perPage", PER_PAGE, "skills", List.of(skill))));

            Request request = new Request.Builder()
                    .url(GRAPHQL_URL)
                    .post(RequestBody.create(body, JSON))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (compatible; Jrawler/1.0)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[{}] HTTP {} for skill '{}'", SOURCE_ID, response.code(), skill);
                    return List.of();
                }
                return parseNodes(response.body().string(), skill);
            }
        } catch (Exception e) {
            log.error("[{}] Fetch error for skill '{}': {}", SOURCE_ID, skill, e.getMessage());
            return List.of();
        }
    }

    private List<RawVacancy> parseNodes(String body, String skill) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errors = root.path("errors");
            if (errors.isArray() && errors.size() > 0) {
                log.warn("[{}] GraphQL error for skill '{}': {}",
                        SOURCE_ID, skill, errors.get(0).path("message").asText());
                return result;
            }

            JsonNode nodes = root.path("data").path("searchJobs").path("nodes");
            if (!nodes.isArray()) return result;

            for (JsonNode job : nodes) {
                String id = job.path("id").asText(null);
                String title = job.path("position").asText(null);
                if (id == null || title == null) continue;

                StringBuilder desc = new StringBuilder();
                JsonNode tags = job.path("technologyTags");
                if (tags.isArray()) {
                    for (JsonNode tag : tags) {
                        desc.append(tag.asText("")).append(" ");
                    }
                }
                String experienceLevel = job.path("experienceLevel").asText("");
                if (!experienceLevel.isEmpty()) {
                    desc.append(experienceLevel);
                }

                Instant fetchedAt = Instant.now();
                String publishedAt = job.path("publishedAt").asText(null);
                if (publishedAt != null) {
                    try { fetchedAt = java.time.OffsetDateTime.parse(publishedAt).toInstant(); }
                    catch (Exception ignored) { }
                }

                result.add(RawVacancy.builder(SOURCE_ID)
                        .externalId(id)
                        .title(title)
                        .companyName(job.path("company").path("name").asText(null))
                        .url(JOB_BASE_URL + id)
                        .location(job.path("city").asText(null))
                        .description(desc.toString().trim())
                        .remoteTypeRaw(job.path("remote").asBoolean(false) ? "remote" : "office")
                        .fetchedAt(fetchedAt)
                        .build());
            }
            log.info("[{}] Fetched {} jobs for skill '{}'", SOURCE_ID, result.size(), skill);
        } catch (Exception e) {
            log.error("[{}] Parse error for skill '{}': {}", SOURCE_ID, skill, e.getMessage());
        }
        return result;
    }
}
