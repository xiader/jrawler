package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractRssAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import okhttp3.OkHttpClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * WeWorkRemotely RSS feed: https://weworkremotely.com/categories/remote-programming-jobs.rss
 */
@Component
public class WeWorkRemotelyAdapter extends AbstractRssAdapter {

    private static final String SOURCE_ID = "weworkremotely";

    public WeWorkRemotelyAdapter(OkHttpClient httpClient,
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
        return "https://weworkremotely.com/categories/remote-programming-jobs.rss";
    }

    @Override
    protected RawVacancy mapEntry(SyndEntry entry) {
        String title = entry.getTitle();
        String url = entry.getLink();
        String description = entry.getDescription() != null
                ? entry.getDescription().getValue() : null;

        // WWR title format: "Company: Position Title"
        String company = null;
        String position = title;
        if (title != null && title.contains(": ")) {
            int sep = title.indexOf(": ");
            company = title.substring(0, sep).trim();
            position = title.substring(sep + 2).trim();
        }

        Instant fetchedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant() : Instant.now();

        return RawVacancy.builder(SOURCE_ID)
                .externalId(entry.getUri())
                .title(position)
                .companyName(company)
                .url(url)
                .location("Remote")
                .description(description)
                .remoteTypeRaw("remote")
                .fetchedAt(fetchedAt)
                .build();
    }
}
