import { useTranslation } from 'react-i18next'
import type { Host, Cluster } from '../../../../types/host'

function PingIcon({ spinning = false }: { spinning?: boolean }) {
    return (
        <svg
            viewBox="0 0 20 20"
            fill="none"
            width="16"
            height="16"
            aria-hidden="true"
            className={spinning ? 'hr-host-card-action-icon-spinning' : undefined}
        >
            <path
                d="M10 12.2a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4Z"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M4.85 10a5.15 5.15 0 0 1 10.3 0"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M2.8 10a7.2 7.2 0 0 1 14.4 0"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

function TestResultIcon({ ok }: { ok: boolean }) {
    return ok ? (
        <svg viewBox="0 0 20 20" fill="none" width="14" height="14" aria-hidden="true">
            <path
                d="m5.75 10.2 2.45 2.45 6.05-6.05"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    ) : (
        <svg viewBox="0 0 20 20" fill="none" width="14" height="14" aria-hidden="true">
            <path
                d="M10 6.2v4.3"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
            <path
                d="M10 13.4h.01"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
            />
            <path
                d="M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

function EditIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" width="16" height="16" aria-hidden="true">
            <path
                d="M4.75 13.95 4 16l2.05-.75 8.5-8.5-1.3-1.3-8.5 8.5Z"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="m11.95 6.05 1.3 1.3m.65-.65 1.05-1.05a1.15 1.15 0 0 0 0-1.6l-.5-.5a1.15 1.15 0 0 0-1.6 0L11.8 4.6"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M4 16h12"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
            />
        </svg>
    )
}

function TrashIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" width="16" height="16" aria-hidden="true">
            <path
                d="M6.5 5.5h7m-6 0V4.75A1.75 1.75 0 0 1 9.25 3h1.5A1.75 1.75 0 0 1 12.5 4.75v.75m-8 0h11m-1 0-.6 8.39a1.75 1.75 0 0 1-1.75 1.61H7.85A1.75 1.75 0 0 1 6.1 13.89L5.5 5.5m2.75 2.5v4m4-4v4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

type Props = {
    host: Host
    cluster?: Cluster
    selected?: boolean
    testing?: boolean
    testResult?: { ok: boolean; msg: string } | null
    onClick: () => void
    onEdit: () => void
    onDelete: () => void
    onTest?: () => void
}

export default function HostCard({ host, cluster, selected, testing, testResult, onClick, onEdit, onDelete, onTest }: Props) {
    const { t } = useTranslation()
    const roleLabel = host.role === 'primary'
        ? t('hostResource.hostRolePrimary')
        : host.role === 'backup'
            ? t('hostResource.hostRoleBackup')
            : null
    const testActionLabel = testing ? t('remoteDiagnosis.hosts.testing') : t('remoteDiagnosis.hosts.testConnection')

    return (
        <div
            className={`hr-host-card ${selected ? 'hr-host-card-selected' : ''}`}
            onClick={onClick}
        >
            <div className="hr-host-card-header">
                <div className="hr-host-card-title-block">
                    <div className="hr-host-card-tags">
                        {roleLabel && (
                            <span className={`hr-host-card-tag hr-host-card-tag-role hr-host-card-tag-role-${host.role}`}>
                                {roleLabel}
                            </span>
                        )}
                    </div>
                    <div className="hr-host-card-title-line">
                        <h3 className="hr-host-card-name">{host.name}</h3>
                        {host.description && (
                            <p className="hr-host-card-desc">{host.description}</p>
                        )}
                    </div>
                </div>
            </div>

            <div className="hr-host-card-meta">
                <div className="hr-host-card-meta-field">
                    <span className="hr-host-card-meta-label">{t('hostResource.ipPort')}</span>
                    <span className="hr-host-card-meta-value hr-host-card-address-row">
                        <span className="hr-host-card-mono">{host.ip}:{host.port}</span>
                        {testResult && (
                            <button
                                type="button"
                                className={`hr-host-card-result-trigger ${testResult.ok ? 'hr-host-card-result-trigger-ok' : 'hr-host-card-result-trigger-fail'}`}
                                title={testResult.msg}
                                aria-label={testResult.msg}
                                onClick={event => event.stopPropagation()}
                            >
                                <TestResultIcon ok={testResult.ok} />
                            </button>
                        )}
                    </span>
                </div>
                {host.businessIp && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.businessIp')}</span>
                        <span className="hr-host-card-meta-value hr-host-card-mono">{host.businessIp}</span>
                    </div>
                )}
                <div className="hr-host-card-meta-field">
                    <span className="hr-host-card-meta-label">{t('hostResource.username')}</span>
                    <span className="hr-host-card-meta-value">{host.username}</span>
                </div>
                {host.location && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.location')}</span>
                        <span className="hr-host-card-meta-value">{host.location}</span>
                    </div>
                )}
                {host.business && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.business')}</span>
                        <span className="hr-host-card-meta-value">{host.business}</span>
                    </div>
                )}
                {cluster && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.clusterName')}</span>
                        <span className="hr-host-card-meta-value">{cluster.name}</span>
                    </div>
                )}
            </div>

            <div className="hr-host-card-footer" onClick={e => e.stopPropagation()}>
                {onTest && (
                    <button
                        type="button"
                        className="hr-host-card-action"
                        onClick={onTest}
                        disabled={testing}
                        aria-label={testActionLabel}
                        title={testActionLabel}
                    >
                        <PingIcon spinning={Boolean(testing)} />
                    </button>
                )}
                <button
                    type="button"
                    className="hr-host-card-action"
                    onClick={onEdit}
                    aria-label={t('common.edit')}
                    title={t('common.edit')}
                >
                    <EditIcon />
                </button>
                <button
                    type="button"
                    className="hr-host-card-action hr-host-card-action-danger"
                    onClick={onDelete}
                    aria-label={t('common.delete')}
                    title={t('common.delete')}
                >
                    <TrashIcon />
                </button>
            </div>
        </div>
    )
}
