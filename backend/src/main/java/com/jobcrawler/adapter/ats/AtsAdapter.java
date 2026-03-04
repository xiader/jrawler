package com.jobcrawler.adapter.ats;

import com.jobcrawler.adapter.model.RawVacancy;
import com.jobcrawler.company.Company;

import java.util.List;

public interface AtsAdapter {

    /** ATS type string matching `companies.ats_type` column. */
    String getAtsType();

    /** Fetch raw vacancies from this company's ATS page. */
    List<RawVacancy> fetchJobs(Company company);
}
