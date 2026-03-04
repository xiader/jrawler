package com.jobcrawler.adapter.p0;

import com.jobcrawler.adapter.base.AbstractWebCrawlerAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TheProtocol.it — Polish IT job board, scraping with Jsoup.
 * URL: https://theprotocol.it/filtry/java;t
 */
@Component
public class TheProtocolAdapter extends AbstractWebCrawlerAdapter {

    private static final String SOURCE_ID = "theprotocol";
    private static final String BASE_URL = "https://theprotocol.it";

    public TheProtocolAdapter(OkHttpClient httpClient,
                               RedisTemplate<String, String> redisTemplate,
                               SourceRepository sourceRepository) {
        super(httpClient, redisTemplate, sourceRepository);
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    protected String buildSearchUrl(SearchCriteria criteria) {
        if (criteria == null || criteria.keywords().isEmpty()) {
            return BASE_URL + "/filtry/java;t";
        }
        String tag = criteria.keywords().stream()
                .limit(2)
                .collect(Collectors.joining(","));
        return BASE_URL + "/filtry/" + URLEncoder.encode(tag, StandardCharsets.UTF_8) + ";t";
    }

    @Override
    protected List<RawVacancy> parseJobList(Document document) {
        List<RawVacancy> result = new ArrayList<>();

        // TheProtocol uses data-test attributes for job cards
        Elements jobCards = document.select("[data-test='list-item-offer']");
        if (jobCards.isEmpty()) {
            // Fallback: look for common offer card selectors
            jobCards = document.select("a[href*='/szczegoly/']");
        }

        for (Element card : jobCards) {
            try {
                String url = card.attr("abs:href");
                if (url.isEmpty()) {
                    Element link = card.selectFirst("a[href*='/szczegoly/']");
                    if (link != null) url = link.absUrl("href");
                }

                String title = card.select("[data-test='text-jobTitle'], h3, .title").text();
                String company = card.select("[data-test='text-employerName'], .company").text();
                String location = card.select("[data-test='text-city'], .location").text();
                String salary = card.select("[data-test='text-salary'], .salary").text();

                if (!title.isEmpty() && !url.isEmpty()) {
                    result.add(RawVacancy.builder(SOURCE_ID)
                            .externalId(extractId(url))
                            .title(title)
                            .companyName(company.isEmpty() ? null : company)
                            .url(url)
                            .location(location.isEmpty() ? "Poland" : location)
                            .salaryRaw(salary.isEmpty() ? null : salary)
                            .remoteTypeRaw("hybrid")
                            .build());
                }
            } catch (Exception e) {
                log.debug("[{}] Failed to parse card: {}", SOURCE_ID, e.getMessage());
            }
        }
        return result;
    }

    @Override
    protected boolean requiresJavaScript() {
        return false;
    }

    private String extractId(String url) {
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : url;
    }
}
