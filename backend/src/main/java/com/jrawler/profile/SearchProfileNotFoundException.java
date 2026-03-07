package com.jrawler.profile;

import java.util.UUID;

public class SearchProfileNotFoundException extends RuntimeException {
    public SearchProfileNotFoundException(UUID id) {
        super("Search profile not found: " + id);
    }
}
