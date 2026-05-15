import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Button from '../../../../platform/ui/primitives/Button'
import type { AgentConfig, AgentModelConfig, CreateProviderRequest, LlmProvider, UpdateProviderRequest } from '../../../../../types/agentConfig'
import CreateProviderModal from './CreateProviderModal'
import { formatProviderEngine } from './providerDisplay'

interface ModelConfigSectionProps {
    config: AgentConfig
    onSave: (updates: AgentModelConfig) => Promise<boolean>
    onCreateProvider: (provider: CreateProviderRequest) => Promise<boolean>
    onUpdateProvider: (providerName: string, provider: UpdateProviderRequest) => Promise<boolean>
}

const PARAM_FIELDS: Array<{ key: keyof AgentModelConfig; labelKey: string }> = [
    { key: 'GOOSE_TEMPERATURE', labelKey: 'temperature' },
    { key: 'GOOSE_MAX_TOKENS', labelKey: 'maxTokens' },
    { key: 'GOOSE_CONTEXT_LIMIT', labelKey: 'contextLimit' },
    { key: 'GOOSE_CONTEXT_STRATEGY', labelKey: 'contextStrategy' },
    { key: 'GOOSE_AUTO_COMPACT_THRESHOLD', labelKey: 'autoCompactThreshold' },
    { key: 'GOOSE_MAX_TURNS', labelKey: 'maxTurns' },
]

function providerLabel(provider: LlmProvider): string {
    return provider.display_name || provider.name
}

function firstProviderModel(provider?: LlmProvider): string {
    if (!provider) {
        return ''
    }
    const model = provider.models?.[0]
    return model?.name || provider.display_name || provider.name
}

function firstProviderContextLimit(provider?: LlmProvider): string {
    const contextLimit = provider?.models?.[0]?.context_limit
    return contextLimit === undefined || contextLimit === '' ? '' : String(contextLimit)
}

export default function ModelConfigSection({ config, onSave, onCreateProvider, onUpdateProvider }: ModelConfigSectionProps) {
    const { t } = useTranslation()
    const providers = useMemo(() => config.providers || [], [config.providers])
    const [form, setForm] = useState<AgentModelConfig>({})
    const [isSaving, setIsSaving] = useState(false)
    const [isCreateOpen, setIsCreateOpen] = useState(false)
    const [isEditOpen, setIsEditOpen] = useState(false)

    useEffect(() => {
        setForm({
            ...(config.modelConfig || {}),
            GOOSE_PROVIDER: config.modelConfig?.GOOSE_PROVIDER || config.provider || '',
            GOOSE_MODEL: config.modelConfig?.GOOSE_MODEL || config.model || '',
        })
    }, [config])

    const selectedProvider = providers.find(provider => provider.name === form.GOOSE_PROVIDER)
    const selectedModel = firstProviderModel(selectedProvider)
    const providerModelName = selectedProvider?.models?.[0]?.name || ''

    const handleProviderChange = (providerName: string) => {
        const provider = providers.find(item => item.name === providerName)
        setForm(current => ({
            ...current,
            GOOSE_PROVIDER: providerName,
            GOOSE_MODEL: firstProviderModel(provider),
        }))
    }

    const handleSave = async () => {
        setIsSaving(true)
        await onSave({
            ...form,
            GOOSE_MODEL: selectedModel,
        })
        setIsSaving(false)
    }

    return (
        <section className="agent-configure-section">
            <div className="agent-configure-section-header">
                <div className="agent-configure-section-copy">
                    <h2 className="agent-configure-section-title">{t('agentConfigure.modelConfigTitle')}</h2>
                    <p className="agent-configure-section-desc">{t('agentConfigure.modelConfigDesc')}</p>
                </div>
                <div className="agent-configure-actions agent-configure-actions-top">
                    <Button variant="secondary" size="sm" onClick={() => setIsCreateOpen(true)}>
                        {t('agentConfigure.createAction')}
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setIsEditOpen(true)} disabled={!selectedProvider}>
                        {t('common.edit')}
                    </Button>
                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={handleSave}
                        disabled={isSaving || !selectedProvider || !selectedModel}
                    >
                        {isSaving ? t('agentConfigure.saving') : t('common.save')}
                    </Button>
                </div>
            </div>

            {providers.length === 0 && !form.GOOSE_PROVIDER ? (
                <div className="agent-model-empty">
                    <p>{t('agentConfigure.noProviders')}</p>
                </div>
            ) : (
                <div className="agent-model-form">
                    <div className="agent-model-primary-grid">
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.provider')}</span>
                            <select
                                className="form-input"
                                value={form.GOOSE_PROVIDER || ''}
                                onChange={event => handleProviderChange(event.target.value)}
                            >
                                <option value="">{t('agentConfigure.selectProvider')}</option>
                                {providers.map(provider => (
                                    <option key={provider.name} value={provider.name}>{providerLabel(provider)}</option>
                                ))}
                            </select>
                        </label>
                    </div>

                    {selectedProvider && (
                        <div className="agent-provider-summary">
                            <div className="agent-provider-summary-grid">
                                <ReadonlySummary label={t('agentConfigure.providerFile')} value={selectedProvider.fileName} />
                                <ReadonlySummary label={t('agentConfigure.providerDisplayName')} value={selectedProvider.display_name} />
                                <ReadonlySummary label={t('agentConfigure.providerEngine')} value={formatProviderEngine(selectedProvider.engine)} />
                                <ReadonlySummary label={t('agentConfigure.model')} value={selectedModel} />
                                <ReadonlySummary label={t('agentConfigure.contextLimit')} value={firstProviderContextLimit(selectedProvider)} />
                                <ReadonlySummary label={t('agentConfigure.apiKeyEnv')} value={selectedProvider.api_key_env} />
                                <ReadonlySummary label={t('agentConfigure.baseUrl')} value={selectedProvider.base_url} />
                                <ReadonlySummary label={t('agentConfigure.providerDescription')} value={selectedProvider.description} />
                            </div>
                            {!providerModelName && (
                                <p className="agent-model-warning">{t('agentConfigure.modelNameMissing')}</p>
                            )}
                        </div>
                    )}

                    <div className="agent-model-param-grid">
                        {PARAM_FIELDS.map(field => (
                            <label key={field.key} className="form-group">
                                <span className="form-label">{t(`agentConfigure.${field.labelKey}`)}</span>
                                <input
                                    className="form-input"
                                    value={String(form[field.key] || '')}
                                    onChange={event => setForm(current => ({ ...current, [field.key]: event.target.value }))}
                                />
                            </label>
                        ))}
                    </div>
                </div>
            )}

            {isCreateOpen && (
                <CreateProviderModal
                    mode="create"
                    onClose={() => setIsCreateOpen(false)}
                    onCreate={onCreateProvider}
                />
            )}
            {isEditOpen && selectedProvider && (
                <CreateProviderModal
                    mode="edit"
                    provider={selectedProvider}
                    onClose={() => setIsEditOpen(false)}
                    onUpdate={onUpdateProvider}
                />
            )}
        </section>
    )
}

function ReadonlySummary({ label, value }: { label: string; value?: string }) {
    return (
        <div className="agent-provider-summary-item">
            <span className="agent-provider-summary-label">{label}</span>
            <span className={`agent-provider-summary-value ${value ? '' : 'is-empty'}`}>{value || '-'}</span>
        </div>
    )
}
