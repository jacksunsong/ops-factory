import { useCallback, useState } from 'react'
import type { SkillMarketEntry, SkillMarketListResponse } from '../../../../types/skillMarket'
import { GATEWAY_URL, SKILL_MARKET_SERVICE_URL, gatewayHeaders } from '../../../../config/runtime'
import { getErrorMessage } from '../../../../utils/errorMessages'
import { useUser } from '../../../platform/providers/UserContext'

interface UseAgentSkillMarketResult {
    skills: SkillMarketEntry[]
    isLoading: boolean
    error: string | null
    fetchSkills: () => Promise<void>
    installSkill: (agentId: string, skillId: string) => Promise<{ success: boolean; conflict?: boolean; error?: string }>
}

export function useAgentSkillMarket(): UseAgentSkillMarketResult {
    const { userId } = useUser()
    const [skills, setSkills] = useState<SkillMarketEntry[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchSkills = useCallback(async () => {
        setIsLoading(true)
        setError(null)

        try {
            const response = await fetch(`${SKILL_MARKET_SERVICE_URL}/skills`, {
                signal: AbortSignal.timeout(10000),
            })
            if (!response.ok) throw new Error(await response.text())
            const data = await response.json() as SkillMarketListResponse
            setSkills(data.items || [])
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setIsLoading(false)
        }
    }, [])

    const installSkill = useCallback(async (agentId: string, skillId: string) => {
        try {
            const response = await fetch(`${GATEWAY_URL}/agents/${encodeURIComponent(agentId)}/skills/install`, {
                method: 'POST',
                headers: gatewayHeaders(userId),
                body: JSON.stringify({ skillId }),
            })
            if (response.status === 409) {
                return { success: false, conflict: true, error: await response.text() }
            }
            if (!response.ok) throw new Error(await response.text())
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [userId])

    return {
        skills,
        isLoading,
        error,
        fetchSkills,
        installSkill,
    }
}
