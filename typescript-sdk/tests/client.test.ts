/**
 * SDK contract tests. These tests intentionally mock fetch so `npm test`
 * does not depend on a goosed process listening on a fixed local port.
 */

import { afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { GoosedClient } from '../src/index.js';

const BASE_URL = 'http://127.0.0.1:3002/ops-gateway';
const SECRET_KEY = 'test-secret';

interface CapturedRequest {
    url: string;
    method: string;
    headers: Record<string, string>;
    body?: unknown;
}

const jsonResponse = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
});

const textResponse = (body: string, status = 200, contentType = 'text/plain') => new Response(body, {
    status,
    headers: { 'content-type': contentType },
});

function buildTextMessage(text: string) {
    return {
        role: 'user' as const,
        created: 1776928807,
        content: [{ type: 'text' as const, text }],
        metadata: { userVisible: true, agentVisible: true },
    };
}

function installMockFetch(handler: (request: CapturedRequest) => Response | Promise<Response>) {
    const requests: CapturedRequest[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, options?: RequestInit) => {
        const rawBody = typeof options?.body === 'string' ? options.body : undefined;
        const captured: CapturedRequest = {
            url: String(input),
            method: options?.method ?? 'GET',
            headers: options?.headers as Record<string, string>,
            body: rawBody ? JSON.parse(rawBody) : undefined,
        };
        requests.push(captured);
        return handler(captured);
    }) as typeof fetch;
    return requests;
}

describe('GoosedClient', () => {
    const originalFetch = globalThis.fetch;
    let client: GoosedClient;

    beforeEach(() => {
        client = new GoosedClient({
            baseUrl: BASE_URL,
            secretKey: SECRET_KEY,
            userId: 'sdk-user',
            timeout: 500,
        });
    });

    afterEach(() => {
        globalThis.fetch = originalFetch;
    });

    describe('Status APIs', () => {
        test('status() returns text response', async () => {
            const requests = installMockFetch(() => textResponse('ok'));

            const result = await client.status();

            assert.equal(result, 'ok');
            assert.equal(requests[0].url, `${BASE_URL}/status`);
            assert.equal(requests[0].method, 'GET');
            assert.equal(requests[0].headers['x-secret-key'], SECRET_KEY);
            assert.equal(requests[0].headers['x-user-id'], 'sdk-user');
        });

        test('systemInfo() returns model metadata', async () => {
            installMockFetch(() => jsonResponse({
                app_version: '1.0.0',
                provider: 'openai',
                model: 'gpt-test',
            }));

            const info = await client.systemInfo();

            assert.equal(info.app_version, '1.0.0');
            assert.equal(info.provider, 'openai');
            assert.equal(info.model, 'gpt-test');
        });
    });

    describe('Session Management', () => {
        test('startSession() posts working_dir and deleteSession() deletes by id', async () => {
            const requests = installMockFetch((request) => {
                if (request.url.endsWith('/agent/start')) {
                    return jsonResponse({ id: 'session-1', working_dir: '/tmp/ts-sdk-test' });
                }
                if (request.url.endsWith('/sessions/session-1') && request.method === 'DELETE') {
                    return textResponse('');
                }
                throw new Error(`unexpected request ${request.method} ${request.url}`);
            });

            const session = await client.startSession('/tmp/ts-sdk-test');
            await client.deleteSession(session.id);

            assert.equal(session.id, 'session-1');
            assert.equal(session.working_dir, '/tmp/ts-sdk-test');
            assert.deepEqual(requests[0].body, { working_dir: '/tmp/ts-sdk-test' });
            assert.equal(requests[1].method, 'DELETE');
            assert.equal(requests[1].url, `${BASE_URL}/sessions/session-1`);
        });

        test('listSessions() unwraps sessions array', async () => {
            installMockFetch(() => jsonResponse({
                sessions: [{ id: 'session-1', working_dir: '/tmp/one' }],
            }));

            const sessions = await client.listSessions();

            assert.deepEqual(sessions.map(session => session.id), ['session-1']);
        });

        test('resumeSession() posts load_model_and_extensions and unwraps results', async () => {
            const requests = installMockFetch(() => jsonResponse({
                session: { id: 'session-1', working_dir: '/tmp/one' },
                extension_results: [{ name: 'developer', status: 'enabled' }],
            }));

            const result = await client.resumeSession('session-1');

            assert.equal(requests[0].url, `${BASE_URL}/agent/resume`);
            assert.deepEqual(requests[0].body, {
                session_id: 'session-1',
                load_model_and_extensions: true,
            });
            assert.equal(result.session.id, 'session-1');
            assert.deepEqual(result.extensionResults, [{ name: 'developer', status: 'enabled' }]);
        });

        test('updateSessionName() uses PUT and getSession() reads updated name', async () => {
            const requests = installMockFetch((request) => {
                if (request.method === 'PUT') return textResponse('');
                return jsonResponse({ id: 'session-1', name: 'TS Test Session' });
            });

            await client.updateSessionName('session-1', 'TS Test Session');
            const updated = await client.getSession('session-1');

            assert.equal(requests[0].method, 'PUT');
            assert.equal(requests[0].url, `${BASE_URL}/sessions/session-1/name`);
            assert.deepEqual(requests[0].body, { name: 'TS Test Session' });
            assert.equal(updated.name, 'TS Test Session');
        });
    });

    describe('Agent APIs', () => {
        test('getTools() encodes session and extension query parameters', async () => {
            const requests = installMockFetch(() => jsonResponse([
                { name: 'todo__todo_write', description: 'write todo', input_schema: {} },
            ]));

            const tools = await client.getTools('session 1', 'developer tools');

            assert.equal(requests[0].url, `${BASE_URL}/agent/tools?session_id=session%201&extension_name=developer%20tools`);
            assert.equal(tools[0].name, 'todo__todo_write');
        });

        test('callTool() posts tool request body', async () => {
            const requests = installMockFetch(() => jsonResponse({
                is_error: false,
                content: [{ type: 'text', text: 'done' }],
            }));

            const result = await client.callTool('session-1', 'todo__todo_write', {
                content: 'TS SDK Test TODO',
            });

            assert.deepEqual(requests[0].body, {
                session_id: 'session-1',
                name: 'todo__todo_write',
                arguments: { content: 'TS SDK Test TODO' },
            });
            assert.equal(result.is_error, false);
        });

        test('restartSession() unwraps extension_results', async () => {
            const requests = installMockFetch(() => jsonResponse({
                extension_results: [{ name: 'developer', status: 'restarted' }],
            }));

            const results = await client.restartSession('session-1');

            assert.equal(requests[0].url, `${BASE_URL}/agent/restart`);
            assert.deepEqual(requests[0].body, { session_id: 'session-1' });
            assert.deepEqual(results, [{ name: 'developer', status: 'restarted' }]);
        });
    });

    describe('Chat APIs', () => {
        test('session events stream reads message and finish events for submitted request', async () => {
            const requestId = '00000000-0000-0000-0000-000000000001';
            const requests = installMockFetch((request) => {
                if (request.url.endsWith('/sessions/session-1/events')) {
                    return textResponse([
                        'id: 1',
                        `data: {"type":"Message","chat_request_id":"${requestId}","message":{"role":"assistant","created":1776928808,"content":[{"type":"text","text":"hello"}],"metadata":{"userVisible":true,"agentVisible":true}}}`,
                        '',
                        'id: 2',
                        `data: {"type":"Finish","chat_request_id":"${requestId}","reason":"stop"}`,
                        '',
                    ].join('\n'), 200, 'text/event-stream');
                }
                if (request.url.endsWith('/sessions/session-1/reply')) {
                    return jsonResponse({ request_id: requestId });
                }
                throw new Error(`unexpected request ${request.method} ${request.url}`);
            });

            const stream = client.subscribeSessionEvents('session-1');
            const submit = await client.submitSessionReply('session-1', {
                request_id: requestId,
                user_message: buildTextMessage('Say hello'),
            });
            const events = [];
            for await (const item of stream) {
                events.push(item);
            }

            assert.equal(submit.request_id, requestId);
            assert.equal(requests[0].url, `${BASE_URL}/sessions/session-1/reply`);
            assert.equal(requests[1].url, `${BASE_URL}/sessions/session-1/events`);
            assert.deepEqual(requests[0].body, {
                request_id: requestId,
                user_message: buildTextMessage('Say hello'),
            });
            assert.deepEqual(events.map(item => item.event.type), ['Message', 'Finish']);
            assert.deepEqual(events.map(item => item.eventId), ['1', '2']);
        });
    });

    describe('Export APIs', () => {
        test('exportSession() returns text export payload', async () => {
            const requests = installMockFetch(() => textResponse('session export session-1'));

            const exported = await client.exportSession('session-1');

            assert.equal(requests[0].url, `${BASE_URL}/sessions/session-1/export`);
            assert.equal(exported, 'session export session-1');
        });
    });
});
