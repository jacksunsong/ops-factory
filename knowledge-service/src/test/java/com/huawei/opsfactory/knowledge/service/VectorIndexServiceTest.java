package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.huawei.opsfactory.knowledge.config.KnowledgeRuntimeProperties;
import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorIndexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIndexAndSearchWhenEmbeddingsMatchConfiguredDimension() {
        KnowledgeRuntimeProperties runtimeProperties = new KnowledgeRuntimeProperties();
        runtimeProperties.setBaseDir(tempDir.toString());
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setDimensions(1024);
        StorageManager storageManager = new StorageManager(runtimeProperties);
        VectorIndexService service = new VectorIndexService(
            properties,
            storageManager,
            mock(com.huawei.opsfactory.knowledge.repository.ChunkRepository.class),
            mock(EmbeddingService.class)
        );

        SearchService.SearchableChunk chunk = new SearchService.SearchableChunk(
            "chk-1",
            "doc-1",
            "src-1",
            "ITSM deployment",
            List.of("Operations", "Deployment"),
            List.of("itsm", "deployment"),
            "ITSM deployment lives in the operations environment",
            "ITSM deployment lives in the operations environment",
            1,
            1,
            1,
            "ACTIVE",
            "tester"
        );

        List<Double> vector = createVector(1024, 0.25d);
        service.rebuildIndex(List.of(chunk), java.util.Map.of(chunk.id(), vector));

        List<VectorIndexService.SemanticHit> hits = service.search(vector, List.of(chunk), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().chunkId()).isEqualTo(chunk.id());
        assertThat(hits.getFirst().score()).isGreaterThan(0);
    }

    @Test
    void shouldFailFastWhenEmbeddingDimensionDiffersFromConfiguredDimension() {
        KnowledgeRuntimeProperties runtimeProperties = new KnowledgeRuntimeProperties();
        runtimeProperties.setBaseDir(tempDir.toString());
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setDimensions(1024);
        StorageManager storageManager = new StorageManager(runtimeProperties);
        VectorIndexService service = new VectorIndexService(
            properties,
            storageManager,
            mock(com.huawei.opsfactory.knowledge.repository.ChunkRepository.class),
            mock(EmbeddingService.class)
        );

        SearchService.SearchableChunk chunk = new SearchService.SearchableChunk(
            "chk-1",
            "doc-1",
            "src-1",
            "ITSM deployment",
            List.of("Operations", "Deployment"),
            List.of("itsm", "deployment"),
            "ITSM deployment lives in the operations environment",
            "ITSM deployment lives in the operations environment",
            1,
            1,
            1,
            "ACTIVE",
            "tester"
        );

        assertThatThrownBy(() -> service.rebuildIndex(List.of(chunk), java.util.Map.of(chunk.id(), createVector(384, 0.25d))))
            .isInstanceOf(com.huawei.opsfactory.knowledge.common.error.RetrievalConfigurationException.class)
            .hasMessageContaining("Embedding dimension mismatch");
    }

    private List<Double> createVector(int size, double value) {
        Double[] values = new Double[size];
        for (int index = 0; index < size; index++) {
            values[index] = value;
        }
        return List.of(values);
    }
}
