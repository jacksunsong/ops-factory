import { useTranslation } from 'react-i18next'
import { Trash2 } from 'lucide-react'
import type { SkillEntry } from '../../../../../types/skill'
import Button from '../../../../platform/ui/primitives/Button'
import ResourceCard from '../../../../platform/ui/primitives/ResourceCard'
import './Skill.css'

interface SkillCardProps {
    skill: SkillEntry
    onUninstall: (skill: SkillEntry) => void
    isUninstalling?: boolean
}

export default function SkillCard({ skill, onUninstall, isUninstalling = false }: SkillCardProps) {
    const { t } = useTranslation()
    const descriptionText = skill.description?.trim() || t('skill.noDescription')

    return (
        <ResourceCard
            className="skill-installed-card"
            title={skill.name}
            statusLabel={t('skill.installed')}
            statusTone="success"
            summary={(
                <p
                    className={[
                        'resource-card-summary-text',
                        'skill-card-description',
                        !skill.description ? 'resource-card-summary-placeholder' : '',
                    ].filter(Boolean).join(' ')}
                    title={descriptionText}
                >
                    {descriptionText}
                </p>
            )}
            metrics={[
                {
                    label: t('skill.path'),
                    value: <span className="skill-card-path-value">{skill.path}</span>,
                },
            ]}
            footer={(
                <div className="skill-installed-card-footer">
                    <Button variant="danger" tone="quiet" size="sm" onClick={() => onUninstall(skill)}>
                        <Trash2 size={14} /> {isUninstalling ? t('skill.uninstalling') : t('skill.uninstall')}
                    </Button>
                </div>
            )}
        />
    )
}
