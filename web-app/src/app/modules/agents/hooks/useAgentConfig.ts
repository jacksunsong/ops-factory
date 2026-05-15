import { useState, useCallback } from 'react'
import type {
    AgentConfig,
    AgentModelConfig,
    CreateProviderRequest,
    CreateProviderResponse,
    UpdateAgentConfigRequest,
    UpdateAgentConfigResponse,
    UpdateProviderRequest,
} from '../../../../types/agentConfig'
import { runtime, gatewayHeaders } from '../../../../config/runtime'
import { getErrorMessage } from '../../../../utils/errorMessages'
import { useUser } from '../../../platform/providers/UserContext'

interface UseAgentConfigResult {
    config: AgentConfig | null
    isLoading: boolean
    error: string | null
    fetchConfig: (agentId: string) => Promise<void>
    updateConfig: (agentId: string, updates: UpdateAgentConfigRequest) => Promise<UpdateAgentConfigResponse>
    updateModelConfig: (agentId: string, updates: AgentModelConfig) => Promise<UpdateAgentConfigResponse>
    createProvider: (agentId: string, provider: CreateProviderRequest) => Promise<CreateProviderResponse>
    updateProvider: (agentId: string, providerName: string, provider: UpdateProviderRequest) => Promise<CreateProviderResponse>
}

export function useAgentConfig(): UseAgentConfigResult {
    const { userId } = useUser()
    const [config, setConfig] = useState<AgentConfig | null>(null)
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchConfig = useCallback(async (agentId: string) => {
        setIsLoading(true)
        setError(null)

        try {
            const res = await fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/config`, {
                headers: gatewayHeaders(userId),
                signal: AbortSignal.timeout(10000),
            })

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${await res.text()}`)
            }

            const data: AgentConfig = await res.json()
            setConfig(data)
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setIsLoading(false)
        }
    }, [userId])

    const updateConfig = useCallback(async (
        agentId: string,
        updates: UpdateAgentConfigRequest
    ): Promise<UpdateAgentConfigResponse> => {
        setError(null)

        try {
            const res = await fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/config`, {
                method: 'PUT',
                headers: gatewayHeaders(userId),
                body: JSON.stringify(updates),
                signal: AbortSignal.timeout(10000),
            })

            const data: UpdateAgentConfigResponse = await res.json()

            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to update config')
            }

            return data
        } catch (err) {
            const errorMsg = getErrorMessage(err)
            setError(errorMsg)
            return { success: false, error: errorMsg }
        }
    }, [userId])

    const updateModelConfig = useCallback(async (
        agentId: string,
        updates: AgentModelConfig
    ): Promise<UpdateAgentConfigResponse> => {
        setError(null)

        try {
            const res = await fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/model-config`, {
                method: 'PUT',
                headers: gatewayHeaders(userId),
                body: JSON.stringify(updates),
                signal: AbortSignal.timeout(10000),
            })

            const data: UpdateAgentConfigResponse = await res.json()

            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to update model config')
            }

            return data
        } catch (err) {
            const errorMsg = getErrorMessage(err)
            setError(errorMsg)
            return { success: false, error: errorMsg }
        }
    }, [userId])

    const createProvider = useCallback(async (
        agentId: string,
        provider: CreateProviderRequest
    ): Promise<CreateProviderResponse> => {
        setError(null)

        try {
            const res = await fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/providers`, {
                method: 'POST',
                headers: gatewayHeaders(userId),
                body: JSON.stringify(provider),
                signal: AbortSignal.timeout(10000),
            })

            const data: CreateProviderResponse = await res.json()

            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to create provider')
            }

            return data
        } catch (err) {
            const errorMsg = getErrorMessage(err)
            setError(errorMsg)
            return { success: false, error: errorMsg }
        }
    }, [userId])

    const updateProvider = useCallback(async (
        agentId: string,
        providerName: string,
        provider: UpdateProviderRequest
    ): Promise<CreateProviderResponse> => {
        setError(null)

        try {
            const res = await fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/providers/${encodeURIComponent(providerName)}`, {
                method: 'PUT',
                headers: gatewayHeaders(userId),
                body: JSON.stringify(provider),
                signal: AbortSignal.timeout(10000),
            })

            const data: CreateProviderResponse = await res.json()

            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to update provider')
            }

            return data
        } catch (err) {
            const errorMsg = getErrorMessage(err)
            setError(errorMsg)
            return { success: false, error: errorMsg }
        }
    }, [userId])

    return {
        config,
        isLoading,
        error,
        fetchConfig,
        updateConfig,
        updateModelConfig,
        createProvider,
        updateProvider,
    }
}
