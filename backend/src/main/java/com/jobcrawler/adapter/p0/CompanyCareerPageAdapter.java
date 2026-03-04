package com.jobcrawler.adapter.p0;

import com.jobcrawler.adapter.JobSearchAdapter;
import com.jobcrawler.adapter.ats.AtsAdapterFactory;
import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.adapter.model.SearchCriteria;
import com.jobcrawler.company.Company;
import com.jobcrawler.company.CompanyRepository;
import com.jobcrawler.source.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aggregator adapter that crawls all active companies' career pages.
 * Delegates to the appropriate ATS adapter based on company.atsType.
 */
@Component
@RequiredArgsConstructor
public class CompanyCareerPageAdapter implements JobSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(CompanyCareerPageAdapter.class);
    private static final String SOURCE_ID = "company_ats";

    private final CompanyRepository companyRepository;
    private final AtsAdapterFactory atsAdapterFactory;
    private final SourceRepository sourceRepository;

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        List<Company> activeCompanies = companyRepository.findAllByActiveTrue();
        log.info("[{}] Crawling {} active companies", SOURCE_ID, activeCompanies.size());

        return activeCompanies.stream()
                .flatMap(company -> fetchForCompany(company).stream())
                .toList();
    }

    @Override
    public boolean isEnabled() {
        return sourceRepository.findById(SOURCE_ID)
                .map(s -> s.isEnabled())
                .orElse(false);
    }

    private List<RawVacancy> fetchForCompany(Company company) {
        String atsType = company.getAtsType();
        return atsAdapterFactory.forType(atsType)
                .map(adapter -> {
                    try {
                        List<RawVacancy> jobs = adapter.fetchJobs(company);
                        log.debug("[{}] {} -> {} jobs", SOURCE_ID, company.getName(), jobs.size());
                        return jobs;
                    } catch (Exception e) {
                        log.error("[{}] Error crawling {}: {}", SOURCE_ID, company.getName(), e.getMessage());
                        return List.<RawVacancy>of();
                    }
                })
                .orElseGet(() -> {
                    log.warn("[{}] No ATS adapter for type '{}' (company: {})",
                            SOURCE_ID, atsType, company.getName());
                    return List.of();
                });
    }
}
