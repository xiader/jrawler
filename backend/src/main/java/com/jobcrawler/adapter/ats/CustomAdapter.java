package com.jobcrawler.adapter.ats;

import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.company.Company;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom ATS adapter — uses CSS selectors from company.customSelectors map.
 *
 * Expected selector keys:
 *   "jobList"     — container/item selector (required)
 *   "title"       — job title within each item
 *   "link"        — URL within each item (href attribute)
 *   "location"    — location text within each item
 *   "salary"      — salary text within each item
 *   "description" — description text within each item
 */
@Component
public class CustomAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(CustomAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final OkHttpClient httpClient;

    public CustomAdapter(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getAtsType() {
        return "custom";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String careerUrl = company.getCareerPageUrl();
        Map<String, String> selectors = company.getCustomSelectors();

        if (careerUrl == null || careerUrl.isBlank()) {
            log.warn("[custom] No careerPageUrl for company {}", company.getName());
            return List.of();
        }
        if (selectors == null || !selectors.containsKey("jobList")) {
            log.warn("[custom] No 'jobList' selector for company {}", company.getName());
            return List.of();
        }

        List<RawVacancy> result = new ArrayList<>();

        try {
            Request request = new Request.Builder().url(careerUrl)
                    .header("User-Agent", "Mozilla/5.0 (compatible; JobCrawler/1.0)")
                    .header("Accept", "text/html")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[custom] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                Document doc = Jsoup.parse(response.body().string(), careerUrl);
                Elements items = doc.select(selectors.get("jobList"));

                for (Element item : items) {
                    String title = selectText(item, selectors.get("title"));
                    String jobUrl = selectLink(item, selectors.get("link"), careerUrl);
                    String location = selectText(item, selectors.get("location"));
                    String salary = selectText(item, selectors.get("salary"));
                    String description = selectText(item, selectors.get("description"));

                    if (title != null && !title.isEmpty()) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("custom-" + company.getId() + "-" + (jobUrl != null ? jobUrl.hashCode() : title.hashCode()))
                                .title(title)
                                .companyName(company.getName())
                                .url(jobUrl != null ? jobUrl : careerUrl)
                                .location(location)
                                .salaryRaw(salary)
                                .description(description)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[custom] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }

    private String selectText(Element root, String selector) {
        if (selector == null || selector.isBlank() || root == null) return null;
        Element el = root.selectFirst(selector);
        return el != null ? el.text() : null;
    }

    private String selectLink(Element root, String selector, String baseUrl) {
        if (root == null) return null;
        Element el = selector != null && !selector.isBlank()
                ? root.selectFirst(selector)
                : root.selectFirst("a[href]");
        if (el == null) return null;
        String href = el.absUrl("href");
        return href.isEmpty() ? el.attr("href") : href;
    }
}
