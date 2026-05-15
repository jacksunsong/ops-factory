import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { usePrompts } from '../../hooks/usePrompts'
import { useToast } from '../../../../platform/providers/ToastContext'
import type { SystemPromptContent } from '../../../../../types/systemPrompt'
import Button from '../../../../platform/ui/primitives/Button'
import './PromptsSection.css'

interface PromptsSectionProps {
    agentId: string | null
    agentsMd: string
    isSavingPrompt: boolean
    onSavePrompt: (value: string) => Promise<boolean>
}

export default function PromptsSection({
    agentId,
    agentsMd,
    isSavingPrompt,
    onSavePrompt,
}: PromptsSectionProps) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const {
        templates,
        isLoading,
        error,
        fetchPrompts,
        getPrompt,
        savePrompt,
        resetPrompt,
        resetAllPrompts,
    } = usePrompts(agentId)

    const [expandedName, setExpandedName] = useState<string | null>(null)
    const [editContent, setEditContent] = useState('')
    const [promptData, setPromptData] = useState<SystemPromptContent | null>(null)
    const [isSaving, setIsSaving] = useState(false)
    const [isLoadingPrompt, setIsLoadingPrompt] = useState(false)
    const [hasChanges, setHasChanges] = useState(false)
    const [isRoleExpanded, setIsRoleExpanded] = useState(false)
    const [roleEditContent, setRoleEditContent] = useState(agentsMd)
    const [hasRoleChanges, setHasRoleChanges] = useState(false)

    useEffect(() => {
        if (agentId) {
            fetchPrompts()
        }
    }, [agentId, fetchPrompts])

    useEffect(() => {
        setRoleEditContent(agentsMd)
        setHasRoleChanges(false)
    }, [agentsMd])

    const handleExpand = useCallback(async (name: string) => {
        if (expandedName === name) {
            setExpandedName(null)
            setPromptData(null)
            setEditContent('')
            setHasChanges(false)
            return
        }

        setIsLoadingPrompt(true)
        setExpandedName(name)

        const data = await getPrompt(name)
        if (data) {
            setPromptData(data)
            setEditContent(data.content)
            setHasChanges(false)
        }

        setIsLoadingPrompt(false)
    }, [expandedName, getPrompt])

    const handleContentChange = (value: string) => {
        setEditContent(value)
        setHasChanges(value !== promptData?.content)
    }

    const handleSave = async () => {
        if (!expandedName) return
        setIsSaving(true)

        const shouldResetToDefault = promptData?.is_customized && editContent === promptData.default_content
        const success = shouldResetToDefault
            ? await resetPrompt(expandedName)
            : await savePrompt(expandedName, editContent)
        if (success) {
            showToast('success', shouldResetToDefault ? t('prompts.resetSuccess') : t('prompts.saved'))
            setPromptData(prev => prev ? {
                ...prev,
                content: editContent,
                is_customized: shouldResetToDefault ? false : true,
            } : prev)
            setHasChanges(false)
        } else {
            showToast('error', shouldResetToDefault ? t('prompts.resetFailed') : t('prompts.saveFailed'))
        }

        setIsSaving(false)
    }

    const handleRestoreDefault = () => {
        if (promptData) {
            setEditContent(promptData.default_content)
            setHasChanges(promptData.default_content !== promptData.content)
        }
    }

    const handleResetAll = async () => {
        const customizedCount = templates.filter(t => t.is_customized).length
        if (customizedCount === 0) return

        const success = await resetAllPrompts()
        if (success) {
            showToast('success', t('prompts.resetAllSuccess'))
            setExpandedName(null)
            setPromptData(null)
            setEditContent('')
            setHasChanges(false)
        } else {
            showToast('error', t('prompts.resetAllFailed'))
        }
    }

    const handleRoleExpand = () => {
        if (isRoleExpanded) {
            setIsRoleExpanded(false)
            setRoleEditContent(agentsMd)
            setHasRoleChanges(false)
            return
        }
        setIsRoleExpanded(true)
        setRoleEditContent(agentsMd)
        setHasRoleChanges(false)
    }

    const handleRoleContentChange = (value: string) => {
        setRoleEditContent(value)
        setHasRoleChanges(value !== agentsMd)
    }

    const handleRoleSave = async () => {
        const success = await onSavePrompt(roleEditContent)
        if (success) {
            setHasRoleChanges(false)
        }
    }

    if (!agentId) return null

    const customizedCount = templates.filter(t => t.is_customized).length
    const hasPendingDefaultReset = Boolean(promptData?.is_customized && editContent === promptData.default_content)

    return (
        <div className="prompts-section">
            <div className="agent-configure-subsection">
                <div className="prompts-section-header">
                    <h3 className="prompts-section-title">{t('agentConfigure.rolePromptTitle')}</h3>
                </div>
                <p className="prompts-section-desc">{t('agentConfigure.rolePromptDesc')}</p>
                <div className={`prompts-item ${isRoleExpanded ? 'prompts-item-expanded' : ''}`}>
                    <div
                        className="prompts-item-header"
                        onClick={handleRoleExpand}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                                event.preventDefault()
                                handleRoleExpand()
                            }
                        }}
                    >
                        <div className="prompts-item-info">
                            <div className="prompts-item-name-row">
                                <span className="prompts-item-name">AGENTS.md</span>
                            </div>
                            <span className="prompts-item-desc">{t('agentConfigure.rolePromptItemDesc')}</span>
                        </div>
                        <Button
                            variant="secondary"
                            size="sm"
                            className="prompts-edit-btn"
                            onClick={(event) => {
                                event.stopPropagation()
                                handleRoleExpand()
                            }}
                        >
                            {isRoleExpanded ? t('prompts.collapse') : t('common.edit')}
                        </Button>
                    </div>
                    {isRoleExpanded && (
                        <div className="prompts-item-editor">
                            <textarea
                                className="prompts-textarea"
                                value={roleEditContent}
                                onChange={(event) => handleRoleContentChange(event.target.value)}
                                placeholder={t('agentConfigure.promptPlaceholder')}
                                rows={16}
                                spellCheck={false}
                            />
                            <div className="prompts-editor-actions">
                                <div className="prompts-editor-actions-left" />
                                <div className="prompts-editor-actions-right">
                                    <Button variant="secondary" onClick={handleRoleExpand}>
                                        {t('common.cancel')}
                                    </Button>
                                    <Button
                                        variant="primary"
                                        onClick={handleRoleSave}
                                        disabled={isSavingPrompt || !hasRoleChanges}
                                    >
                                        {isSavingPrompt ? t('agentConfigure.saving') : t('common.save')}
                                    </Button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            <div className="agent-configure-subsection">
                <div className="prompts-section-header">
                    <h3 className="prompts-section-title">{t('prompts.title')}</h3>
                </div>

                <p className="prompts-section-desc">{t('prompts.description')}</p>

                <div className="prompts-warning">
                    {t('prompts.templateWarning')}
                </div>

                {error && (
                    <div className="prompts-alert prompts-alert-error">{error}</div>
                )}

                {isLoading && (
                    <div className="prompts-loading">{t('prompts.loading')}</div>
                )}
                {!isLoading && templates.length > 0 && (
                    <div className="prompts-list">
                        {templates.map(template => {
                            const isExpanded = expandedName === template.name
                            return (
                                <div
                                    key={template.name}
                                    className={`prompts-item ${isExpanded ? 'prompts-item-expanded' : ''}`}
                                >
                                    <div
                                        className="prompts-item-header"
                                        onClick={() => handleExpand(template.name)}
                                        role="button"
                                        tabIndex={0}
                                        onKeyDown={(event) => {
                                            if (event.key === 'Enter' || event.key === ' ') {
                                                event.preventDefault()
                                                handleExpand(template.name)
                                            }
                                        }}
                                    >
                                        <div className="prompts-item-info">
                                            <div className="prompts-item-name-row">
                                                <span className="prompts-item-name">{template.name}</span>
                                                {template.is_customized && (
                                                    <span className="prompts-customized-badge">
                                                        {t('prompts.customized')}
                                                    </span>
                                                )}
                                            </div>
                                            <span className="prompts-item-desc">{template.description}</span>
                                        </div>
                                        <Button
                                            variant="secondary"
                                            size="sm"
                                            className="prompts-edit-btn"
                                            onClick={(event) => {
                                                event.stopPropagation()
                                                handleExpand(template.name)
                                            }}
                                        >
                                            {isExpanded ? t('prompts.collapse') : t('common.edit')}
                                        </Button>
                                    </div>

                                    {isExpanded && (
                                        <div className="prompts-item-editor">
                                            {isLoadingPrompt ? (
                                                <div className="prompts-loading">{t('common.loading')}</div>
                                            ) : (
                                                <>
                                                    <textarea
                                                        className="prompts-textarea"
                                                        value={editContent}
                                                        onChange={(event) => handleContentChange(event.target.value)}
                                                        rows={16}
                                                        spellCheck={false}
                                                    />
                                                    <div className="prompts-editor-actions">
                                                        <div className="prompts-editor-actions-left">
                                                            <Button
                                                                variant="secondary"
                                                                onClick={handleRestoreDefault}
                                                                disabled={editContent === promptData?.default_content}
                                                            >
                                                                {t('prompts.restoreDefault')}
                                                            </Button>
                                                        </div>
                                                        <div className="prompts-editor-actions-right">
                                                            <Button
                                                                variant="secondary"
                                                                onClick={() => handleExpand(template.name)}
                                                            >
                                                                {t('common.cancel')}
                                                            </Button>
                                                            <Button
                                                                variant="primary"
                                                                onClick={handleSave}
                                                                disabled={isSaving || (!hasChanges && !hasPendingDefaultReset)}
                                                            >
                                                                {isSaving ? t('agentConfigure.saving') : t('common.save')}
                                                            </Button>
                                                        </div>
                                                    </div>
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )
                        })}
                    </div>
                )}
                {!isLoading && templates.length === 0 && (
                    <div className="prompts-empty">
                        <p>{t('prompts.noPrompts')}</p>
                    </div>
                )}

                {customizedCount > 0 && !isLoading && templates.length > 0 && (
                    <div className="prompts-bulk-actions">
                        <Button
                            variant="danger"
                            tone="quiet"
                            size="sm"
                            onClick={handleResetAll}
                        >
                            {t('prompts.resetAll')}
                        </Button>
                    </div>
                )}
            </div>
        </div>
    )
}
