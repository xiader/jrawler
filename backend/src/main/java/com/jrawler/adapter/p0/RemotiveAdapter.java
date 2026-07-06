package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractRestApiAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
                String id = job.path("id").asString(null);
                String title = job.path("title").asString(null);
                String company = job.path("company_name").asString(null);
                String url = job.path("url").asString(null);
                String description = job.path("description").asString(null);
                String location = job.path("candidate_required_location").asString("Remote");
                String salary = job.path("salary").asString(null);
                String jobType = job.path("job_type").asString(null);
                String publishedAt = job.path("publication_date").asString(null);

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
