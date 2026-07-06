package com.jrawler.adapter.p0;

import com.jrawler.adapter.JobSearchAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.Source;
import com.jrawler.source.SourceRepository;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * NoFluffJobs search API (POST).
 * Endpoint: POST https://nofluffjobs.com/api/search/posting
 * Required query params: salaryCurrency, salaryPeriod, region
 * Body: {"page": N, "criteriaSearch": {"requirement": ["java"]}}
 * Old RSS feed (/feed.xml) redirects to HTML — unusable.
 */
@Component
public class NoFluffJobsAdapter implements JobSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(NoFluffJobsAdapter.class);
    private static final String SOURCE_ID = "nofluffjobs";
    private static final String BASE_URL = "https://nofluffjobs.com";
    private static final String SEARCH_URL = BASE_URL + "/api/search/posting"
            + "?limit=100&offset=0&salaryCurrency=PLN&salaryPeriod=month&region=pl";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final SourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public NoFluffJobsAdapter(OkHttpClient httpClient,
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
        return sourceRepository.findById(SOURCE_ID).map(Source::isEnabled).orElse(false);
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        List<String> keywords = criteria != null && !criteria.keywords().isEmpty()
                ? criteria.keywords()
                : List.of("java");

        List<RawVacancy> all = new ArrayList<>();
        // One request per primary keyword to cover all active profiles
        for (String keyword : keywords) {
            all.addAll(fetchForKeyword(keyword));
        }
        // Deduplicate by vacancy ID (same job may appear for multiple keywords)
        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RawVacancy::externalId,
                        v -> v,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
    }

    private List<RawVacancy> fetchForKeyword(String keyword) {
        try {
            String bodyJson = objectMapper.writeValueAsString(
                    java.util.Map.of("page", 1, "criteriaSearch",
                            java.util.Map.of("requirement", List.of(keyword))));

            Request request = new Request.Builder()
                    .url(SEARCH_URL)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (compatible; Jrawler/1.0)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[{}] HTTP {} for keyword '{}'", SOURCE_ID, response.code(), keyword);
                    return List.of();
                }
                return parsePostings(response.body().string(), keyword);
            }
        } catch (Exception e) {
            log.error("[{}] Fetch error for keyword '{}': {}", SOURCE_ID, keyword, e.getMessage());
            return List.of();
        }
    }

    private List<RawVacancy> parsePostings(String body, String keyword) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode postings = root.path("postings");
            if (!postings.isArray()) return result;

            for (JsonNode p : postings) {
                String id = p.path("id").asString(null);
                String title = p.path("title").asString(null);
                String company = p.path("name").asString(null);
                String urlSlug = p.path("url").asString(null);

                if (id == null || title == null || urlSlug == null) continue;

                String url = BASE_URL + "/pl/job/" + urlSlug;

                // Location: prefer first non-remote place
                JsonNode places = p.path("location").path("places");
                String location = null;
                if (places.isArray()) {
                    for (JsonNode place : places) {
                        String city = place.path("city").asString(null);
                        if (city != null && !city.equalsIgnoreCase("Remote")) {
                            location = city;
                            break;
                        }
                    }
                    if (location == null && !places.isEmpty()) {
                        location = places.get(0).path("city").asString(null);
                    }
                }
                boolean fullyRemote = p.path("fullyRemote").asBoolean(false);
                String remoteType = fullyRemote ? "remote" : "hybrid";

                // Salary
                String salary = null;
                JsonNode sal = p.path("salary");
                if (!sal.isMissingNode()) {
                    double from = sal.path("from").asDouble(0);
                    double to = sal.path("to").asDouble(0);
                    String currency = sal.path("currency").asString("PLN");
                    if (from > 0 || to > 0) {
                        salary = (int) from + " - " + (int) to + " " + currency;
                    }
                }

                // Description: tiles (skills/requirements)
                StringBuilder desc = new StringBuilder();
                JsonNode tiles = p.path("tiles").path("values");
                if (tiles.isArray()) {
                    for (JsonNode t : tiles) {
                        String val = t.path("value").asString(null);
                        if (val != null) desc.append(val).append(" ");
                    }
                }

                long postedMs = p.path("posted").asLong(0);
                Instant fetchedAt = postedMs > 0 ? Instant.ofEpochMilli(postedMs) : Instant.now();

                result.add(RawVacancy.builder(SOURCE_ID)
                        .externalId(id)
                        .title(title)
                        .companyName(company)
                        .url(url)
                        .location(location)
                        .description(desc.toString().trim())
                        .salaryRaw(salary)
                        .remoteTypeRaw(remoteType)
                        .fetchedAt(fetchedAt)
                        .build());
            }
            log.info("[{}] Fetched {} postings for keyword '{}'", SOURCE_ID, result.size(), keyword);
        } catch (Exception e) {
            log.error("[{}] Parse error for keyword '{}': {}", SOURCE_ID, keyword, e.getMessage());
        }
        return result;
    }
}