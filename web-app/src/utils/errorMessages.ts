import i18n from '../i18n'

/**
 * Extract HTTP status code from error messages like "HTTP 500: ..."
 */
function extractHttpStatus(message: string): number | null {
    const match = message.match(/^HTTP (\d{3})/)
    return match ? parseInt(match[1], 10) : null
}

/**
 * Map an error to a user-friendly i18n message.
 * Handles HTTP status codes, network errors, timeouts, and generic errors.
 */
export function getErrorMessage(error: unknown): string {
    if (!error) return i18n.t('errors.unknown')

    const message = error instanceof Error ? error.message : String(error)

    // Timeout (AbortSignal.timeout or fetch timeout)
    if (message.includes('TimeoutError') || message.includes('timed out') || message.includes('timeout')) {
        return i18n.t('errors.timeout')
    }

    // Network errors (fetch failures)
    if (message.includes('Failed to fetch') || message.includes('NetworkError') || message.includes('net::')) {
        return i18n.t('errors.networkError')
    }

    // HTTP status code errors
    const status = extractHttpStatus(message)
    if (status) {
        if (status === 401 || status === 403) return i18n.t('errors.unauthorized')
        if (status === 404) return i18n.t('errors.notFound')
        if (status >= 500) return i18n.t('errors.serverError')
        return i18n.t('errors.requestFailed', { status })
    }

    return i18n.t('errors.unknown')
}
