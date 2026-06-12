package com.jrawler.adapter.p0;

import com.jrawler.adapter.base.AbstractWebCrawlerAdapter;
import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;
import com.jrawler.source.SourceRepository;
import okhttp3.OkHttpClient;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Relocate.me — curated relocation/visa-sponsorship jobs.
 * Static HTML listing at https://relocate.me/international-jobs (20 jobs/page,
 * ?page=N pagination, no keyword search — filtering happens in the pipeline).
 */
@Component
public class RelocateMeAdapter extends AbstractWebCrawlerAdapter {

    private static final String SOURCE_ID = "relocateme";
    private static final String BASE_URL = "https://relocate.me";
    private static final String LISTING_URL = BASE_URL + "/international-jobs";
    private static final int MAX_PAGES = 5;

    public RelocateMeAdapter(OkHttpClient httpClient,
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
        return LISTING_URL;
    }

    @Override
    protected boolean requiresJavaScript() {
        return false;
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        List<RawVacancy> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                Document doc = fetchWithJsoup(LISTING_URL + "?page=" + page);
                List<RawVacancy> pageItems = parseJobList(doc);
                if (pageItems.isEmpty()) break;
                result.addAll(pageItems);
            } catch (Exception e) {
                log.error("[{}] Web crawl error on page {}: {}", SOURCE_ID, page, e.getMessage());
                break;
            }
        }
        log.info("[{}] Fetched {} vacancies", SOURCE_ID, result.size());
        return result;
    }

    @Override
    protected List<RawVacancy> parseJobList(Document document) {
        List<RawVacancy> result = new ArrayList<>();
        for (Element card : document.select("div.jobs-list__job")) {
            Element link = card.selectFirst(".job__title a[href]");
            if (link == null) continue;

            String url = link.absUrl("href");
            Element titleEl = link.selectFirst("b");
            String title = titleEl != null ? titleEl.text().trim() : link.text().trim();
            if (url.isEmpty() || title.isEmpty()) continue;

            // link text is "<b>Title</b> in City"
            String city = link.text().replaceFirst("^.*?\\bin\\b", "").trim();

            // info cells: [country, company]
            List<String> infoCells = card.select(".job__info .job__company p").eachText();
            String country = infoCells.size() > 0 ? infoCells.get(0) : "";
            String company = infoCells.size() > 1 ? infoCells.get(1) : null;

            String location = city.isEmpty() ? country : city + ", " + country;
            boolean remote = "Remote".equalsIgnoreCase(country);

            Element preview = card.selectFirst("p.job__preview");

            // externalId: trailing numeric id from the URL slug
            String externalId = url.replaceAll(".*-(\\d+)$", "$1");

            result.add(RawVacancy.builder(SOURCE_ID)
                    .externalId(externalId)
                    .title(title)
                    .companyName(company)
                    .url(url)
                    .location(location)
                    .description(preview != null ? preview.text().trim() : "")
                    .remoteTypeRaw(remote ? "remote" : "office")
                    .build());
        }
        return result;
    }
}
