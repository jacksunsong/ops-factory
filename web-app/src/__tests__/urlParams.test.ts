import { afterEach, describe, expect, it } from 'vitest'
import { getUrlParam, getUrlParams } from '../utils/urlParams'

const originalLocation = window.location

function setLocation(url: string) {
    Object.defineProperty(window, 'location', {
        value: new URL(url),
        writable: true,
        configurable: true,
    })
}

describe('urlParams', () => {
    afterEach(() => {
        Object.defineProperty(window, 'location', {
            value: originalLocation,
            writable: true,
            configurable: true,
        })
    })

    it('reads query params from hash routes', () => {
        setLocation('http://localhost/#/history?embed=true&userId=embed-user')

        expect(getUrlParam('embed')).toBe('true')
        expect(getUrlParam('userId')).toBe('embed-user')
    })

    it('keeps normal search params ahead of hash params', () => {
        setLocation('http://localhost/?userId=search-user#/history?userId=hash-user&embed=true')

        const params = getUrlParams()

        expect(params.get('userId')).toBe('search-user')
        expect(params.get('embed')).toBe('true')
    })
})
