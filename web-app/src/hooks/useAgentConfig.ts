import { useState, useCallback } from 'react'
import type { AgentConfig, UpdateAgentConfigRequest, UpdateAgentConfigResponse } from '../types/agentConfig'
import { GATEWAY_URL, gatewayHeaders } from '../config/runtime'
import { getErrorMessage } from '../utils/errorMessages'
import { useUser } from '../contexts/UserContext'

interface UseAgentConfigResult {
    config: AgentConfig | null
    isLoading: boolean
    error: string | null
    fetchConfig: (agentId: string) => Promise<void>
    updateConfig: (agentId: string, updates: UpdateAgentConfigRequest) => Promise<UpdateAgentConfigResponse>
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
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/config`, {
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
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/config`, {
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

    return {
        config,
        isLoading,
        error,
        fetchConfig,
        updateConfig,
    }
}
