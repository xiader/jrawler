package com.jobcrawler.adapter.p0;

import com.jobcrawler.adapter.base.AbstractRssAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.SourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import okhttp3.OkHttpClient;
import org.jsoup.Jsoup;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * NoFluffJobs RSS feed: https://nofluffjobs.com/feed.xml
 */
@Component
public class NoFluffJobsAdapter extends AbstractRssAdapter {

    private static final String SOURCE_ID = "nofluffjobs";

    public NoFluffJobsAdapter(OkHttpClient httpClient,
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
        return "https://nofluffjobs.com/feed.xml";
    }

    @Override
    protected RawVacancy mapEntry(SyndEntry entry) {
        if (entry == null) return null;

        String title = entry.getTitle();
        String url = entry.getLink();

        // Description may contain HTML
        String rawDesc = entry.getDescription() != null
                ? entry.getDescription().getValue() : null;
        String description = rawDesc != null ? Jsoup.parse(rawDesc).text() : null;

        // Categories contain location and tags
        String location = entry.getCategories().stream()
                .map(c -> c.getName())
                .filter(n -> n.contains(",") || n.matches("[A-Z][a-z]+.*"))
                .findFirst()
                .orElse(null);

        Instant fetchedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant() : Instant.now();

        return RawVacancy.builder(SOURCE_ID)
                .externalId(entry.getUri())
                .title(title)
                .url(url)
                .location(location)
                .description(description)
                .remoteTypeRaw("hybrid")
                .fetchedAt(fetchedAt)
                .build();
    }
}
