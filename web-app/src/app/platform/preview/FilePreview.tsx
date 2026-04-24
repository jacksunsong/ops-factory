import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Maximize2, Minimize2 } from 'lucide-react'
import { usePreview } from '../providers/PreviewContext'
import { useToast } from '../providers/ToastContext'
import { useUser } from '../providers/UserContext'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import './FilePreview.css'
import { inferFileType, needsTextContent } from '../../../utils/filePreview'
import OnlyOfficePreview from './OnlyOfficePreview'
import { GATEWAY_URL, GATEWAY_SECRET_KEY, KNOWLEDGE_SERVICE_URL, gatewayHeaders, isAdminUser } from '../../../config/runtime'

const MAX_LOG_LINE_NUMBER_LINES = 12000
const MAX_LOG_LINE_NUMBER_CHARS = 1200000

interface KnowledgeSourceOption {
    id: string
    name: string
    status: string
}

interface KnowledgeSourceListResponse {
    items: KnowledgeSourceOption[]
}

interface KnowledgeIngestResponse {
    documentCount: number
}

// Map file extensions to highlight.js language names
const HLJS_LANG_MAP: Record<string, string> = {
    js: 'javascript',
    ts: 'typescript',
    jsx: 'javascript',
    tsx: 'typescript',
    py: 'python',
    sh: 'bash',
    bash: 'bash',
    yaml: 'yaml',
    yml: 'yaml',
    json: 'json',
    html: 'html',
    htm: 'html',
    css: 'css',
    sql: 'sql',
    xml: 'xml',
    svg: 'xml',
    go: 'go',
    rs: 'rust',
    java: 'java',
    rb: 'ruby',
    php: 'php',
    c: 'c',
    cpp: 'cpp',
    h: 'c',
    hpp: 'cpp',
    cs: 'csharp',
    swift: 'swift',
    kt: 'kotlin',
    scala: 'scala',
    r: 'r',
    lua: 'lua',
    perl: 'perl',
    dockerfile: 'dockerfile',
    makefile: 'makefile',
    nginx: 'nginx',
    ini: 'ini',
    toml: 'ini',
    diff: 'diff',
    graphql: 'graphql',
    vue: 'xml',
    svelte: 'xml',
}

// Map file types to display names
function getLanguageName(type: string): string {
    const map: Record<string, string> = {
        js: 'JavaScript',
        ts: 'TypeScript',
        jsx: 'JSX',
        tsx: 'TSX',
        py: 'Python',
        sh: 'Shell',
        bash: 'Bash',
        yaml: 'YAML',
        yml: 'YAML',
        json: 'JSON',
        html: 'HTML',
        htm: 'HTML',
        css: 'CSS',
        md: 'Markdown',
        markdown: 'Markdown',
        txt: 'Text',
        sql: 'SQL',
        xml: 'XML',
        svg: 'SVG',
        go: 'Go',
        rs: 'Rust',
        java: 'Java',
        csv: 'CSV',
        tsv: 'TSV',
        pdf: 'PDF',
        mp3: 'Audio',
        wav: 'Audio',
        ogg: 'Audio',
        m4a: 'Audio',
        mp4: 'Video',
        webm: 'Video',
        mov: 'Video',
        docx: 'DOCX',
        doc: 'DOC',
        xlsx: 'XLSX',
        xls: 'XLS',
        pptx: 'PPTX',
        ppt: 'PPT',
    }
    return map[type.toLowerCase()] || type.toUpperCase()
}

// Safely decode URL-encoded filename
function decodeFileName(name: string): string {
    try {
        return decodeURIComponent(name)
    } catch {
        return name
    }
}

function appendRootId(url: string, rootId?: string): string {
    if (!rootId) return url
    return `${url}${url.includes('?') ? '&' : '?'}rootId=${encodeURIComponent(rootId)}`
}

function renderLineNumberText(content: string, className: string) {
    const lines = content.split('\n')

    return (
        <div className={className}>
            {lines.map((line, index) => (
                <div key={`line-${index + 1}`} className="file-preview-lined-row">
                    <span className="file-preview-line-number">{index + 1}</span>
                    <span className="file-preview-line-content">{line || '\u00A0'}</span>
                </div>
            ))}
        </div>
    )
}

function renderHighlightedLineNumberText(rawContent: string, highlightedContent: string, className: string) {
    const rawLines = rawContent.split('\n')
    const highlightedLines = highlightedContent.split('\n')

    return (
        <div className={`${className} is-highlighted`}>
            {rawLines.map((_, index) => (
                <div key={`line-${index + 1}`} className="file-preview-lined-row">
                    <span className="file-preview-line-number">{index + 1}</span>
                    <span className="file-preview-line-content">
                        <code dangerouslySetInnerHTML={{ __html: highlightedLines[index] || '&nbsp;' }} />
                    </span>
                </div>
            ))}
        </div>
    )
}

function shouldUseLineNumbers(content: string, displayType: string, previewKind?: string): boolean {
    if (previewKind === 'markdown' || displayType === 'txt') {
        return true
    }

    if (displayType === 'log') {
        const lineCount = content.split('\n').length
        return lineCount <= MAX_LOG_LINE_NUMBER_LINES && content.length <= MAX_LOG_LINE_NUMBER_CHARS
    }

    return false
}

export default function FilePreview({ embedded = false }: { embedded?: boolean }) {
    const { t } = useTranslation()
    const navigate = useNavigate()
    const { showToast } = useToast()
    const {
        previewFile,
        isLoading,
        error,
        isPreviewFullscreen,
        openPreview,
        closePreview,
        togglePreviewFullscreen,
        exitPreviewFullscreen,
    } = usePreview()
    const { userId, role } = useUser()
    const [copied, setCopied] = useState(false)
    const [showSource, setShowSource] = useState(false)
    const [isEditorOpen, setIsEditorOpen] = useState(false)
    const [editDraft, setEditDraft] = useState('')
    const [editError, setEditError] = useState<string | null>(null)
    const [isSaving, setIsSaving] = useState(false)
    const [isKnowledgeMenuOpen, setIsKnowledgeMenuOpen] = useState(false)
    const [knowledgeSources, setKnowledgeSources] = useState<KnowledgeSourceOption[]>([])
    const [selectedKnowledgeSourceId, setSelectedKnowledgeSourceId] = useState('')
    const [knowledgeSourcesLoading, setKnowledgeSourcesLoading] = useState(false)
    const [knowledgeImporting, setKnowledgeImporting] = useState(false)
    const [knowledgeImportError, setKnowledgeImportError] = useState<string | null>(null)
    const [knowledgeImportSuccess, setKnowledgeImportSuccess] = useState<{ sourceId: string; sourceName: string; count: number } | null>(null)
    const knowledgeSourcesRequestRef = useRef(0)

    // Reset state when file changes
    useEffect(() => {
        setCopied(false)
        setShowSource(false)
        setIsEditorOpen(false)
        setEditDraft(previewFile?.content || '')
        setEditError(null)
        setIsKnowledgeMenuOpen(false)
        setKnowledgeImportError(null)
        setKnowledgeImportSuccess(null)
    }, [previewFile?.path, previewFile?.rootId])

    useEffect(() => {
        if (!isPreviewFullscreen) return

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                exitPreviewFullscreen()
            }
        }

        window.addEventListener('keydown', handleKeyDown)
        return () => window.removeEventListener('keydown', handleKeyDown)
    }, [exitPreviewFullscreen, isPreviewFullscreen])

    const handleCopy = useCallback(async () => {
        if (!previewFile?.content) return
        try {
            await navigator.clipboard.writeText(previewFile.content)
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
        } catch (err) {
            console.error('Failed to copy:', err)
            showToast('error', t('errors.copyFailed'))
        }
    }, [previewFile?.content, showToast, t])

    const handleOpenEditor = useCallback(() => {
        setEditDraft(previewFile?.content || '')
        setEditError(null)
        setIsEditorOpen(true)
    }, [previewFile?.content])

    const handleCloseEditor = useCallback(() => {
        if (isSaving) return
        setIsEditorOpen(false)
        setEditError(null)
    }, [isSaving])

    const handleSaveEdit = useCallback(async () => {
        if (!previewFile?.agentId) return

        const targetFile = {
            name: previewFile.name,
            path: previewFile.path,
            type: previewFile.type,
            agentId: previewFile.agentId,
            rootId: previewFile.rootId,
            displayPath: previewFile.displayPath,
        }

        setIsSaving(true)
        setEditError(null)

        try {
            const response = await fetch(appendRootId(`${GATEWAY_URL}/agents/${targetFile.agentId}/files/${encodeURIComponent(targetFile.path)}`, targetFile.rootId), {
                method: 'PUT',
                headers: gatewayHeaders(userId),
                body: JSON.stringify({ content: editDraft }),
            })

            if (!response.ok) {
                const data = await response.json().catch(() => null)
                throw new Error(data?.error || `Failed to save file: ${response.status}`)
            }

            setIsEditorOpen(false)
            showToast('success', t('files.editSaveSuccess'))
            window.dispatchEvent(new CustomEvent('opsfactory:file-updated', {
                detail: { agentId: targetFile.agentId, rootId: targetFile.rootId, path: targetFile.path },
            }))
            await openPreview(targetFile)
        } catch (err) {
            console.error('Failed to save file:', err)
            const message = err instanceof Error ? err.message : t('files.editSaveFailed')
            setEditError(message)
            showToast('error', t('files.editSaveFailed'))
        } finally {
            setIsSaving(false)
        }
    }, [editDraft, openPreview, previewFile, showToast, t, userId])

    const getDownloadUrl = useCallback(() => {
        if (!previewFile) return ''
        if (previewFile.downloadUrl) {
            return previewFile.downloadUrl
        }
        if (!previewFile.agentId) {
            return ''
        }
        let url = `${GATEWAY_URL}/agents/${previewFile.agentId}/files/${encodeURIComponent(previewFile.path)}?key=${GATEWAY_SECRET_KEY}`
        if (previewFile.rootId) url += `&rootId=${encodeURIComponent(previewFile.rootId)}`
        if (userId) url += `&uid=${encodeURIComponent(userId)}`
        return url
    }, [previewFile, userId])

    const loadKnowledgeSources = useCallback(async () => {
        const requestId = knowledgeSourcesRequestRef.current + 1
        knowledgeSourcesRequestRef.current = requestId
        setKnowledgeSourcesLoading(true)
        setKnowledgeImportError(null)
        setKnowledgeSources([])

        try {
            const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/sources?page=1&pageSize=100`)
            const data = await response.json().catch(() => null) as KnowledgeSourceListResponse | { message?: string } | null

            if (!response.ok) {
                throw new Error((data as { message?: string } | null)?.message || response.statusText)
            }

            const sources = ((data as KnowledgeSourceListResponse | null)?.items || [])
                .filter(source => source.status?.toUpperCase() === 'ACTIVE')
            if (knowledgeSourcesRequestRef.current !== requestId) return
            setKnowledgeSources(sources)
            setSelectedKnowledgeSourceId(current => {
                if (current && sources.some(source => source.id === current)) {
                    return current
                }
                return sources[0]?.id || ''
            })
        } catch (err) {
            if (knowledgeSourcesRequestRef.current !== requestId) return
            const message = err instanceof Error ? err.message : t('errors.unknown')
            setKnowledgeSources([])
            setSelectedKnowledgeSourceId('')
            setKnowledgeImportError(t('files.knowledgeLoadFailed', { error: message }))
        } finally {
            if (knowledgeSourcesRequestRef.current === requestId) {
                setKnowledgeSourcesLoading(false)
            }
        }
    }, [t])

    const handleToggleKnowledgeMenu = useCallback(() => {
        if (!isKnowledgeMenuOpen) {
            setKnowledgeImportError(null)
            setKnowledgeImportSuccess(null)
            void loadKnowledgeSources()
        }
        setIsKnowledgeMenuOpen(current => !current)
    }, [isKnowledgeMenuOpen, loadKnowledgeSources])

    const handleImportToKnowledge = useCallback(async () => {
        if (!previewFile || !selectedKnowledgeSourceId) return

        const source = knowledgeSources.find(item => item.id === selectedKnowledgeSourceId)
        const downloadUrl = getDownloadUrl()
        if (!source || !downloadUrl) return

        setKnowledgeImporting(true)
        setKnowledgeImportError(null)
        setKnowledgeImportSuccess(null)

        try {
            const fileUrl = `${downloadUrl}${downloadUrl.includes('?') ? '&' : '?'}download=true`
            const fileResponse = await fetch(fileUrl, {
                headers: previewFile.downloadUrl ? undefined : gatewayHeaders(userId),
            })

            if (!fileResponse.ok) {
                throw new Error(fileResponse.statusText || `Download failed: ${fileResponse.status}`)
            }

            const blob = await fileResponse.blob()
            const file = new File(
                [blob],
                decodeFileName(previewFile.name),
                { type: blob.type || 'application/octet-stream' }
            )
            const formData = new FormData()
            formData.append('files', file)

            const ingestResponse = await fetch(`${KNOWLEDGE_SERVICE_URL}/sources/${source.id}/documents:ingest`, {
                method: 'POST',
                body: formData,
            })
            const ingestData = await ingestResponse.json().catch(() => null) as KnowledgeIngestResponse | { message?: string } | null

            if (!ingestResponse.ok) {
                throw new Error((ingestData as { message?: string } | null)?.message || ingestResponse.statusText)
            }

            const importedCount = (ingestData as KnowledgeIngestResponse | null)?.documentCount || 1
            setKnowledgeImportSuccess({ sourceId: source.id, sourceName: source.name, count: importedCount })
            showToast('success', t('files.knowledgeImportSuccess', { name: source.name }))
        } catch (err) {
            const message = err instanceof Error ? err.message : t('errors.unknown')
            setKnowledgeImportError(t('files.knowledgeImportFailed', { error: message }))
        } finally {
            setKnowledgeImporting(false)
        }
    }, [getDownloadUrl, knowledgeSources, previewFile, selectedKnowledgeSourceId, showToast, t, userId])

    const handleGoToKnowledge = useCallback(() => {
        if (!knowledgeImportSuccess) return
        setIsKnowledgeMenuOpen(false)
        navigate(`/knowledge/${knowledgeImportSuccess.sourceId}?tab=documents`)
    }, [knowledgeImportSuccess, navigate])

    // Syntax highlighted code
    const highlightedCode = useMemo(() => {
        if (!previewFile?.content) return ''
        const lang = HLJS_LANG_MAP[inferFileType(previewFile)]
        if (lang) {
            try {
                return hljs.highlight(previewFile.content, { language: lang }).value
            } catch {
                // Fallback to auto-detection
                try {
                    return hljs.highlightAuto(previewFile.content).value
                } catch {
                    return ''
                }
            }
        }
        // Try auto-detection for unknown types
        try {
            return hljs.highlightAuto(previewFile.content).value
        } catch {
            return ''
        }
    }, [previewFile?.content, previewFile?.type])

    const isOpen = !!previewFile
    const previewKind = previewFile?.previewKind
    const canToggleSource = previewKind === 'html' || previewKind === 'markdown'
    const canCopyContent = !!previewFile?.content
    const canDownload = !!getDownloadUrl()
    const displayType = previewFile ? inferFileType(previewFile) : ''
    const canEditContent = !!previewFile?.agentId && previewFile.content !== undefined && !!previewKind && needsTextContent(previewKind)
    const canImportToKnowledge = isAdminUser(userId, role) && canDownload && !!previewFile?.agentId && !previewFile.path.startsWith('knowledge-document:')
    const canUseLineNumbers = !!previewFile?.content && shouldUseLineNumbers(previewFile.content, displayType, previewKind)
    const isLineNumberTextPreview = previewKind === 'code' && (displayType === 'txt' || displayType === 'log') && canUseLineNumbers
    const showLineNumberSource = canUseLineNumbers && (isLineNumberTextPreview || (previewKind === 'markdown' && showSource))
    const lineNumberClassName = `file-preview-lined-text${displayType === 'log' ? ' is-log-view' : ''}`
    const showLoadingOverlay = isLoading || (!!previewKind && needsTextContent(previewKind) && previewFile?.content === '')

    const content = isOpen && previewFile ? (
                <>
                    <div className="file-preview-header">
                        <div className="file-preview-title">
                            <span className="file-preview-name">{decodeFileName(previewFile.name)}</span>
                            <span className="file-preview-lang">{getLanguageName(displayType)}</span>
                        </div>
                        <div className="file-preview-actions">
                            {canToggleSource && (
                                <button
                                    className={`file-preview-btn ${showSource ? 'active' : ''}`}
                                    onClick={() => setShowSource(!showSource)}
                                    title={showSource ? t('files.showRendered') : t('files.showSource')}
                                >
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                        {showSource ? (
                                            <>
                                                <circle cx="12" cy="12" r="10" />
                                                <line x1="2" y1="12" x2="22" y2="12" />
                                                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
                                            </>
                                        ) : (
                                            <>
                                                <polyline points="16 18 22 12 16 6" />
                                                <polyline points="8 6 2 12 8 18" />
                                            </>
                                        )}
                                    </svg>
                                </button>
                            )}
                            {canEditContent && (
                                <button
                                    className="file-preview-btn"
                                    onClick={handleOpenEditor}
                                    title={t('common.edit')}
                                >
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                        <path d="M12 20h9" />
                                        <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
                                    </svg>
                                </button>
                            )}
                            {canImportToKnowledge && (
                                <div className="file-preview-action-wrap">
                                    <button
                                        className={`file-preview-btn ${isKnowledgeMenuOpen ? 'active' : ''}`}
                                        onClick={handleToggleKnowledgeMenu}
                                        title={t('files.importToKnowledge')}
                                        aria-haspopup="dialog"
                                        aria-expanded={isKnowledgeMenuOpen}
                                    >
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                                            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
                                            <path d="M12 8v6" />
                                            <path d="M9 11h6" />
                                        </svg>
                                    </button>

                                    {isKnowledgeMenuOpen && (
                                        <div className="file-knowledge-popover" role="dialog" aria-label={t('files.importToKnowledge')}>
                                            {knowledgeImportSuccess ? (
                                                <>
                                                    <div className="file-knowledge-success">
                                                        <div className="file-knowledge-success-icon" aria-hidden="true">
                                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
                                                                <polyline points="20 6 9 17 4 12" />
                                                            </svg>
                                                        </div>
                                                        <div>
                                                            <div className="file-knowledge-title">{t('files.knowledgeImportDone')}</div>
                                                            <div className="file-knowledge-note">
                                                                {t('files.knowledgeImportDoneDetail', {
                                                                    name: knowledgeImportSuccess.sourceName,
                                                                    count: knowledgeImportSuccess.count,
                                                                })}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className="file-knowledge-actions">
                                                        <button type="button" className="btn btn-secondary" onClick={() => setIsKnowledgeMenuOpen(false)}>
                                                            {t('files.stayInFiles')}
                                                        </button>
                                                        <button type="button" className="btn btn-primary" onClick={handleGoToKnowledge}>
                                                            {t('files.goToKnowledge')}
                                                        </button>
                                                    </div>
                                                </>
                                            ) : (
                                                <>
                                                    <div className="file-knowledge-title">{t('files.importToKnowledge')}</div>
                                                    <label className="file-knowledge-label" htmlFor="file-knowledge-source-select">
                                                        {t('files.knowledgeSelectLabel')}
                                                    </label>
                                                    <select
                                                        id="file-knowledge-source-select"
                                                        className="file-knowledge-select"
                                                        value={selectedKnowledgeSourceId}
                                                        onChange={event => setSelectedKnowledgeSourceId(event.target.value)}
                                                        disabled={knowledgeSourcesLoading || knowledgeImporting || knowledgeSources.length === 0}
                                                    >
                                                        {knowledgeSources.length === 0 ? (
                                                            <option value="">{knowledgeSourcesLoading ? t('common.loading') : t('files.knowledgeNoSources')}</option>
                                                        ) : knowledgeSources.map(source => (
                                                            <option key={source.id} value={source.id}>{source.name}</option>
                                                        ))}
                                                    </select>

                                                    {knowledgeImportError && (
                                                        <div className="file-knowledge-error" role="alert">
                                                            {knowledgeImportError}
                                                        </div>
                                                    )}

                                                    <div className="file-knowledge-actions">
                                                        <button
                                                            type="button"
                                                            className="btn btn-secondary"
                                                            onClick={() => setIsKnowledgeMenuOpen(false)}
                                                            disabled={knowledgeImporting}
                                                        >
                                                            {t('common.cancel')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="btn btn-primary"
                                                            onClick={() => void handleImportToKnowledge()}
                                                            disabled={knowledgeSourcesLoading || knowledgeImporting || !selectedKnowledgeSourceId}
                                                        >
                                                            {knowledgeImporting ? t('files.knowledgeImporting') : t('files.import')}
                                                        </button>
                                                    </div>
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )}
                            {canCopyContent && (
                                <button
                                    className="file-preview-btn"
                                    onClick={handleCopy}
                                    title={copied ? t('files.copied') : t('files.copyContent')}
                                >
                                    {copied ? (
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                            <polyline points="20 6 9 17 4 12" />
                                        </svg>
                                    ) : (
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                                        </svg>
                                    )}
                                </button>
                            )}
                            {canDownload && (
                                <a
                                    href={previewFile.downloadUrl ? getDownloadUrl() : `${getDownloadUrl()}&download=true`}
                                    className="file-preview-btn"
                                    title={t('files.download')}
                                    download
                                >
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                                        <polyline points="7 10 12 15 17 10" />
                                        <line x1="12" y1="15" x2="12" y2="3" />
                                    </svg>
                                </a>
                            )}
                            <button
                                className={`file-preview-btn ${isPreviewFullscreen ? 'active' : ''}`}
                                onClick={togglePreviewFullscreen}
                                title={isPreviewFullscreen ? t('files.exitFullscreen') : t('files.enterFullscreen')}
                                aria-label={isPreviewFullscreen ? t('files.exitFullscreen') : t('files.enterFullscreen')}
                            >
                                {isPreviewFullscreen
                                    ? <Minimize2 size={16} strokeWidth={2} />
                                    : <Maximize2 size={16} strokeWidth={2} />}
                            </button>
                            <button
                                className="file-preview-btn file-preview-close"
                                onClick={closePreview}
                                title={t('files.closePreview')}
                            >
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                    <line x1="18" y1="6" x2="6" y2="18" />
                                    <line x1="6" y1="6" x2="18" y2="18" />
                                </svg>
                            </button>
                        </div>
                    </div>

                    <div className="file-preview-content">
                        {showLoadingOverlay && (
                            <div className="file-preview-loading-shell" aria-live="polite">
                                <div className="file-preview-transition-loading-row">
                                    <div className="loading-spinner file-preview-loading-spinner" />
                                    <p>{t('files.loadingPreview')}</p>
                                </div>
                                <div className="file-preview-skeleton file-preview-skeleton-inline">
                                    <div className="file-preview-skeleton-line w-60" />
                                    <div className="file-preview-skeleton-line w-92" />
                                    <div className="file-preview-skeleton-line w-84" />
                                    <div className="file-preview-skeleton-line w-96" />
                                    <div className="file-preview-skeleton-line w-88" />
                                    <div className="file-preview-skeleton-line w-72" />
                                </div>
                            </div>
                        )}

                        {error && (
                            <div className="file-preview-error">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="24" height="24">
                                    <circle cx="12" cy="12" r="10" />
                                    <line x1="12" y1="8" x2="12" y2="12" />
                                    <line x1="12" y1="16" x2="12.01" y2="16" />
                                </svg>
                                <p>{error}</p>
                            </div>
                        )}

                        {!showLoadingOverlay && !error && previewFile && (
                            <>
                                {previewKind === 'image' && (
                                    <div className="file-preview-media-wrapper">
                                        <img
                                            className="file-preview-media-image"
                                            src={getDownloadUrl()}
                                            alt={previewFile.name}
                                        />
                                    </div>
                                )}

                                {previewKind === 'pdf' && (
                                    <iframe
                                        className="file-preview-iframe"
                                        src={getDownloadUrl()}
                                        title={previewFile.name}
                                    />
                                )}

                                {previewKind === 'audio' && (
                                    <div className="file-preview-media-wrapper">
                                        <audio className="file-preview-media-audio" controls src={getDownloadUrl()} />
                                    </div>
                                )}

                                {previewKind === 'video' && (
                                    <div className="file-preview-media-wrapper">
                                        <video className="file-preview-media-video" controls src={getDownloadUrl()} />
                                    </div>
                                )}

                                {previewKind === 'office' && previewFile.onlyofficeUrl && previewFile.fileBaseUrl && (
                                    <OnlyOfficePreview
                                        name={previewFile.name}
                                        path={previewFile.path}
                                        agentId={previewFile.agentId || ''}
                                        type={previewFile.type}
                                        rootId={previewFile.rootId}
                                        onlyofficeUrl={previewFile.onlyofficeUrl}
                                        fileBaseUrl={previewFile.fileBaseUrl}
                                    />
                                )}

                                {previewKind === 'spreadsheet' && previewFile.tableData && (
                                    <div className="file-preview-table-wrap">
                                        <table className="file-preview-table">
                                            <tbody>
                                                {previewFile.tableData.map((row, rowIdx) => (
                                                    <tr key={`row-${rowIdx}`}>
                                                        {row.map((cell, cellIdx) => (
                                                            <td key={`cell-${rowIdx}-${cellIdx}`}>{cell}</td>
                                                        ))}
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}

                                {/* HTML: render in iframe or show source */}
                                {previewKind === 'html' && !showSource && previewFile.content !== undefined && (
                                    <iframe
                                        className="file-preview-iframe"
                                        srcDoc={previewFile.content}
                                        sandbox="allow-same-origin allow-scripts"
                                        title={previewFile.name}
                                    />
                                )}

                                {/* Markdown: render or show source */}
                                {previewKind === 'markdown' && !showSource && previewFile.content !== undefined && previewFile.content !== '' && (
                                    <div className="file-preview-markdown">
                                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                            {previewFile.content}
                                        </ReactMarkdown>
                                    </div>
                                )}

                                {/* Source view for renderable types */}
                                {canToggleSource && showSource && previewFile.content !== undefined && (
                                    showLineNumberSource
                                        ? renderHighlightedLineNumberText(previewFile.content, highlightedCode || previewFile.content, lineNumberClassName)
                                        : (
                                            <pre className="file-preview-code">
                                                <code
                                                    dangerouslySetInnerHTML={{ __html: highlightedCode || previewFile.content }}
                                                />
                                            </pre>
                                        )
                                )}

                                {/* Code files: syntax highlighted */}
                                {previewKind === 'code' && previewFile.content !== undefined && (
                                    isLineNumberTextPreview
                                        ? renderLineNumberText(previewFile.content, lineNumberClassName)
                                        : (
                                            <pre className="file-preview-code">
                                                <code
                                                    dangerouslySetInnerHTML={{ __html: highlightedCode || previewFile.content }}
                                                />
                                            </pre>
                                        )
                                )}
                            </>
                        )}
                    </div>
                    {isEditorOpen && previewFile && createPortal((
                        <div
                            className="modal-overlay"
                            role="dialog"
                            aria-modal="true"
                            aria-labelledby="file-preview-edit-title"
                            onClick={handleCloseEditor}
                        >
                            <div className="modal modal-wide file-edit-modal" onClick={(event) => event.stopPropagation()}>
                                <div className="modal-header">
                                    <div className="file-edit-modal-heading">
                                        <h2 className="modal-title" id="file-preview-edit-title">
                                            {t('files.editTitle', { name: decodeFileName(previewFile.name) })}
                                        </h2>
                                        <span className="file-preview-lang">{getLanguageName(displayType)}</span>
                                    </div>
                                    <button
                                        type="button"
                                        className="modal-close"
                                        onClick={handleCloseEditor}
                                        disabled={isSaving}
                                        aria-label={t('common.close')}
                                    >
                                        &times;
                                    </button>
                                </div>

                                <div className="modal-body">
                                    <label className="file-edit-label" htmlFor="file-preview-edit-content">
                                        {t('files.editContentLabel')}
                                    </label>
                                    <textarea
                                        id="file-preview-edit-content"
                                        className="file-edit-textarea"
                                        value={editDraft}
                                        onChange={(event) => setEditDraft(event.target.value)}
                                        spellCheck={false}
                                        disabled={isSaving}
                                    />
                                    {editError && (
                                        <div className="file-edit-error" role="alert">
                                            {editError}
                                        </div>
                                    )}
                                </div>

                                <div className="modal-footer">
                                    <button
                                        type="button"
                                        className="btn btn-secondary"
                                        onClick={handleCloseEditor}
                                        disabled={isSaving}
                                    >
                                        {t('common.cancel')}
                                    </button>
                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        onClick={() => void handleSaveEdit()}
                                        disabled={isSaving}
                                    >
                                        {isSaving ? t('common.saving') : t('common.save')}
                                    </button>
                                </div>
                            </div>
                        </div>
                    ), document.body)}
                </>
    ) : null

    if (embedded) return content

    return (
        <div className={`file-preview-panel ${isOpen ? 'open' : ''}`}>
            {content}
        </div>
    )
}
