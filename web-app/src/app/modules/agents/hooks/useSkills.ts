import { useState, useCallback } from 'react'
import type { SkillEntry, SkillsResponse } from '../../../../types/skill'
import { GATEWAY_URL, gatewayHeaders } from '../../../../config/runtime'
import { getErrorMessage } from '../../../../utils/errorMessages'
import { useUser } from '../../../platform/providers/UserContext'

interface UseSkillsResult {
    skills: SkillEntry[]
    isLoading: boolean
    error: string | null
    fetchSkills: (agentId: string) => Promise<void>
    uninstallSkill: (agentId: string, skillId: string) => Promise<{ success: boolean; error?: string }>
}

export function useSkills(): UseSkillsResult {
    const { userId } = useUser()
    const [skills, setSkills] = useState<SkillEntry[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchSkills = useCallback(async (agentId: string) => {
        setIsLoading(true)
        setError(null)

        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/skills`, {
                headers: gatewayHeaders(userId),
                signal: AbortSignal.timeout(10000),
            })

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${await res.text()}`)
            }

            const data: SkillsResponse = await res.json()
            setSkills(data.skills || [])
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setIsLoading(false)
        }
    }, [userId])

    const uninstallSkill = useCallback(async (agentId: string, skillId: string) => {
        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${encodeURIComponent(agentId)}/skills/${encodeURIComponent(skillId)}`, {
                method: 'DELETE',
                headers: gatewayHeaders(userId),
                signal: AbortSignal.timeout(10000),
            })

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${await res.text()}`)
            }

            await fetchSkills(agentId)
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [fetchSkills, userId])

    return {
        skills,
        isLoading,
        error,
        fetchSkills,
        uninstallSkill,
    }
}
