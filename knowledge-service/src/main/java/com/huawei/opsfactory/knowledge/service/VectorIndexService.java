package com.huawei.opsfactory.knowledge.service;

import com.huawei.opsfactory.knowledge.common.error.RetrievalConfigurationException;
import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.repository.ChunkRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class VectorIndexService {

    private static final String INDEX_NAME = "vectors";
    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_SOURCE_ID = "sourceId";
    private static final String FIELD_VECTOR = "embedding";

    private final int vectorDimension;
    private final StorageManager storageManager;
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    public VectorIndexService(
        KnowledgeProperties properties,
        StorageManager storageManager,
        ChunkRepository chunkRepository,
        EmbeddingService embeddingService
    ) {
        this.vectorDimension = Math.max(1, Math.min(properties.getEmbedding().getDimensions(), 1024));
        this.storageManager = storageManager;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        List<SearchService.SearchableChunk> chunks = chunkRepository.findAll().stream()
            .map(this::toSearchableChunk)
            .toList();
        Map<String, List<Double>> vectors = embeddingService.ensureChunkEmbeddings(chunks);
        rebuildIndex(chunks, vectors);
    }

    public void rebuildIndex(List<SearchService.SearchableChunk> chunks, Map<String, List<Double>> vectors) {
        try (Directory directory = openDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (SearchService.SearchableChunk chunk : chunks) {
                    Document document = toDocument(chunk, vectors.get(chunk.id()));
                    if (document != null) {
                        writer.addDocument(document);
                    }
                }
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rebuild vector index", e);
        }
    }

    public void upsertChunks(Collection<SearchService.SearchableChunk> chunks, Map<String, List<Double>> vectors) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try (Directory directory = openDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (SearchService.SearchableChunk chunk : chunks) {
                    Document document = toDocument(chunk, vectors.get(chunk.id()));
                    if (document == null) {
                        writer.deleteDocuments(new Term(FIELD_CHUNK_ID, chunk.id()));
                    } else {
                        writer.updateDocument(new Term(FIELD_CHUNK_ID, chunk.id()), document);
                    }
                }
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upsert vector index documents", e);
        }
    }

    public void deleteChunk(String chunkId) {
        deleteByQuery(new TermQuery(new Term(FIELD_CHUNK_ID, chunkId)));
    }

    public void deleteDocument(String documentId) {
        deleteByQuery(new TermQuery(new Term(FIELD_DOCUMENT_ID, documentId)));
    }

    public void deleteSource(String sourceId) {
        deleteByQuery(new TermQuery(new Term(FIELD_SOURCE_ID, sourceId)));
    }

    public List<SemanticHit> search(List<Double> queryVector, List<SearchService.SearchableChunk> chunks, int topK) {
        if (topK <= 0 || queryVector == null || queryVector.isEmpty() || chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Set<String> allowedChunkIds = chunks.stream().map(SearchService.SearchableChunk::id).collect(Collectors.toSet());
        Query filter = new TermInSetQuery(FIELD_CHUNK_ID, allowedChunkIds.stream().map(BytesRef::new).toList());
        float[] target = toFloatArray(queryVector);
        if (target.length == 0) {
            return List.of();
        }

        try (Directory directory = openDirectory()) {
            if (!DirectoryReader.indexExists(directory)) {
                return List.of();
            }

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs topDocs = searcher.search(new KnnFloatVectorQuery(FIELD_VECTOR, target, topK, filter), topK);
                float maxScore = topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[0].score : 0;
                List<SemanticHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    String chunkId = document.get(FIELD_CHUNK_ID);
                    double normalizedScore = maxScore > 1 ? clamp(scoreDoc.score / maxScore) : clamp(scoreDoc.score);
                    hits.add(new SemanticHit(chunkId, normalizedScore));
                }
                return hits;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to search vector index", e);
        }
    }

    private void deleteByQuery(Query query) {
        try (Directory directory = openDirectory()) {
            if (!DirectoryReader.indexExists(directory)) {
                return;
            }
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(query);
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete vector index documents", e);
        }
    }

    private Document toDocument(SearchService.SearchableChunk chunk, List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        float[] luceneVector = toFloatArray(vector);
        if (luceneVector.length == 0) {
            return null;
        }
        Document document = new Document();
        document.add(new StringField(FIELD_CHUNK_ID, chunk.id(), Field.Store.YES));
        document.add(new StringField(FIELD_DOCUMENT_ID, chunk.documentId(), Field.Store.NO));
        document.add(new StringField(FIELD_SOURCE_ID, chunk.sourceId(), Field.Store.NO));
        document.add(new KnnFloatVectorField(FIELD_VECTOR, luceneVector, VectorSimilarityFunction.COSINE));
        return document;
    }

    private float[] toFloatArray(List<Double> vector) {
        if (vector.size() != vectorDimension) {
            throw new RetrievalConfigurationException(
                "Embedding dimension mismatch: expected " + vectorDimension + " but got " + vector.size()
            );
        }
        float[] floats = new float[vectorDimension];
        for (int i = 0; i < vectorDimension; i++) {
            floats[i] = vector.get(i).floatValue();
        }
        return floats;
    }

    private Directory openDirectory() throws IOException {
        Path indexDir = storageManager.indexDir(INDEX_NAME);
        Files.createDirectories(indexDir);
        return FSDirectory.open(indexDir);
    }

    private SearchService.SearchableChunk toSearchableChunk(ChunkRepository.ChunkRecord record) {
        return new SearchService.SearchableChunk(
            record.id(),
            record.documentId(),
            record.sourceId(),
            record.title(),
            record.titlePath(),
            record.keywords(),
            record.text(),
            record.markdown(),
            record.pageFrom(),
            record.pageTo(),
            record.ordinal(),
            record.editStatus(),
            record.updatedBy()
        );
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record SemanticHit(String chunkId, double score) {
    }
}
