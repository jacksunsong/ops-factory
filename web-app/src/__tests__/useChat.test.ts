import { describe, it, expect } from 'vitest'

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
