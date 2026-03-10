import { describe, it, expect } from 'vitest'
import { getErrorMessage } from '../utils/errorMessages'

describe('getErrorMessage', () => {
    // ── HTTP status codes ──────────────────────────────────────
    it('maps HTTP 500 to server error message', () => {
        const msg = getErrorMessage(new Error('HTTP 500: Internal Server Error'))
        expect(msg).toBe('Service temporarily unavailable. Please try again later.')
    })

    it('maps HTTP 502 to server error message', () => {
        const msg = getErrorMessage(new Error('HTTP 502: Bad Gateway'))
        expect(msg).toBe('Service temporarily unavailable. Please try again later.')
    })

    it('maps HTTP 401 to unauthorized message', () => {
        const msg = getErrorMessage(new Error('HTTP 401: Unauthorized'))
        expect(msg).toBe('Authentication expired. Please log in again.')
    })

    it('maps HTTP 403 to unauthorized message', () => {
        const msg = getErrorMessage(new Error('HTTP 403: Forbidden'))
        expect(msg).toBe('Authentication expired. Please log in again.')
    })

    it('maps HTTP 404 to not found message', () => {
        const msg = getErrorMessage(new Error('HTTP 404: Not Found'))
        expect(msg).toBe('The requested resource was not found.')
    })

    it('maps HTTP 429 to request failed with status', () => {
        const msg = getErrorMessage(new Error('HTTP 429: Too Many Requests'))
        expect(msg).toContain('Request failed')
        expect(msg).toContain('429')
    })

    // ── Network errors ─────────────────────────────────────────
    it('maps "Failed to fetch" to network error message', () => {
        const msg = getErrorMessage(new Error('Failed to fetch'))
        expect(msg).toBe('Network connection failed. Please check your connection.')
    })

    it('maps "NetworkError" to network error message', () => {
        const msg = getErrorMessage(new Error('NetworkError when attempting to fetch resource'))
        expect(msg).toBe('Network connection failed. Please check your connection.')
    })

    it('maps "net::" prefixed errors to network error message', () => {
        const msg = getErrorMessage(new Error('net::ERR_CONNECTION_REFUSED'))
        expect(msg).toBe('Network connection failed. Please check your connection.')
    })

    // ── Timeout errors ─────────────────────────────────────────
    it('maps TimeoutError to timeout message', () => {
        const msg = getErrorMessage(new Error('TimeoutError: signal timed out'))
        expect(msg).toBe('Request timed out. Please try again.')
    })

    it('maps "timed out" string to timeout message', () => {
        const msg = getErrorMessage(new Error('The operation timed out'))
        expect(msg).toBe('Request timed out. Please try again.')
    })

    it('maps "timeout" string to timeout message', () => {
        const msg = getErrorMessage(new Error('Request timeout exceeded'))
        expect(msg).toBe('Request timed out. Please try again.')
    })

    // ── Edge cases ─────────────────────────────────────────────
    it('returns unknown error for null', () => {
        const msg = getErrorMessage(null)
        expect(msg).toBe('An unexpected error occurred. Please try again.')
    })

    it('returns unknown error for undefined', () => {
        const msg = getErrorMessage(undefined)
        expect(msg).toBe('An unexpected error occurred. Please try again.')
    })

    it('returns unknown error for generic string', () => {
        const msg = getErrorMessage('Something went wrong')
        expect(msg).toBe('An unexpected error occurred. Please try again.')
    })

    it('handles plain string errors', () => {
        const msg = getErrorMessage('HTTP 500: Server Error')
        expect(msg).toBe('Service temporarily unavailable. Please try again later.')
    })

    it('returns unknown error for empty string', () => {
        const msg = getErrorMessage('')
        expect(msg).toBe('An unexpected error occurred. Please try again.')
    })
})
