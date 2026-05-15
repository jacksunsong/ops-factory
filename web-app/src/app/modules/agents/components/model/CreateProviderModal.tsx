import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Button from '../../../../platform/ui/primitives/Button'
import type { CreateProviderRequest, LlmProvider, UpdateProviderRequest } from '../../../../../types/agentConfig'
import { formatProviderEngine } from './providerDisplay'

interface CreateProviderModalProps {
    mode: 'create' | 'edit'
    provider?: LlmProvider
    onClose: () => void
    onCreate?: (provider: CreateProviderRequest) => Promise<boolean>
    onUpdate?: (providerName: string, provider: UpdateProviderRequest) => Promise<boolean>
}

function firstModelName(provider?: LlmProvider): string {
    return provider?.models?.[0]?.name || ''
}

function firstContextLimit(provider?: LlmProvider): string {
    const contextLimit = provider?.models?.[0]?.context_limit
    return contextLimit === undefined || contextLimit === '' ? '' : String(contextLimit)
}

function ReadonlyProviderField({ label, value }: { label: string; value?: string }) {
    return (
        <label className="form-group">
            <span className="form-label">{label}</span>
            <input className="form-input agent-provider-readonly-input" value={value || '-'} readOnly />
        </label>
    )
}

export default function CreateProviderModal({ mode, provider, onClose, onCreate, onUpdate }: CreateProviderModalProps) {
    const { t } = useTranslation()
    const isEdit = mode === 'edit'
    const [name, setName] = useState(provider?.name || '')
    const [displayName, setDisplayName] = useState(provider?.display_name || '')
    const [baseUrl, setBaseUrl] = useState(provider?.base_url || '')
    const [apiKey, setApiKey] = useState('')
    const [modelName, setModelName] = useState(firstModelName(provider))
    const [contextLimit, setContextLimit] = useState(firstContextLimit(provider))
    const [description, setDescription] = useState(provider?.description || '')
    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const isValid = useMemo(() => {
        if (isEdit) {
            return Boolean(provider?.name) && Boolean(baseUrl.trim())
        }
        return /^[A-Za-z0-9._-]+$/.test(name.trim()) && Boolean(baseUrl.trim())
    }, [baseUrl, isEdit, name, provider?.name])

    const buildProviderPayload = (): UpdateProviderRequest => ({
        base_url: baseUrl.trim(),
        api_key: apiKey.trim(),
        description: description.trim(),
        models: [{
            name: modelName.trim(),
            ...(contextLimit.trim() ? { context_limit: contextLimit.trim() } : {}),
        }],
    })

    const handleSave = async () => {
        setError(null)
        if (!isValid) {
            setError(t('agentConfigure.providerValidation'))
            return
        }
        setIsSaving(true)
        const payload = buildProviderPayload()
        const success = isEdit
            ? Boolean(provider?.name && onUpdate && await onUpdate(provider.name, payload))
            : Boolean(onCreate && await onCreate({
                name: name.trim(),
                display_name: displayName.trim() || name.trim(),
                ...payload,
            })
        )
        setIsSaving(false)
        if (success) {
            onClose()
        }
    }

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal modal-wide" onClick={(event) => event.stopPropagation()}>
                <div className="modal-header">
                    <h2 className="modal-title">{isEdit ? t('agentConfigure.editProvider') : t('agentConfigure.createProvider')}</h2>
                    <button type="button" className="modal-close" onClick={onClose}>&times;</button>
                </div>
                <div className="modal-body">
                    {error && <div className="agents-alert agents-alert-error">{error}</div>}
                    <div className="agent-provider-form-grid">
                        {isEdit && (
                            <ReadonlyProviderField label={t('agentConfigure.providerFile')} value={provider?.fileName} />
                        )}
                        {isEdit ? (
                            <ReadonlyProviderField label={t('agentConfigure.providerName')} value={name} />
                        ) : (
                            <label className="form-group">
                                <span className="form-label">{t('agentConfigure.providerName')}</span>
                                <input className="form-input" value={name} onChange={event => setName(event.target.value)} />
                            </label>
                        )}
                        {isEdit ? (
                            <ReadonlyProviderField label={t('agentConfigure.providerDisplayName')} value={displayName} />
                        ) : (
                            <label className="form-group">
                                <span className="form-label">{t('agentConfigure.providerDisplayName')}</span>
                                <input className="form-input" value={displayName} onChange={event => setDisplayName(event.target.value)} />
                            </label>
                        )}
                        <ReadonlyProviderField label={t('agentConfigure.providerEngine')} value={formatProviderEngine(provider?.engine)} />
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.apiKey')}</span>
                            <input
                                className="form-input"
                                type="password"
                                value={apiKey}
                                onChange={event => setApiKey(event.target.value)}
                                placeholder={t('agentConfigure.apiKeyPlaceholder')}
                            />
                        </label>
                        <label className="form-group agent-provider-form-wide">
                            <span className="form-label">{t('agentConfigure.baseUrl')}</span>
                            <input className="form-input" value={baseUrl} onChange={event => setBaseUrl(event.target.value)} />
                        </label>
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.modelName')}</span>
                            <input className="form-input" value={modelName} onChange={event => setModelName(event.target.value)} />
                        </label>
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.contextLimit')}</span>
                            <input className="form-input" value={contextLimit} onChange={event => setContextLimit(event.target.value)} />
                        </label>
                        <label className="form-group agent-provider-form-wide">
                            <span className="form-label">{t('agentConfigure.providerDescription')}</span>
                            <textarea className="form-input" value={description} onChange={event => setDescription(event.target.value)} />
                        </label>
                    </div>
                </div>
                <div className="modal-footer">
                    <Button variant="secondary" onClick={onClose} disabled={isSaving}>{t('common.cancel')}</Button>
                    <Button variant="primary" onClick={handleSave} disabled={isSaving || !isValid}>
                        {isSaving ? t('agentConfigure.savingProvider') : isEdit ? t('common.save') : t('agentConfigure.createAction')}
                    </Button>
                </div>
            </div>
        </div>
    )
}
