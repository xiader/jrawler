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
 * SmartRecruiters ATS: https://api.smartrecruiters.com/v1/companies/{companyId}/postings
 */
@Component
public class SmartRecruitersAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmartRecruitersAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SmartRecruitersAdapter(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAtsType() {
        return "smartrecruiters";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String companyId = company.getAtsCompanyId();
        if (companyId == null || companyId.isBlank()) {
            log.warn("[smartrecruiters] No atsCompanyId for company {}", company.getName());
            return List.of();
        }

        String url = "https://api.smartrecruiters.com/v1/companies/" + companyId + "/postings";
        List<RawVacancy> result = new ArrayList<>();

        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Jrawler/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[smartrecruiters] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode content = root.path("content");
                if (!content.isArray()) return List.of();

                for (JsonNode posting : content) {
                    String id = posting.path("id").asString(null);
                    String title = posting.path("name").asString(null);
                    String refNumber = posting.path("refNumber").asString(null);
                    String city = posting.path("location").path("city").asString(null);
                    String country = posting.path("location").path("country").asString(null);
                    boolean remote = posting.path("location").path("remote").asBoolean(false);

                    String location = city != null && country != null ? city + ", " + country : city != null ? city : country;
                    String jobUrl = "https://jobs.smartrecruiters.com/" + companyId + "/" + id;

                    if (title != null && id != null) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("sr-" + id)
                                .title(title)
                                .companyName(company.getName())
                                .url(jobUrl)
                                .location(location)
                                .remoteTypeRaw(remote ? "remote" : null)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[smartrecruiters] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }
}
