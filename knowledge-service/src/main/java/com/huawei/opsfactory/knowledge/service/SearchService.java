package com.huawei.opsfactory.knowledge.service;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final KnowledgeProperties properties;
    private final EmbeddingService embeddingService;
    private final LexicalIndexService lexicalIndexService;
    private final VectorIndexService vectorIndexService;

    public SearchService(
        KnowledgeProperties properties,
        EmbeddingService embeddingService,
        LexicalIndexService lexicalIndexService,
        VectorIndexService vectorIndexService
    ) {
        this.properties = properties;
        this.embeddingService = embeddingService;
        this.lexicalIndexService = lexicalIndexService;
        this.vectorIndexService = vectorIndexService;
    }

    public List<SearchMatch> search(List<SearchableChunk> chunks, String query, SearchOptions options) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        Map<String, SearchableChunk> chunkById = chunks.stream()
            .collect(Collectors.toMap(SearchableChunk::id, chunk -> chunk));
        List<LexicalIndexService.LexicalHit> lexicalHits = lexicalIndexService.search(normalizedQuery, chunks, options.lexicalTopK());
        Map<String, LexicalIndexService.LexicalHit> lexicalByChunkId = lexicalHits.stream()
            .collect(Collectors.toMap(LexicalIndexService.LexicalHit::chunkId, hit -> hit));

        List<Double> queryEmbedding = embeddingService.embedQuery(normalizedQuery);
        List<VectorIndexService.SemanticHit> semanticHits = vectorIndexService.search(queryEmbedding, chunks, options.semanticTopK());
        Map<String, Double> semanticByChunkId = semanticHits.stream()
            .collect(Collectors.toMap(VectorIndexService.SemanticHit::chunkId, VectorIndexService.SemanticHit::score));

        return switch (options.mode()) {
        case "semantic" -> filterByThreshold(semanticHits.stream()
            .map(hit -> {
                SearchableChunk chunk = chunkById.get(hit.chunkId());
                if (chunk == null) {
                    return null;
                }
                return new SearchMatch(chunk, hit.score(), lexicalScore(lexicalByChunkId, hit.chunkId()), hit.score(), hit.score());
            })
            .filter(java.util.Objects::nonNull)
            .toList(), options.scoreThreshold())
            .stream()
            .limit(options.finalTopK())
            .toList();
        case "hybrid" -> hybridSearch(chunkById, lexicalHits, semanticHits, lexicalByChunkId, semanticByChunkId, options);
        default -> filterByThreshold(lexicalHits.stream()
            .map(hit -> {
                SearchableChunk chunk = chunkById.get(hit.chunkId());
                if (chunk == null) {
                    return null;
                }
                double semanticScore = semanticScore(semanticByChunkId, hit.chunkId());
                return new SearchMatch(chunk, hit.score(), hit.score(), semanticScore, hit.score());
            })
            .filter(java.util.Objects::nonNull)
            .toList(), options.scoreThreshold())
            .stream()
            .limit(options.finalTopK())
            .toList();
        };
    }

    public ExplainResult explain(SearchableChunk chunk, String query, SearchOptions options) {
        String normalizedQuery = query == null ? "" : query.trim();
        List<Double> queryEmbedding = embeddingService.embedQuery(normalizedQuery);
        double semanticScore = vectorIndexService.search(queryEmbedding, List.of(chunk), 1).stream()
            .findFirst()
            .map(VectorIndexService.SemanticHit::score)
            .orElse(0.0);
        LexicalIndexService.LexicalHit lexicalHit = lexicalIndexService.search(normalizedQuery, List.of(chunk), 1).stream()
            .findFirst()
            .orElse(new LexicalIndexService.LexicalHit(chunk.id(), 0, List.of()));

        String fusionMode = "hybrid".equals(options.mode()) ? "rrf" : options.mode();
        double finalScore = switch (options.mode()) {
        case "semantic" -> semanticScore;
        case "hybrid" -> reciprocalRank(lexicalHit.score() > 0 ? 1 : null, options.rrfK())
            + reciprocalRank(semanticScore > 0 ? 1 : null, options.rrfK());
        default -> lexicalHit.score();
        };
        return new ExplainResult(lexicalHit.matchedFields(), lexicalHit.score(), semanticScore, finalScore, fusionMode);
    }

    private List<SearchMatch> hybridSearch(
        Map<String, SearchableChunk> chunkById,
        List<LexicalIndexService.LexicalHit> lexicalHits,
        List<VectorIndexService.SemanticHit> semanticHits,
        Map<String, LexicalIndexService.LexicalHit> lexicalByChunkId,
        Map<String, Double> semanticByChunkId,
        SearchOptions options
    ) {
        List<String> lexical = lexicalHits.stream()
            .limit(options.lexicalTopK())
            .map(LexicalIndexService.LexicalHit::chunkId)
            .toList();
        List<String> semantic = semanticHits.stream()
            .limit(options.semanticTopK())
            .map(VectorIndexService.SemanticHit::chunkId)
            .toList();

        Map<String, Integer> lexicalRanks = buildRanksForChunkIds(lexical);
        Map<String, Integer> semanticRanks = buildRanksForChunkIds(semantic);
        Map<String, SearchableChunk> union = new LinkedHashMap<>();
        lexical.forEach(chunkId -> {
            SearchableChunk chunk = chunkById.get(chunkId);
            if (chunk != null) {
                union.put(chunkId, chunk);
            }
        });
        semantic.forEach(chunkId -> {
            SearchableChunk chunk = chunkById.get(chunkId);
            if (chunk != null) {
                union.put(chunkId, chunk);
            }
        });

        return filterByThreshold(union.values().stream()
            .map(chunk -> {
                double lexicalScore = lexicalScore(lexicalByChunkId, chunk.id());
                double semanticScore = semanticScore(semanticByChunkId, chunk.id());
                double fusionScore = reciprocalRank(lexicalRanks.get(chunk.id()), options.rrfK())
                    + reciprocalRank(semanticRanks.get(chunk.id()), options.rrfK());
                return new SearchMatch(chunk, fusionScore, lexicalScore, semanticScore, fusionScore);
            })
            .sorted(Comparator.comparingDouble(SearchMatch::score).reversed()
                .thenComparing(Comparator.comparingDouble(SearchMatch::semanticScore).reversed())
                .thenComparing(Comparator.comparingDouble(SearchMatch::lexicalScore).reversed()))
            .toList(), options.scoreThreshold())
            .stream()
            .limit(options.finalTopK())
            .toList();
    }

    private <T> List<T> limit(List<T> items, int topK) {
        if (topK <= 0 || items.size() <= topK) {
            return items;
        }
        return new ArrayList<>(items.subList(0, topK));
    }

    private List<SearchMatch> filterByThreshold(List<SearchMatch> matches, Double threshold) {
        if (threshold == null) {
            return matches;
        }
        return matches.stream()
            .filter(match -> match.score() >= threshold)
            .toList();
    }

    private Map<String, Integer> buildRanksForChunkIds(List<String> chunkIds) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < chunkIds.size(); index++) {
            ranks.put(chunkIds.get(index), index + 1);
        }
        return ranks;
    }

    private double reciprocalRank(Integer rank, int rrfK) {
        if (rank == null || rank <= 0) {
            return 0;
        }
        return 1.0 / (rrfK + rank);
    }

    private double lexicalScore(Map<String, LexicalIndexService.LexicalHit> lexicalByChunkId, String chunkId) {
        LexicalIndexService.LexicalHit hit = lexicalByChunkId.get(chunkId);
        return hit != null ? hit.score() : 0;
    }

    private double semanticScore(Map<String, Double> semanticByChunkId, String chunkId) {
        return semanticByChunkId.getOrDefault(chunkId, 0.0);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record SearchOptions(
        String mode,
        int lexicalTopK,
        int semanticTopK,
        int finalTopK,
        int rrfK,
        Double scoreThreshold
    ) {
    }

    public record SearchableChunk(
        String id,
        String documentId,
        String sourceId,
        String title,
        List<String> titlePath,
        List<String> keywords,
        String text,
        String markdown,
        Integer pageFrom,
        Integer pageTo,
        int ordinal,
        String editStatus,
        String updatedBy
    ) {
    }

    public record SearchMatch(
        SearchableChunk chunk,
        double score,
        double lexicalScore,
        double semanticScore,
        double fusionScore
    ) {
    }

    public record ExplainResult(
        List<String> matchedFields,
        double lexicalScore,
        double semanticScore,
        double fusionScore,
        String fusionMode
    ) {
    }
}
