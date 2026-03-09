import { useState, useCallback } from 'react'
import type { SkillEntry, SkillsResponse } from '../types/skill'
import { GATEWAY_URL, GATEWAY_SECRET_KEY } from '../config/runtime'

interface UseSkillsResult {
    skills: SkillEntry[]
    isLoading: boolean
    error: string | null
    fetchSkills: (agentId: string) => Promise<void>
}

export function useSkills(): UseSkillsResult {
    const [skills, setSkills] = useState<SkillEntry[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchSkills = useCallback(async (agentId: string) => {
        setIsLoading(true)
        setError(null)

        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/skills`, {
                headers: { 'x-secret-key': GATEWAY_SECRET_KEY },
                signal: AbortSignal.timeout(10000),
            })

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${await res.text()}`)
            }

            const data: SkillsResponse = await res.json()
            setSkills(data.skills || [])
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch skills')
        } finally {
            setIsLoading(false)
        }
    }, [])

    return {
        skills,
        isLoading,
        error,
        fetchSkills,
    }
}
