package com.jrawler.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, String> {

    List<Source> findByEnabledTrue();
}
