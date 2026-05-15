import { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAgentConfig } from '../hooks/useAgentConfig'
import { useToast } from '../../../platform/providers/ToastContext'
import { McpSection } from '../components/mcp'
import { BasicInfoSection } from '../components/info'
import { ModelConfigSection } from '../components/model'
import { SkillMarketDrawer, SkillSection } from '../components/skill'
import { PromptsSection } from '../components/prompt'
import { MemorySection } from '../components/memory'
import PageBackLink from '../../../platform/ui/primitives/PageBackLink'
import { useGoosed } from '../../../platform/providers/GoosedContext'
import type { AgentModelConfig, CreateProviderRequest, UpdateProviderRequest } from '../../../../types/agentConfig'
import type { SkillEntry } from '../../../../types/skill'
import '../styles/agents.css'

type ConfigTab = 'basic' | 'model' | 'prompts' | 'mcp' | 'skills' | 'memory'

export default function AgentConfigure() {
    const { t } = useTranslation()
    const { agentId } = useParams<{ agentId: string }>()
    const navigate = useNavigate()
    const { config, isLoading, error, fetchConfig, updateConfig, updateModelConfig, createProvider, updateProvider } = useAgentConfig()
    const { showToast } = useToast()
    const { refreshAgents } = useGoosed()

    // Tab state
    const [activeTab, setActiveTab] = useState<ConfigTab>('basic')
    const [isSkillMarketOpen, setIsSkillMarketOpen] = useState(false)
    const [skillRefreshKey, setSkillRefreshKey] = useState(0)
    const [installedSkills, setInstalledSkills] = useState<SkillEntry[]>([])

    // Form state
    const [agentsMd, setAgentsMd] = useState('')
    const [isSavingPrompt, setIsSavingPrompt] = useState(false)

    const handleSkillsLoaded = useCallback((skills: SkillEntry[]) => {
        setInstalledSkills(skills)
    }, [])

    const handleSkillInstalled = useCallback(() => {
        setSkillRefreshKey(value => value + 1)
        void refreshAgents()
    }, [refreshAgents])

    useEffect(() => {
        if (agentId) {
            fetchConfig(agentId)
        }
    }, [agentId, fetchConfig])

    useEffect(() => {
        if (config) {
            setAgentsMd(config.agentsMd)
        }
    }, [config])

    useEffect(() => {
        if (activeTab !== 'skills') {
            setIsSkillMarketOpen(false)
        }
    }, [activeTab])

    const handleSavePrompt = async (nextAgentsMd?: string) => {
        if (!agentId) return false
        setIsSavingPrompt(true)

        const content = nextAgentsMd ?? agentsMd
        const result = await updateConfig(agentId, { agentsMd: content })

        if (result.success) {
            setAgentsMd(content)
            showToast('success', t('agentConfigure.promptSaved'))
            setIsSavingPrompt(false)
            return true
        } else {
            showToast('error', result.error || t('agentConfigure.promptSaveFailed'))
        }

        setIsSavingPrompt(false)
        return false
    }

    const handleSaveModelConfig = async (updates: AgentModelConfig) => {
        if (!agentId) return false
        const result = await updateModelConfig(agentId, updates)
        if (result.success) {
            showToast('success', t('agentConfigure.modelConfigSaved'))
            await fetchConfig(agentId)
            await refreshAgents()
            return true
        }
        showToast('error', result.error || t('agentConfigure.modelConfigSaveFailed'))
        return false
    }

    const handleCreateProvider = async (provider: CreateProviderRequest) => {
        if (!agentId) return false
        const result = await createProvider(agentId, provider)
        if (result.success) {
            showToast('success', t('agentConfigure.providerCreated'))
            await fetchConfig(agentId)
            return true
        }
        showToast('error', result.error || t('agentConfigure.providerCreateFailed'))
        return false
    }

    const handleUpdateProvider = async (providerName: string, provider: UpdateProviderRequest) => {
        if (!agentId) return false
        const result = await updateProvider(agentId, providerName, provider)
        if (result.success) {
            showToast('success', t('agentConfigure.providerUpdated'))
            await fetchConfig(agentId)
            return true
        }
        showToast('error', result.error || t('agentConfigure.providerUpdateFailed'))
        return false
    }

    if (isLoading) {
        return (
            <div className="page-container sidebar-top-page agent-configure-page">
                <div className="agent-configure-loading">{t('agentConfigure.loadingConfig')}</div>
            </div>
        )
    }

    if (error || !config) {
        return (
            <div className="page-container sidebar-top-page agent-configure-page">
                <div className="agent-configure-error">
                    {error || t('agentConfigure.agentNotFound')}
                    <button type="button" onClick={() => navigate('/agents')}>
                        {t('agentConfigure.backToAgents')}
                    </button>
                </div>
            </div>
        )
    }

    const tabs: { key: ConfigTab; label: string }[] = [
        { key: 'basic', label: t('configTabs.basic') },
        { key: 'model', label: t('configTabs.model') },
        { key: 'prompts', label: t('configTabs.prompts') },
        { key: 'mcp', label: t('configTabs.mcp') },
        { key: 'skills', label: t('configTabs.skills') },
        { key: 'memory', label: t('configTabs.memory') },
    ]

    return (
        <div className={`agent-configure-workspace ${isSkillMarketOpen ? 'agent-configure-workspace-with-drawer' : ''}`}>
            <div className="agent-configure-scroll-area">
                <div className="page-container sidebar-top-page agent-configure-page">
                    <div className="agent-configure-header">
                        <PageBackLink onClick={() => navigate('/agents')}>
                            {t('agentConfigure.backToAgents')}
                        </PageBackLink>
                        <div className="agent-configure-title-section">
                            <h1 className="agent-configure-title">{config.name}</h1>
                            <span className="agent-configure-id">{config.id}</span>
                        </div>
                    </div>

                    {/* Tab Navigation */}
                    <div className="config-tabs">
                        {tabs.map(tab => (
                            <button
                                key={tab.key}
                                type="button"
                                className={`config-tab ${activeTab === tab.key ? 'config-tab-active' : ''}`}
                                onClick={() => setActiveTab(tab.key)}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>

                    {/* Tab Content */}
                    <div className="agent-configure-content">
                        {activeTab === 'basic' && (
                            <BasicInfoSection config={config} />
                        )}

                        {activeTab === 'model' && (
                            <ModelConfigSection
                                config={config}
                                onSave={handleSaveModelConfig}
                                onCreateProvider={handleCreateProvider}
                                onUpdateProvider={handleUpdateProvider}
                            />
                        )}

                        {activeTab === 'prompts' && (
                            <section className="agent-configure-section">
                                <PromptsSection
                                    agentId={agentId || null}
                                    agentsMd={agentsMd}
                                    isSavingPrompt={isSavingPrompt}
                                    onSavePrompt={handleSavePrompt}
                                />
                            </section>
                        )}

                        {activeTab === 'mcp' && (
                            <section className="agent-configure-section">
                                <McpSection agentId={agentId || null} />
                            </section>
                        )}

                        {activeTab === 'skills' && (
                            <section className="agent-configure-section">
                                <SkillSection
                                    agentId={agentId || ''}
                                    onBrowseMarket={() => setIsSkillMarketOpen(true)}
                                    refreshKey={skillRefreshKey}
                                    onSkillsLoaded={handleSkillsLoaded}
                                />
                            </section>
                        )}

                        {activeTab === 'memory' && (
                            <section className="agent-configure-section">
                                <MemorySection agentId={agentId || null} />
                            </section>
                        )}
                    </div>
                </div>
            </div>
            <SkillMarketDrawer
                isOpen={isSkillMarketOpen}
                agentId={agentId || ''}
                agentName={config.name}
                installedSkills={installedSkills}
                onClose={() => setIsSkillMarketOpen(false)}
                onInstalled={handleSkillInstalled}
            />
        </div>
    )
}
