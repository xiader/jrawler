package com.jrawler.adaptation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdaptationStore {

    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public record StoredAdaptation(String docxBase64, List<EditDto> edits) {}

    public String save(byte[] docxBytes, List<EditDto> edits) {
        String id = UUID.randomUUID().toString();
        StoredAdaptation stored = new StoredAdaptation(
                Base64.getEncoder().encodeToString(docxBytes), edits);
        try {
            redisTemplate.opsForValue().set(key(id), objectMapper.writeValueAsString(stored), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize adaptation", e);
        }
        return id;
    }

    public Optional<StoredAdaptation> find(String id) {
        String json = redisTemplate.opsForValue().get(key(id));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, StoredAdaptation.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String key(String id) {
        return "adaptation:" + id;
    }
}
