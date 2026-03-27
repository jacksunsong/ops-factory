package com.huawei.opsfactory.knowledge.service;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.repository.ChunkRepository;
import com.huawei.opsfactory.knowledge.repository.ProfileRepository;
import com.huawei.opsfactory.knowledge.repository.SourceRepository;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class LexicalIndexService {

    private static final String INDEX_NAME = "chunks";
    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_SOURCE_ID = "sourceId";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_TITLE_PATH = "titlePath";
    private static final String FIELD_KEYWORDS = "keywords";
    private static final String FIELD_TEXT = "text";

    private final StorageManager storageManager;
    private final KnowledgeProperties properties;
    private final ChunkRepository chunkRepository;
    private final SourceRepository sourceRepository;
    private final ProfileRepository profileRepository;
    private final ProfileBootstrapService profileBootstrapService;

    public LexicalIndexService(
        StorageManager storageManager,
        KnowledgeProperties properties,
        ChunkRepository chunkRepository,
        SourceRepository sourceRepository,
        ProfileRepository profileRepository,
        ProfileBootstrapService profileBootstrapService
    ) {
        this.storageManager = storageManager;
        this.properties = properties;
        this.chunkRepository = chunkRepository;
        this.sourceRepository = sourceRepository;
        this.profileRepository = profileRepository;
        this.profileBootstrapService = profileBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        chunkRepository.findAll().stream()
            .map(this::toSearchableChunk)
            .collect(Collectors.groupingBy(SearchService.SearchableChunk::sourceId))
            .forEach(this::rebuildSourceIndex);
    }

    public void rebuildIndex(List<SearchService.SearchableChunk> chunks) {
        chunks.stream()
            .collect(Collectors.groupingBy(SearchService.SearchableChunk::sourceId))
            .forEach(this::rebuildSourceIndex);
    }

    public void upsertChunks(List<SearchService.SearchableChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        chunks.stream()
            .collect(Collectors.groupingBy(SearchService.SearchableChunk::sourceId))
            .forEach(this::upsertSourceChunks);
    }

    public void deleteChunk(String sourceId, String chunkId) {
        deleteByQuery(sourceId, new TermQuery(new Term(FIELD_CHUNK_ID, chunkId)));
    }

    public void deleteDocument(String sourceId, String documentId) {
        deleteByQuery(sourceId, new TermQuery(new Term(FIELD_DOCUMENT_ID, documentId)));
    }

    public void deleteSource(String sourceId) {
        storageManager.deleteRecursively(storageManager.indexDir(INDEX_NAME).resolve(sourceId));
    }

    public List<LexicalHit> search(String query, List<SearchService.SearchableChunk> chunks, int topK) {
        if (topK <= 0 || query == null || query.isBlank() || chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<LexicalHit> mergedHits = new ArrayList<>();
        chunks.stream()
            .collect(Collectors.groupingBy(SearchService.SearchableChunk::sourceId))
            .forEach((sourceId, sourceChunks) -> mergedHits.addAll(searchSource(sourceId, query, sourceChunks, topK)));

        return mergedHits.stream()
            .sorted(java.util.Comparator.comparingDouble(LexicalHit::score).reversed())
            .limit(topK)
            .toList();
    }

    private void rebuildSourceIndex(String sourceId, List<SearchService.SearchableChunk> chunks) {
        ResolvedIndexSettings settings = resolveIndexSettings(sourceId);
        try (Directory directory = openDirectory(sourceId);
             Analyzer analyzer = buildAnalyzer(settings.indexAnalyzer())) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setSimilarity(buildSimilarity(settings));
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (SearchService.SearchableChunk chunk : chunks) {
                    writer.addDocument(toDocument(chunk));
                }
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rebuild lexical index for source " + sourceId, e);
        }
    }

    private void upsertSourceChunks(String sourceId, List<SearchService.SearchableChunk> chunks) {
        ResolvedIndexSettings settings = resolveIndexSettings(sourceId);
        try (Directory directory = openDirectory(sourceId);
             Analyzer analyzer = buildAnalyzer(settings.indexAnalyzer())) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setSimilarity(buildSimilarity(settings));
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (SearchService.SearchableChunk chunk : chunks) {
                    writer.updateDocument(new Term(FIELD_CHUNK_ID, chunk.id()), toDocument(chunk));
                }
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upsert lexical index documents for source " + sourceId, e);
        }
    }

    private void deleteByQuery(String sourceId, Query query) {
        ResolvedIndexSettings settings = resolveIndexSettings(sourceId);
        try (Directory directory = openDirectory(sourceId);
             Analyzer analyzer = buildAnalyzer(settings.indexAnalyzer())) {
            if (!DirectoryReader.indexExists(directory)) {
                return;
            }
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setSimilarity(buildSimilarity(settings));
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(query);
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete lexical index documents", e);
        }
    }

    private List<LexicalHit> searchSource(String sourceId, String query, List<SearchService.SearchableChunk> chunks, int topK) {
        Map<String, SearchService.SearchableChunk> chunkById = chunks.stream()
            .collect(Collectors.toMap(SearchService.SearchableChunk::id, chunk -> chunk));
        ResolvedIndexSettings settings = resolveIndexSettings(sourceId);
        Query luceneQuery = buildSearchQuery(query, chunkById.keySet(), settings);
        if (luceneQuery == null) {
            return List.of();
        }

        try (Directory directory = openDirectory(sourceId)) {
            if (!DirectoryReader.indexExists(directory)) {
                return List.of();
            }

            try (DirectoryReader reader = DirectoryReader.open(directory);
                 Analyzer analyzer = buildAnalyzer(settings.queryAnalyzer())) {
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(buildSimilarity(settings));
                TopDocs topDocs = searcher.search(luceneQuery, topK);
                float maxScore = topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[0].score : 0;
                List<String> queryTerms = analyzeToTerms(analyzer, query);
                List<LexicalHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    String chunkId = document.get(FIELD_CHUNK_ID);
                    SearchService.SearchableChunk chunk = chunkById.get(chunkId);
                    if (chunk == null) {
                        continue;
                    }
                    double normalizedScore = maxScore > 0 ? clamp(scoreDoc.score / maxScore) : 0;
                    hits.add(new LexicalHit(chunkId, normalizedScore, matchedFields(queryTerms, analyzer, chunk)));
                }
                return hits;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to search lexical index for source " + sourceId, e);
        }
    }

    private Query buildSearchQuery(String query, Set<String> allowedChunkIds, ResolvedIndexSettings settings) {
        if (allowedChunkIds.isEmpty()) {
            return null;
        }
        try (Analyzer analyzer = buildAnalyzer(settings.queryAnalyzer())) {
            Map<String, Float> boosts = new HashMap<>();
            boosts.put(FIELD_TITLE, (float) settings.titleBoost());
            boosts.put(FIELD_TITLE_PATH, (float) settings.titlePathBoost());
            boosts.put(FIELD_KEYWORDS, (float) settings.keywordBoost());
            boosts.put(FIELD_TEXT, (float) settings.contentBoost());

            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[] { FIELD_TITLE, FIELD_TITLE_PATH, FIELD_KEYWORDS, FIELD_TEXT },
                analyzer,
                boosts
            );
            parser.setDefaultOperator(QueryParser.Operator.OR);

            Query textQuery = parser.parse(QueryParser.escape(query));
            Query phraseBoostQuery = buildPhraseBoostQuery(analyzer, query, settings);
            Query filter = new TermInSetQuery(FIELD_CHUNK_ID, allowedChunkIds.stream().map(BytesRef::new).toList());

            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder()
                .add(textQuery, BooleanClause.Occur.MUST)
                .add(filter, BooleanClause.Occur.FILTER);
            if (phraseBoostQuery != null) {
                queryBuilder.add(phraseBoostQuery, BooleanClause.Occur.SHOULD);
            }
            return queryBuilder.build();
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to build lexical query", e);
        }
    }

    private Query buildPhraseBoostQuery(Analyzer analyzer, String query, ResolvedIndexSettings settings) {
        List<String> terms = analyzeToTerms(analyzer, query);
        if (terms.size() < 2) {
            return null;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        addPhraseClause(builder, FIELD_TITLE, terms, (float) (settings.titleBoost() * 2.0));
        addPhraseClause(builder, FIELD_TITLE_PATH, terms, (float) (settings.titlePathBoost() * 1.8));
        addPhraseClause(builder, FIELD_KEYWORDS, terms, (float) (settings.keywordBoost() * 1.6));
        addPhraseClause(builder, FIELD_TEXT, terms, (float) (settings.contentBoost() * 1.4));
        BooleanQuery phraseQuery = builder.build();
        return phraseQuery.clauses().isEmpty() ? null : phraseQuery;
    }

    private void addPhraseClause(BooleanQuery.Builder builder, String field, List<String> terms, float boost) {
        PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
        for (int i = 0; i < terms.size(); i++) {
            phraseBuilder.add(new Term(field, terms.get(i)), i);
        }
        builder.add(new BoostQuery(phraseBuilder.build(), boost), BooleanClause.Occur.SHOULD);
    }

    private Document toDocument(SearchService.SearchableChunk chunk) {
        Document document = new Document();
        document.add(new StringField(FIELD_CHUNK_ID, chunk.id(), Field.Store.YES));
        document.add(new StringField(FIELD_DOCUMENT_ID, chunk.documentId(), Field.Store.NO));
        document.add(new StringField(FIELD_SOURCE_ID, chunk.sourceId(), Field.Store.NO));
        document.add(new TextField(FIELD_TITLE, defaultString(chunk.title()), Field.Store.NO));
        document.add(new TextField(FIELD_TITLE_PATH, String.join(" ", chunk.titlePath()), Field.Store.NO));
        document.add(new TextField(FIELD_KEYWORDS, String.join(" ", chunk.keywords()), Field.Store.NO));
        document.add(new TextField(FIELD_TEXT, defaultString(chunk.text()), Field.Store.NO));
        return document;
    }

    private List<String> matchedFields(List<String> queryTerms, Analyzer analyzer, SearchService.SearchableChunk chunk) {
        List<String> fields = new ArrayList<>();
        if (hasTermOverlap(queryTerms, analyzeToTerms(analyzer, chunk.title()))) {
            fields.add("title");
        }
        if (hasTermOverlap(queryTerms, analyzeToTerms(analyzer, String.join(" ", chunk.titlePath())))) {
            fields.add("titlePath");
        }
        if (hasTermOverlap(queryTerms, analyzeToTerms(analyzer, String.join(" ", chunk.keywords())))) {
            fields.add("keywords");
        }
        if (hasTermOverlap(queryTerms, analyzeToTerms(analyzer, chunk.text()))) {
            fields.add("content");
        }
        return fields;
    }

    private boolean hasTermOverlap(List<String> queryTerms, List<String> fieldTerms) {
        if (queryTerms.isEmpty() || fieldTerms.isEmpty()) {
            return false;
        }
        Set<String> fieldTermSet = new HashSet<>(fieldTerms);
        return queryTerms.stream().anyMatch(fieldTermSet::contains);
    }

    private List<String> analyzeToTerms(Analyzer analyzer, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try (TokenStream tokenStream = analyzer.tokenStream("f", new StringReader(value))) {
            tokenStream.reset();
            CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
            List<String> terms = new ArrayList<>();
            while (tokenStream.incrementToken()) {
                String term = attr.toString().trim().toLowerCase(Locale.ROOT);
                if (!term.isBlank()) {
                    terms.add(term);
                }
            }
            tokenStream.end();
            return terms;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to analyze text", e);
        }
    }

    private Analyzer buildAnalyzer(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
        case "smartcn" -> new SmartChineseAnalyzer();
        case "keyword" -> new KeywordAnalyzer();
        default -> new StandardAnalyzer();
        };
    }

    private Directory openDirectory(String sourceId) throws IOException {
        Path indexDir = storageManager.indexDir(INDEX_NAME).resolve(sourceId);
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

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private BM25Similarity buildSimilarity(ResolvedIndexSettings settings) {
        return new BM25Similarity(settings.bm25K1(), settings.bm25B());
    }

    private ResolvedIndexSettings resolveIndexSettings(String sourceId) {
        KnowledgeProperties.Analysis analysisDefaults = properties.getAnalysis();
        KnowledgeProperties.Indexing indexingDefaults = properties.getIndexing();
        Map<String, Object> profileConfig = sourceRepository.findById(sourceId)
            .flatMap(source -> Optional.ofNullable(source.indexProfileId()))
            .flatMap(profileRepository::findIndexById)
            .map(ProfileRepository.ProfileRecord::config)
            .orElseGet(() -> profileBootstrapService.defaultIndexProfileId() == null
                ? Map.of()
                : profileRepository.findIndexById(profileBootstrapService.defaultIndexProfileId())
                    .map(ProfileRepository.ProfileRecord::config)
                    .orElse(Map.of()));

        Map<String, Object> analysis = section(profileConfig, "analysis");
        Map<String, Object> indexing = section(profileConfig, "indexing");
        Map<String, Object> bm25 = section(indexing, "bm25");

        return new ResolvedIndexSettings(
            stringValue(analysis.get("indexAnalyzer"), analysisDefaults.getIndexAnalyzer()),
            stringValue(analysis.get("queryAnalyzer"), analysisDefaults.getQueryAnalyzer()),
            numberValue(indexing.get("titleBoost"), indexingDefaults.getTitleBoost()),
            numberValue(indexing.get("titlePathBoost"), indexingDefaults.getTitlePathBoost()),
            numberValue(indexing.get("keywordBoost"), indexingDefaults.getKeywordBoost()),
            numberValue(indexing.get("contentBoost"), indexingDefaults.getContentBoost()),
            floatValue(bm25.get("k1"), indexingDefaults.getBm25().getK1()),
            floatValue(bm25.get("b"), indexingDefaults.getBm25().getB())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private double numberValue(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private float floatValue(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record LexicalHit(String chunkId, double score, List<String> matchedFields) {
    }

    private record ResolvedIndexSettings(
        String indexAnalyzer,
        String queryAnalyzer,
        double titleBoost,
        double titlePathBoost,
        double keywordBoost,
        double contentBoost,
        float bm25K1,
        float bm25B
    ) {
    }
}
