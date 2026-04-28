import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { ChatState, pushMessage, useChat } from '../app/platform/chat/useChat'
import type { GoosedClient, SessionSSEEvent, TokenState } from '@goosed/sdk'
import type { ChatMessage } from '../types/message'

/**
 * Test the streamReducer logic for STREAM_FINISH error propagation.
 *
 * The SSE closure bug fix: previously, `state.error` from the closure snapshot
 * was used in the finally block, which could be stale. Now `streamErrorRef`
 * (a ref) is used to track errors during the stream, ensuring STREAM_FINISH
 * receives the correct error value.
 *
 * We test the reducer directly since it's a pure function.
 */

// Re-create the reducer locally for unit testing (it's not exported)
interface StreamState {
    messages: unknown[]
    chatState: string
    error: string | null
    tokenState: unknown | null
}

type StreamAction =
    | { type: 'SET_ERROR'; payload: string | null }
    | { type: 'START_STREAMING' }
    | { type: 'STREAM_FINISH'; error?: string }

function streamReducer(state: StreamState, action: StreamAction): StreamState {
    switch (action.type) {
        case 'SET_ERROR':
            return { ...state, error: action.payload }
        case 'START_STREAMING':
            return { ...state, chatState: 'streaming', error: null }
        case 'STREAM_FINISH':
            return {
                ...state,
                chatState: action.error ? 'errored' : 'idle',
                error: action.error ?? state.error,
            }
        default:
            return state
    }
}

describe('streamReducer — STREAM_FINISH error handling', () => {
    const initialState: StreamState = {
        messages: [],
        chatState: 'idle',
        error: null,
        tokenState: null,
    }

    it('START_STREAMING clears previous error', () => {
        const stateWithError = { ...initialState, error: 'old error' }
        const result = streamReducer(stateWithError, { type: 'START_STREAMING' })
        expect(result.error).toBeNull()
        expect(result.chatState).toBe('streaming')
    })

    it('STREAM_FINISH with error sets errored state', () => {
        const streaming = { ...initialState, chatState: 'streaming' }
        const result = streamReducer(streaming, { type: 'STREAM_FINISH', error: 'SSE Error' })
        expect(result.chatState).toBe('errored')
        expect(result.error).toBe('SSE Error')
    })

    it('STREAM_FINISH without error sets idle state', () => {
        const streaming = { ...initialState, chatState: 'streaming' }
        const result = streamReducer(streaming, { type: 'STREAM_FINISH' })
        expect(result.chatState).toBe('idle')
        expect(result.error).toBeNull()
    })

    it('STREAM_FINISH preserves SET_ERROR value when no explicit error passed', () => {
        // Simulate: SET_ERROR during stream, then STREAM_FINISH without explicit error
        let state = streamReducer(initialState, { type: 'START_STREAMING' })
        state = streamReducer(state, { type: 'SET_ERROR', payload: 'Stream error during SSE' })
        state = streamReducer(state, { type: 'STREAM_FINISH' })

        // The error from SET_ERROR should be preserved via state.error fallback
        expect(state.error).toBe('Stream error during SSE')
    })

    it('STREAM_FINISH explicit error overrides SET_ERROR value', () => {
        let state = streamReducer(initialState, { type: 'START_STREAMING' })
        state = streamReducer(state, { type: 'SET_ERROR', payload: 'First error' })
        state = streamReducer(state, { type: 'STREAM_FINISH', error: 'Final error' })

        expect(state.error).toBe('Final error')
        expect(state.chatState).toBe('errored')
    })

    it('streamErrorRef pattern: error set during stream is passed to STREAM_FINISH', () => {
        // This simulates the fixed pattern:
        // 1. START_STREAMING → streamErrorRef.current = null
        // 2. Error event → streamErrorRef.current = errorMsg; SET_ERROR dispatched
        // 3. finally → STREAM_FINISH with error = streamErrorRef.current
        //
        // Before the fix, step 3 used state.error from closure (always null at start).
        // After the fix, streamErrorRef captures the error correctly.

        let streamErrorRef: string | null = null

        // Step 1: Start streaming
        let state = streamReducer(initialState, { type: 'START_STREAMING' })
        streamErrorRef = null

        // Step 2: Error event during stream
        const errorMsg = 'Connection lost during stream'
        streamErrorRef = errorMsg
        state = streamReducer(state, { type: 'SET_ERROR', payload: errorMsg })

        // Step 3: Finally block — uses ref, not closure
        state = streamReducer(state, {
            type: 'STREAM_FINISH',
            error: streamErrorRef ?? undefined,
        })

        expect(state.chatState).toBe('errored')
        expect(state.error).toBe('Connection lost during stream')
    })

    it('streamErrorRef pattern: no error means idle state', () => {
        let streamErrorRef: string | null = null

        let state = streamReducer(initialState, { type: 'START_STREAMING' })
        streamErrorRef = null

        // No error during stream
        state = streamReducer(state, {
            type: 'STREAM_FINISH',
            error: streamErrorRef ?? undefined,
        })

        expect(state.chatState).toBe('idle')
        expect(state.error).toBeNull()
    })
})

describe('pushMessage — structured reasoning/thinking streaming merge', () => {
    it('replaces repeated full reasoning chunk (prevents duplicate reasoning display)', () => {
        const current: ChatMessage[] = [{
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: '用户询问A。' }],
        }]

        const next = pushMessage(current, {
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: '用户询问A。' }],
        })

        expect(next).toHaveLength(1)
        expect(next[0].content).toHaveLength(1)
        expect(next[0].content[0]).toEqual({ type: 'reasoning', text: '用户询问A。' })
    })

    it('updates reasoning when backend sends full accumulated text (startsWith)', () => {
        const current: ChatMessage[] = [{
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: '用户询问A。' }],
        }]

        const next = pushMessage(current, {
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: '用户询问A。我需要先查询系统资源。' }],
        })

        expect(next[0].content).toHaveLength(1)
        expect(next[0].content[0]).toEqual({ type: 'reasoning', text: '用户询问A。我需要先查询系统资源。' })
    })

    it('appends reasoning delta when backend sends only incremental text', () => {
        const current: ChatMessage[] = [{
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: '用户询问A。' }],
        }]

        const next = pushMessage(current, {
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'reasoning', text: ' 我需要先查询系统资源。' }],
        })

        expect(next[0].content).toHaveLength(1)
        expect(next[0].content[0]).toEqual({ type: 'reasoning', text: '用户询问A。 我需要先查询系统资源。' })
    })

    it('applies the same merge behavior for thinking chunks', () => {
        const current: ChatMessage[] = [{
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'thinking', thinking: 'step1' }],
        }]

        const next = pushMessage(current, {
            id: 'assistant-1',
            role: 'assistant',
            content: [{ type: 'thinking', thinking: 'step1\nstep2' }],
        })

        expect(next[0].content).toHaveLength(1)
        expect(next[0].content[0]).toEqual({ type: 'thinking', thinking: 'step1\nstep2' })
    })
})

describe('useChat — session event state machine', () => {
    const tokenState: TokenState = {
        inputTokens: 1,
        outputTokens: 2,
        totalTokens: 3,
        accumulatedInputTokens: 1,
        accumulatedOutputTokens: 2,
        accumulatedTotalTokens: 3,
    }

    function delay() {
        return new Promise(resolve => setTimeout(resolve, 0))
    }

    it('waits for Finish after ActiveRequests no longer lists the request', async () => {
        let submittedRequestId = ''
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                yield { event: { type: 'ActiveRequests', request_ids: [] } }
                yield { event: { type: 'Finish', chat_request_id: submittedRequestId, token_state: tokenState } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Idle)
            expect(result.current.tokenState?.totalTokens).toBe(3)
        })
    })

    it('does not treat ActiveRequests empty as completed without a terminal event', async () => {
        let submittedRequestId = ''
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                yield {
                    event: {
                        type: 'Message',
                        chat_request_id: submittedRequestId,
                        message: {
                            id: 'assistant-1',
                            role: 'assistant',
                            created: 1776928807,
                            content: [{ type: 'text', text: 'done without finish' }],
                            metadata: { userVisible: true, agentVisible: true },
                        },
                    },
                }
                yield { event: { type: 'ActiveRequests', request_ids: [] } }
                while (!options.signal?.aborted) {
                    await delay()
                }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Reconnecting)
            expect(result.current.messages.some(message => message.id === 'assistant-1')).toBe(true)
            expect(result.current.sessionError).toBeNull()
        })
    })

    it('clears cancellation locally without waiting for a terminal event', async () => {
        let submittedRequestId = ''
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            cancelSessionReply: async () => undefined,
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!options.signal?.aborted) {
                    await delay()
                }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Streaming)
        })

        await act(async () => {
            await result.current.stopMessage()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Cancelled)
            expect(result.current.isLoading).toBe(false)
        })

        vi.useFakeTimers()
        act(() => {
            vi.advanceTimersByTime(2500)
        })
        vi.useRealTimers()

        expect(result.current.chatState).toBe(ChatState.Cancelled)
        expect(result.current.isLoading).toBe(false)
    })

    it('ignores late ActiveRequests that still list the cancelled request', async () => {
        let submittedRequestId = ''
        let cancelSubmitted = false
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            cancelSessionReply: async () => {
                cancelSubmitted = true
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!cancelSubmitted && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!options.signal?.aborted) {
                    await delay()
                }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Streaming)
        })

        await act(async () => {
            await result.current.stopMessage()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Cancelled)
            expect(result.current.isLoading).toBe(false)
        })
    })

    it('keeps the UI cancelled when cancel submission fails', async () => {
        let submittedRequestId = ''
        let cancelAttempts = 0
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            cancelSessionReply: async () => {
                cancelAttempts += 1
                throw new Error('cancel failed')
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!options.signal?.aborted) {
                    await delay()
                }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Streaming)
        })

        await act(async () => {
            const stopped = await result.current.stopMessage()
            expect(stopped).toBe(true)
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Cancelled)
            expect(result.current.isLoading).toBe(false)
            expect(result.current.sessionError).toBeNull()
        })

        await act(async () => {
            const stoppedAgain = await result.current.stopMessage()
            expect(stoppedAgain).toBe(false)
        })

        expect(cancelAttempts).toBe(1)
    })

    it('stays cancelled when ActiveRequests later no longer lists the request', async () => {
        let submittedRequestId = ''
        let cancelSubmitted = false
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            cancelSessionReply: async () => {
                cancelSubmitted = true
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!cancelSubmitted && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [] } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Streaming)
        })

        await act(async () => {
            await result.current.stopMessage()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Cancelled)
            expect(result.current.isLoading).toBe(false)
        })
    })

    it('stays cancelled when a terminal event arrives after local cancellation', async () => {
        let submittedRequestId = ''
        let cancelSubmitted = false
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            cancelSessionReply: async () => {
                cancelSubmitted = true
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                while (!cancelSubmitted && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [] } }
                yield { event: { type: 'Finish', chat_request_id: submittedRequestId, token_state: tokenState } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Streaming)
        })

        await act(async () => {
            await result.current.stopMessage()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Cancelled)
        })
    })

    it('restores an active request and waits for Finish after ActiveRequests becomes empty', async () => {
        let subscriptionCount = 0
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => ({ request_id: request.request_id }),
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                subscriptionCount += 1
                if (subscriptionCount === 1) {
                    yield { event: { type: 'ActiveRequests', request_ids: ['restored-request'] } }
                    return
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [] } }
                yield { event: { type: 'Finish', chat_request_id: 'restored-request', token_state: tokenState } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Idle)
            expect(result.current.tokenState?.totalTokens).toBe(3)
        })

        expect(subscriptionCount).toBeGreaterThanOrEqual(2)
    })

    it('keeps an accepted request reconnecting when the events stream disconnects', async () => {
        let submittedRequestId = ''
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                throw new TypeError('network disconnected')
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Reconnecting)
            expect(result.current.sessionError).toBeNull()
            expect(result.current.error).toBeNull()
        })
    })

    it('merges stale history instead of overwriting live restored messages while streaming', async () => {
        let submittedRequestId = ''
        let releaseFinish: (() => void) | null = null
        const finishGate = new Promise<void>(resolve => {
            releaseFinish = resolve
        })
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                yield {
                    event: {
                        type: 'Message',
                        chat_request_id: submittedRequestId,
                        message: {
                            id: 'assistant-live',
                            role: 'assistant',
                            created: 1776928807,
                            content: [{ type: 'text', text: 'live response' }],
                            metadata: { userVisible: true, agentVisible: true },
                        },
                    },
                }
                await finishGate
                if (options.signal?.aborted) return
                yield { event: { type: 'Finish', chat_request_id: submittedRequestId } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.messages.some(message => message.id === 'assistant-live')).toBe(true)
        })

        act(() => {
            result.current.setInitialMessages([{
                id: 'history-user',
                role: 'user',
                content: [{ type: 'text', text: 'history only' }],
                created: 1776928700,
            }])
        })

        expect(result.current.messages.some(message => message.id === 'assistant-live')).toBe(true)

        act(() => {
            releaseFinish?.()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Idle)
        })
    })

    it('keeps the live message version when stale history has the same message id', async () => {
        let submittedRequestId = ''
        let releaseFinish: (() => void) | null = null
        const finishGate = new Promise<void>(resolve => {
            releaseFinish = resolve
        })
        const client = {
            submitSessionReply: async (_sessionId: string, request: { request_id: string }) => {
                submittedRequestId = request.request_id
                return { request_id: request.request_id }
            },
            subscribeSessionEvents: async function *(_sessionId: string, options: { signal?: AbortSignal } = {}): AsyncGenerator<SessionSSEEvent> {
                while (!submittedRequestId && !options.signal?.aborted) {
                    await delay()
                }
                if (options.signal?.aborted) return

                yield { event: { type: 'ActiveRequests', request_ids: [submittedRequestId] } }
                yield {
                    event: {
                        type: 'Message',
                        chat_request_id: submittedRequestId,
                        message: {
                            id: 'assistant-same-id',
                            role: 'assistant',
                            created: 1776928807,
                            content: [{ type: 'text', text: 'live response with new tokens' }],
                            metadata: { userVisible: true, agentVisible: true },
                        },
                    },
                }
                await finishGate
                if (options.signal?.aborted) return
                yield { event: { type: 'Finish', chat_request_id: submittedRequestId } }
            },
            getSession: async () => ({ conversation: [] }),
        } as unknown as GoosedClient

        const { result } = renderHook(() => useChat({ sessionId: 'session-1', client }))

        act(() => {
            result.current.sendMessage('hello')
        })

        await waitFor(() => {
            expect(result.current.messages.some(message => message.id === 'assistant-same-id')).toBe(true)
        })

        act(() => {
            result.current.setInitialMessages([{
                id: 'assistant-same-id',
                role: 'assistant',
                content: [{ type: 'text', text: 'stale history response' }],
                created: 1776928700,
            }])
        })

        const assistantMessage = result.current.messages.find(message => message.id === 'assistant-same-id')
        expect(assistantMessage?.content[0]).toEqual({ type: 'text', text: 'live response with new tokens' })

        act(() => {
            releaseFinish?.()
        })

        await waitFor(() => {
            expect(result.current.chatState).toBe(ChatState.Idle)
        })
    })
})
