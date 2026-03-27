import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { KNOWLEDGE_SERVICE_URL } from '../../config/runtime'
import { useToast } from '../../contexts/ToastContext'
import type {
    KnowledgeCapabilities,
    KnowledgeDefaults,
    KnowledgeDocumentSummary,
    KnowledgeProfileDetail,
    KnowledgeSource,
    PagedResponse,
} from '../../types/knowledge'

type RetrievalMode = 'semantic' | 'lexical' | 'hybrid'
interface RetrievalSettings {
    displayCount: number
    scoreThresholdEnabled: boolean
    scoreThreshold: number
}

interface RetrievalSearchHit {
    chunkId: string
    documentId: string
    sourceId: string
    title: string
    titlePath: string[]
    snippet: string
    score: number
    lexicalScore: number
    semanticScore: number
    fusionScore: number
    pageFrom: number | null
    pageTo: number | null
}

interface RetrievalCompareModeResponse {
    hits: RetrievalSearchHit[]
    total: number
}

interface RetrievalCompareResponse {
    query: string
    fetchedTopK: number
    hybrid: RetrievalCompareModeResponse
    semantic: RetrievalCompareModeResponse
    lexical: RetrievalCompareModeResponse
}

interface RetrievalFetchResponse {
    chunkId: string
    documentId: string
    sourceId: string
    title: string
    titlePath: string[]
    text: string
    markdown: string
    keywords: string[]
    pageFrom: number | null
    pageTo: number | null
    previousChunkId: string | null
    nextChunkId: string | null
}

interface RetrievalHistoryEntry {
    id: string
    query: string
    createdAt: string
}

interface RetrievalDisplayHit extends RetrievalSearchHit {
    documentName: string
    displayScore: number
    displayPercent: number
}

interface RetrievalSelection {
    mode: RetrievalMode
    hit: RetrievalDisplayHit
}

interface RetrievalModeResultState {
    hits: RetrievalSearchHit[]
    total: number
    error: string | null
}

interface CompareCacheState {
    query: string
    fetchedTopK: number
    results: Record<RetrievalMode, RetrievalModeResultState>
}

interface KnowledgeRetrievalTabProps {
    source: KnowledgeSource
    capabilities: KnowledgeCapabilities | null
    defaults: KnowledgeDefaults | null
    retrievalProfileDetail: KnowledgeProfileDetail | null
}

const QUERY_MAX_LENGTH = 200
const HISTORY_LIMIT = 8
const RECENT_HISTORY_LIMIT = 5
const TOP_K_MIN = 1
const TOP_K_MAX = 10
const COMPARE_FETCH_TOP_K = 64
const SCORE_THRESHOLD_MIN = 0
const SCORE_THRESHOLD_MAX = 1
const SCORE_THRESHOLD_STEP = 0.01
const MODE_ORDER: RetrievalMode[] = ['hybrid', 'semantic', 'lexical']

function clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value))
}

function formatScore(value: number): string {
    return value.toFixed(2)
}

function normalizeRetrievalMode(value: string | null | undefined): RetrievalMode | null {
    switch (value?.toLowerCase()) {
    case 'semantic':
    case 'vector':
        return 'semantic'
    case 'lexical':
    case 'keyword':
    case 'keywords':
    case 'full_text':
        return 'lexical'
    case 'hybrid':
        return 'hybrid'
    default:
        return null
    }
}

function getStorageKey(sourceId: string): string {
    return `opsfactory:knowledge:retrieval-history:${sourceId}:v1`
}

function normalizeHistoryEntry(raw: unknown): RetrievalHistoryEntry | null {
    if (!raw || typeof raw !== 'object') return null

    const record = raw as Record<string, unknown>
    const query = typeof record.query === 'string' ? record.query.trim() : ''
    if (!query) return null

    return {
        id: typeof record.id === 'string' ? record.id : `${Date.now()}:compare:${query}`,
        query,
        createdAt: typeof record.createdAt === 'string' ? record.createdAt : new Date().toISOString(),
    }
}

function loadHistory(storageKey: string): RetrievalHistoryEntry[] {
    if (typeof window === 'undefined') return []

    try {
        const raw = window.localStorage.getItem(storageKey)
        if (!raw) return []

        const parsed = JSON.parse(raw) as unknown
        if (!Array.isArray(parsed)) return []

        return parsed
            .map(entry => normalizeHistoryEntry(entry))
            .filter((entry): entry is RetrievalHistoryEntry => Boolean(entry))
            .slice(0, HISTORY_LIMIT)
    } catch {
        return []
    }
}

function saveHistory(storageKey: string, entries: RetrievalHistoryEntry[]): void {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(storageKey, JSON.stringify(entries))
}

function getTopK(defaults: KnowledgeDefaults | null): number {
    return clamp(defaults?.retrieval.finalTopK ?? 3, TOP_K_MIN, TOP_K_MAX)
}

function getSupportedModes(capabilities: KnowledgeCapabilities | null): Set<RetrievalMode> {
    const modes = (capabilities?.retrievalModes || [])
        .map(mode => normalizeRetrievalMode(mode))
        .filter((mode): mode is RetrievalMode => Boolean(mode))

    if (modes.length === 0) {
        return new Set<RetrievalMode>(['semantic', 'lexical', 'hybrid'])
    }

    return new Set(modes)
}

function getOrderedModes(supportedModes: Set<RetrievalMode>): RetrievalMode[] {
    return MODE_ORDER.filter(mode => supportedModes.has(mode))
}

function getConfiguredMode(
    defaults: KnowledgeDefaults | null,
    retrievalProfileDetail: KnowledgeProfileDetail | null,
    fallbackModes: RetrievalMode[]
): RetrievalMode {
    const profileMode = normalizeRetrievalMode(
        isRecord(retrievalProfileDetail?.config)
            ? getNestedString(retrievalProfileDetail.config, 'retrieval', 'mode')
            : null
    )
    if (profileMode) return profileMode

    const defaultMode = normalizeRetrievalMode(defaults?.retrieval.mode)
    if (defaultMode) return defaultMode

    return fallbackModes[0] || 'hybrid'
}

function buildInitialSettings(defaults: KnowledgeDefaults | null): RetrievalSettings {
    return {
        displayCount: getTopK(defaults),
        scoreThresholdEnabled: true,
        scoreThreshold: 0.3,
    }
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function getNestedString(value: Record<string, unknown>, ...keys: string[]): string | null {
    let current: unknown = value

    for (const key of keys) {
        if (!isRecord(current) || !(key in current)) {
            return null
        }
        current = current[key]
    }

    return typeof current === 'string' ? current : null
}

function getModeLabelKey(mode: RetrievalMode): string {
    switch (mode) {
    case 'semantic':
        return 'knowledge.retrievalModeSemantic'
    case 'lexical':
        return 'knowledge.retrievalModeLexical'
    case 'hybrid':
        return 'knowledge.retrievalModeHybrid'
    }
}

function getDocumentName(documentId: string, names: Record<string, string>): string {
    return names[documentId] || documentId
}

function getModeScore(hit: RetrievalSearchHit, mode: RetrievalMode): number {
    const modeScore = mode === 'hybrid'
        ? hit.fusionScore
        : mode === 'semantic'
            ? hit.semanticScore
            : hit.lexicalScore

    if (Number.isFinite(modeScore) && modeScore > 0) {
        return modeScore
    }

    return hit.score
}

function buildDisplayResults(
    mode: RetrievalMode,
    hits: RetrievalSearchHit[],
    documentNames: Record<string, string>,
    settings: RetrievalSettings
): RetrievalDisplayHit[] {
    return hits
        .filter(hit => !settings.scoreThresholdEnabled || getModeScore(hit, mode) >= settings.scoreThreshold)
        .slice(0, settings.displayCount)
        .map(hit => {
        const rawScore = getModeScore(hit, mode)

        return {
            ...hit,
            documentName: getDocumentName(hit.documentId, documentNames),
            displayScore: rawScore,
            displayPercent: Math.round(clamp(rawScore, 0, 1) * 100),
        }
    })
}

function upsertHistoryEntry(entries: RetrievalHistoryEntry[], entry: RetrievalHistoryEntry): RetrievalHistoryEntry[] {
    const remaining = entries.filter(item =>
        item.query !== entry.query
    )

    return [entry, ...remaining].slice(0, HISTORY_LIMIT)
}

function buildPageRange(pageFrom: number | null, pageTo: number | null, fallback: string): string {
    if (pageFrom === null || pageFrom === undefined) return fallback
    if (pageTo === null || pageTo === undefined || pageTo === pageFrom) return String(pageFrom)
    return `${pageFrom}-${pageTo}`
}

function createEmptyModeResults(): Record<RetrievalMode, RetrievalModeResultState> {
    return {
        semantic: {
            hits: [],
            total: 0,
            error: null,
        },
        lexical: {
            hits: [],
            total: 0,
            error: null,
        },
        hybrid: {
            hits: [],
            total: 0,
            error: null,
        },
    }
}

function toCompareModeResponse(payload: { hits?: RetrievalSearchHit[]; total?: number } | null | undefined): RetrievalCompareModeResponse {
    const hits = payload?.hits || []
    return {
        hits,
        total: payload?.total ?? hits.length,
    }
}

function RetrievalModePanel({
    mode,
    results,
    rawCount,
    error,
    searched,
    loading,
    selectedKey,
    onSelect,
}: {
    mode: RetrievalMode
    results: RetrievalDisplayHit[]
    rawCount: number
    error: string | null
    searched: boolean
    loading: boolean
    selectedKey: string | null
    onSelect: (mode: RetrievalMode, hit: RetrievalDisplayHit) => void
}) {
    const { t } = useTranslation()
    const emptyState = !searched
        ? t('knowledge.retrievalModeIdle')
        : rawCount > 0
            ? t('knowledge.retrievalNoResultsThreshold')
        : t('knowledge.retrievalNoResults')
    return (
        <section className="knowledge-retrieval-mode-panel">
            <div className="knowledge-retrieval-mode-panel-header">
                <div className="knowledge-retrieval-mode-panel-copy">
                    <div className="knowledge-retrieval-mode-panel-title-row">
                        <h3 className="knowledge-retrieval-mode-panel-title">{t(getModeLabelKey(mode))}</h3>
                        {mode === 'hybrid' && (
                            <span className="knowledge-retrieval-mode-pill">{t('knowledge.retrievalModeRecommended')}</span>
                        )}
                    </div>
                    <div className="resource-card-tags">
                        <span className="resource-card-tag">{t('knowledge.retrievalColumnRawCount', { count: rawCount })}</span>
                        <span className="resource-card-tag">{t('knowledge.retrievalColumnShownCount', { count: results.length })}</span>
                    </div>
                </div>

            </div>

            {error && (
                <div className="conn-banner conn-banner-error">
                    {t('common.connectionError', { error })}
                </div>
            )}

            <div className="knowledge-retrieval-mode-results">
                {loading ? (
                    <div className="knowledge-retrieval-mode-empty">{t('common.loading')}</div>
                ) : results.length === 0 ? (
                    <div className="knowledge-retrieval-mode-empty">{emptyState}</div>
                ) : (
                    results.map((hit, index) => {
                        const selectionKey = `${mode}:${hit.chunkId}`
                        return (
                            <button
                                key={selectionKey}
                                type="button"
                                className={`knowledge-retrieval-hit-card ${selectedKey === selectionKey ? 'selected' : ''}`}
                                onClick={() => onSelect(mode, hit)}
                            >
                                <span className="knowledge-retrieval-hit-rank">#{index + 1}</span>
                                <div className="knowledge-retrieval-hit-main">
                                    <div className="knowledge-retrieval-hit-head">
                                        <strong className="knowledge-retrieval-hit-title">{hit.documentName}</strong>
                                        <span className="knowledge-retrieval-score-pill">{formatScore(hit.displayScore)}</span>
                                    </div>
                                    <p className="knowledge-retrieval-hit-snippet">{hit.snippet || hit.title || hit.chunkId}</p>
                                    <div className="knowledge-retrieval-hit-footer">
                                        <span className="knowledge-retrieval-result-meta">
                                            {t('knowledge.retrievalPageShort')} {buildPageRange(hit.pageFrom, hit.pageTo, t('knowledge.notAvailable'))}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta">
                                            {hit.title || t('knowledge.notAvailable')}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta knowledge-retrieval-hit-chunk">
                                            {hit.chunkId}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta">
                                            {t('knowledge.retrievalModeScoreLabel')} {formatScore(hit.displayScore)}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta">
                                            {t('knowledge.retrievalLexicalScoreLabel')} {formatScore(hit.lexicalScore)}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta">
                                            {t('knowledge.retrievalSemanticScoreLabel')} {formatScore(hit.semanticScore)}
                                        </span>
                                        <span className="knowledge-retrieval-result-meta">
                                            {t('knowledge.retrievalFusionScoreLabel')} {formatScore(hit.fusionScore)}
                                        </span>
                                    </div>
                                    <div className="knowledge-retrieval-bar">
                                        <span style={{ width: `${hit.displayPercent}%` }} />
                                    </div>
                                </div>
                            </button>
                        )
                    })
                )}
            </div>
        </section>
    )
}

function RetrievalDetailPanel({
    selection,
    detail,
    loading,
    error,
    onClear,
    variant,
}: {
    selection: RetrievalSelection | null
    detail: RetrievalFetchResponse | null
    loading: boolean
    error: string | null
    onClear: () => void
    variant: 'panel' | 'modal'
}) {
    const { t } = useTranslation()

    if (!selection) {
        if (variant === 'modal') {
            return null
        }

        return (
            <aside className="knowledge-detail-panel knowledge-retrieval-preview-panel">
                <div className="knowledge-panel-header">
                    <div>
                        <h2 className="knowledge-panel-title">{t('knowledge.retrievalDetailTitle')}</h2>
                        <p className="knowledge-panel-description">{t('knowledge.retrievalDetailPlaceholder')}</p>
                    </div>
                </div>
                <div className="knowledge-doc-preview-empty">{t('knowledge.retrievalDetailEmpty')}</div>
            </aside>
        )
    }

    const { hit, mode } = selection
    const content = detail?.text || detail?.markdown || hit.snippet || ''
    const panelContent = (
        <>
            <div className="knowledge-panel-header">
                <div>
                    <h2 className="knowledge-panel-title">{hit.documentName}</h2>
                    <p className="knowledge-panel-description">{t(getModeLabelKey(mode))}</p>
                </div>
                <button type="button" className="knowledge-panel-close" onClick={onClear} aria-label={t('common.close')}>
                    &times;
                </button>
            </div>

            <div className="knowledge-panel-body">
                <div className="resource-card-tags">
                    <span className="resource-card-tag">{t('knowledge.retrievalModeScoreLabel')} {formatScore(hit.displayScore)}</span>
                    <span className="resource-card-tag">{t('knowledge.retrievalLexicalScoreLabel')} {formatScore(hit.lexicalScore)}</span>
                    <span className="resource-card-tag">{t('knowledge.retrievalSemanticScoreLabel')} {formatScore(hit.semanticScore)}</span>
                    <span className="resource-card-tag">{t('knowledge.retrievalFusionScoreLabel')} {formatScore(hit.fusionScore)}</span>
                    <span className="resource-card-tag">
                        {t('knowledge.retrievalPageShort')} {buildPageRange(detail?.pageFrom ?? hit.pageFrom, detail?.pageTo ?? hit.pageTo, t('knowledge.notAvailable'))}
                    </span>
                    <span className="resource-card-tag">{hit.chunkId}</span>
                </div>

                {loading ? (
                    <div className="knowledge-doc-preview-empty">{t('knowledge.retrievalDetailLoading')}</div>
                ) : error ? (
                    <div className="conn-banner conn-banner-error">
                        {t('common.connectionError', { error })}
                    </div>
                ) : (
                    <>
                        <section className="knowledge-panel-group">
                            <h3 className="knowledge-panel-group-title">{t('knowledge.retrievalDetailContent')}</h3>
                            <div className="knowledge-retrieval-detail-content-panel">
                                <div className="knowledge-retrieval-detail-content-text">{content || t('knowledge.notAvailable')}</div>
                            </div>
                        </section>

                        <section className="knowledge-panel-group">
                            <h3 className="knowledge-panel-group-title">{t('knowledge.retrievalDetailKeywords')}</h3>
                            {detail?.keywords && detail.keywords.length > 0 ? (
                                <div className="resource-card-tags knowledge-retrieval-keywords">
                                    {detail.keywords.map(keyword => (
                                        <span key={keyword} className="resource-card-tag">#{keyword}</span>
                                    ))}
                                </div>
                            ) : (
                                <p className="knowledge-section-empty">{t('knowledge.notAvailable')}</p>
                            )}
                        </section>
                    </>
                )}

                <section className="knowledge-panel-group">
                    <h3 className="knowledge-panel-group-title">{t('knowledge.retrievalDetailMetadata')}</h3>
                    <div className="knowledge-kv-grid knowledge-retrieval-detail-grid">
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalDetailDocument')}</span>
                            <span className="knowledge-kv-value">{hit.documentName}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalDetailMode')}</span>
                            <span className="knowledge-kv-value">{t(getModeLabelKey(mode))}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalDetailChunkId')}</span>
                            <span className="knowledge-kv-value knowledge-kv-code">{hit.chunkId}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalModeScoreLabel')}</span>
                            <span className="knowledge-kv-value">{formatScore(hit.displayScore)}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalLexicalScoreLabel')}</span>
                            <span className="knowledge-kv-value">{formatScore(hit.lexicalScore)}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalSemanticScoreLabel')}</span>
                            <span className="knowledge-kv-value">{formatScore(hit.semanticScore)}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalFusionScoreLabel')}</span>
                            <span className="knowledge-kv-value">{formatScore(hit.fusionScore)}</span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalDetailPageRange')}</span>
                            <span className="knowledge-kv-value">
                                {buildPageRange(detail?.pageFrom ?? hit.pageFrom, detail?.pageTo ?? hit.pageTo, t('knowledge.notAvailable'))}
                            </span>
                        </div>
                        <div className="knowledge-kv-item">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalDetailTitlePath')}</span>
                            <span className="knowledge-kv-value">
                                {(detail?.titlePath || hit.titlePath).length > 0
                                    ? (detail?.titlePath || hit.titlePath).join(' / ')
                                    : t('knowledge.notAvailable')}
                            </span>
                        </div>
                    </div>
                </section>
            </div>
        </>
    )

    if (variant === 'modal') {
        return (
            <div className="modal-overlay" onClick={onClear}>
                <div
                    className="modal knowledge-retrieval-detail-modal knowledge-retrieval-detail-modal-compare"
                    onClick={event => event.stopPropagation()}
                >
                    {panelContent}
                </div>
            </div>
        )
    }

    return (
        <aside className="knowledge-detail-panel knowledge-retrieval-preview-panel">
            {panelContent}
        </aside>
    )
}

export default function KnowledgeRetrievalTab({
    source,
    capabilities,
    defaults,
    retrievalProfileDetail,
}: KnowledgeRetrievalTabProps) {
    const { t } = useTranslation()
    const { showToast } = useToast()

    const allowRequestOverride = capabilities?.featureFlags.allowRequestOverride ?? true
    const systemSupportedModes = useMemo(() => getSupportedModes(capabilities), [capabilities])
    const configuredMode = useMemo(
        () => getConfiguredMode(defaults, retrievalProfileDetail, getOrderedModes(systemSupportedModes)),
        [defaults, retrievalProfileDetail, systemSupportedModes]
    )
    const supportedModes = useMemo(() => {
        if (allowRequestOverride) {
            return systemSupportedModes
        }

        return new Set<RetrievalMode>([configuredMode])
    }, [allowRequestOverride, configuredMode, systemSupportedModes])
    const orderedModes = useMemo(() => getOrderedModes(supportedModes), [supportedModes])
    const storageKey = useMemo(() => getStorageKey(source.id), [source.id])

    const [settings, setSettings] = useState<RetrievalSettings>(() => buildInitialSettings(defaults))
    const [query, setQuery] = useState('')
    const [history, setHistory] = useState<RetrievalHistoryEntry[]>(() => loadHistory(storageKey))
    const [documentNames, setDocumentNames] = useState<Record<string, string>>({})
    const [modeResults, setModeResults] = useState<Record<RetrievalMode, RetrievalModeResultState>>(() => createEmptyModeResults())
    const [compareCache, setCompareCache] = useState<CompareCacheState | null>(null)
    const [lastExecutedQuery, setLastExecutedQuery] = useState('')
    const [searchedModes, setSearchedModes] = useState<RetrievalMode[]>([])
    const [activeSearchModes, setActiveSearchModes] = useState<RetrievalMode[]>([])
    const [searchError, setSearchError] = useState<string | null>(null)
    const [selection, setSelection] = useState<RetrievalSelection | null>(null)
    const [detail, setDetail] = useState<RetrievalFetchResponse | null>(null)
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState<string | null>(null)

    useEffect(() => {
        setSettings(buildInitialSettings(defaults))
        setQuery('')
        setModeResults(createEmptyModeResults())
        setCompareCache(null)
        setLastExecutedQuery('')
        setSearchedModes([])
        setActiveSearchModes([])
        setSearchError(null)
        setSelection(null)
        setDetail(null)
        setDetailError(null)
    }, [defaults, orderedModes, source.id])

    useEffect(() => {
        setHistory(loadHistory(storageKey))
    }, [storageKey])

    useEffect(() => {
        saveHistory(storageKey, history)
    }, [history, storageKey])

    useEffect(() => {
        let cancelled = false

        const loadDocumentNames = async () => {
            try {
                const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/documents?sourceId=${source.id}&page=1&pageSize=100`)
                const data = await response.json().catch(() => null) as PagedResponse<KnowledgeDocumentSummary> | { message?: string } | null

                if (!response.ok) {
                    throw new Error(
                        data && typeof data === 'object' && 'message' in data
                            ? String(data.message || response.statusText)
                            : response.statusText
                    )
                }

                if (cancelled) return

                const items = (data as PagedResponse<KnowledgeDocumentSummary>).items || []
                setDocumentNames(Object.fromEntries(items.map(item => [item.id, item.name])))
            } catch {
                if (!cancelled) {
                    setDocumentNames({})
                }
            }
        }

        void loadDocumentNames()

        return () => {
            cancelled = true
        }
    }, [source.id])

    const displayResultsByMode = useMemo<Record<RetrievalMode, RetrievalDisplayHit[]>>(() => ({
        semantic: buildDisplayResults('semantic', modeResults.semantic.hits, documentNames, settings),
        lexical: buildDisplayResults('lexical', modeResults.lexical.hits, documentNames, settings),
        hybrid: buildDisplayResults('hybrid', modeResults.hybrid.hits, documentNames, settings),
    }), [documentNames, modeResults, settings])

    const selectedKey = selection ? `${selection.mode}:${selection.hit.chunkId}` : null
    const recentHistory = history.slice(0, RECENT_HISTORY_LIMIT)
    const hasSearchedVisibleModes = orderedModes.some(mode => searchedModes.includes(mode))
    const hasActiveVisibleModes = orderedModes.some(mode => activeSearchModes.includes(mode))
    const effectiveQuery = query.trim()
    const testButtonDisabled = activeSearchModes.length > 0
        || !effectiveQuery
        || (effectiveQuery === lastExecutedQuery && compareCache !== null)
    const compareDiagnostic = useMemo(() => {
        if (orderedModes.length < 2) return null
        if (!orderedModes.every(mode => searchedModes.includes(mode))) return null

        const referenceIds = modeResults[orderedModes[0]].hits.map(hit => hit.chunkId)
        const identicalResults = orderedModes.slice(1).every(mode => {
            const currentIds = modeResults[mode].hits.map(hit => hit.chunkId)
            return currentIds.length === referenceIds.length
                && currentIds.every((chunkId, index) => chunkId === referenceIds[index])
        })

        const semanticAllZero = modeResults.semantic.hits.length > 0
            && modeResults.semantic.hits.every(hit => hit.semanticScore <= 0)

        if (identicalResults && semanticAllZero) {
            return t('knowledge.retrievalCompareWarningSemanticInactive')
        }

        if (identicalResults) {
            return t('knowledge.retrievalCompareWarningIdentical')
        }

        if (semanticAllZero) {
            return t('knowledge.retrievalCompareWarningSemanticZero')
        }

        return null
    }, [modeResults, orderedModes, searchedModes, t])

    const executeCompareSearch = useCallback(async (
        effectiveQuery: string,
        modes: RetrievalMode[]
    ) => {
        const baseBody: Record<string, unknown> = {
            query: effectiveQuery,
            sourceIds: [source.id],
        }

        if (source.retrievalProfileId) {
            baseBody.retrievalProfileId = source.retrievalProfileId
        }

        const compareBody = {
            ...baseBody,
            modes,
        }

        const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/search/compare`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(compareBody),
        })
        const data = await response.json().catch(() => null) as RetrievalCompareResponse | { message?: string } | null

        if (response.status === 404 || response.status === 405) {
            const modeResponses = await Promise.all(modes.map(async mode => {
                const legacyResponse = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/search`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        ...baseBody,
                        topK: COMPARE_FETCH_TOP_K,
                        override: {
                            mode,
                            includeScores: true,
                            lexicalTopK: COMPARE_FETCH_TOP_K,
                            semanticTopK: COMPARE_FETCH_TOP_K,
                        },
                    }),
                })
                const legacyData = await legacyResponse.json().catch(() => null) as RetrievalCompareModeResponse | { hits?: RetrievalSearchHit[]; total?: number; message?: string } | null

                if (!legacyResponse.ok) {
                    throw new Error(
                        legacyData && typeof legacyData === 'object' && 'message' in legacyData
                            ? String(legacyData.message || legacyResponse.statusText)
                            : legacyResponse.statusText
                    )
                }

                return [mode, toCompareModeResponse(legacyData)] as const
            }))

            const resultMap = Object.fromEntries(modeResponses) as Partial<Record<RetrievalMode, RetrievalCompareModeResponse>>

            return {
                query: effectiveQuery,
                fetchedTopK: COMPARE_FETCH_TOP_K,
                hybrid: resultMap.hybrid ?? { hits: [], total: 0 },
                semantic: resultMap.semantic ?? { hits: [], total: 0 },
                lexical: resultMap.lexical ?? { hits: [], total: 0 },
            } satisfies RetrievalCompareResponse
        }

        if (!response.ok) {
            throw new Error(
                data && typeof data === 'object' && 'message' in data
                    ? String(data.message || response.statusText)
                    : response.statusText
            )
        }

        return data as RetrievalCompareResponse
    }, [source.id, source.retrievalProfileId])

    const executeSearch = useCallback(async (nextQuery?: string) => {
        const effectiveQuery = (nextQuery ?? query).trim()

        if (!effectiveQuery) {
            setSearchError(t('knowledge.retrievalQueryRequired'))
            return
        }

        const modesToQuery = orderedModes

        if (modesToQuery.length === 0) {
            return
        }

        setSearchError(null)
        setSelection(null)
        setDetail(null)
        setDetailError(null)
        setActiveSearchModes(modesToQuery)

        try {
            const compareResponse = await executeCompareSearch(effectiveQuery, modesToQuery)
            const nextResults: Record<RetrievalMode, RetrievalModeResultState> = {
                hybrid: {
                    hits: compareResponse.hybrid.hits || [],
                    total: compareResponse.hybrid.total ?? (compareResponse.hybrid.hits || []).length,
                    error: null,
                },
                semantic: {
                    hits: compareResponse.semantic.hits || [],
                    total: compareResponse.semantic.total ?? (compareResponse.semantic.hits || []).length,
                    error: null,
                },
                lexical: {
                    hits: compareResponse.lexical.hits || [],
                    total: compareResponse.lexical.total ?? (compareResponse.lexical.hits || []).length,
                    error: null,
                },
            }

            setModeResults(nextResults)
            setCompareCache({
                query: effectiveQuery,
                fetchedTopK: compareResponse.fetchedTopK,
                results: nextResults,
            })
            setLastExecutedQuery(effectiveQuery)
            setSearchedModes(modesToQuery)

            if (modesToQuery.length > 0) {
                setHistory(current => upsertHistoryEntry(current, {
                    id: `${Date.now()}:compare:${effectiveQuery}`,
                    query: effectiveQuery,
                    createdAt: new Date().toISOString(),
                }))
            }
        } catch (error) {
            const message = error instanceof Error ? error.message : t('errors.unknown')
            setSearchError(message)
            showToast('error', message)
        } finally {
            setActiveSearchModes([])
        }
    }, [executeCompareSearch, orderedModes, query, settings, showToast, t])

    const handleReplayHistory = useCallback((entry: RetrievalHistoryEntry) => {
        setQuery(entry.query)
        if (compareCache && lastExecutedQuery === entry.query) {
            return
        }
        void executeSearch(entry.query)
    }, [compareCache, executeSearch, lastExecutedQuery])

    useEffect(() => {
        if (!selection) {
            setDetail(null)
            setDetailError(null)
            setDetailLoading(false)
            return
        }

        let cancelled = false

        const loadDetail = async () => {
            setDetailLoading(true)
            setDetailError(null)

            try {
                const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/fetch/${selection.hit.chunkId}?includeNeighbors=true&neighborWindow=1`)
                const data = await response.json().catch(() => null) as RetrievalFetchResponse | { message?: string } | null

                if (!response.ok) {
                    throw new Error(
                        data && typeof data === 'object' && 'message' in data
                            ? String(data.message || response.statusText)
                            : response.statusText
                    )
                }

                if (!cancelled) {
                    setDetail(data as RetrievalFetchResponse)
                }
            } catch (err) {
                if (!cancelled) {
                    setDetail(null)
                    setDetailError(err instanceof Error ? err.message : t('errors.unknown'))
                }
            } finally {
                if (!cancelled) {
                    setDetailLoading(false)
                }
            }
        }

        void loadDetail()

        return () => {
            cancelled = true
        }
    }, [selection, t])

    useEffect(() => {
        if (!selection) return

        const stillVisible = displayResultsByMode[selection.mode]
            .some(hit => hit.chunkId === selection.hit.chunkId)

        if (!stillVisible) {
            setSelection(null)
        }
    }, [displayResultsByMode, selection])

    const sectionTitle = t('knowledge.retrievalCompareTitle')
    const sectionDescription = t('knowledge.retrievalCompareDescription', { count: settings.displayCount })
    const showCompareBoard = hasSearchedVisibleModes || hasActiveVisibleModes || Boolean(searchError)

    return (
        <>
            <div className="knowledge-detail-layout knowledge-retrieval-workbench">
            <div className="knowledge-detail-main">
                <section className="knowledge-section-card">
                    <div className="knowledge-section-header">
                        <div>
                            <h2 className="knowledge-section-title">{t('knowledge.retrievalTitle')}</h2>
                        </div>
                    </div>

                    <div className="knowledge-retrieval-query-head">
                        <div className="knowledge-retrieval-query-title-row">
                            <label className="form-label" htmlFor="knowledge-retrieval-query">{t('knowledge.retrievalQueryLabel')}</label>
                        </div>
                        <button
                            type="button"
                            className="btn btn-primary knowledge-section-action"
                            onClick={() => void executeSearch()}
                            disabled={testButtonDisabled}
                        >
                            {activeSearchModes.length > 0
                                ? t('knowledge.retrievalRunning')
                                : testButtonDisabled && effectiveQuery && compareCache && effectiveQuery === lastExecutedQuery
                                    ? t('knowledge.retrievalRunCurrent')
                                    : t('knowledge.retrievalRun')}
                        </button>
                    </div>

                    <div className="form-group">
                        <div className="knowledge-retrieval-query-shell">
                            <textarea
                                id="knowledge-retrieval-query"
                                className="form-input knowledge-retrieval-query"
                                rows={5}
                                maxLength={QUERY_MAX_LENGTH}
                                placeholder={t('knowledge.retrievalQueryPlaceholder')}
                                value={query}
                                onChange={event => setQuery(event.target.value)}
                            />
                            <span className="knowledge-retrieval-query-count">
                                {query.length}/{QUERY_MAX_LENGTH}
                            </span>
                        </div>
                    </div>

                    <div className="knowledge-retrieval-workbench-card is-flat">
                        <div className="knowledge-retrieval-workbench-section">
                            <div className="knowledge-retrieval-workbench-head">
                                <span className="knowledge-kv-label">{t('knowledge.retrievalMethodLabel')}</span>
                            </div>

                            <div className="knowledge-retrieval-settings-list is-two-column">
                                <div className="knowledge-retrieval-setting-row">
                                    <div className="knowledge-retrieval-setting-head">
                                        <label className="form-label" htmlFor="retrieval-top-k-input">{t('knowledge.retrievalDisplayCountLabel')}</label>
                                    </div>
                                    <div className="knowledge-retrieval-range-row">
                                        <input
                                            id="retrieval-top-k-input"
                                            className="form-input knowledge-retrieval-number-input"
                                            type="number"
                                            min={TOP_K_MIN}
                                            max={TOP_K_MAX}
                                            value={settings.displayCount}
                                            onChange={event => setSettings(current => ({
                                                ...current,
                                                displayCount: clamp(Number(event.target.value) || TOP_K_MIN, TOP_K_MIN, TOP_K_MAX),
                                            }))}
                                        />
                                        <input
                                            className="knowledge-retrieval-range-input"
                                            type="range"
                                            min={TOP_K_MIN}
                                            max={TOP_K_MAX}
                                            value={settings.displayCount}
                                            onChange={event => setSettings(current => ({
                                                ...current,
                                                displayCount: clamp(Number(event.target.value), TOP_K_MIN, TOP_K_MAX),
                                            }))}
                                        />
                                    </div>
                                </div>

                                <div className="knowledge-retrieval-setting-row">
                                    <div className="knowledge-retrieval-setting-head">
                                        <div className="knowledge-retrieval-threshold-head">
                                            <label className="form-label" htmlFor="retrieval-threshold-input">{t('knowledge.retrievalFilterThresholdLabel')}</label>
                                            <label className="mcp-toggle">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.scoreThresholdEnabled}
                                                    onChange={event => setSettings(current => ({
                                                        ...current,
                                                        scoreThresholdEnabled: event.target.checked,
                                                    }))}
                                                />
                                                <span className="mcp-toggle-slider" />
                                            </label>
                                        </div>
                                    </div>
                                    <div className={`knowledge-retrieval-range-row ${!settings.scoreThresholdEnabled ? 'is-disabled' : ''}`}>
                                        <input
                                            id="retrieval-threshold-input"
                                            className="form-input knowledge-retrieval-number-input"
                                            type="number"
                                            min={SCORE_THRESHOLD_MIN}
                                            max={SCORE_THRESHOLD_MAX}
                                            step={SCORE_THRESHOLD_STEP}
                                            value={settings.scoreThreshold}
                                            disabled={!settings.scoreThresholdEnabled}
                                            onChange={event => setSettings(current => ({
                                                ...current,
                                                scoreThreshold: clamp(Number(event.target.value) || 0, SCORE_THRESHOLD_MIN, SCORE_THRESHOLD_MAX),
                                            }))}
                                        />
                                        <input
                                            className="knowledge-retrieval-range-input"
                                            type="range"
                                            min={SCORE_THRESHOLD_MIN}
                                            max={SCORE_THRESHOLD_MAX}
                                            step={SCORE_THRESHOLD_STEP}
                                            value={settings.scoreThreshold}
                                            disabled={!settings.scoreThresholdEnabled}
                                            onChange={event => setSettings(current => ({
                                                ...current,
                                                scoreThreshold: clamp(Number(event.target.value), SCORE_THRESHOLD_MIN, SCORE_THRESHOLD_MAX),
                                            }))}
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {recentHistory.length > 0 && (
                        <div className="knowledge-retrieval-history-strip is-standalone">
                            <span className="knowledge-kv-label">{t('knowledge.retrievalRecentTitle')}</span>
                            <div className="knowledge-retrieval-history-chips">
                                {recentHistory.map(entry => (
                                    <button
                                        key={entry.id}
                                        type="button"
                                        className="knowledge-retrieval-history-chip"
                                        onClick={() => handleReplayHistory(entry)}
                                    >
                                        <span className="knowledge-retrieval-history-chip-query">{entry.query}</span>
                                        <span className="knowledge-retrieval-history-chip-mode">{t('knowledge.retrievalViewCompare')}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </section>

                <section className="knowledge-section-card">
                    <div className="knowledge-section-header">
                        <div>
                            <h2 className="knowledge-section-title">{sectionTitle}</h2>
                            <p className="knowledge-section-description">{sectionDescription}</p>
                        </div>
                    </div>

                    {searchError && (
                        <div className="conn-banner conn-banner-error">
                            {t('common.connectionError', { error: searchError })}
                        </div>
                    )}

                    {compareDiagnostic && (
                        <div className="conn-banner conn-banner-warning">
                            {compareDiagnostic}
                        </div>
                    )}

                    {!showCompareBoard ? (
                        <div className="knowledge-retrieval-results-placeholder">
                            {t('knowledge.retrievalCompareEmpty')}
                        </div>
                    ) : (
                        <div className="knowledge-retrieval-compare-grid">
                            {orderedModes.map(mode => (
                                <RetrievalModePanel
                                    key={mode}
                                    mode={mode}
                                    results={displayResultsByMode[mode]}
                                    rawCount={modeResults[mode].total}
                                    error={modeResults[mode].error}
                                    searched={searchedModes.includes(mode)}
                                    loading={activeSearchModes.includes(mode)}
                                    selectedKey={selectedKey}
                                    onSelect={(selectedMode, hit) => setSelection({ mode: selectedMode, hit })}
                                />
                            ))}
                        </div>
                    )}
                </section>
            </div>
            </div>

            <RetrievalDetailPanel
                selection={selection}
                detail={detail}
                loading={detailLoading}
                error={detailError}
                onClear={() => setSelection(null)}
                variant="modal"
            />
        </>
    )
}
