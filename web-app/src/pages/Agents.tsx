import { useMemo, useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useGoosed } from '../contexts/GoosedContext'
import { useUser } from '../contexts/UserContext'
import { useMcp } from '../hooks/useMcp'
import { GATEWAY_URL, gatewayHeaders, slugify } from '../config/runtime'
import ResourceCard from '../components/ResourceCard'

const DEFAULT_LLM = { provider: 'openai', model: 'qwen/qwen3.5-35b-a3b' }

function getModelSummary(model?: string, provider?: string, unknownLabel = 'Unknown'): string {
    if (model) return model
    if (provider) return provider
    return unknownLabel
}

function shouldShowProviderTag(provider?: string, model?: string): boolean {
    return Boolean(provider && model && provider !== model)
}

// Component to fetch and display MCP count for an agent
function McpCount({ agentId }: { agentId: string }) {
    const { entries, fetchMcp } = useMcp(agentId)

    useEffect(() => {
        fetchMcp()
    }, [fetchMcp])

    const enabledCount = entries.filter(e => e.enabled).length
    return <span>{enabledCount}</span>
}

function CreateAgentModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const [name, setName] = useState('')
    const [id, setId] = useState('')
    const [idManuallyEdited, setIdManuallyEdited] = useState(false)
    const [creating, setCreating] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const handleNameChange = useCallback((value: string) => {
        setName(value)
        if (!idManuallyEdited) {
            setId(slugify(value))
        }
    }, [idManuallyEdited])

    const handleIdChange = useCallback((value: string) => {
        setIdManuallyEdited(true)
        setId(value)
    }, [])

    const isValidId = /^[a-z0-9][a-z0-9-]*[a-z0-9]$/.test(id) && id.length >= 2

    const handleCreate = useCallback(async () => {
        setError(null)
        if (!name.trim()) { setError(t('agents.nameRequired')); return }
        if (!id.trim()) { setError(t('agents.idRequired')); return }
        if (!isValidId) { setError(t('agents.idInvalid')); return }

        setCreating(true)
        try {
            const res = await fetch(`${GATEWAY_URL}/agents`, {
                method: 'POST',
                headers: gatewayHeaders(userId),
                body: JSON.stringify({ id: id.trim(), name: name.trim() }),
            })
            const data = await res.json()
            if (!res.ok || !data.success) {
                setError(data.error || t('agents.createFailed', { error: 'Unknown error' }))
                return
            }
            onCreated()
            onClose()
        } catch (err) {
            setError(t('agents.createFailed', { error: err instanceof Error ? err.message : 'Network error' }))
        } finally {
            setCreating(false)
        }
    }, [name, id, isValidId, userId, t, onCreated, onClose])

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">{t('agents.createAgentTitle')}</h2>
                    <button className="modal-close" onClick={onClose}>&times;</button>
                </div>

                <div className="modal-body">
                    {error && (
                        <div className="agents-alert agents-alert-error" style={{ marginBottom: 'var(--spacing-4)' }}>
                            {error}
                        </div>
                    )}

                    <div className="form-group">
                        <label className="form-label">{t('agents.agentName')}</label>
                        <input
                            className="form-input"
                            type="text"
                            placeholder={t('agents.agentNamePlaceholder')}
                            value={name}
                            onChange={e => handleNameChange(e.target.value)}
                            autoFocus
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label">{t('agents.agentId')}</label>
                        <input
                            className="form-input"
                            type="text"
                            placeholder={t('agents.agentIdPlaceholder')}
                            value={id}
                            onChange={e => handleIdChange(e.target.value)}
                        />
                        <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: 'var(--spacing-1)' }}>
                            {t('agents.agentIdHint')}
                        </p>
                    </div>

                    <div className="form-group">
                        <label className="form-label">{t('agents.llmConfig')}</label>
                        <div style={{
                            background: 'var(--color-bg-secondary)',
                            border: '1px solid var(--color-border)',
                            borderRadius: 'var(--radius-lg)',
                            padding: 'var(--spacing-3) var(--spacing-4)',
                        }}>
                            <div className="agent-meta-row">
                                <span className="agent-meta-label">{t('agents.provider')}</span>
                                <span className="agent-meta-value">{DEFAULT_LLM.provider}</span>
                            </div>
                            <div className="agent-meta-row">
                                <span className="agent-meta-label">{t('agents.model')}</span>
                                <span className="agent-meta-value">{DEFAULT_LLM.model}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="modal-footer">
                    <button className="btn btn-secondary" onClick={onClose} disabled={creating}>
                        {t('common.cancel')}
                    </button>
                    <button
                        className="btn btn-primary"
                        onClick={handleCreate}
                        disabled={creating || !name.trim() || !isValidId}
                    >
                        {creating ? t('agents.creating') : t('agents.createAgentTitle')}
                    </button>
                </div>
            </div>
        </div>
    )
}

// ─── Delete Agent Modal ──────────────────────────────────

function DeleteAgentModal({ agentId, agentName, onClose, onDeleted }: {
    agentId: string
    agentName: string
    onClose: () => void
    onDeleted: () => void
}) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const [deleting, setDeleting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const handleDelete = useCallback(async () => {
        setError(null)
        setDeleting(true)
        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}`, {
                method: 'DELETE',
                headers: gatewayHeaders(userId),
            })
            const data = await res.json()
            if (!res.ok || !data.success) {
                setError(data.error || t('agents.deleteFailed', { error: 'Unknown error' }))
                return
            }
            onDeleted()
            onClose()
        } catch (err) {
            setError(t('agents.deleteFailed', { error: err instanceof Error ? err.message : 'Network error' }))
        } finally {
            setDeleting(false)
        }
    }, [agentId, userId, t, onDeleted, onClose])

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">{t('agents.deleteAgentTitle')}</h2>
                    <button className="modal-close" onClick={onClose}>&times;</button>
                </div>

                <div className="modal-body">
                    {error && (
                        <div className="agents-alert agents-alert-error" style={{ marginBottom: 'var(--spacing-4)' }}>
                            {error}
                        </div>
                    )}

                    <p style={{ fontSize: 'var(--font-size-base)', color: 'var(--color-text-primary)', marginBottom: 'var(--spacing-4)' }}>
                        {t('agents.deleteAgentConfirm', { name: agentName })}
                    </p>

                    <div className="agents-alert agents-alert-warning" style={{ marginBottom: 0 }}>
                        {t('agents.deleteAgentWarning')}
                    </div>
                </div>

                <div className="modal-footer">
                    <button className="btn btn-secondary" onClick={onClose} disabled={deleting}>
                        {t('common.cancel')}
                    </button>
                    <button
                        className="btn btn-danger"
                        onClick={handleDelete}
                        disabled={deleting}
                    >
                        {deleting ? t('agents.deleting') : t('common.delete')}
                    </button>
                </div>
            </div>
        </div>
    )
}

// ─── Agents Page ─────────────────────────────────────────

export default function Agents() {
    const { t } = useTranslation()
    const { agents, isConnected, error, refreshAgents } = useGoosed()
    const { role } = useUser()
    const navigate = useNavigate()
    const isAdmin = role === 'admin'
    const [showCreateModal, setShowCreateModal] = useState(false)
    const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null)

    const agentSkillsMap = useMemo(() => {
        return new Map(agents.map(agent => [agent.id, agent.skills || []]))
    }, [agents])

    return (
        <div className="page-container sidebar-top-page resource-page">
            <div className="page-header">
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <h1 className="page-title">{t('agents.title')}</h1>
                    {isAdmin && (
                        <button
                            className="action-btn-primary btn btn-primary"
                            onClick={() => setShowCreateModal(true)}
                        >
                            {t('agents.createAgent')}
                        </button>
                    )}
                </div>
                <p className="page-subtitle">{t('agents.subtitle')}</p>
            </div>

            {error && (
                <div className="conn-banner conn-banner-error">{t('common.connectionError', { error })}</div>
            )}
            {!isConnected && !error && (
                <div className="conn-banner conn-banner-warning">{t('common.connectingGateway')}</div>
            )}

            {agents.length === 0 ? (
                <div className="empty-state">
                    <svg className="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                        <circle cx="12" cy="12" r="9" />
                        <path d="M12 7v5l3 2" />
                    </svg>
                    <h3 className="empty-state-title">{t('agents.noAgents')}</h3>
                    <p className="empty-state-description">{t('agents.noAgentsHint')}</p>
                </div>
            ) : (
                <div className="resource-grid">
                    {agents.map(agent => {
                        const skills = agentSkillsMap.get(agent.id) || []
                        const modelSummary = getModelSummary(agent.model, agent.provider, t('agents.unknown'))
                        return (
                            <ResourceCard
                                key={agent.id}
                                title={agent.name}
                                summary={(
                                    <div className="resource-card-summary-stack">
                                        {shouldShowProviderTag(agent.provider, agent.model) && (
                                            <div className="resource-card-tags">
                                                <span className="resource-card-tag" title={agent.provider}>
                                                    {agent.provider}
                                                </span>
                                            </div>
                                        )}
                                        <p className="resource-card-summary-text resource-card-summary-code" title={modelSummary}>
                                            {modelSummary}
                                        </p>
                                    </div>
                                )}
                                metrics={[
                                    { label: t('agents.skills'), value: skills.length },
                                    { label: t('agents.mcp'), value: <McpCount agentId={agent.id} /> },
                                ]}
                                footer={isAdmin ? (
                                    <>
                                        <button
                                            type="button"
                                            className="resource-card-danger-action"
                                            onClick={() => setDeleteTarget({ id: agent.id, name: agent.name })}
                                        >
                                            {t('agents.deleteAgent')}
                                        </button>
                                        <button
                                            type="button"
                                            className="resource-card-primary-action"
                                            onClick={() => navigate(`/agents/${agent.id}/configure`)}
                                        >
                                            {t('agents.configure')}
                                        </button>
                                    </>
                                ) : undefined}
                            />
                        )
                    })}
                </div>
            )}

            {showCreateModal && (
                <CreateAgentModal
                    onClose={() => setShowCreateModal(false)}
                    onCreated={refreshAgents}
                />
            )}

            {deleteTarget && (
                <DeleteAgentModal
                    agentId={deleteTarget.id}
                    agentName={deleteTarget.name}
                    onClose={() => setDeleteTarget(null)}
                    onDeleted={refreshAgents}
                />
            )}
        </div>
    )
}
