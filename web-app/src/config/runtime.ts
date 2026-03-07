import type { UserRole } from '../contexts/UserContext'

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

function isLoopbackHost(host: string): boolean {
    return LOOPBACK_HOSTS.has(host)
}

function resolveGatewayUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallback = `${pageProtocol}//${pageHost}:3000`

    if (!raw) return fallback

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && !isLoopbackHost(pageHost)) {
            url.hostname = pageHost
        }
        return url.toString().replace(/\/$/, '')
    } catch {
        return fallback
    }
}

export const GATEWAY_URL = resolveGatewayUrl(import.meta.env.VITE_GATEWAY_URL)
export const GATEWAY_SECRET_KEY = import.meta.env.VITE_GATEWAY_SECRET_KEY || 'test'

export function isAdminUser(userId: string | null, role: UserRole | null): boolean {
    if (role === 'admin') return true
    return userId === 'sys'
}
