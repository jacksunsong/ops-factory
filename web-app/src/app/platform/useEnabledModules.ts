import { useMemo } from 'react'
import { useUser } from './providers/UserContext'
import { loadModules } from './ModuleLoader'
import type { ModuleContext } from './module-types'

const ALL_MODULES = loadModules()

export function useModuleContext(): ModuleContext {
    const { userId } = useUser()

    return {
        isAuthenticated: !!userId,
        userId,
    }
}

export function useEnabledModules() {
    const ctx = useModuleContext()

    return useMemo(
        () => ALL_MODULES.filter((module) => module.enabled?.(ctx) ?? true),
        [ctx.isAuthenticated, ctx.userId]
    )
}
