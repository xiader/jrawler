package com.jrawler.adapter.base;

import com.jrawler.adapter.JobSearchAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.Source;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractRestApiAdapter implements JobSearchAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final OkHttpClient httpClient;
    protected final SourceRepository sourceRepository;

    protected AbstractRestApiAdapter(OkHttpClient httpClient, SourceRepository sourceRepository) {
        this.httpClient = httpClient;
        this.sourceRepository = sourceRepository;
    }

    protected abstract String buildRequestUrl(SearchCriteria criteria);

    protected abstract List<RawVacancy> parseResponse(String responseBody);

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        String url = buildRequestUrl(criteria);
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Jrawler/1.0")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return parseResponse(response.body().string());
                    }
                    log.warn("[{}] HTTP {} on attempt {}/{}", getSourceId(), response.code(), attempt, maxRetries);
                    if (response.code() == 429 || response.code() >= 500) {
                        sleepBefore(attempt);
                        continue;
                    }
                    return List.of();
                }
            } catch (Exception e) {
                log.error("[{}] Attempt {}/{} failed: {}", getSourceId(), attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) sleepBefore(attempt);
            }
        }
        return List.of();
    }

    @Override
    public boolean isEnabled() {
        return sourceRepository.findById(getSourceId())
                .map(Source::isEnabled)
                .orElse(false);
    }

    private void sleepBefore(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
