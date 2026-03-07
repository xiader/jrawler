package com.jrawler.adapter;

import com.jrawler.adapter.model.RawVacancy;
import com.jrawler.adapter.model.SearchCriteria;

import java.util.List;

public interface JobSearchAdapter {

    /** Source ID matching the `sources.id` column, e.g. "remoteok", "linkedin". */
    String getSourceId();

    /** Fetch raw vacancies for the given search criteria. Never throws — returns empty list on error. */
    List<RawVacancy> fetchJobs(SearchCriteria criteria);

    /** Whether this adapter is enabled (checked via the sources table). */
    boolean isEnabled();
}
