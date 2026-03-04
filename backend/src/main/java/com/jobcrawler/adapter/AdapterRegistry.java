package com.jobcrawler.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdapterRegistry {

    private final List<JobSearchAdapter> adapters;

    public List<JobSearchAdapter> getAll() {
        return adapters;
    }

    public List<JobSearchAdapter> getEnabled() {
        return adapters.stream()
                .filter(JobSearchAdapter::isEnabled)
                .toList();
    }
}
