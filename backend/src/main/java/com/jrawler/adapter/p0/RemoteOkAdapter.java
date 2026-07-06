package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractRestApiAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RemoteOK public API: https://remoteok.com/api?tags=java
 * Returns a JSON array; first element is a legal notice object, rest are job listings.
 */
@Component
public class RemoteOkAdapter extends AbstractRestApiAdapter {

    private static final String SOURCE_ID = "remoteok";
    private final ObjectMapper objectMapper;

    public RemoteOkAdapter(OkHttpClient httpClient,
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
        if (criteria == null || criteria.keywords().isEmpty()) {
            return "https://remoteok.com/api";
        }
        // RemoteOK API only accepts a single tag; use the primary keyword
        String tag = criteria.keywords().getFirst();
        return "https://remoteok.com/api?tag=" + tag;
    }

    @Override
    protected List<RawVacancy> parseResponse(String responseBody) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) return result;

            for (JsonNode node : root) {
                // Skip the first element (legal notice) and non-job objects
                if (!node.has("id") || !node.has("position")) continue;

                String id = node.path("id").asString(null);
                String title = node.path("position").asString(null);
                String company = node.path("company").asString(null);
                String url = node.path("url").asString(null);
                String description = node.path("description").asString(null);
                String location = node.path("location").asString("Remote");
                String tags = node.path("tags").isArray()
                        ? objectMapper.convertValue(node.path("tags"), List.class).toString()
                        : "";

                long epochSec = node.path("epoch").asLong(0);
                Instant fetchedAt = epochSec > 0 ? Instant.ofEpochSecond(epochSec) : Instant.now();

                // Build salary string
                String salary = null;
                if (node.has("salary_min") && node.has("salary_max")) {
                    int min = node.path("salary_min").asInt(0);
                    int max = node.path("salary_max").asInt(0);
                    if (min > 0 || max > 0) {
                        salary = "$" + min + " - $" + max;
                    }
                }

                if (title != null && url != null) {
                    result.add(RawVacancy.builder(SOURCE_ID)
                            .externalId(id)
                            .title(title)
                            .companyName(company)
                            .url(url)
                            .location(location)
                            .description(description != null ? description : tags)
                            .salaryRaw(salary)
                            .remoteTypeRaw("remote")
                            .fetchedAt(fetchedAt)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("[{}] Parse error: {}", SOURCE_ID, e.getMessage());
        }
        return result;
    }
}
