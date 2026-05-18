import type { ReactNode } from 'react'
import type { AccessLevel } from './module-types'

export function hasAccess(access: AccessLevel, ctx: { isAuthenticated: boolean }) {
    if (access === 'public') {
        return true
    }

    return ctx.isAuthenticated
}

export function AccessGuard({
    children,
}: {
    access?: AccessLevel
    children: ReactNode
}) {
    return <>{children}</>
}
