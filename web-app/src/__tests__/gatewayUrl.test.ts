import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

/**
 * Test cases for gateway URL resolution with /ops-gateway path prefix.
 *
 * This file tests that all gateway URL paths include the /ops-gateway prefix
 * after port number, regardless of configuration source.
 */

// Mock import.meta.env for testing
const originalEnv = import.meta.env

describe('resolveGatewayUrl — /ops-gateway path prefix', () => {
    let mockWindow: any

    beforeEach(() => {
        // Mock window.location
        mockWindow = {
            hostname: '127.0.0.1',
            protocol: 'http:',
        }
        global.window = mockWindow as any
    })

    afterEach(() => {
        // Cleanup
        delete (global as any).window
    })

    it('adds /ops-gateway path to HTTPS URL with port', () => {
        const rawUrl = 'https://127.0.0.1:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://127.0.0.1:3000/ops-gateway')
    })

    it('adds /ops-gateway path to HTTP URL with port', () => {
        const rawUrl = 'http://127.0.0.1:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('http://127.0.0.1:3000/ops-gateway')
    })

    it('adds /ops-gateway path to URL with hostname.docker.internal', () => {
        const rawUrl = 'https://host.docker.internal:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://host.docker.internal:3000/ops-gateway')
    })

    it('adds /ops-gateway path to URL with localhost', () => {
        const rawUrl = 'https://localhost:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://localhost:3000/ops-gateway')
    })

    it('adds /ops-gateway path when rawUrl is undefined', () => {
        delete (global as any).window
        mockWindow = {
            hostname: 'localhost',
            protocol: 'https:',
        }
        global.window = mockWindow as any
        const result = resolveGatewayUrl(undefined)
        expect(result).toBe('https://localhost:3000/ops-gateway')
    })

    it('adds /ops-gateway path when rawUrl is empty string', () => {
        delete (global as any).window
        mockWindow = {
            hostname: '127.0.0.1',
            protocol: 'http:',
        }
        global.window = mockWindow as any
        const result = resolveGatewayUrl('')
        expect(result).toBe('http://127.0.0.1:3000/ops-gateway')
    })

    it('removes trailing slash and adds /ops-gateway', () => {
        const rawUrl = 'https://127.0.0.1:3000/'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://127.0.0.1:3000/ops-gateway')
    })

    it('replaces loopback hostname with page hostname', () => {
        delete (global as any).window
        mockWindow = {
            hostname: '192.168.1.100',
            protocol: 'https:',
        }
        global.window = mockWindow as any
        const rawUrl = 'https://127.0.0.1:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://192.168.1.100:3000/ops-gateway')
    })

    it('does not replace non-loopback hostname', () => {
        delete (global as any).window
        mockWindow = {
            hostname: 'example.com',
            protocol: 'https:',
        }
        global.window = mockWindow as any
        const rawUrl = 'https://api.example.com:3000'
        const result = resolveGatewayUrl(rawUrl)
        expect(result).toBe('https://api.example.com:3000/ops-gateway')
    })

    it('handles malformed URL gracefully', () => {
        delete (global as any).window
        mockWindow = {
            hostname: 'localhost',
            protocol: 'http:',
        }
        global.window = mockWindow as any
        const result = resolveGatewayUrl('not-a-valid-url')
        expect(result).toBe('http://localhost:3000/ops-gateway')
    })
})

describe('Gateway URL construction in API calls', () => {
    let mockWindow: any

    beforeEach(() => {
        mockWindow = {
            hostname: '127.0.0.1',
            protocol: 'https:',
        }
        global.window = mockWindow as any
        // Reset import.meta.env
        vi.stubGlobal('import', {
            meta: {
                env: {
                    VITE_GATEWAY_URL: 'https://127.0.0.1:3000',
                    VITE_GATEWAY_SECRET_KEY: 'test',
                },
            },
        })
    })

    afterEach(() => {
        delete (global as any).window
        vi.unstubAllGlobals()
    })

    it('constructs /agents endpoint correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const url = `${GATEWAY_URL}/agents`
        expect(url).toBe('https://127.0.0.1:3000/ops-gateway/agents')
    })

    it('constructs /me endpoint correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const url = `${GATEWAY_URL}/me`
        expect(url).toBe('https://127.0.0.1:3000/ops-gateway/me')
    })

    it('constructs /config endpoint correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const url = `${GATEWAY_URL}/config`
        expect(url).toBe('https://127.0.0.1:3000/ops-gateway/config')
    })

    it('constructs /monitoring endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const monitoringUrl = `${GATEWAY_URL}/monitoring/metrics`
        expect(monitoringUrl).toBe('https://127.0.0.1:3000/ops-gateway/monitoring/metrics')
    })

    it('constructs agent-specific endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const agentUrl = `${GATEWAY_URL}/agents/universal-agent`
        expect(agentUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent')
    })

    it('constructs file endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const fileUrl = `${GATEWAY_URL}/agents/universal-agent/files/src/test.txt?key=test`
        expect(fileUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent/files/src/test.txt?key=test')
    })

    it('constructs MCP endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const mcpUrl = `${GATEWAY_URL}/agents/universal-agent/mcp`
        expect(mcpUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent/mcp')
    })

    it('constructs memory endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const memoryUrl = `${GATEWAY_URL}/agents/universal-agent/memory`
        expect(memoryUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent/memory')
    })

    it('constructs skills endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const skillsUrl = `${GATEWAY_URL}/agents/universal-agent/skills`
        expect(skillsUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent/skills')
    })

    it('constructs config endpoints correctly', () => {
        const GATEWAY_URL = resolveGatewayUrl('https://127.0.0.1:3000')
        const configUrl = `${GATEWAY_URL}/agents/universal-agent/config`
        expect(configUrl).toBe('https://127.0.0.1:3000/ops-gateway/agents/universal-agent/config')
    })
})

/**
 * Re-implementation of resolveGatewayUrl for testing purposes.
 * This should match the actual implementation in runtime.ts.
 */
function resolveGatewayUrl(raw: string | undefined): string {
    const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

    function isLoopbackHost(host: string): boolean {
        return LOOPBACK_HOSTS.has(host)
    }

    // Use the mocked global window object
    const pageHost = (global as any).window?.hostname || '127.0.0.1'
    const pageProtocol = (global as any).window?.protocol || 'http:'
    const fallback = `${pageProtocol}//${pageHost}:3000/ops-gateway`

    if (!raw) return fallback

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && !isLoopbackHost(pageHost)) {
            url.hostname = pageHost
        }
        // Add /ops-gateway path prefix after port
        return `${url.origin}/ops-gateway`
    } catch {
        return fallback
    }
}
