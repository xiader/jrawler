package com.jobcrawler.company;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository repository;

    public List<CompanyDto.Response> findAll() {
        return repository.findAll().stream()
                .map(CompanyDto::toResponse)
                .toList();
    }

    public CompanyDto.Response findById(UUID id) {
        return repository.findById(id)
                .map(CompanyDto::toResponse)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    @Transactional
    public CompanyDto.Response create(CompanyDto.Request request) {
        Company company = new Company();
        applyRequest(company, request);
        return CompanyDto.toResponse(repository.save(company));
    }

    @Transactional
    public CompanyDto.Response update(UUID id, CompanyDto.Request request) {
        Company company = repository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        applyRequest(company, request);
        return CompanyDto.toResponse(repository.save(company));
    }

    @Transactional
    public CompanyDto.Response toggleActive(UUID id) {
        Company company = repository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        company.setActive(!company.isActive());
        return CompanyDto.toResponse(repository.save(company));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new CompanyNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private void applyRequest(Company company, CompanyDto.Request request) {
        company.setName(request.name());
        company.setCareerPageUrl(request.careerPageUrl());
        company.setAtsType(request.atsType());
        company.setAtsCompanyId(request.atsCompanyId());
        company.setCustomSelectors(request.customSelectors());
        company.setActive(request.active());
    }
}
