package com.huawei.opsfactory.knowledge.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.knowledge.common.util.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class EmbeddingRepository {

    private static final TypeReference<List<Double>> VECTOR_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<EmbeddingRecord> mapper = this::map;

    public EmbeddingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<EmbeddingRecord> findByChunkId(String chunkId) {
        return jdbcTemplate.query("select * from embedding_record where chunk_id = ?", mapper, chunkId).stream().findFirst();
    }

    public Map<String, EmbeddingRecord> findByChunkIds(Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = chunkIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<EmbeddingRecord> records = jdbcTemplate.query(
            "select * from embedding_record where chunk_id in (" + placeholders + ")",
            mapper,
            chunkIds.toArray()
        );
        return records.stream().collect(Collectors.toMap(EmbeddingRecord::chunkId, record -> record));
    }

    public void upsert(String chunkId, String model, List<Double> vector, String vectorHash) {
        Instant now = Instant.now();
        String vectorJson = writeVector(vector);
        int dimension = vector == null ? 0 : vector.size();
        Optional<EmbeddingRecord> existing = findByChunkId(chunkId);

        if (existing.isPresent()) {
            jdbcTemplate.update(
                "update embedding_record set model = ?, dimension = ?, vector_hash = ?, vector_json = ?, created_at = ? where chunk_id = ?",
                model, dimension, vectorHash, vectorJson, now.toString(), chunkId
            );
            return;
        }

        jdbcTemplate.update(
            "insert into embedding_record (id, chunk_id, model, dimension, vector_hash, vector_json, created_at) values (?,?,?,?,?,?,?)",
            Ids.newId("emb"), chunkId, model, dimension, vectorHash, vectorJson, now.toString()
        );
    }

    public void deleteByChunkId(String chunkId) {
        jdbcTemplate.update("delete from embedding_record where chunk_id = ?", chunkId);
    }

    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update("delete from embedding_record where chunk_id in (select id from document_chunk where document_id = ?)", documentId);
    }

    public void deleteBySourceId(String sourceId) {
        jdbcTemplate.update("delete from embedding_record where chunk_id in (select id from document_chunk where source_id = ?)", sourceId);
    }

    private EmbeddingRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new EmbeddingRecord(
            rs.getString("id"),
            rs.getString("chunk_id"),
            rs.getString("model"),
            rs.getInt("dimension"),
            rs.getString("vector_hash"),
            readVector(rs.getString("vector_json")),
            Instant.parse(rs.getString("created_at"))
        );
    }

    private List<Double> readVector(String json) {
        try {
            return objectMapper.readValue(json, VECTOR_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read embedding vector", e);
        }
    }

    private String writeVector(List<Double> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write embedding vector", e);
        }
    }

    public record EmbeddingRecord(
        String id,
        String chunkId,
        String model,
        int dimension,
        String vectorHash,
        List<Double> vector,
        Instant createdAt
    ) {
    }
}
