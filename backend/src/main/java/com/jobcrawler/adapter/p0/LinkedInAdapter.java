package com.jobcrawler.adapter.p0;

import com.jobcrawler.adapter.base.AbstractRssAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.SourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import okhttp3.OkHttpClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * LinkedIn Jobs RSS feed.
 * Note: LinkedIn has restricted public RSS access. This adapter uses the public jobs RSS
 * endpoint which may require session cookies in production. Works best for public/open jobs.
 * URL format: https://www.linkedin.com/jobs/search/?keywords=java&location=Europe&f_WT=2
 */
@Component
public class LinkedInAdapter extends AbstractRssAdapter {

    private static final String SOURCE_ID = "linkedin";

    public LinkedInAdapter(OkHttpClient httpClient,
                           RedisTemplate<String, String> redisTemplate,
                           SourceRepository sourceRepository) {
        super(httpClient, redisTemplate, sourceRepository);
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    protected String getRssFeedUrl(SearchCriteria criteria) {
        String keywords = criteria != null && !criteria.keywords().isEmpty()
                ? criteria.keywords().stream().limit(3).collect(Collectors.joining(" "))
                : "java backend";
        String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
        // f_WT=2 = Remote jobs filter
        return "https://www.linkedin.com/jobs/search/?keywords=" + encoded
                + "&location=Europe&f_WT=2&f_TPR=r86400&format=json";
    }

    @Override
    protected RawVacancy mapEntry(SyndEntry entry) {
        if (entry == null) return null;
        String title = entry.getTitle();
        String url = entry.getLink();
        String description = entry.getDescription() != null
                ? entry.getDescription().getValue() : null;

        // Extract company from description or author
        String company = entry.getAuthor();

        Instant fetchedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant() : Instant.now();

        return RawVacancy.builder(SOURCE_ID)
                .externalId(entry.getUri())
                .title(title)
                .companyName(company)
                .url(url)
                .description(description)
                .remoteTypeRaw("remote")
                .fetchedAt(fetchedAt)
                .build();
    }
}
