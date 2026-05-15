import { useTranslation } from 'react-i18next'
import type { AgentConfig } from '../../../../../types/agentConfig'

interface BasicInfoSectionProps {
    config: AgentConfig
}

function ReadonlyField({ label, value }: { label: string; value?: string }) {
    return (
        <div className="agent-info-field">
            <div className="agent-info-label">{label}</div>
            <div className={`agent-info-value ${value ? '' : 'is-empty'}`}>{value || '-'}</div>
        </div>
    )
}

function formatWorkingDir(path: string): string {
    const marker = '/gateway/agents/'
    const markerIndex = path.indexOf(marker)
    if (markerIndex >= 0) {
        return `gateway/agents/${path.slice(markerIndex + marker.length)}`
    }
    return path
}

function formatValue(value: string | number | boolean | undefined): string {
    if (value === undefined || value === '') {
        return ''
    }
    return String(value)
}

export default function BasicInfoSection({ config }: BasicInfoSectionProps) {
    const { t } = useTranslation()
    const summary = config.configSummary

    return (
        <div className="agent-basic-info">
            <section className="agent-configure-section">
                <div className="agent-configure-section-header">
                    <div className="agent-configure-section-copy">
                        <h2 className="agent-configure-section-title">{t('agentConfigure.basicInfoTitle')}</h2>
                    </div>
                </div>
                <div className="agent-info-grid">
                    <ReadonlyField label={t('agentConfigure.roleName')} value={config.name} />
                    <ReadonlyField label={t('agentConfigure.roleId')} value={config.id} />
                    <ReadonlyField label={t('agentConfigure.workingDir')} value={formatWorkingDir(config.workingDir)} />
                    <ReadonlyField label={t('agentConfigure.configMode')} value={formatValue(summary?.mode)} />
                    <ReadonlyField label={t('agentConfigure.disableKeyring')} value={formatValue(summary?.disableKeyring)} />
                    <ReadonlyField label={t('agentConfigure.telemetryEnabled')} value={formatValue(summary?.telemetryEnabled)} />
                    <ReadonlyField label={t('agentConfigure.enabledExtensions')} value={formatValue(summary?.enabledExtensions)} />
                    <ReadonlyField label={t('agentConfigure.disabledExtensions')} value={formatValue(summary?.disabledExtensions)} />
                </div>
            </section>
        </div>
    )
}
