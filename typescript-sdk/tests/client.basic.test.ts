import { afterEach, beforeEach, describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
    GoosedAuthError,
    GoosedClient,
    GoosedConnectionError,
    normalizeSessionError,
} from '../src/index.js'

describe('GoosedClient basic smoke tests', () => {
    const originalFetch = globalThis.fetch

    beforeEach(() => {
        globalThis.fetch = undefined as typeof fetch
    })

    afterEach(() => {
        globalThis.fetch = originalFetch
    })

    test('status() sends secret key and user headers', async () => {
        let request: RequestInfo | URL | undefined
        let init: RequestInit | undefined

        globalThis.fetch = (async (input: RequestInfo | URL, options?: RequestInit) => {
            request = input
            init = options
            return new Response('ok', {
                status: 200,
                headers: { 'content-type': 'text/plain' },
            })
        }) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
            userId: 'unit-user',
            timeout: 500,
        })

        const result = await client.status()

        assert.equal(result, 'ok')
        assert.equal(String(request), 'http://127.0.0.1:3002/ops-gateway/status')
        assert.equal(init?.method, 'GET')
        assert.equal((init?.headers as Record<string, string>)['x-secret-key'], 'unit-secret')
        assert.equal((init?.headers as Record<string, string>)['x-user-id'], 'unit-user')
    })

    test('status() maps 401 responses to GoosedAuthError', async () => {
        globalThis.fetch = (async () => new Response('denied', { status: 401 })) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
        })

        await assert.rejects(() => client.status(), GoosedAuthError)
    })

    test('status() maps fetch failures to GoosedConnectionError', async () => {
        globalThis.fetch = (async () => {
            throw new TypeError('fetch failed')
        }) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
        })

        await assert.rejects(() => client.status(), GoosedConnectionError)
    })

    test('submitSessionReply() posts goosed session reply shape', async () => {
        let request: RequestInfo | URL | undefined
        let init: RequestInit | undefined

        globalThis.fetch = (async (input: RequestInfo | URL, options?: RequestInit) => {
            request = input
            init = options
            return new Response(JSON.stringify({ request_id: '00000000-0000-0000-0000-000000000001' }), {
                status: 200,
                headers: { 'content-type': 'application/json' },
            })
        }) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
            userId: 'unit-user',
            timeout: 500,
        })

        const result = await client.submitSessionReply('session-1', {
            request_id: '00000000-0000-0000-0000-000000000001',
            user_message: {
                role: 'user',
                created: 1776928807,
                content: [{ type: 'text', text: 'hello' }],
                metadata: { userVisible: true, agentVisible: true },
            },
        })

        assert.equal(result.request_id, '00000000-0000-0000-0000-000000000001')
        assert.equal(String(request), 'http://127.0.0.1:3002/ops-gateway/sessions/session-1/reply')
        assert.equal(init?.method, 'POST')
        assert.equal((init?.headers as Record<string, string>)['x-secret-key'], 'unit-secret')
        assert.equal((init?.headers as Record<string, string>)['x-user-id'], 'unit-user')
        assert.deepEqual(JSON.parse(init?.body as string), {
            request_id: '00000000-0000-0000-0000-000000000001',
            user_message: {
                role: 'user',
                created: 1776928807,
                content: [{ type: 'text', text: 'hello' }],
                metadata: { userVisible: true, agentVisible: true },
            },
        })
    })

    test('cancelSessionReply() posts request_id to session cancel', async () => {
        let request: RequestInfo | URL | undefined
        let init: RequestInit | undefined

        globalThis.fetch = (async (input: RequestInfo | URL, options?: RequestInit) => {
            request = input
            init = options
            return new Response('', { status: 200 })
        }) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
        })

        await client.cancelSessionReply('session-1', '00000000-0000-0000-0000-000000000001')

        assert.equal(String(request), 'http://127.0.0.1:3002/ops-gateway/sessions/session-1/cancel')
        assert.equal(init?.method, 'POST')
        assert.deepEqual(JSON.parse(init?.body as string), {
            request_id: '00000000-0000-0000-0000-000000000001',
        })
    })

    test('subscribeSessionEvents() forwards Last-Event-ID and parses event ids', async () => {
        let request: RequestInfo | URL | undefined
        let init: RequestInit | undefined

        globalThis.fetch = (async (input: RequestInfo | URL, options?: RequestInit) => {
            request = input
            init = options
            return new Response([
                'data: {"type":"ActiveRequests","request_ids":["req-1"]}',
                '',
                ': ping 0',
                '',
                'id: 7',
                'data: {"type":"Message","chat_request_id":"req-1","request_id":"req-1","message":{"role":"assistant","created":1776928808,"content":[{"type":"text","text":"hi"}],"metadata":{"userVisible":true,"agentVisible":true}}}',
                '',
                'id: 8',
                'data: {"type":"Finish","reason":"stop"}',
                '',
            ].join('\n'), {
                status: 200,
                headers: { 'content-type': 'text/event-stream' },
            })
        }) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
            userId: 'unit-user',
        })

        const events = []
        for await (const item of client.subscribeSessionEvents('session-1', { lastEventId: '6' })) {
            events.push(item)
        }

        assert.equal(String(request), 'http://127.0.0.1:3002/ops-gateway/sessions/session-1/events')
        assert.equal(init?.method, 'GET')
        assert.equal((init?.headers as Record<string, string>)['Last-Event-ID'], '6')
        assert.equal(events.length, 3)
        assert.equal(events[0].event.type, 'ActiveRequests')
        assert.deepEqual(events[0].event.request_ids, ['req-1'])
        assert.equal(events[0].eventId, undefined)
        assert.equal(events[1].event.type, 'Message')
        assert.equal(events[1].event.chat_request_id, 'req-1')
        assert.equal(events[1].eventId, '7')
        assert.equal(events[2].event.type, 'Finish')
        assert.equal(events[2].eventId, '8')
    })

    test('normalizeSessionError() preserves gateway envelope fields', () => {
        const error = normalizeSessionError({
            type: 'Error',
            layer: 'gateway',
            code: 'gateway_goosed_unavailable',
            message: 'Gateway could not connect to goosed',
            retryable: true,
            suggested_actions: ['retry', 'contact_support'],
            session_id: 'session-1',
            request_id: 'req-1',
            trace_id: 'trace-1',
        })

        assert.equal(error.layer, 'gateway')
        assert.equal(error.code, 'gateway_goosed_unavailable')
        assert.equal(error.retryable, true)
        assert.deepEqual(error.suggested_actions, ['retry', 'contact_support'])
        assert.equal(error.session_id, 'session-1')
        assert.equal(error.request_id, 'req-1')
        assert.equal(error.trace_id, 'trace-1')
    })

    test('submitSessionReply() exposes structured error envelope on HTTP errors', async () => {
        globalThis.fetch = (async () => new Response(JSON.stringify({
            type: 'Error',
            layer: 'gateway',
            code: 'gateway_agent_unavailable',
            message: 'Agent unavailable',
            retryable: true,
            suggested_actions: ['retry'],
            session_id: 'session-1',
            request_id: 'req-1',
        }), {
            status: 424,
            headers: { 'content-type': 'application/json' },
        })) as typeof fetch

        const client = new GoosedClient({
            baseUrl: 'http://127.0.0.1:3002/ops-gateway',
            secretKey: 'unit-secret',
        })

        await assert.rejects(
            () => client.submitSessionReply('session-1', {
                request_id: 'req-1',
                user_message: {
                    role: 'user',
                    created: 1776928807,
                    content: [{ type: 'text', text: 'hello' }],
                    metadata: { userVisible: true, agentVisible: true },
                },
            }),
            (err: unknown) => {
                assert.equal((err as { sessionError?: { code?: string } }).sessionError?.code, 'gateway_agent_unavailable')
                return true
            }
        )
    })
})
