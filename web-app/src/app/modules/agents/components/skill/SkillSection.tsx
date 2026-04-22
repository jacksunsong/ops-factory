import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSkills } from '../../hooks/useSkills'
import Button from '../../../../platform/ui/primitives/Button'
import { useToast } from '../../../../platform/providers/ToastContext'
import { useGoosed } from '../../../../platform/providers/GoosedContext'
import SkillCard from './SkillCard'
import type { SkillEntry } from '../../../../../types/skill'
import './Skill.css'

interface SkillSectionProps {
    agentId: string
    onBrowseMarket?: () => void
    refreshKey?: number
    onSkillsLoaded?: (skills: SkillEntry[]) => void
}

export default function SkillSection({ agentId, onBrowseMarket, refreshKey = 0, onSkillsLoaded }: SkillSectionProps) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const { refreshAgents } = useGoosed()
    const { skills, isLoading, error, fetchSkills, uninstallSkill } = useSkills()
    const [uninstallTarget, setUninstallTarget] = useState<SkillEntry | null>(null)
    const [isUninstalling, setIsUninstalling] = useState(false)

    useEffect(() => {
        if (agentId) {
            fetchSkills(agentId)
        }
    }, [agentId, fetchSkills, refreshKey])

    useEffect(() => {
        onSkillsLoaded?.(skills)
    }, [onSkillsLoaded, skills])

    if (!agentId) return null

    const getSkillId = (skill: SkillEntry) => {
        if (skill.id) return skill.id
        const segments = skill.path.split('/').filter(Boolean)
        return segments[segments.length - 1] || skill.name
    }

    const handleConfirmUninstall = async () => {
        if (!uninstallTarget) return
        setIsUninstalling(true)
        const result = await uninstallSkill(agentId, getSkillId(uninstallTarget))
        if (result.success) {
            showToast('success', t('skill.uninstallSuccess', { name: uninstallTarget.name }))
            void refreshAgents()
            setUninstallTarget(null)
        } else {
            showToast('error', result.error || t('skill.uninstallFailed'))
        }
        setIsUninstalling(false)
    }

    const handleCloseUninstallModal = () => {
        if (!isUninstalling) {
            setUninstallTarget(null)
        }
    }

    const handleOpenUninstallModal = (skill: SkillEntry) => {
        if (!isUninstalling) {
            setUninstallTarget(skill)
        }
    }

    return (
        <div className="skill-section">
            <div className="skill-section-header">
                <div className="skill-section-heading">
                    <h3 className="skill-section-title">{t('skill.title')}</h3>
                    <span className="skill-section-count">{skills.length}</span>
                </div>
                {onBrowseMarket && (
                    <Button variant="secondary" size="sm" onClick={onBrowseMarket}>
                        {t('market.browseMarket')}
                    </Button>
                )}
            </div>

            {error && (
                <div className="skill-alert skill-alert-error">{error}</div>
            )}

            {isLoading ? (
                <div className="skill-loading">{t('skill.loadingSkills')}</div>
            ) : skills.length > 0 ? (
                <div className="skill-grid">
                    {skills.map(skill => (
                        <SkillCard
                            key={skill.id || skill.path || skill.name}
                            skill={skill}
                            onUninstall={handleOpenUninstallModal}
                            isUninstalling={isUninstalling && uninstallTarget?.path === skill.path}
                        />
                    ))}
                </div>
            ) : (
                <div className="skill-empty">
                    <p>{t('skill.noSkills')}</p>
                </div>
            )}

            {uninstallTarget && (
                <div className="modal-overlay" onClick={handleCloseUninstallModal}>
                    <div className="modal" onClick={event => event.stopPropagation()}>
                        <div className="modal-header">
                            <h2 className="modal-title">{t('skill.uninstallTitle')}</h2>
                            <button
                                type="button"
                                className="modal-close"
                                onClick={handleCloseUninstallModal}
                                disabled={isUninstalling}
                            >
                                &times;
                            </button>
                        </div>
                        <div className="modal-body">
                            <p className="skill-uninstall-summary">
                                {t('skill.uninstallConfirm', { name: uninstallTarget.name })}
                            </p>
                            <div className="agents-alert agents-alert-warning">
                                {t('skill.uninstallWarning')}
                            </div>
                        </div>
                        <div className="modal-footer">
                            <Button variant="secondary" onClick={handleCloseUninstallModal} disabled={isUninstalling}>
                                {t('common.cancel')}
                            </Button>
                            <Button variant="danger" onClick={() => void handleConfirmUninstall()} disabled={isUninstalling}>
                                {isUninstalling ? t('skill.uninstalling') : t('skill.uninstall')}
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
