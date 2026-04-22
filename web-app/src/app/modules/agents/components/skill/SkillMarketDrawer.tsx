import { useEffect, useMemo, useState } from 'react'
import { Check, Download, Search, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { SkillEntry } from '../../../../../types/skill'
import type { SkillMarketEntry } from '../../../../../types/skillMarket'
import { useToast } from '../../../../platform/providers/ToastContext'
import Button from '../../../../platform/ui/primitives/Button'
import ResourceCard, { type ResourceStatusTone } from '../../../../platform/ui/primitives/ResourceCard'
import { useAgentSkillMarket } from '../../hooks/useAgentSkillMarket'
import './Skill.css'

interface SkillMarketDrawerProps {
    isOpen: boolean
    agentId: string
    agentName: string
    installedSkills: SkillEntry[]
    onClose: () => void
    onInstalled: () => void
}

function formatDate(value?: string | null): string {
    if (!value) return '-'
    return new Date(value).toLocaleDateString(undefined, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    })
}

function formatSize(bytes: number, t: (key: string, options?: Record<string, unknown>) => string): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return t('skillMarket.sizeKb', { value: 0 })
    if (bytes < 1024 * 1024) return t('skillMarket.sizeKb', { value: Math.ceil(bytes / 1024) })
    return t('skillMarket.sizeMb', { value: (bytes / 1024 / 1024).toFixed(1) })
}

function getStatusTone(skill: SkillMarketEntry): ResourceStatusTone {
    return skill.containsScripts ? 'warning' : 'neutral'
}

function normalize(value: string): string {
    return value.trim().toLowerCase()
}

function collectInstalledKeys(skills: SkillEntry[]): Set<string> {
    const keys = new Set<string>()
    skills.forEach(skill => {
        if (skill.name) keys.add(normalize(skill.name))
        if (skill.path) {
            keys.add(normalize(skill.path))
            const segments = skill.path.split('/').filter(Boolean)
            const lastSegment = segments[segments.length - 1]
            if (lastSegment) keys.add(normalize(lastSegment))
        }
    })
    return keys
}

export default function SkillMarketDrawer({
    isOpen,
    agentId,
    agentName,
    installedSkills,
    onClose,
    onInstalled,
}: SkillMarketDrawerProps) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const { skills, isLoading, error, fetchSkills, installSkill } = useAgentSkillMarket()
    const [query, setQuery] = useState('')
    const [installingSkillId, setInstallingSkillId] = useState<string | null>(null)
    const [installedSkillIds, setInstalledSkillIds] = useState<Set<string>>(() => new Set())

    useEffect(() => {
        if (isOpen) {
            void fetchSkills()
        }
    }, [fetchSkills, isOpen])

    useEffect(() => {
        setInstalledSkillIds(new Set())
    }, [agentId])

    const installedKeys = useMemo(() => {
        const keys = collectInstalledKeys(installedSkills)
        installedSkillIds.forEach(id => keys.add(normalize(id)))
        return keys
    }, [installedSkillIds, installedSkills])

    const filteredSkills = useMemo(() => {
        const term = query.trim().toLowerCase()
        if (!term) return skills
        return skills.filter(skill =>
            skill.name.toLowerCase().includes(term)
            || skill.id.toLowerCase().includes(term)
            || skill.description.toLowerCase().includes(term)
        )
    }, [query, skills])

    const isInstalled = (skill: SkillMarketEntry) => (
        installedKeys.has(normalize(skill.id))
        || installedKeys.has(normalize(skill.name))
        || installedKeys.has(normalize(skill.path))
    )

    const handleInstall = async (skill: SkillMarketEntry) => {
        if (isInstalled(skill) || installingSkillId) return

        setInstallingSkillId(skill.id)
        const result = await installSkill(agentId, skill.id)
        if (result.success || result.conflict) {
            setInstalledSkillIds(prev => new Set(prev).add(skill.id))
            onInstalled()
            showToast('success', t('skillMarket.installSuccess', { name: skill.name }))
        } else {
            showToast('error', result.error || t('skillMarket.installFailed'))
        }
        setInstallingSkillId(null)
    }

    if (!isOpen) return null

    return (
        <aside className="skill-market-drawer" aria-label={t('skillMarket.title')}>
            <div className="skill-market-drawer-header">
                <div className="skill-market-drawer-title-block">
                    <h2 className="skill-market-drawer-title">{t('skillMarket.title')}</h2>
                    <p className="skill-market-drawer-subtitle">
                        {t('skillMarket.drawerSubtitle', { agent: agentName || agentId })}
                    </p>
                </div>
                <Button variant="ghost" size="sm" iconOnly className="skill-market-drawer-close" onClick={onClose} aria-label={t('common.close')}>
                    <X size={20} />
                </Button>
            </div>

            <div className="skill-market-drawer-target">
                <span>{t('skillMarket.installingTo')}</span>
                <strong>{agentName || agentId}</strong>
            </div>

            <div className="skill-market-drawer-search">
                <Search size={16} />
                <input
                    value={query}
                    onChange={event => setQuery(event.target.value)}
                    placeholder={t('skillMarket.searchPlaceholder')}
                    aria-label={t('skillMarket.searchPlaceholder')}
                />
            </div>

            {error && <div className="skill-alert skill-alert-error">{error}</div>}

            <div className="skill-market-drawer-meta">
                {query.trim()
                    ? t('common.resultsFound', { count: filteredSkills.length })
                    : t('skillMarket.totalSkills', { count: filteredSkills.length })}
            </div>

            <div className="skill-market-drawer-content">
                {isLoading ? (
                    <div className="empty-state">
                        <div className="empty-state-title">{t('skillMarket.loading')}</div>
                    </div>
                ) : filteredSkills.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-title">{query.trim() ? t('common.noResults') : t('skillMarket.emptyTitle')}</div>
                        <div className="empty-state-description">
                            {query.trim() ? t('skillMarket.noMatchSkills') : t('skillMarket.emptyDescription')}
                        </div>
                    </div>
                ) : (
                    <div className="skill-market-drawer-list">
                        {filteredSkills.map(skill => {
                            const installed = isInstalled(skill)
                            const isInstalling = installingSkillId === skill.id
                            const descriptionText = skill.description?.trim() || t('skill.noDescription')
                            return (
                                <ResourceCard
                                    key={skill.id}
                                    className="skill-market-drawer-card"
                                    title={skill.name}
                                    statusLabel={skill.containsScripts ? t('skillMarket.containsScripts') : t('skillMarket.textOnly')}
                                    statusTone={getStatusTone(skill)}
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
                                        { label: t('skillMarket.updatedAt'), value: formatDate(skill.updatedAt) },
                                    ]}
                                    footer={(
                                        <div className="skill-market-drawer-card-footer">
                                            <span className="skill-market-drawer-path">{skill.path}</span>
                                            <Button
                                                variant={installed ? 'secondary' : 'primary'}
                                                tone={installed ? 'subtle' : undefined}
                                                size="sm"
                                                onClick={() => void handleInstall(skill)}
                                                disabled={installed || isInstalling || !!installingSkillId}
                                            >
                                                {installed ? (
                                                    <>
                                                        <Check size={14} /> {t('skill.installed')}
                                                    </>
                                                ) : isInstalling ? (
                                                    t('skillMarket.installing')
                                                ) : (
                                                    <>
                                                        <Download size={14} /> {t('skillMarket.install')}
                                                    </>
                                                )}
                                            </Button>
                                        </div>
                                    )}
                                />
                            )
                        })}
                    </div>
                )}
            </div>
        </aside>
    )
}
