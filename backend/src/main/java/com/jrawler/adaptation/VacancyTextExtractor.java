package com.jrawler.adaptation;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VacancyTextExtractor {

    private static final int MIN_TEXT_LENGTH = 300;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Logger log = LoggerFactory.getLogger(VacancyTextExtractor.class);

    private final OkHttpClient httpClient;

    public VacancyTextExtractor(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String extract(String url) {
        String text;
        try {
            text = extractText(fetchWithJsoup(url));
            if (text.length() < MIN_TEXT_LENGTH) {
                log.info("Static fetch of {} too short ({} chars), retrying with Playwright", url, text.length());
                text = extractText(fetchWithPlaywright(url));
            }
        } catch (Exception e) {
            log.warn("Vacancy fetch failed for {}: {}", url, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not fetch the vacancy page — paste the text manually");
        }
        if (text.length() < MIN_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The page contains too little text — paste the vacancy manually");
        }
        return text;
    }

    static String extractText(Document doc) {
        doc.select("script, style, noscript, svg, nav, footer, header, iframe").remove();
        return doc.body() != null ? doc.body().text().strip() : "";
    }

    private Document fetchWithJsoup(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return Jsoup.parse(response.body().string(), url);
        }
    }

    private Document fetchWithPlaywright(String url) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
            try (Browser browser = playwright.chromium().launch(opts)) {
                Page page = browser.newPage();
                page.setExtraHTTPHeaders(java.util.Map.of("User-Agent", USER_AGENT));
                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return Jsoup.parse(page.content(), url);
            }
        }
    }
}
