package com.jrawler.adapter.ats;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.company.Company;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Greenhouse ATS: https://boards-api.greenhouse.io/v1/boards/{company}/jobs?content=true
 */
@Component
public class GreenhouseAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GreenhouseAdapter(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAtsType() {
        return "greenhouse";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String companyId = company.getAtsCompanyId();
        if (companyId == null || companyId.isBlank()) {
            log.warn("[greenhouse] No atsCompanyId for company {}", company.getName());
            return List.of();
        }

        String url = "https://boards-api.greenhouse.io/v1/boards/" + companyId + "/jobs?content=true";
        List<RawVacancy> result = new ArrayList<>();

        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Jrawler/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[greenhouse] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode jobs = root.path("jobs");
                if (!jobs.isArray()) return List.of();

                for (JsonNode job : jobs) {
                    String id = job.path("id").asString(null);
                    String title = job.path("title").asString(null);
                    String jobUrl = job.path("absolute_url").asString(null);
                    String content = job.path("content").asString(null);
                    String location = job.path("location").path("name").asString(null);

                    if (title != null && jobUrl != null) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("greenhouse-" + id)
                                .title(title)
                                .companyName(company.getName())
                                .url(jobUrl)
                                .location(location)
                                .description(content)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[greenhouse] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }
}
