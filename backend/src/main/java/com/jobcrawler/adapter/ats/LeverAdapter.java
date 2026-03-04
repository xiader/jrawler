package com.jobcrawler.adapter.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.company.Company;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Lever ATS: https://api.lever.co/v0/postings/{company}?mode=json
 */
@Component
public class LeverAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(LeverAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LeverAdapter(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAtsType() {
        return "lever";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String companyId = company.getAtsCompanyId();
        if (companyId == null || companyId.isBlank()) {
            log.warn("[lever] No atsCompanyId for company {}", company.getName());
            return List.of();
        }

        String url = "https://api.lever.co/v0/postings/" + companyId + "?mode=json";
        List<RawVacancy> result = new ArrayList<>();

        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "JobCrawler/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[lever] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                if (!root.isArray()) return List.of();

                for (JsonNode posting : root) {
                    String id = posting.path("id").asText(null);
                    String title = posting.path("text").asText(null);
                    String hostedUrl = posting.path("hostedUrl").asText(null);
                    String location = posting.path("categories").path("location").asText(null);
                    String workplaceType = posting.path("categories").path("workplaceType").asText(null);

                    // Build description from content lists
                    StringBuilder desc = new StringBuilder();
                    JsonNode lists = posting.path("lists");
                    if (lists.isArray()) {
                        for (JsonNode section : lists) {
                            desc.append(section.path("text").asText("")).append("\n");
                            desc.append(section.path("content").asText("")).append("\n");
                        }
                    }

                    if (title != null && hostedUrl != null) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("lever-" + id)
                                .title(title)
                                .companyName(company.getName())
                                .url(hostedUrl)
                                .location(location)
                                .description(desc.toString().trim())
                                .remoteTypeRaw(workplaceType)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[lever] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }
}
