package com.jrawler.adapter.ats;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.company.Company;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Workday ATS — uses the Workday CX Suite JSON API.
 * URL pattern: https://{tenant}.wd5.myworkdayjobs.com/wday/cxs/{tenant}/External/jobs
 * The company's career_page_url is used to derive the tenant/endpoint.
 */
@Component
public class WorkdayAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(WorkdayAdapter.class);
    private static final String SOURCE_ID = "company_ats";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WorkdayAdapter(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAtsType() {
        return "workday";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String careerUrl = company.getCareerPageUrl();
        if (careerUrl == null || careerUrl.isBlank()) {
            log.warn("[workday] No careerPageUrl for company {}", company.getName());
            return List.of();
        }

        // Derive Workday API endpoint from career page URL
        // e.g. https://spotify.wd1.myworkdayjobs.com/en-US/Life_at_Spotify
        // → https://spotify.wd1.myworkdayjobs.com/wday/cxs/spotify/Life_at_Spotify/jobs
        String apiUrl = deriveApiUrl(careerUrl);
        if (apiUrl == null) {
            log.warn("[workday] Cannot derive API URL from: {}", careerUrl);
            return List.of();
        }

        List<RawVacancy> result = new ArrayList<>();
        String requestBody = "{\"appliedFacets\":{},\"limit\":20,\"offset\":0,\"searchText\":\"\"}";

        try {
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(requestBody, JSON))
                    .header("User-Agent", "Jrawler/1.0")
                    .header("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[workday] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode jobPostings = root.path("jobPostings");
                if (!jobPostings.isArray()) return List.of();

                String baseUrl = extractBaseUrl(careerUrl);

                for (JsonNode posting : jobPostings) {
                    String title = posting.path("title").asString(null);
                    String externalPath = posting.path("externalPath").asString(null);
                    String location = posting.path("locationsText").asString(null);
                    String bulletFields = posting.path("bulletFields").toString();

                    String url = baseUrl != null && externalPath != null
                            ? baseUrl + externalPath : externalPath;

                    if (title != null && url != null) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("workday-" + externalPath)
                                .title(title)
                                .companyName(company.getName())
                                .url(url)
                                .location(location)
                                .description(bulletFields)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[workday] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }

    private String deriveApiUrl(String careerUrl) {
        try {
            // https://spotify.wd1.myworkdayjobs.com/en-US/Life_at_Spotify
            // Extract: tenant = "spotify", instance = "wd1", board = "Life_at_Spotify"
            java.net.URI uri = new java.net.URI(careerUrl);
            String host = uri.getHost(); // spotify.wd1.myworkdayjobs.com
            String path = uri.getPath(); // /en-US/Life_at_Spotify or /Life_at_Spotify

            String[] hostParts = host.split("\\.");
            String tenant = hostParts[0]; // spotify

            // Path: strip locale prefix if present (e.g. /en-US/)
            String[] pathParts = path.split("/");
            String board = null;
            for (String part : pathParts) {
                if (!part.isEmpty() && !part.matches("[a-z]{2}-[A-Z]{2}")) {
                    board = part;
                    break;
                }
            }
            if (board == null) return null;

            return "https://" + host + "/wday/cxs/" + tenant + "/" + board + "/jobs";
        } catch (Exception _) {
            return null;
        }
    }

    private String extractBaseUrl(String careerUrl) {
        try {
            java.net.URI uri = new java.net.URI(careerUrl);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception _) {
            return null;
        }
    }
}
