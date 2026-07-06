package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractRestApiAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dice.com public search API used by their own frontend:
 * GET https://job-search-api.svc.dhigroupinc.com/v1/dice/jobs/search
 * Requires an x-api-key header (the frontend's public key, overridable via env).
 * One paginated query per keyword, dedup by job id.
 */
@Component
public class DiceAdapter extends AbstractRestApiAdapter {

    private static final String SOURCE_ID = "dice";
    private static final String API_URL = "https://job-search-api.svc.dhigroupinc.com/v1/dice/jobs/search";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES_PER_KEYWORD = 3;
    private static final int MAX_KEYWORDS = 3;

    private final ObjectMapper objectMapper;
    private final String apiKey;

    public DiceAdapter(OkHttpClient httpClient,
                       SourceRepository sourceRepository,
                       ObjectMapper objectMapper,
                       @Value("${crawler.dice.api-key:1YAt0R9wBg4WfsF9VB2778F5CHLAPMVW3WAZcKd8}") String apiKey) {
        super(httpClient, sourceRepository);
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    protected String buildRequestUrl(SearchCriteria criteria) {
        String keyword = criteria == null || criteria.keywords().isEmpty()
                ? "java" : criteria.keywords().getFirst();
        return buildPageUrl(keyword, 1);
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        List<String> keywords = criteria == null || criteria.keywords().isEmpty()
                ? List.of("java")
                : criteria.keywords().stream().limit(MAX_KEYWORDS).toList();

        Map<String, RawVacancy> byExternalId = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (int page = 1; page <= MAX_PAGES_PER_KEYWORD; page++) {
                List<RawVacancy> pageItems = fetchPage(keyword, page);
                if (pageItems.isEmpty()) break;
                for (RawVacancy v : pageItems) {
                    byExternalId.putIfAbsent(v.externalId(), v);
                }
                if (pageItems.size() < PAGE_SIZE) break;
            }
        }

        log.info("[{}] Fetched {} unique vacancies for {} keywords",
                SOURCE_ID, byExternalId.size(), keywords.size());
        return new ArrayList<>(byExternalId.values());
    }

    private List<RawVacancy> fetchPage(String keyword, int page) {
        String url = buildPageUrl(keyword, page);
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("x-api-key", apiKey)
                    .header("User-Agent", "Mozilla/5.0 (compatible; Jrawler/1.0)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return parseResponse(response.body().string());
                }
                log.warn("[{}] HTTP {} for keyword '{}' page {}",
                        SOURCE_ID, response.code(), keyword, page);
            }
        } catch (Exception e) {
            log.warn("[{}] Fetch failed for keyword '{}' page {}: {}",
                    SOURCE_ID, keyword, page, e.getMessage());
        }
        return List.of();
    }

    private String buildPageUrl(String keyword, int page) {
        return API_URL
                + "?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&page=" + page
                + "&pageSize=" + PAGE_SIZE
                + "&filters.postedDate=SEVEN";
    }

    @Override
    protected List<RawVacancy> parseResponse(String responseBody) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) return result;

            for (JsonNode job : data) {
                String id = job.path("id").asString(null);
                String title = job.path("title").asString(null);
                String url = job.path("detailsPageUrl").asString(null);
                if (id == null || title == null || url == null) continue;

                String location = job.path("jobLocation").path("displayName").asString(null);
                String remoteType = "TRUE".equalsIgnoreCase(
                        job.path("workFromHomeAvailability").asString("")) || job.path("isRemote").asBoolean(false)
                        ? "remote" : "office";

                String posted = job.path("postedDate").asString(null);
                Instant fetchedAt = Instant.now();
                if (posted != null) {
                    try { fetchedAt = Instant.parse(posted); } catch (Exception _) { }
                }

                result.add(RawVacancy.builder(SOURCE_ID)
                        .externalId(id)
                        .title(title)
                        .companyName(job.path("companyName").asString(null))
                        .url(url)
                        .location(location)
                        .description(job.path("summary").asString(""))
                        .salaryRaw(job.path("salary").asString(null))
                        .remoteTypeRaw(remoteType)
                        .fetchedAt(fetchedAt)
                        .build());
            }
        } catch (Exception e) {
            log.error("[{}] Parse error: {}", SOURCE_ID, e.getMessage());
        }
        return result;
    }
}
