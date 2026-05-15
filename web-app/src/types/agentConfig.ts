// Agent configuration types

export interface AgentConfig {
    id: string
    name: string
    agentsMd: string  // AGENTS.md content
    workingDir: string
    provider?: string
    model?: string
    modelConfig?: AgentModelConfig
    configSummary?: AgentConfigSummary
    providers?: LlmProvider[]
}

export interface UpdateAgentConfigRequest {
    agentsMd?: string
}

export interface AgentModelConfig {
    GOOSE_PROVIDER?: string
    GOOSE_MODEL?: string
    GOOSE_MODE?: string
    GOOSE_CONTEXT_LIMIT?: string
    GOOSE_MAX_TOKENS?: string
    GOOSE_TEMPERATURE?: string
    GOOSE_CONTEXT_STRATEGY?: string
    GOOSE_AUTO_COMPACT_THRESHOLD?: string
    GOOSE_MAX_TURNS?: string
}

export interface AgentConfigSummary {
    mode?: string
    disableKeyring?: string | boolean
    telemetryEnabled?: string | boolean
    enabledExtensions?: number
    disabledExtensions?: number
}

export interface LlmProviderModel {
    name: string
    context_limit?: number | string
}

export interface LlmProvider {
    name: string
    engine?: string
    display_name?: string
    description?: string
    api_key_env?: string
    base_url?: string
    models?: LlmProviderModel[]
    supports_streaming?: boolean
    requires_auth?: boolean
    fileName?: string
}

export interface CreateProviderRequest {
    name: string
    display_name?: string
    description?: string
    api_key?: string
    base_url?: string
    models: LlmProviderModel[]
}

export interface UpdateProviderRequest {
    description?: string
    api_key?: string
    base_url?: string
    models: LlmProviderModel[]
}

export interface UpdateAgentConfigResponse {
    success: boolean
    error?: string
}

export interface CreateProviderResponse extends UpdateAgentConfigResponse {
    provider?: LlmProvider
}
