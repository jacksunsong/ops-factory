import {
    createSessionLocator,
    isValidSessionLocator,
    parseSessionLocatorFromSearchParams,
    type SessionLocator,
    type SessionLocatorState,
} from '../../../utils/sessionLocator'
import type { SelectedSkill } from '../../../types/message'

const CHAT_LOCATOR_STORAGE_KEY = 'opsfactory:chat:session-locator'
const START_NEW_QUERY_PARAM = 'startNew'
const SESSION_ID_QUERY_PARAM = 'sessionId'
const AGENT_QUERY_PARAM = 'agent'

export interface ChatLocationState {
    initialMessage?: string
    initialSelectedSkill?: SelectedSkill
    preferredAgentId?: string
    sessionLocator?: SessionLocator
    startNew?: boolean
}

export interface ResolveChatRouteResult {
    initialMessage?: string
    initialSelectedSkill?: SelectedSkill
    locatorState: SessionLocatorState
    preferredAgentId: string | null
    source: 'idle' | 'search' | 'startNew' | 'state' | 'storage'
}

function normalizeOptionalString(value: unknown): string | null {
    if (typeof value !== 'string') return null
    const normalized = value.trim()
    return normalized || null
}

function readLocationState(value: unknown): ChatLocationState | null {
    if (!value || typeof value !== 'object') return null
    return value as ChatLocationState
}

function normalizeSelectedSkill(value: unknown): SelectedSkill | undefined {
    if (!value || typeof value !== 'object') return undefined
    const record = value as Record<string, unknown>
    if (
        typeof record.id !== 'string' ||
        typeof record.name !== 'string' ||
        typeof record.path !== 'string'
    ) {
        return undefined
    }
    return {
        id: record.id,
        name: record.name,
        path: record.path,
        ...(typeof record.description === 'string' ? { description: record.description } : {}),
    }
}

function readSessionLocatorFromLocationState(value: unknown): SessionLocatorState | null {
    const state = readLocationState(value)
    if (!state || state.sessionLocator === undefined) return null

    if (!isValidSessionLocator(state.sessionLocator)) {
        return {
            kind: 'corrupted',
            reason: 'Route state contains an invalid session locator',
            rawValue: state.sessionLocator,
        }
    }

    return {
        kind: 'ready',
        locator: createSessionLocator(state.sessionLocator.sessionId, state.sessionLocator.agentId),
    }
}

function safeSessionStorageGetItem(key: string): string | null {
    try {
        return window.sessionStorage.getItem(key)
    } catch {
        return null
    }
}

function safeSessionStorageSetItem(key: string, value: string) {
    try {
        window.sessionStorage.setItem(key, value)
    } catch {
        // Ignore storage failures and keep navigation working in-memory.
    }
}

function safeSessionStorageRemoveItem(key: string) {
    try {
        window.sessionStorage.removeItem(key)
    } catch {
        // Ignore storage failures and keep navigation working in-memory.
    }
}

export function buildChatSessionState(
    sessionId: string,
    agentId: string,
    extras: Pick<ChatLocationState, 'initialMessage' | 'initialSelectedSkill'> = {},
): ChatLocationState {
    const state: ChatLocationState = {
        sessionLocator: createSessionLocator(sessionId, agentId),
    }

    const initialMessage = normalizeOptionalString(extras.initialMessage)
    if (initialMessage) {
        state.initialMessage = initialMessage
    }

    const initialSelectedSkill = normalizeSelectedSkill(extras.initialSelectedSkill)
    if (initialSelectedSkill) {
        state.initialSelectedSkill = initialSelectedSkill
    }

    return state
}

export function buildNewChatState(preferredAgentId?: string, initialMessage?: string, initialSelectedSkill?: SelectedSkill): ChatLocationState {
    const state: ChatLocationState = {
        startNew: true,
    }

    const normalizedAgentId = normalizeOptionalString(preferredAgentId)
    const normalizedInitialMessage = normalizeOptionalString(initialMessage)

    if (normalizedAgentId) {
        state.preferredAgentId = normalizedAgentId
    }

    if (normalizedInitialMessage) {
        state.initialMessage = normalizedInitialMessage
    }

    const normalizedSelectedSkill = normalizeSelectedSkill(initialSelectedSkill)
    if (normalizedSelectedSkill) {
        state.initialSelectedSkill = normalizedSelectedSkill
    }

    return state
}

export function persistChatSessionLocator(locator: SessionLocator) {
    safeSessionStorageSetItem(CHAT_LOCATOR_STORAGE_KEY, JSON.stringify(locator))
}

export function clearPersistedChatSessionLocator() {
    safeSessionStorageRemoveItem(CHAT_LOCATOR_STORAGE_KEY)
}

export function readPersistedChatSessionLocator(): SessionLocator | null {
    const raw = safeSessionStorageGetItem(CHAT_LOCATOR_STORAGE_KEY)
    if (!raw) return null

    try {
        const parsed = JSON.parse(raw)
        if (!isValidSessionLocator(parsed)) return null
        return createSessionLocator(parsed.sessionId, parsed.agentId)
    } catch {
        return null
    }
}

function isEmbedNewChatRequest(searchParams: URLSearchParams): boolean {
    return searchParams.get('embed') === 'true' && searchParams.get(START_NEW_QUERY_PARAM) === 'true'
}

export function buildConsumedNewChatPath(searchParams: URLSearchParams): string {
    const nextParams = new URLSearchParams(searchParams)
    nextParams.delete(START_NEW_QUERY_PARAM)
    nextParams.delete(SESSION_ID_QUERY_PARAM)
    nextParams.delete(AGENT_QUERY_PARAM)
    const nextSearch = nextParams.toString()
    return nextSearch ? `/chat?${nextSearch}` : '/chat'
}

export function resolveChatRouteState(searchParams: URLSearchParams, locationState: unknown): ResolveChatRouteResult {
    const state = readLocationState(locationState)
    const initialMessage = normalizeOptionalString(state?.initialMessage) ?? undefined
    const initialSelectedSkill = normalizeSelectedSkill(state?.initialSelectedSkill)
    const preferredAgentId = normalizeOptionalString(state?.preferredAgentId)

    if (state?.startNew) {
        return {
            initialMessage,
            initialSelectedSkill,
            locatorState: { kind: 'idle' },
            preferredAgentId,
            source: 'startNew',
        }
    }

    if (isEmbedNewChatRequest(searchParams)) {
        return {
            initialMessage,
            initialSelectedSkill,
            locatorState: { kind: 'idle' },
            preferredAgentId,
            source: 'startNew',
        }
    }

    const stateLocator = readSessionLocatorFromLocationState(locationState)
    if (stateLocator) {
        return {
            initialMessage,
            initialSelectedSkill,
            locatorState: stateLocator,
            preferredAgentId,
            source: 'state',
        }
    }

    const searchLocator = parseSessionLocatorFromSearchParams(searchParams)
    if (searchLocator.kind !== 'idle') {
        return {
            initialMessage,
            initialSelectedSkill,
            locatorState: searchLocator,
            preferredAgentId,
            source: 'search',
        }
    }

    const storedLocator = readPersistedChatSessionLocator()
    if (storedLocator) {
        return {
            initialMessage,
            initialSelectedSkill,
            locatorState: { kind: 'ready', locator: storedLocator },
            preferredAgentId,
            source: 'storage',
        }
    }

    return {
        initialMessage,
        initialSelectedSkill,
        locatorState: { kind: 'idle' },
        preferredAgentId,
        source: 'idle',
    }
}
