package com.jrawler.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationStoreTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private AdaptationStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new AdaptationStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void savesWithTtlAndRoundTrips() {
        byte[] docx = {1, 2, 3};
        List<EditDto> edits = List.of(new EditDto(2, "old text", "new text"));

        String id = store.save(docx, edits);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("adaptation:" + id), json.capture(), eq(Duration.ofHours(1)));

        when(valueOps.get("adaptation:" + id)).thenReturn(json.getValue());
        Optional<AdaptationStore.StoredAdaptation> loaded = store.find(id);

        assertThat(loaded).isPresent();
        assertThat(Base64.getDecoder().decode(loaded.get().docxBase64())).isEqualTo(docx);
        assertThat(loaded.get().edits()).containsExactly(new EditDto(2, "old text", "new text"));
    }

    @Test
    void findReturnsEmptyWhenKeyMissing() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertThat(store.find("nope")).isEmpty();
    }
}
