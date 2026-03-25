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
type RetrievalViewMode = RetrievalMode | 'compare'

interface RetrievalSettings {
    topK: number
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

interface RetrievalSearchResponse {
    query: string
    hits: RetrievalSearchHit[]
    total: number
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
    viewMode: RetrievalViewMode
    topK: number
    scoreThresholdEnabled: boolean
    scoreThreshold: number
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

function normalizeViewMode(value: string | null | undefined): RetrievalViewMode | null {
    if (value === 'compare') return 'compare'
    return normalizeRetrievalMode(value)
}

function getStorageKey(sourceId: string): string {
    return `opsfactory:knowledge:retrieval-history:${sourceId}:v1`
}

function normalizeHistoryEntry(raw: unknown): RetrievalHistoryEntry | null {
    if (!raw || typeof raw !== 'object') return null

    const record = raw as Record<string, unknown>
    const query = typeof record.query === 'string' ? record.query.trim() : ''
    if (!query) return null

    const legacyMode = typeof record.mode === 'string' ? normalizeRetrievalMode(record.mode) : null
    const viewMode = typeof record.viewMode === 'string'
        ? normalizeViewMode(record.viewMode)
        : legacyMode

    if (!viewMode) return null

    return {
        id: typeof record.id === 'string' ? record.id : `${Date.now()}:${viewMode}:${query}`,
        query,
        viewMode,
        topK: clamp(Number(record.topK) || TOP_K_MIN, TOP_K_MIN, TOP_K_MAX),
        scoreThresholdEnabled: Boolean(record.scoreThresholdEnabled),
        scoreThreshold: clamp(
            Number(record.scoreThreshold) || 0,
            SCORE_THRESHOLD_MIN,
            SCORE_THRESHOLD_MAX
        ),
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

function buildInitialViewMode(capabilities: KnowledgeCapabilities | null): RetrievalViewMode {
    const orderedModes = getOrderedModes(getSupportedModes(capabilities))
    if (orderedModes.length > 1) return 'compare'
    return orderedModes[0] || 'hybrid'
}

function buildInitialSettings(defaults: KnowledgeDefaults | null): RetrievalSettings {
    return {
        topK: getTopK(defaults),
        scoreThresholdEnabled: false,
        scoreThreshold: 0.2,
    }
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

function getViewModeLabelKey(viewMode: RetrievalViewMode): string {
    if (viewMode === 'compare') {
        return 'knowledge.retrievalViewCompare'
    }

    return getModeLabelKey(viewMode)
}

function getDocumentName(documentId: string, names: Record<string, string>): string {
    return names[documentId] || documentId
}

function buildDisplayResults(
    hits: RetrievalSearchHit[],
    documentNames: Record<string, string>,
    settings: RetrievalSettings
): RetrievalDisplayHit[] {
    const maxScore = hits.reduce((currentMax, hit) => Math.max(currentMax, hit.score || 0), 0)
    const useRawScore = maxScore > 0 && maxScore <= 1.0001

    return hits
        .map(hit => {
            const normalized = maxScore > 0
                ? clamp(useRawScore ? hit.score : hit.score / maxScore, 0, 1)
                : 0

            return {
                ...hit,
                documentName: getDocumentName(hit.documentId, documentNames),
                displayScore: normalized,
                displayPercent: Math.round(normalized * 100),
            }
        })
        .filter(hit => !settings.scoreThresholdEnabled || hit.displayScore >= settings.scoreThreshold)
}

function upsertHistoryEntry(entries: RetrievalHistoryEntry[], entry: RetrievalHistoryEntry): RetrievalHistoryEntry[] {
    const remaining = entries.filter(item =>
        !(
            item.query === entry.query
            && item.viewMode === entry.viewMode
            && item.topK === entry.topK
            && item.scoreThresholdEnabled === entry.scoreThresholdEnabled
            && item.scoreThreshold === entry.scoreThreshold
        )
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

function RetrievalModePanel({
    mode,
    results,
    rawCount,
    error,
    searched,
    loading,
    thresholdEnabled,
    selectedKey,
    onSelect,
    onFocusMode,
    single,
}: {
    mode: RetrievalMode
    results: RetrievalDisplayHit[]
    rawCount: number
    error: string | null
    searched: boolean
    loading: boolean
    thresholdEnabled: boolean
    selectedKey: string | null
    onSelect: (mode: RetrievalMode, hit: RetrievalDisplayHit) => void
    onFocusMode?: (mode: RetrievalMode) => void
    single?: boolean
}) {
    const { t } = useTranslation()
    const emptyState = !searched
        ? t('knowledge.retrievalModeIdle')
        : rawCount > 0 && thresholdEnabled && results.length === 0
            ? t('knowledge.retrievalNoResultsThreshold')
            : t('knowledge.retrievalNoResults')
    const showFocusAction = Boolean(onFocusMode) && searched

    return (
        <section className={`knowledge-retrieval-mode-panel ${single ? 'is-single' : ''}`}>
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

                {showFocusAction && onFocusMode && (
                    <button
                        type="button"
                        className="btn btn-secondary knowledge-retrieval-focus-btn"
                        onClick={() => onFocusMode(mode)}
                    >
                        {t('knowledge.retrievalFocusModeAction')}
                    </button>
                )}
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
                    <span className="resource-card-tag">{t('knowledge.retrievalScoreLabel')} {formatScore(hit.displayScore)}</span>
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
                            <span className="knowledge-kv-label">{t('knowledge.retrievalScoreLabel')}</span>
                            <span className="knowledge-kv-value">{formatScore(hit.displayScore)}</span>
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

    const supportedModes = useMemo(() => getSupportedModes(capabilities), [capabilities])
    const orderedModes = useMemo(() => getOrderedModes(supportedModes), [supportedModes])
    const canCompare = orderedModes.length > 1
    const storageKey = useMemo(() => getStorageKey(source.id), [source.id])

    const [viewMode, setViewMode] = useState<RetrievalViewMode>(() => buildInitialViewMode(capabilities))
    const [settings, setSettings] = useState<RetrievalSettings>(() => buildInitialSettings(defaults))
    const [query, setQuery] = useState('')
    const [history, setHistory] = useState<RetrievalHistoryEntry[]>(() => loadHistory(storageKey))
    const [documentNames, setDocumentNames] = useState<Record<string, string>>({})
    const [modeResults, setModeResults] = useState<Record<RetrievalMode, RetrievalModeResultState>>(() => createEmptyModeResults())
    const [searchedModes, setSearchedModes] = useState<RetrievalMode[]>([])
    const [activeSearchModes, setActiveSearchModes] = useState<RetrievalMode[]>([])
    const [searchError, setSearchError] = useState<string | null>(null)
    const [selection, setSelection] = useState<RetrievalSelection | null>(null)
    const [detail, setDetail] = useState<RetrievalFetchResponse | null>(null)
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState<string | null>(null)

    useEffect(() => {
        setViewMode(buildInitialViewMode(capabilities))
        setSettings(buildInitialSettings(defaults))
        setQuery('')
        setModeResults(createEmptyModeResults())
        setSearchedModes([])
        setActiveSearchModes([])
        setSearchError(null)
        setSelection(null)
        setDetail(null)
        setDetailError(null)
    }, [source.id])

    useEffect(() => {
        if (viewMode === 'compare' && canCompare) return
        if (viewMode !== 'compare' && supportedModes.has(viewMode)) return

        setViewMode(canCompare ? 'compare' : orderedModes[0] || 'hybrid')
    }, [canCompare, orderedModes, supportedModes, viewMode])

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
        semantic: buildDisplayResults(modeResults.semantic.hits, documentNames, settings),
        lexical: buildDisplayResults(modeResults.lexical.hits, documentNames, settings),
        hybrid: buildDisplayResults(modeResults.hybrid.hits, documentNames, settings),
    }), [documentNames, modeResults, settings])

    const visibleModes = useMemo(
        () => (viewMode === 'compare' ? orderedModes : [viewMode]),
        [orderedModes, viewMode]
    )

    const selectedKey = selection ? `${selection.mode}:${selection.hit.chunkId}` : null
    const recentHistory = history.slice(0, RECENT_HISTORY_LIMIT)
    const bindingName = retrievalProfileDetail?.name || source.retrievalProfileId || t('knowledge.notBound')
    const selectedModeCount = viewMode === 'compare' ? 0 : displayResultsByMode[viewMode].length
    const isCompareView = viewMode === 'compare'
    const showDetailPanel = !isCompareView && Boolean(selection)
    const hasSearchedVisibleModes = visibleModes.some(mode => searchedModes.includes(mode))
    const hasActiveVisibleModes = visibleModes.some(mode => activeSearchModes.includes(mode))

    const handleChangeViewMode = useCallback((nextViewMode: RetrievalViewMode) => {
        setViewMode(nextViewMode)
        setSearchError(null)
        setDetail(null)
        setDetailError(null)

        if (nextViewMode === 'compare') {
            setSelection(null)
            return
        }

        if (selection && selection.mode !== nextViewMode) {
            setSelection(null)
        }
    }, [selection])

    const executeModeSearch = useCallback(async (
        mode: RetrievalMode,
        effectiveQuery: string,
        effectiveSettings: RetrievalSettings
    ) => {
        const body: Record<string, unknown> = {
            query: effectiveQuery,
            sourceIds: [source.id],
            topK: effectiveSettings.topK,
            override: {
                mode,
                includeScores: true,
            },
        }

        if (source.retrievalProfileId) {
            body.retrievalProfileId = source.retrievalProfileId
        }

        const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/search`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(body),
        })
        const data = await response.json().catch(() => null) as RetrievalSearchResponse | { message?: string } | null

        if (!response.ok) {
            throw new Error(
                data && typeof data === 'object' && 'message' in data
                    ? String(data.message || response.statusText)
                    : response.statusText
            )
        }

        const nextHits = (data as RetrievalSearchResponse).hits || []
        return {
            hits: nextHits,
            total: (data as RetrievalSearchResponse).total ?? nextHits.length,
        }
    }, [source.id, source.retrievalProfileId])

    const executeSearch = useCallback(async (
        nextQuery?: string,
        nextViewMode?: RetrievalViewMode,
        nextSettings?: RetrievalSettings
    ) => {
        const effectiveQuery = (nextQuery ?? query).trim()
        const effectiveViewMode = nextViewMode ?? viewMode
        const effectiveSettings = nextSettings ?? settings

        if (!effectiveQuery) {
            setSearchError(t('knowledge.retrievalQueryRequired'))
            return
        }

        const modesToQuery = effectiveViewMode === 'compare'
            ? orderedModes
            : [effectiveViewMode]

        if (modesToQuery.length === 0) {
            return
        }

        setSearchError(null)
        setSelection(null)
        setDetail(null)
        setDetailError(null)
        setActiveSearchModes(modesToQuery)

        try {
            const settled = await Promise.allSettled(
                modesToQuery.map(mode => executeModeSearch(mode, effectiveQuery, effectiveSettings))
            )

            setModeResults(current => {
                const next = { ...current }

                settled.forEach((result, index) => {
                    const mode = modesToQuery[index]

                    if (result.status === 'fulfilled') {
                        next[mode] = {
                            hits: result.value.hits,
                            total: result.value.total,
                            error: null,
                        }
                        return
                    }

                    next[mode] = {
                        hits: [],
                        total: 0,
                        error: result.reason instanceof Error ? result.reason.message : t('errors.unknown'),
                    }
                })

                return next
            })

            setSearchedModes(current => Array.from(new Set([...current, ...modesToQuery])))

            const successfulModes = settled.filter(result => result.status === 'fulfilled').length

            if (successfulModes > 0) {
                setHistory(current => upsertHistoryEntry(current, {
                    id: `${Date.now()}:${effectiveViewMode}:${effectiveQuery}`,
                    query: effectiveQuery,
                    viewMode: effectiveViewMode,
                    topK: effectiveSettings.topK,
                    scoreThresholdEnabled: effectiveSettings.scoreThresholdEnabled,
                    scoreThreshold: effectiveSettings.scoreThreshold,
                    createdAt: new Date().toISOString(),
                }))
            } else {
                const firstError = settled.find(result => result.status === 'rejected')
                const message = firstError && firstError.status === 'rejected' && firstError.reason instanceof Error
                    ? firstError.reason.message
                    : t('errors.unknown')
                setSearchError(message)
                showToast('error', message)
            }
        } finally {
            setActiveSearchModes([])
        }
    }, [executeModeSearch, orderedModes, query, settings, showToast, t, viewMode])

    const handleReplayHistory = useCallback((entry: RetrievalHistoryEntry) => {
        const nextViewMode = entry.viewMode === 'compare' && !canCompare
            ? orderedModes[0] || 'hybrid'
            : entry.viewMode

        const replaySettings: RetrievalSettings = {
            topK: entry.topK,
            scoreThresholdEnabled: entry.scoreThresholdEnabled,
            scoreThreshold: entry.scoreThreshold,
        }

        setQuery(entry.query)
        handleChangeViewMode(nextViewMode)
        setSettings(replaySettings)
        void executeSearch(entry.query, nextViewMode, replaySettings)
    }, [canCompare, executeSearch, handleChangeViewMode, orderedModes])

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

        if (!visibleModes.includes(selection.mode)) {
            setSelection(null)
            return
        }

        const stillVisible = displayResultsByMode[selection.mode]
            .some(hit => hit.chunkId === selection.hit.chunkId)

        if (!stillVisible) {
            setSelection(null)
        }
    }, [displayResultsByMode, selection, visibleModes])

    useEffect(() => {
        if (isCompareView || selection || activeSearchModes.length > 0) return

        for (const mode of visibleModes) {
            const firstHit = displayResultsByMode[mode][0]
            if (firstHit) {
                setSelection({ mode, hit: firstHit })
                return
            }
        }
    }, [activeSearchModes.length, displayResultsByMode, isCompareView, selection, visibleModes])

    const sectionTitle = viewMode === 'compare'
        ? t('knowledge.retrievalCompareTitle')
        : t('knowledge.retrievalResultsTitle', { count: selectedModeCount })
    const sectionDescription = viewMode === 'compare'
        ? t('knowledge.retrievalCompareDescription', { count: settings.topK })
        : t('knowledge.retrievalResultsDescription')
    const showCompareBoard = isCompareView && (hasSearchedVisibleModes || hasActiveVisibleModes || Boolean(searchError))
    const showSinglePanel = !isCompareView && (hasSearchedVisibleModes || hasActiveVisibleModes || Boolean(searchError))

    return (
        <>
            <div className={`knowledge-detail-layout knowledge-retrieval-workbench ${showDetailPanel ? 'has-panel' : ''}`.trim()}>
            <div className="knowledge-detail-main">
                <section className="knowledge-section-card">
                    <div className="knowledge-section-header">
                        <div>
                            <h2 className="knowledge-section-title">{t('knowledge.retrievalTitle')}</h2>
                            <p className="knowledge-section-description">{t('knowledge.retrievalWorkbenchDescription')}</p>
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="knowledge-retrieval-query">{t('knowledge.retrievalQueryLabel')}</label>
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

                    <div className="knowledge-retrieval-guidance">
                        {t('knowledge.retrievalGuidance')}
                    </div>

                    <div className="knowledge-retrieval-control-stack">
                        <div className="knowledge-retrieval-control-toprow">
                            <div className="knowledge-retrieval-view-block">
                                <span className="knowledge-kv-label">{t('knowledge.retrievalMethodLabel')}</span>
                                <div className="seg-filter knowledge-retrieval-mode-filter" role="tablist" aria-label={t('knowledge.retrievalMethodLabel')}>
                                    {canCompare && (
                                        <button
                                            type="button"
                                            className={`seg-filter-btn ${viewMode === 'compare' ? 'active' : ''}`}
                                            onClick={() => handleChangeViewMode('compare')}
                                        >
                                            {t('knowledge.retrievalViewCompare')}
                                        </button>
                                    )}
                                    {orderedModes.map(mode => (
                                        <button
                                            key={mode}
                                            type="button"
                                            className={`seg-filter-btn ${viewMode === mode ? 'active' : ''}`}
                                            onClick={() => handleChangeViewMode(mode)}
                                        >
                                            {t(getModeLabelKey(mode))}
                                        </button>
                                    ))}
                                </div>
                                <p className="knowledge-form-help">{t('knowledge.retrievalThresholdRelativeHint')}</p>
                            </div>

                            <div className="knowledge-retrieval-run-card">
                                <div className="knowledge-retrieval-run-copy">
                                    <span className="knowledge-kv-label">{t('knowledge.retrievalCurrentBinding')}</span>
                                    <strong className="knowledge-retrieval-run-title">{bindingName}</strong>
                                    <p className="knowledge-form-help">{t('knowledge.retrievalBindingHint', { name: bindingName })}</p>
                                </div>
                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => void executeSearch()}
                                    disabled={activeSearchModes.length > 0 || !query.trim()}
                                >
                                    {activeSearchModes.length > 0 ? t('knowledge.retrievalRunning') : t('knowledge.retrievalRun')}
                                </button>
                            </div>
                        </div>

                        <div className="knowledge-retrieval-control-grid">
                            <div className="knowledge-retrieval-parameter-card">
                                <div className="knowledge-retrieval-parameter-head">
                                    <label className="form-label" htmlFor="retrieval-top-k-input">{t('knowledge.retrievalTopKLabel')}</label>
                                    <span className="knowledge-retrieval-parameter-value">{settings.topK}</span>
                                </div>
                                <p className="knowledge-form-help">{t('knowledge.retrievalTopKHint')}</p>
                                <div className="knowledge-retrieval-range-row">
                                    <input
                                        id="retrieval-top-k-input"
                                        className="form-input knowledge-retrieval-number-input"
                                        type="number"
                                        min={TOP_K_MIN}
                                        max={TOP_K_MAX}
                                        value={settings.topK}
                                        onChange={event => setSettings(current => ({
                                            ...current,
                                            topK: clamp(Number(event.target.value) || TOP_K_MIN, TOP_K_MIN, TOP_K_MAX),
                                        }))}
                                    />
                                    <input
                                        className="knowledge-retrieval-range-input"
                                        type="range"
                                        min={TOP_K_MIN}
                                        max={TOP_K_MAX}
                                        value={settings.topK}
                                        onChange={event => setSettings(current => ({
                                            ...current,
                                            topK: clamp(Number(event.target.value), TOP_K_MIN, TOP_K_MAX),
                                        }))}
                                    />
                                </div>
                            </div>

                            <div className="knowledge-retrieval-parameter-card">
                                <div className="knowledge-retrieval-parameter-head">
                                    <div className="knowledge-retrieval-threshold-head">
                                        <label className="form-label" htmlFor="retrieval-threshold-input">{t('knowledge.retrievalThresholdLabel')}</label>
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
                                    <span className="knowledge-retrieval-parameter-value">
                                        {settings.scoreThresholdEnabled
                                            ? formatScore(settings.scoreThreshold)
                                            : t('knowledge.retrievalThresholdOff')}
                                    </span>
                                </div>
                                <p className="knowledge-form-help">{t('knowledge.retrievalThresholdHint')}</p>
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

                    {recentHistory.length > 0 && (
                        <div className="knowledge-retrieval-history-strip">
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
                                        <span className="knowledge-retrieval-history-chip-mode">{t(getViewModeLabelKey(entry.viewMode))}</span>
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

                    {isCompareView && !showCompareBoard ? (
                        <div className="knowledge-retrieval-results-placeholder">
                            {t('knowledge.retrievalCompareEmpty')}
                        </div>
                    ) : isCompareView ? (
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
                                    thresholdEnabled={settings.scoreThresholdEnabled}
                                    selectedKey={selectedKey}
                                    onSelect={(selectedMode, hit) => setSelection({ mode: selectedMode, hit })}
                                    onFocusMode={nextMode => handleChangeViewMode(nextMode)}
                                />
                            ))}
                        </div>
                    ) : !showSinglePanel ? (
                        <div className="knowledge-retrieval-results-placeholder">
                            {t('knowledge.retrievalEmptyDescription')}
                        </div>
                    ) : (
                        <RetrievalModePanel
                            mode={viewMode}
                            results={displayResultsByMode[viewMode]}
                            rawCount={modeResults[viewMode].total}
                            error={modeResults[viewMode].error}
                            searched={searchedModes.includes(viewMode)}
                            loading={activeSearchModes.includes(viewMode)}
                            thresholdEnabled={settings.scoreThresholdEnabled}
                            selectedKey={selectedKey}
                            onSelect={(selectedMode, hit) => setSelection({ mode: selectedMode, hit })}
                            single
                        />
                    )}
                </section>
            </div>

            {showDetailPanel && (
                <RetrievalDetailPanel
                    selection={selection}
                    detail={detail}
                    loading={detailLoading}
                    error={detailError}
                    onClear={() => setSelection(null)}
                    variant="panel"
                />
            )}
            </div>

            {isCompareView && (
                <RetrievalDetailPanel
                    selection={selection}
                    detail={detail}
                    loading={detailLoading}
                    error={detailError}
                    onClear={() => setSelection(null)}
                    variant="modal"
                />
            )}
        </>
    )
}
