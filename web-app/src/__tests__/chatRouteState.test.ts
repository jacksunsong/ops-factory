import { beforeEach, describe, expect, it } from 'vitest'
import {
    buildChatSessionState,
    buildConsumedNewChatPath,
    buildNewChatState,
    clearPersistedChatSessionLocator,
    persistChatSessionLocator,
    resolveChatRouteState,
} from '../app/platform/chat/chatRouteState'

describe('chatRouteState', () => {
    beforeEach(() => {
        window.sessionStorage.clear()
        clearPersistedChatSessionLocator()
    })

    it('builds new chat state without embedding session params in the url', () => {
        expect(buildNewChatState('universal-agent', 'hello')).toEqual({
            startNew: true,
            preferredAgentId: 'universal-agent',
            initialMessage: 'hello',
        })
    })

    it('resolves session locator from route state first', () => {
        const result = resolveChatRouteState(
            new URLSearchParams('sessionId=legacy-session&agent=legacy-agent'),
            buildChatSessionState('state-session', 'state-agent'),
        )

        expect(result.source).toBe('state')
        expect(result.locatorState).toEqual({
            kind: 'ready',
            locator: {
                sessionId: 'state-session',
                agentId: 'state-agent',
            },
        })
    })

    it('uses session storage when the url has no session params', () => {
        persistChatSessionLocator({
            sessionId: 'stored-session',
            agentId: 'stored-agent',
        })

        const result = resolveChatRouteState(new URLSearchParams(''), null)

        expect(result.source).toBe('storage')
        expect(result.locatorState).toEqual({
            kind: 'ready',
            locator: {
                sessionId: 'stored-session',
                agentId: 'stored-agent',
            },
        })
    })

    it('treats explicit new-chat route state as higher priority than stored session', () => {
        persistChatSessionLocator({
            sessionId: 'stored-session',
            agentId: 'stored-agent',
        })

        const result = resolveChatRouteState(new URLSearchParams(''), buildNewChatState('universal-agent'))

        expect(result.source).toBe('startNew')
        expect(result.preferredAgentId).toBe('universal-agent')
        expect(result.locatorState).toEqual({ kind: 'idle' })
    })

    it('treats embed startNew query as a new-chat request', () => {
        persistChatSessionLocator({
            sessionId: 'stored-session',
            agentId: 'stored-agent',
        })

        const result = resolveChatRouteState(new URLSearchParams('embed=true&startNew=true&uid=embed-user'), null)

        expect(result.source).toBe('startNew')
        expect(result.locatorState).toEqual({ kind: 'idle' })
    })

    it('does not treat non-embed startNew query as a new-chat request', () => {
        persistChatSessionLocator({
            sessionId: 'stored-session',
            agentId: 'stored-agent',
        })

        const result = resolveChatRouteState(new URLSearchParams('startNew=true'), null)

        expect(result.source).toBe('storage')
        expect(result.locatorState).toEqual({
            kind: 'ready',
            locator: {
                sessionId: 'stored-session',
                agentId: 'stored-agent',
            },
        })
    })

    it('consumes startNew from chat URL while preserving embed parameters', () => {
        const path = buildConsumedNewChatPath(new URLSearchParams('embed=true&uid=embed-user&startNew=true'))

        expect(path).toBe('/chat?embed=true&uid=embed-user')
    })

    it('removes stale session locator parameters when consuming a new-chat URL', () => {
        const path = buildConsumedNewChatPath(new URLSearchParams('embed=true&startNew=true&sessionId=old-session&agent=old-agent'))

        expect(path).toBe('/chat?embed=true')
    })

    it('builds the default chat path when there are no query parameters left', () => {
        const path = buildConsumedNewChatPath(new URLSearchParams('startNew=true'))

        expect(path).toBe('/chat')
    })
})
