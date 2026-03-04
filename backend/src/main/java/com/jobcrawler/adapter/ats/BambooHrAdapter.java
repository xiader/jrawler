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

/**
 * BambooHR ATS — HTML scraping of {company}.bamboohr.com/careers
 */
@Component
public class BambooHrAdapter implements AtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(BambooHrAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final OkHttpClient httpClient;

    public BambooHrAdapter(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getAtsType() {
        return "bamboohr";
    }

    @Override
    public List<RawVacancy> fetchJobs(Company company) {
        String companyId = company.getAtsCompanyId();
        if (companyId == null || companyId.isBlank()) {
            log.warn("[bamboohr] No atsCompanyId for company {}", company.getName());
            return List.of();
        }

        String url = "https://" + companyId + ".bamboohr.com/careers/list";
        List<RawVacancy> result = new ArrayList<>();

        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (compatible; JobCrawler/1.0)")
                    .header("Accept", "text/html")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[bamboohr] HTTP {} for company {}", response.code(), company.getName());
                    return List.of();
                }

                Document doc = Jsoup.parse(response.body().string(), url);
                Elements jobItems = doc.select(".ResJobList-item, [data-testid='job-list-item'], li[class*='job']");

                for (Element item : jobItems) {
                    Element linkEl = item.selectFirst("a[href]");
                    if (linkEl == null) continue;

                    String jobUrl = linkEl.absUrl("href");
                    String title = item.select("h2, h3, .ResJobList-jobTitle, [data-testid='job-title']").text();
                    if (title.isEmpty()) title = linkEl.text();
                    String location = item.select(".ResJobList-jobLocation, [data-testid='job-location']").text();
                    String dept = item.select(".ResJobList-jobDepartment, [data-testid='job-department']").text();

                    if (!title.isEmpty() && !jobUrl.isEmpty()) {
                        result.add(RawVacancy.builder(SOURCE_ID)
                                .externalId("bamboohr-" + extractId(jobUrl))
                                .title(title)
                                .companyName(company.getName())
                                .url(jobUrl)
                                .location(location.isEmpty() ? null : location)
                                .description(dept.isEmpty() ? null : dept)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[bamboohr] Error fetching {} jobs: {}", company.getName(), e.getMessage());
        }
        return result;
    }

    private String extractId(String url) {
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : url;
    }
}
