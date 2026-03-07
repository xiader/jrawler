package com.jobcrawler.adapter.p0;

import com.jobcrawler.adapter.base.AbstractWebCrawlerAdapter;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * LinkedIn Jobs via public guest API (no auth required).
 *
 * Listing: GET https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search
 * Detail:  GET https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{jobId}
 *
 * Fetches up to 3 pages × 25 = 75 vacancies from the last week,
 * then enriches each with a full description from the detail endpoint.
 */
@Component
public class LinkedInAdapter extends AbstractWebCrawlerAdapter {

    private static final String SOURCE_ID = "linkedin";
    private static final String LISTING_API =
            "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String DETAIL_API =
            "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/";
    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGES = 3;

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
    protected boolean requiresJavaScript() {
        return false;
    }

    // Used by AbstractWebCrawlerAdapter only for rate-limit domain extraction
    @Override
    protected String buildSearchUrl(SearchCriteria criteria) {
        return buildPageUrl(buildKeywords(criteria), 0);
    }

    @Override
    protected List<RawVacancy> parseJobList(Document document) {
        List<RawVacancy> result = new ArrayList<>();
        for (Element li : document.select("li")) {
            try {
                RawVacancy v = parseCard(li);
                if (v != null) result.add(v);
            } catch (Exception e) {
                log.debug("[{}] Skipping malformed card: {}", SOURCE_ID, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        String keywords = buildKeywords(criteria);
        List<RawVacancy> stubs = new ArrayList<>();

        // Phase 1: paginated listing
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = buildPageUrl(keywords, page * PAGE_SIZE);
            try {
                randomSleep(2000, 4000);
                Document doc = fetchWithJsoup(url);
                List<RawVacancy> pageItems = parseJobList(doc);
                if (pageItems.isEmpty()) {
                    log.info("[{}] Empty page {}, stopping pagination", SOURCE_ID, page);
                    break;
                }
                stubs.addAll(pageItems);
                log.debug("[{}] Page {}: {} vacancies", SOURCE_ID, page, pageItems.size());
            } catch (Exception e) {
                log.warn("[{}] Listing page {} failed: {}", SOURCE_ID, page, e.getMessage());
                break;
            }
        }

        if (stubs.isEmpty()) {
            log.warn("[{}] No vacancies found in listings", SOURCE_ID);
            return List.of();
        }

        // Phase 2: enrich with descriptions
        List<RawVacancy> result = new ArrayList<>(stubs.size());
        for (RawVacancy stub : stubs) {
            String description = fetchDescription(stub.externalId());
            result.add(withDescription(stub, description));
            randomSleep(1500, 3000);
        }

        log.info("[{}] Fetched {} vacancies with descriptions", SOURCE_ID, result.size());
        return result;
    }

    private RawVacancy parseCard(Element li) {
        Element card = li.selectFirst("div.base-card");
        if (card == null) return null;

        // Job ID from URN: "urn:li:jobPosting:1234567890"
        String urn = card.attr("data-entity-urn");
        String jobId = urn.contains(":") ? urn.substring(urn.lastIndexOf(':') + 1) : null;
        if (jobId == null || jobId.isBlank()) return null;

        // URL — strip tracking query params
        Element linkEl = card.selectFirst("a.base-card__full-link");
        String url = linkEl != null ? linkEl.attr("href") : null;
        if (url == null || url.isBlank()) {
            url = "https://www.linkedin.com/jobs/view/" + jobId + "/";
        } else if (url.contains("?")) {
            url = url.substring(0, url.indexOf('?'));
        }

        // Title
        Element titleEl = card.selectFirst("h3.base-search-card__title");
        String title = titleEl != null ? titleEl.text().trim() : null;
        if (title == null || title.isBlank()) return null;

        // Company (nested link inside h4, fallback to h4 text)
        Element companyEl = card.selectFirst("h4.base-search-card__subtitle a");
        if (companyEl == null) companyEl = card.selectFirst("h4.base-search-card__subtitle");
        String company = companyEl != null ? companyEl.text().trim() : null;

        // Location
        Element locationEl = card.selectFirst("span.job-search-card__location");
        String location = locationEl != null ? locationEl.text().trim() : null;

        // Posted date
        Instant fetchedAt = Instant.now();
        Element timeEl = card.selectFirst("time.job-search-card__listdate");
        if (timeEl != null) {
            String datetime = timeEl.attr("datetime");
            if (!datetime.isBlank()) {
                try {
                    fetchedAt = LocalDate.parse(datetime).atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (Exception ignored) {}
            }
        }

        return RawVacancy.builder(SOURCE_ID)
                .externalId(jobId)
                .title(title)
                .companyName(company)
                .url(url)
                .location(location)
                .remoteTypeRaw("remote")
                .fetchedAt(fetchedAt)
                .build();
    }

    private String fetchDescription(String jobId) {
        if (jobId == null) return null;
        try {
            Document doc = fetchWithJsoup(DETAIL_API + jobId);
            // Try selectors in order of specificity
            for (String selector : List.of(
                    ".show-more-less-html__markup",
                    ".description__text",
                    ".job-description")) {
                Element el = doc.selectFirst(selector);
                if (el != null) {
                    String text = el.text().trim();
                    if (!text.isBlank()) return text;
                }
            }
        } catch (Exception e) {
            log.debug("[{}] Description fetch failed for jobId {}: {}", SOURCE_ID, jobId, e.getMessage());
        }
        return null;
    }

    private RawVacancy withDescription(RawVacancy stub, String description) {
        return RawVacancy.builder(SOURCE_ID)
                .externalId(stub.externalId())
                .title(stub.title())
                .companyName(stub.companyName())
                .url(stub.url())
                .location(stub.location())
                .description(description)
                .remoteTypeRaw(stub.remoteTypeRaw())
                .fetchedAt(stub.fetchedAt())
                .build();
    }

    private String buildPageUrl(String keywords, int start) {
        String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
        return LISTING_API
                + "?keywords=" + encoded
                + "&location=Europe"
                + "&f_WT=2"
                + "&f_TPR=r604800"
                + "&start=" + start;
    }

    private String buildKeywords(SearchCriteria criteria) {
        if (criteria == null || criteria.keywords().isEmpty()) {
            return "java backend";
        }
        return criteria.keywords().stream()
                .limit(5)
                .collect(Collectors.joining(" "));
    }

    private void randomSleep(long minMs, long maxMs) {
        try {
            Thread.sleep(minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
