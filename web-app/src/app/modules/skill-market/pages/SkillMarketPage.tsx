import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus, Upload } from 'lucide-react'
import type { SkillMarketDetail, SkillMarketEntry } from '../../../../types/skillMarket'
import { useGoosed } from '../../../platform/providers/GoosedContext'
import { useToast } from '../../../platform/providers/ToastContext'
import Button from '../../../platform/ui/primitives/Button'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import CardGrid from '../../../platform/ui/cards/CardGrid'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import ListSearchInput from '../../../platform/ui/list/ListSearchInput'
import ListToolbar from '../../../platform/ui/list/ListToolbar'
import ListWorkbench from '../../../platform/ui/list/ListWorkbench'
import ResourceCard, {
    ResourceCardActionGroup,
    ResourceCardDeleteAction,
    ResourceCardEditAction,
    ResourceCardInstallAction,
    type ResourceStatusTone,
} from '../../../platform/ui/primitives/ResourceCard'
import { useSkillMarket } from '../hooks/useSkillMarket'
import '../styles/skill-market.css'

function formatDate(locale: string, value?: string | null): string {
    if (!value) return '-'
    return new Date(value).toLocaleDateString(locale, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    })
}

function getSkillStatusTone(skill: SkillMarketEntry): ResourceStatusTone {
    return skill.containsScripts ? 'warning' : 'neutral'
}

export default function SkillMarketPage() {
    const { t, i18n } = useTranslation()
    const { agents, refreshAgents } = useGoosed()
    const { showToast } = useToast()
    const { skills, isLoading, error, fetchSkills, fetchSkill, createSkill, updateSkill, importSkill, deleteSkill, installSkill } = useSkillMarket()
    const [query, setQuery] = useState('')
    const [createOpen, setCreateOpen] = useState(false)
    const [editTarget, setEditTarget] = useState<SkillMarketDetail | null>(null)
    const [isImporting, setIsImporting] = useState(false)
    const [deleteTarget, setDeleteTarget] = useState<SkillMarketEntry | null>(null)
    const [installTarget, setInstallTarget] = useState<SkillMarketEntry | null>(null)
    const importInputRef = useRef<HTMLInputElement>(null)

    useEffect(() => {
        fetchSkills()
    }, [fetchSkills])

    const filteredSkills = useMemo(() => {
        const term = query.trim().toLowerCase()
        if (!term) return skills
        return skills.filter(skill =>
            skill.name.toLowerCase().includes(term)
            || skill.id.toLowerCase().includes(term)
            || skill.description.toLowerCase().includes(term)
        )
    }, [query, skills])

    const handleEdit = async (skill: SkillMarketEntry) => {
        const result = await fetchSkill(skill.id)
        if (result.success && result.skill) {
            setEditTarget(result.skill)
        } else {
            showToast('error', result.error || t('skillMarket.loadDetailFailed'))
        }
    }

    const handleDelete = async (skill: SkillMarketEntry) => {
        const result = await deleteSkill(skill.id)
        if (result.success) {
            showToast('success', t('skillMarket.deleteSuccess', { name: skill.name }))
            setDeleteTarget(null)
        } else {
            showToast('error', result.error || t('skillMarket.deleteFailed'))
        }
    }

    const handleInstall = async (agentId: string, skill: SkillMarketEntry) => {
        const result = await installSkill(agentId, skill.id)
        if (result.success) {
            await refreshAgents()
            showToast('success', t('skillMarket.installSuccess', { name: skill.name }))
            setInstallTarget(null)
        } else {
            showToast('error', result.error || t('skillMarket.installFailed'))
        }
    }

    const handleImportFile = async (file: File) => {
        setIsImporting(true)
        const result = await importSkill(file)
        if (result.success) {
            showToast('success', t('skillMarket.importSuccess'))
        } else {
            const message = result.error === 'SKILL_ALREADY_EXISTS'
                ? t('skillMarket.importDuplicateSkill')
                : result.error || t('skillMarket.importFailed')
            showToast('error', message)
        }
        setIsImporting(false)
        if (importInputRef.current) {
            importInputRef.current.value = ''
        }
    }

    return (
        <div className="page-container sidebar-top-page page-shell-wide skill-market-page">
            <PageHeader
                title={t('skillMarket.title')}
                subtitle={t('skillMarket.subtitle')}
                action={(
                    <div className="skill-market-header-actions">
                        <Button variant="secondary" onClick={() => setCreateOpen(true)}>
                            <Plus size={16} /> {t('skillMarket.createSkill')}
                        </Button>
                        <Button variant="primary" onClick={() => importInputRef.current?.click()} disabled={isImporting}>
                            <Upload size={16} /> {isImporting ? t('skillMarket.importing') : t('skillMarket.importSkill')}
                        </Button>
                        <input
                            ref={importInputRef}
                            className="skill-market-import-input"
                            type="file"
                            accept=".zip,application/zip"
                            onChange={event => {
                                const file = event.target.files?.[0]
                                if (file) void handleImportFile(file)
                            }}
                        />
                    </div>
                )}
            />

            {error && <div className="conn-banner conn-banner-error">{error}</div>}

            <ListWorkbench
                controls={(
                    <ListToolbar
                        primary={(
                            <ListSearchInput
                                value={query}
                                placeholder={t('skillMarket.searchPlaceholder')}
                                onChange={setQuery}
                            />
                        )}
                        secondary={query.trim() ? (
                            <ListResultsMeta>{t('common.resultsFound', { count: filteredSkills.length })}</ListResultsMeta>
                        ) : undefined}
                    />
                )}
            >
                {isLoading && (
                    <div className="empty-state">
                        <div className="empty-state-title">{t('skillMarket.loading')}</div>
                    </div>
                )}
                {!isLoading && filteredSkills.length === 0 && (
                    <div className="empty-state">
                        <div className="empty-state-title">{query.trim() ? t('common.noResults') : t('skillMarket.emptyTitle')}</div>
                        <div className="empty-state-description">
                            {query.trim() ? t('skillMarket.noMatchSkills') : t('skillMarket.emptyDescription')}
                        </div>
                    </div>
                )}
                {!isLoading && filteredSkills.length > 0 && (
                    <CardGrid>
                        {filteredSkills.map(skill => {
                            const descriptionText = skill.description?.trim() || t('skill.noDescription')
                            return (
                                <ResourceCard
                                    key={skill.id}
                                    title={skill.name}
                                    statusLabel={skill.containsScripts ? t('skillMarket.containsScripts') : t('skillMarket.textOnly')}
                                    statusTone={getSkillStatusTone(skill)}
                                    tags={(
                                        <div className="resource-card-tags">
                                            <span className="resource-card-tag">{skill.id}</span>
                                        </div>
                                    )}
                                    summary={(
                                        <p
                                            className={[
                                                'resource-card-summary-text',
                                                !skill.description ? 'resource-card-summary-placeholder' : '',
                                            ].filter(Boolean).join(' ')}
                                            title={descriptionText}
                                        >
                                            {descriptionText}
                                        </p>
                                    )}
                                    metrics={[
                                        { label: t('skillMarket.files'), value: skill.fileCount },
                                        { label: t('skillMarket.size'), value: formatSize(skill.sizeBytes, t) },
                                        { label: t('skillMarket.updatedAt'), value: formatDate(i18n.language === 'en' ? 'en-US' : 'zh-CN', skill.updatedAt) },
                                    ]}
                                    footer={(
                                        <ResourceCardActionGroup>
                                            <ResourceCardEditAction
                                                onClick={() => handleEdit(skill)}
                                                label={t('common.edit')}
                                            />
                                            <ResourceCardInstallAction
                                                onClick={() => setInstallTarget(skill)}
                                                label={t('skillMarket.install')}
                                            />
                                            <ResourceCardDeleteAction
                                                onClick={() => setDeleteTarget(skill)}
                                                label={t('common.delete')}
                                            />
                                        </ResourceCardActionGroup>
                                    )}
                                />
                            )
                        })}
                    </CardGrid>
                )}
            </ListWorkbench>

            {createOpen && (
                <SkillFormDialog
                    mode="create"
                    onClose={() => setCreateOpen(false)}
                    onSubmit={async payload => {
                        const result = await createSkill(payload)
                        if (result.success) {
                            showToast('success', t('skillMarket.createSuccess'))
                            setCreateOpen(false)
                        } else {
                            showToast('error', result.error || t('skillMarket.createFailed'))
                        }
                    }}
                />
            )}

            {editTarget && (
                <SkillFormDialog
                    mode="edit"
                    skill={editTarget}
                    onClose={() => setEditTarget(null)}
                    onSubmit={async payload => {
                        const result = await updateSkill(editTarget.id, {
                            name: payload.name,
                            description: payload.description,
                            instructions: payload.instructions,
                        })
                        if (result.success) {
                            showToast('success', t('skillMarket.updateSuccess', { name: payload.name }))
                            setEditTarget(null)
                        } else {
                            showToast('error', result.error || t('skillMarket.updateFailed'))
                        }
                    }}
                />
            )}

            {installTarget && (
                <InstallSkillDialog
                    skill={installTarget}
                    agents={agents}
                    onClose={() => setInstallTarget(null)}
                    onInstall={agentId => handleInstall(agentId, installTarget)}
                />
            )}

            {deleteTarget && (
                <DeleteSkillDialog
                    skill={deleteTarget}
                    onClose={() => setDeleteTarget(null)}
                    onDelete={() => handleDelete(deleteTarget)}
                />
            )}
        </div>
    )
}

function SkillFormDialog({ mode, skill, onClose, onSubmit }: {
    mode: 'create' | 'edit'
    skill?: SkillMarketDetail
    onClose: () => void
    onSubmit: (payload: { id: string; name: string; description: string; instructions: string }) => Promise<void>
}) {
    const { t } = useTranslation()
    const [id, setId] = useState(skill?.id || '')
    const [name, setName] = useState(skill?.name || '')
    const [description, setDescription] = useState(skill?.description || '')
    const [instructions, setInstructions] = useState(() => skill?.instructions || t('skillMarket.defaultInstructions'))
    const [isSaving, setIsSaving] = useState(false)

    const submit = async () => {
        setIsSaving(true)
        await onSubmit({ id, name, description, instructions })
        setIsSaving(false)
    }

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal modal-wide skill-market-editor-modal" onClick={event => event.stopPropagation()}>
                <div className="modal-header">
                    <div>
                        <h2 className="modal-title">{mode === 'create' ? t('skillMarket.createSkill') : t('skillMarket.editSkill')}</h2>
                        {mode === 'edit' && skill && (
                            <p className="skill-market-editor-meta">
                                {t('skillMarket.skillMeta', { id: skill.id, path: skill.path })}
                            </p>
                        )}
                    </div>
                    <button type="button" className="modal-close" onClick={onClose}>&times;</button>
                </div>
                <div className="modal-body">
                    {mode === 'create' && (
                        <div className="form-group">
                            <label className="form-label">{t('skillMarket.directoryName')}</label>
                            <input className="form-input" value={id} onChange={event => setId(event.target.value)} placeholder={t('skillMarket.skillIdPlaceholder')} />
                            <p className="skill-market-form-hint">{t('skillMarket.directoryNameHint')}</p>
                        </div>
                    )}
                    <div className="form-group">
                        <label className="form-label">{t('skillMarket.skillName')}</label>
                        <input className="form-input" value={name} onChange={event => setName(event.target.value)} placeholder={t('skillMarket.skillNamePlaceholder')} />
                    </div>
                    <div className="form-group">
                        <label className="form-label">{t('skillMarket.description')}</label>
                        <textarea
                            className="form-input"
                            value={description}
                            onChange={event => setDescription(event.target.value)}
                            placeholder={t('skillMarket.descriptionPlaceholder')}
                            rows={3}
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">{t('skillMarket.instructions')}</label>
                        <textarea className="form-input skill-market-instructions-input" value={instructions} onChange={event => setInstructions(event.target.value)} rows={18} />
                    </div>
                </div>
                <div className="modal-footer">
                    <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
                    <Button variant="primary" onClick={submit} disabled={isSaving}>{isSaving ? t('common.saving') : t('common.save')}</Button>
                </div>
            </div>
        </div>
    )
}

function DeleteSkillDialog({ skill, onClose, onDelete }: {
    skill: SkillMarketEntry
    onClose: () => void
    onDelete: () => Promise<void>
}) {
    const { t } = useTranslation()
    const [isDeleting, setIsDeleting] = useState(false)

    const submit = async () => {
        setIsDeleting(true)
        await onDelete()
        setIsDeleting(false)
    }

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" onClick={event => event.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">{t('skillMarket.deleteTitle')}</h2>
                    <button type="button" className="modal-close" onClick={onClose}>&times;</button>
                </div>
                <div className="modal-body">
                    <p className="skill-market-install-summary">
                        {t('skillMarket.deleteConfirm', { name: skill.name })}
                    </p>
                    <div className="agents-alert agents-alert-warning">
                        {t('skillMarket.deleteWarning')}
                    </div>
                </div>
                <div className="modal-footer">
                    <Button variant="secondary" onClick={onClose} disabled={isDeleting}>{t('common.cancel')}</Button>
                    <Button variant="danger" onClick={submit} disabled={isDeleting}>
                        {isDeleting ? t('skillMarket.deleting') : t('common.delete')}
                    </Button>
                </div>
            </div>
        </div>
    )
}

function InstallSkillDialog({ skill, agents, onClose, onInstall }: {
    skill: SkillMarketEntry
    agents: Array<{ id: string; name: string }>
    onClose: () => void
    onInstall: (agentId: string) => Promise<void>
}) {
    const { t } = useTranslation()
    const [agentId, setAgentId] = useState(agents[0]?.id || '')
    const [isInstalling, setIsInstalling] = useState(false)

    const submit = async () => {
        if (!agentId) return
        setIsInstalling(true)
        await onInstall(agentId)
        setIsInstalling(false)
    }

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal" onClick={event => event.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">{t('skillMarket.installSkill')}</h2>
                    <button type="button" className="modal-close" onClick={onClose}>&times;</button>
                </div>
                <div className="modal-body">
                    <p className="skill-market-install-summary">{skill.name}</p>
                    <div className="form-group">
                        <label className="form-label">{t('skillMarket.targetAgent')}</label>
                        <select className="form-input" value={agentId} onChange={event => setAgentId(event.target.value)}>
                            {agents.map(agent => (
                                <option key={agent.id} value={agent.id}>{agent.name}</option>
                            ))}
                        </select>
                    </div>
                </div>
                <div className="modal-footer">
                    <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
                    <Button variant="primary" onClick={submit} disabled={!agentId || isInstalling}>
                        {isInstalling ? t('skillMarket.installing') : t('skillMarket.install')}
                    </Button>
                </div>
            </div>
        </div>
    )
}

function formatSize(bytes: number, t: (key: string, options?: Record<string, unknown>) => string): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return t('skillMarket.sizeKb', { value: 0 })
    if (bytes < 1024 * 1024) return t('skillMarket.sizeKb', { value: Math.ceil(bytes / 1024) })
    return t('skillMarket.sizeMb', { value: (bytes / 1024 / 1024).toFixed(1) })
}
