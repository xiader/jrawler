package com.jrawler.adapter.base;

import com.jrawler.adapter.JobSearchAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.Source;
import com.jrawler.source.SourceRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractWebCrawlerAdapter implements JobSearchAdapter {

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    );

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final OkHttpClient httpClient;
    protected final RedisTemplate<String, String> redisTemplate;
    protected final SourceRepository sourceRepository;

    protected AbstractWebCrawlerAdapter(OkHttpClient httpClient,
                                         RedisTemplate<String, String> redisTemplate,
                                         SourceRepository sourceRepository) {
        this.httpClient = httpClient;
        this.redisTemplate = redisTemplate;
        this.sourceRepository = sourceRepository;
    }

    protected abstract String buildSearchUrl(SearchCriteria criteria);

    protected abstract List<RawVacancy> parseJobList(Document document);

    protected abstract boolean requiresJavaScript();

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        applyRateLimit();
        String url = buildSearchUrl(criteria);

        try {
            Document doc = requiresJavaScript() ? fetchWithPlaywright(url) : fetchWithJsoup(url);
            List<RawVacancy> result = parseJobList(doc);
            log.info("[{}] Fetched {} vacancies", getSourceId(), result.size());
            return result;
        } catch (Exception e) {
            log.error("[{}] Web crawl error: {}", getSourceId(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isEnabled() {
        return sourceRepository.findById(getSourceId())
                .map(Source::isEnabled)
                .orElse(false);
    }

    protected Document fetchWithJsoup(String url) throws Exception {
        String userAgent = randomUserAgent();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return Jsoup.parse(response.body().string(), url);
        }
    }

    protected Document fetchWithPlaywright(String url) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
            try (Browser browser = playwright.chromium().launch(opts)) {
                Page page = browser.newPage();
                page.setExtraHTTPHeaders(java.util.Map.of("User-Agent", randomUserAgent()));
                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                String html = page.content();
                return Jsoup.parse(html, url);
            }
        }
    }

    private void applyRateLimit() {
        try {
            String domain = extractDomain(buildSearchUrl(null));
            String key = "ratelimit:" + domain;
            String lastRequestMs = redisTemplate.opsForValue().get(key);

            if (lastRequestMs != null) {
                long elapsed = System.currentTimeMillis() - Long.parseLong(lastRequestMs);
                long minDelay = 2000 + ThreadLocalRandom.current().nextLong(3000);
                if (elapsed < minDelay) {
                    Thread.sleep(minDelay - elapsed);
                }
            }
            redisTemplate.opsForValue().set(key,
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofMinutes(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Rate limit check failed: {}", e.getMessage());
        }
    }

    private String extractDomain(String url) {
        try {
            return new URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    protected String randomUserAgent() {
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }
}
