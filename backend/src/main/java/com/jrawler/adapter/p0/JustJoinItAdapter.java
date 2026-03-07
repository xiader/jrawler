package com.jrawler.adapter.p0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrawler.adapter.base.AbstractRestApiAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JustJoin.it API: https://justjoin.it/api/offers
 * Returns all offers (large payload). We filter locally by keyword match in title/skills.
 */
@Component
public class JustJoinItAdapter extends AbstractRestApiAdapter {

    private static final String SOURCE_ID = "justjoinit";
    private static final String BASE_URL = "https://justjoin.it";
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
        return "https://justjoin.it/api/offers";
    }

    @Override
    protected List<RawVacancy> parseResponse(String responseBody) {
        List<RawVacancy> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) return result;

            for (JsonNode offer : root) {
                String id = offer.path("id").asText(null);
                String title = offer.path("title").asText(null);
                String company = offer.path("company_name").asText(null);
                String city = offer.path("city").asText("");
                String country = offer.path("country_code").asText("");
                String remoteInterview = offer.path("remote_interview").asText("");
                boolean fullyRemote = offer.path("remote").asBoolean(false);

                String location = city.isEmpty() ? country : city + ", " + country;
                String remoteTypeRaw = fullyRemote ? "remote" : "hybrid";

                // salary
                String salary = null;
                JsonNode salaryNode = offer.path("employment_types");
                if (salaryNode.isArray() && salaryNode.size() > 0) {
                    JsonNode firstType = salaryNode.get(0);
                    JsonNode salaryRange = firstType.path("salary");
                    if (!salaryRange.isMissingNode()) {
                        int from = salaryRange.path("from").asInt(0);
                        int to = salaryRange.path("to").asInt(0);
                        String currency = salaryRange.path("currency").asText("PLN");
                        if (from > 0 || to > 0) {
                            salary = from + " - " + to + " " + currency;
                        }
                    }
                }

                // skills as description
                StringBuilder descBuilder = new StringBuilder();
                JsonNode skills = offer.path("skills");
                if (skills.isArray()) {
                    for (JsonNode skill : skills) {
                        descBuilder.append(skill.path("name").asText("")).append(" ");
                    }
                }

                String url = BASE_URL + "/offers/" + id;

                if (title != null && id != null) {
                    result.add(RawVacancy.builder(SOURCE_ID)
                            .externalId(id)
                            .title(title)
                            .companyName(company)
                            .url(url)
                            .location(location)
                            .description(descBuilder.toString().trim())
                            .salaryRaw(salary)
                            .remoteTypeRaw(remoteTypeRaw)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("[{}] Parse error: {}", SOURCE_ID, e.getMessage());
        }
        return result;
    }
}
