package com.jrawler.vacancy;

import java.util.UUID;

public class VacancyNotFoundException extends RuntimeException {
    public VacancyNotFoundException(UUID id) {
        super("Vacancy not found: " + id);
    }
}
