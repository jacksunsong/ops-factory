import { trackedFetch } from '../app/platform/logging/requestClient'
import { configureWebappLogging, type WebappLoggingRuntimeConfig } from '../app/platform/logging/settings'

interface RuntimeConfig {
    gatewayUrl?: string
    gatewaySecretKey?: string
    controlCenterUrl?: string
    controlCenterSecretKey?: string
    knowledgeServiceUrl?: string
    businessIntelligenceServiceUrl?: string
    skillMarketServiceUrl?: string
    operationIntelligenceServiceUrl?: string
    operationIntelligenceSecretKey?: string
    logging?: {
        level?: WebappLoggingRuntimeConfig['level']
        consoleEnabled?: boolean
        bufferSize?: number
        sink?: 'console'
        logDirectory?: string | null
    }
}

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])
const GATEWAY_PATH_PREFIX = '/gateway'
const CONTROL_CENTER_PATH_PREFIX = '/control-center'
const KNOWLEDGE_PATH_PREFIX = '/knowledge'
const BUSINESS_INTELLIGENCE_PATH_PREFIX = '/business-intelligence'
const SKILL_MARKET_PATH_PREFIX = '/skill-market'
const OPERATION_INTELLIGENCE_PATH_PREFIX = '/operation-intelligence'

function isLoopbackHost(host: string): boolean {
    return LOOPBACK_HOSTS.has(host)
}

function resolveGatewayUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:3000`

    if (!raw) return `${GATEWAY_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${GATEWAY_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${GATEWAY_PATH_PREFIX}`
    }
}

function resolveKnowledgeServiceUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:8092`

    if (!raw) return `${KNOWLEDGE_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${KNOWLEDGE_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${KNOWLEDGE_PATH_PREFIX}`
    }
}

function resolveControlCenterUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:8094`

    if (!raw) return `${CONTROL_CENTER_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${CONTROL_CENTER_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${CONTROL_CENTER_PATH_PREFIX}`
    }
}

function resolveBusinessIntelligenceServiceUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:8093`

    if (!raw) return `${BUSINESS_INTELLIGENCE_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${BUSINESS_INTELLIGENCE_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${BUSINESS_INTELLIGENCE_PATH_PREFIX}`
    }
}

function resolveSkillMarketServiceUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:8095`

    if (!raw) return `${SKILL_MARKET_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${SKILL_MARKET_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${SKILL_MARKET_PATH_PREFIX}`
    }
}

function resolveOperationIntelligenceServiceUrl(raw: string | undefined): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:8096`

    if (!raw) return `${OPERATION_INTELLIGENCE_PATH_PREFIX}`

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${OPERATION_INTELLIGENCE_PATH_PREFIX}`
    } catch {
        return `${fallbackOrigin}${OPERATION_INTELLIGENCE_PATH_PREFIX}`
    }
}

const DEFAULT_SECRET_KEY = 'test'

export const runtime = {
    GATEWAY_URL: resolveGatewayUrl(undefined),
    GATEWAY_SECRET_KEY: DEFAULT_SECRET_KEY,
    CONTROL_CENTER_URL: resolveControlCenterUrl(undefined),
    CONTROL_CENTER_SECRET_KEY: DEFAULT_SECRET_KEY,
    KNOWLEDGE_SERVICE_URL: resolveKnowledgeServiceUrl(undefined),
    BUSINESS_INTELLIGENCE_SERVICE_URL: resolveBusinessIntelligenceServiceUrl(undefined),
    SKILL_MARKET_SERVICE_URL: resolveSkillMarketServiceUrl(undefined),
    OPERATION_INTELLIGENCE_SERVICE_URL: resolveOperationIntelligenceServiceUrl(undefined),
    OPERATION_INTELLIGENCE_SECRET_KEY: DEFAULT_SECRET_KEY,
}

function setRuntimeConfig(config: RuntimeConfig): void {
    runtime.GATEWAY_URL = resolveGatewayUrl(config.gatewayUrl)
    runtime.GATEWAY_SECRET_KEY = config.gatewaySecretKey || DEFAULT_SECRET_KEY
    runtime.CONTROL_CENTER_URL = resolveControlCenterUrl(config.controlCenterUrl)
    runtime.CONTROL_CENTER_SECRET_KEY = config.controlCenterSecretKey || DEFAULT_SECRET_KEY
    runtime.KNOWLEDGE_SERVICE_URL = resolveKnowledgeServiceUrl(config.knowledgeServiceUrl)
    runtime.BUSINESS_INTELLIGENCE_SERVICE_URL = resolveBusinessIntelligenceServiceUrl(config.businessIntelligenceServiceUrl)
    runtime.SKILL_MARKET_SERVICE_URL = resolveSkillMarketServiceUrl(config.skillMarketServiceUrl)
    runtime.OPERATION_INTELLIGENCE_SERVICE_URL = resolveOperationIntelligenceServiceUrl(config.operationIntelligenceServiceUrl)
    runtime.OPERATION_INTELLIGENCE_SECRET_KEY = config.operationIntelligenceSecretKey || DEFAULT_SECRET_KEY
    configureWebappLogging(config.logging)
}

async function loadRuntimeConfig(): Promise<RuntimeConfig> {
    const response = await trackedFetch('/config.json', {
        cache: 'no-store',
        category: 'app',
        name: 'app.context_init',
    })
    if (!response.ok) {
        throw new Error(`Failed to load /config.json (${response.status})`)
    }

    return (await response.json()) as RuntimeConfig
}

export async function initializeRuntimeConfig(): Promise<void> {
    const config = await loadRuntimeConfig()
    setRuntimeConfig(config)
}

/** Build gateway request headers with secret key and optional user ID. */
export function gatewayHeaders(userId?: string | null): Record<string, string> {
    const h: Record<string, string> = {
        'Content-Type': 'application/json',
        'x-secret-key': runtime.GATEWAY_SECRET_KEY,
    }
    if (userId) h['x-user-id'] = userId
    return h
}

export function controlCenterHeaders(): Record<string, string> {
    return {
        'Content-Type': 'application/json',
        'x-secret-key': runtime.CONTROL_CENTER_SECRET_KEY,
    }
}

/** Convert a display name to a kebab-case ID. */
export function slugify(value: string): string {
    return value
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
}

/** Check if a session is a scheduled session. */
export function isScheduledSession(session: { session_type?: string; schedule_id?: string | null }): boolean {
    return session.session_type === 'scheduled' || !!session.schedule_id
}
