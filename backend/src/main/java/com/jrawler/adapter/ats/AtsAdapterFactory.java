package com.jrawler.adapter.ats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AtsAdapterFactory {

    private final List<AtsAdapter> adapters;

    private Map<String, AtsAdapter> adapterMap;

    @jakarta.annotation.PostConstruct
    private void init() {
        adapterMap = adapters.stream()
                .collect(Collectors.toMap(AtsAdapter::getAtsType, Function.identity()));
    }

    public Optional<AtsAdapter> forType(String atsType) {
        if (atsType == null) return Optional.empty();
        return Optional.ofNullable(adapterMap.get(atsType.toLowerCase()));
    }
}
