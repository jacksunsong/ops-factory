import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

interface KnowledgeChunkDetailMetaItem {
    label: string
    value: ReactNode
    code?: boolean
}

interface KnowledgeChunkDetailModalProps {
    title: string
    subtitle?: string | null
    badges?: string[]
    headerMeta?: ReactNode
    error?: string | null
    loading?: boolean
    loadingLabel?: string
    metadataTitle: string
    metadataItems: KnowledgeChunkDetailMetaItem[]
    keywordsTitle: string
    keywordsContent: ReactNode
    contentTitle: string
    contentContent: ReactNode
    footer?: ReactNode
    onClose: () => void
    widthClassName?: string
}

export default function KnowledgeChunkDetailModal({
    title,
    subtitle,
    badges = [],
    headerMeta,
    error,
    loading = false,
    loadingLabel,
    metadataTitle,
    metadataItems,
    keywordsTitle,
    keywordsContent,
    contentTitle,
    contentContent,
    footer,
    onClose,
    widthClassName = '',
}: KnowledgeChunkDetailModalProps) {
    const { t } = useTranslation()

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className={`modal knowledge-chunk-detail-modal ${widthClassName}`.trim()}
                onClick={event => event.stopPropagation()}
            >
                <div className="modal-header knowledge-panel-header knowledge-chunk-detail-header">
                    <div className="knowledge-chunk-detail-header-copy">
                        <h3 className="knowledge-panel-title knowledge-chunk-detail-title">{title}</h3>
                        {subtitle ? (
                            <p className="knowledge-panel-description knowledge-chunk-detail-subtitle">{subtitle}</p>
                        ) : null}
                        {headerMeta ? (
                            <div className="knowledge-chunk-detail-header-meta">
                                {headerMeta}
                            </div>
                        ) : null}
                    </div>
                    <button type="button" className="modal-close" onClick={onClose} aria-label={t('common.close')}>
                        &times;
                    </button>
                </div>

                {badges.length > 0 ? (
                    <div className="knowledge-chunk-detail-badges">
                        {badges.map(badge => (
                            <span key={badge} className="resource-card-tag">{badge}</span>
                        ))}
                    </div>
                ) : null}

                {error ? (
                    <div className="agents-alert agents-alert-error">{error}</div>
                ) : null}

                {loading ? (
                    <div className="knowledge-doc-empty">{loadingLabel || t('common.loading')}</div>
                ) : (
                    <div className="modal-body knowledge-chunk-detail-modal-body knowledge-chunk-detail-body">
                        <section className="knowledge-chunk-detail-section knowledge-chunk-detail-section-metadata">
                            <h4 className="knowledge-chunk-detail-section-title">{metadataTitle}</h4>
                            <div className="knowledge-chunk-detail-metadata-grid">
                                {metadataItems.map(item => (
                                    <div key={item.label} className="knowledge-kv-item knowledge-chunk-detail-meta-item">
                                        <span className="knowledge-kv-label">{item.label}</span>
                                        <span className={`knowledge-kv-value ${item.code ? 'knowledge-kv-code' : ''}`.trim()}>
                                            {item.value}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className="knowledge-chunk-detail-section">
                            <h4 className="knowledge-chunk-detail-section-title">{keywordsTitle}</h4>
                            <div className="knowledge-chunk-detail-section-body">
                                {keywordsContent}
                            </div>
                        </section>

                        <section className="knowledge-chunk-detail-section knowledge-chunk-detail-section-content">
                            <h4 className="knowledge-chunk-detail-section-title">{contentTitle}</h4>
                            <div className="knowledge-chunk-detail-section-body">
                                {contentContent}
                            </div>
                        </section>
                    </div>
                )}

                {footer ? (
                    <div className="modal-footer knowledge-chunk-detail-footer">
                        {footer}
                    </div>
                ) : null}
            </div>
        </div>
    )
}
