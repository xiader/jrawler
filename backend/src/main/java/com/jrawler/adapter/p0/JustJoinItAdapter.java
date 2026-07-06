package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractRestApiAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JustJoin.it API v2: https://api.justjoin.it/v2/user-panel/offers
 * Requires "Version: 2" header. Paginated (perPage max 100), multiple
 * keywords[] params are combined with AND, so we run one paginated
 * query per keyword and dedup by guid. Final filtering happens in the pipeline.
 */
@Component
public class JustJoinItAdapter extends AbstractRestApiAdapter {

    private static final String SOURCE_ID = "justjoinit";
    private static final String API_URL = "https://api.justjoin.it/v2/user-panel/offers";
    private static final String OFFER_BASE_URL = "https://justjoin.it/job-offer/";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES_PER_KEYWORD = 5;
    private static final int MAX_KEYWORDS = 3;
    private final ObjectMapper objectMapper;

    public JustJoinItAdapter(OkHttpClient httpClient,
                             SourceRepository sourceRepository,
                             ObjectMapper objectMapper) {
        super(httpClient, sourceRepository);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    protected String buildRequestUrl(SearchCriteria criteria) {
        return buildPageUrl(firstKeyword(criteria), 1);
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
                if (pageItems.size() < PER_PAGE) break;
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
                    .header("Version", "2")
                    .header("User-Agent", "Jrawler/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
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
                + "?page=" + page
                + "&perPage=" + PER_PAGE
                + "&sortBy=published&orderBy=DESC"
                + "&keywords[]=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private String firstKeyword(SearchCriteria criteria) {
        if (criteria == null || criteria.keywords().isEmpty()) return "java";
        return criteria.keywords().getFirst();
    }

    @Override
    protected List<RawVacancy> parseResponse(String responseBody) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) return result;

            for (JsonNode offer : data) {
                String guid = offer.path("guid").asString(null);
                String slug = offer.path("slug").asString(null);
                String title = offer.path("title").asString(null);
                if (guid == null || slug == null || title == null) continue;

                String company = offer.path("companyName").asString(null);
                String city = offer.path("city").asString("");
                String workplaceType = offer.path("workplaceType").asString("office");

                // requiredSkills + niceToHaveSkills as description
                StringBuilder descBuilder = new StringBuilder();
                appendSkills(descBuilder, offer.path("requiredSkills"));
                appendSkills(descBuilder, offer.path("niceToHaveSkills"));
                String experienceLevel = offer.path("experienceLevel").asString("");
                if (!experienceLevel.isEmpty()) {
                    descBuilder.append(experienceLevel).append(" ");
                }

                // salary from first employment type
                String salary = null;
                JsonNode employmentTypes = offer.path("employmentTypes");
                if (employmentTypes.isArray() && !employmentTypes.isEmpty()) {
                    JsonNode first = employmentTypes.get(0);
                    int from = first.path("from").asInt(0);
                    int to = first.path("to").asInt(0);
                    String currency = first.path("currency").asString("pln").toUpperCase();
                    if (from > 0 || to > 0) {
                        salary = from + " - " + to + " " + currency;
                    }
                }

                result.add(RawVacancy.builder(SOURCE_ID)
                        .externalId(guid)
                        .title(title)
                        .companyName(company)
                        .url(OFFER_BASE_URL + slug)
                        .location(city)
                        .description(descBuilder.toString().trim())
                        .salaryRaw(salary)
                        .remoteTypeRaw(workplaceType)
                        .build());
            }
        } catch (Exception e) {
            log.error("[{}] Parse error: {}", SOURCE_ID, e.getMessage());
        }
        return result;
    }

    private void appendSkills(StringBuilder sb, JsonNode skills) {
        if (skills.isArray()) {
            for (JsonNode skill : skills) {
                sb.append(skill.asString("")).append(" ");
            }
        }
    }
}
