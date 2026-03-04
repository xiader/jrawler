package com.jobcrawler.adapter.p0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcrawler.adapter.base.AbstractRestApiAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Remotive public API: https://remotive.com/api/remote-jobs?category=software-dev&search=java
 */
@Component
public class RemotiveAdapter extends AbstractRestApiAdapter {

    private static final String SOURCE_ID = "remotive";
    private final ObjectMapper objectMapper;

    public RemotiveAdapter(OkHttpClient httpClient,
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
        String search = criteria != null && !criteria.keywords().isEmpty()
                ? criteria.keywords().get(0)
                : "java";
        return "https://remotive.com/api/remote-jobs?category=software-dev&search="
                + java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    protected List<RawVacancy> parseResponse(String responseBody) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray()) return result;

            for (JsonNode job : jobs) {
                String id = job.path("id").asText(null);
                String title = job.path("title").asText(null);
                String company = job.path("company_name").asText(null);
                String url = job.path("url").asText(null);
                String description = job.path("description").asText(null);
                String location = job.path("candidate_required_location").asText("Remote");
                String salary = job.path("salary").asText(null);
                String jobType = job.path("job_type").asText(null);
                String publishedAt = job.path("publication_date").asText(null);

                if (title != null && url != null) {
                    result.add(RawVacancy.builder(SOURCE_ID)
                            .externalId(id)
                            .title(title)
                            .companyName(company)
                            .url(url)
                            .location(location)
                            .description(description)
                            .salaryRaw(salary)
                            .remoteTypeRaw(jobType != null ? jobType : "remote")
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("[{}] Parse error: {}", SOURCE_ID, e.getMessage());
        }
        return result;
    }
}
