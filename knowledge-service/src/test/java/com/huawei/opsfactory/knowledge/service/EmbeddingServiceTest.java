package com.huawei.opsfactory.knowledge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.repository.EmbeddingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    @Test
    void shouldRecomputeCachedChunkEmbeddingWhenStoredDimensionDiffersFromCurrentRuntime() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setApiKey("");
        properties.getEmbedding().setDimensions(1024);
        EmbeddingRepository repository = mock(EmbeddingRepository.class);
        EmbeddingService service = new EmbeddingService(properties, repository, new ObjectMapper());

        SearchService.SearchableChunk chunk = new SearchService.SearchableChunk(
            "chk-1",
            "doc-1",
            "src-1",
            "ITSM",
            List.of("Operations"),
            List.of("itsm"),
            "ITSM deployment guide",
            "ITSM deployment guide",
            1,
            1,
            1,
            "ACTIVE",
            "tester"
        );

        when(repository.findByChunkIds(anyCollection())).thenReturn(Map.of(
            "chk-1",
            new EmbeddingRepository.EmbeddingRecord(
                "emb-1",
                "chk-1",
                properties.getEmbedding().getModel(),
                2560,
                embeddingHash(service.buildChunkEmbeddingText(chunk)),
                createVector(2560, 0.1d),
                Instant.now()
            )
        ));

        Map<String, List<Double>> resolved = service.ensureChunkEmbeddings(List.of(chunk));

        verify(repository).upsert(eq("chk-1"), eq(properties.getEmbedding().getModel()), any(), any());
        org.assertj.core.api.Assertions.assertThat(resolved.get("chk-1")).hasSize(1024);
    }

    @Test
    void shouldReuseCachedChunkEmbeddingWhenModelAndDimensionStillMatch() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setApiKey("");
        properties.getEmbedding().setDimensions(1024);
        EmbeddingRepository repository = mock(EmbeddingRepository.class);
        EmbeddingService service = new EmbeddingService(properties, repository, new ObjectMapper());

        SearchService.SearchableChunk chunk = new SearchService.SearchableChunk(
            "chk-1",
            "doc-1",
            "src-1",
            "ITSM",
            List.of("Operations"),
            List.of("itsm"),
            "ITSM deployment guide",
            "ITSM deployment guide",
            1,
            1,
            1,
            "ACTIVE",
            "tester"
        );

        List<Double> vector = createVector(1024, 0.1d);
        when(repository.findByChunkIds(anyCollection())).thenReturn(Map.of(
            "chk-1",
            new EmbeddingRepository.EmbeddingRecord(
                "emb-1",
                "chk-1",
                properties.getEmbedding().getModel(),
                1024,
                embeddingHash(service.buildChunkEmbeddingText(chunk)),
                vector,
                Instant.now()
            )
        ));

        Map<String, List<Double>> resolved = service.ensureChunkEmbeddings(List.of(chunk));

        verify(repository, never()).upsert(eq("chk-1"), eq(properties.getEmbedding().getModel()), any(), any());
        org.assertj.core.api.Assertions.assertThat(resolved.get("chk-1")).isEqualTo(vector);
    }

    @Test
    void shouldRecomputeCachedChunkEmbeddingWhenStoredModelDiffersFromCurrentRuntime() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setApiKey("");
        properties.getEmbedding().setDimensions(1024);
        EmbeddingRepository repository = mock(EmbeddingRepository.class);
        EmbeddingService service = new EmbeddingService(properties, repository, new ObjectMapper());

        SearchService.SearchableChunk chunk = new SearchService.SearchableChunk(
            "chk-1",
            "doc-1",
            "src-1",
            "ITSM",
            List.of("Operations"),
            List.of("itsm"),
            "ITSM deployment guide",
            "ITSM deployment guide",
            1,
            1,
            1,
            "ACTIVE",
            "tester"
        );

        when(repository.findByChunkIds(anyCollection())).thenReturn(Map.of(
            "chk-1",
            new EmbeddingRepository.EmbeddingRecord(
                "emb-1",
                "chk-1",
                "legacy-embedding-model",
                1024,
                embeddingHash(service.buildChunkEmbeddingText(chunk)),
                createVector(1024, 0.1d),
                Instant.now()
            )
        ));

        Map<String, List<Double>> resolved = service.ensureChunkEmbeddings(List.of(chunk));

        verify(repository).upsert(eq("chk-1"), eq(properties.getEmbedding().getModel()), any(), any());
        org.assertj.core.api.Assertions.assertThat(resolved.get("chk-1")).hasSize(1024);
    }

    @Test
    void shouldClampExpectedEmbeddingDimensionToLuceneLimit() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setApiKey("");
        properties.getEmbedding().setDimensions(4096);
        EmbeddingRepository repository = mock(EmbeddingRepository.class);
        EmbeddingService service = new EmbeddingService(properties, repository, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThat(service.expectedEmbeddingDimension()).isEqualTo(1024);
        org.assertj.core.api.Assertions.assertThat(service.embedQuery("ITSM deployment")).hasSize(1024);
    }

    private List<Double> createVector(int size, double value) {
        Double[] values = new Double[size];
        for (int index = 0; index < size; index++) {
            values[index] = value;
        }
        return List.of(values);
    }

    private String embeddingHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
