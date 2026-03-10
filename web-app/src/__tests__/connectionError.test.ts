import { describe, it, expect } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Static analysis tests: verify that ALL pages properly handle
 * gateway disconnection instead of showing infinite loading,
 * and use a unified error banner style.
 */

const SRC_DIR = path.resolve(__dirname, '..')

function readSource(relativePath: string): string {
    return fs.readFileSync(path.join(SRC_DIR, relativePath), 'utf-8')
}

describe('History page — connection error handling', () => {
    it('reads connectionError from useGoosed()', () => {
        const src = readSource('pages/History.tsx')
        expect(src).toContain('error: connectionError')
        expect(src).toContain('useGoosed()')
    })

    it('sets isLoading to false when not connected', () => {
        const src = readSource('pages/History.tsx')
        expect(src).toContain('setIsLoading(false)')
        const earlyReturnBlock = src.match(
            /if\s*\(\s*!isConnected\s*\|\|\s*agents\.length\s*===\s*0\s*\)\s*\{[^}]*setIsLoading\(false\)/s
        )
        expect(earlyReturnBlock).not.toBeNull()
    })

    it('displays connectionError in the error banner', () => {
        const src = readSource('pages/History.tsx')
        expect(src).toContain('connectionError')
        expect(src).toMatch(/!isConnected\s*&&\s*connectionError/)
    })
})

describe('Chat page — connection error handling', () => {
    it('reads goosedError from useGoosed()', () => {
        const src = readSource('pages/Chat.tsx')
        expect(src).toContain('error: goosedError')
    })

    it('shows error state when initializing and not connected', () => {
        const src = readSource('pages/Chat.tsx')
        expect(src).toContain('isInitializing && !isConnected && goosedError')
    })

    it('displays goosedError message in error state', () => {
        const src = readSource('pages/Chat.tsx')
        expect(src).toContain('{goosedError}')
    })

    it('provides a back-to-home button in connection error state', () => {
        const src = readSource('pages/Chat.tsx')
        expect(src).toContain("t('chat.backToHome')")
    })

    it('still shows loading spinner when connected but initializing', () => {
        const src = readSource('pages/Chat.tsx')
        const connectionErrorBlock = src.indexOf('isInitializing && !isConnected && goosedError')
        const loadingBlock = src.indexOf("t('chat.loadingSession')")
        expect(connectionErrorBlock).toBeLessThan(loadingBlock)
    })
})

describe('Files page — connection error handling', () => {
    it('reads connectionError from useGoosed()', () => {
        const src = readSource('pages/Files.tsx')
        expect(src).toContain('error: connectionError')
    })

    it('sets isLoading to false when not connected', () => {
        const src = readSource('pages/Files.tsx')
        const earlyReturnBlock = src.match(
            /if\s*\(\s*!isConnected\s*\|\|\s*agents\.length\s*===\s*0\s*\)\s*\{[^}]*setIsLoading\(false\)/s
        )
        expect(earlyReturnBlock).not.toBeNull()
    })

    it('displays connection error banner', () => {
        const src = readSource('pages/Files.tsx')
        expect(src).toMatch(/!isConnected\s*&&\s*connectionError/)
        expect(src).toContain('conn-banner conn-banner-error')
    })
})

describe('Inbox page — connection error handling', () => {
    it('imports useGoosed', () => {
        const src = readSource('pages/Inbox.tsx')
        expect(src).toContain("import { useGoosed } from '../contexts/GoosedContext'")
    })

    it('reads isConnected and connectionError from useGoosed()', () => {
        const src = readSource('pages/Inbox.tsx')
        expect(src).toContain('isConnected')
        expect(src).toContain('error: connectionError')
    })

    it('displays connection error banner when not connected', () => {
        const src = readSource('pages/Inbox.tsx')
        expect(src).toMatch(/!isConnected\s*&&\s*connectionError/)
        expect(src).toContain('conn-banner conn-banner-error')
    })
})

describe('Monitoring page — connection error handling', () => {
    it('imports useGoosed', () => {
        const src = readSource('pages/Monitoring.tsx')
        expect(src).toContain("import { useGoosed } from '../contexts/GoosedContext'")
    })

    it('shows page-level conn-banner error from GoosedContext', () => {
        const src = readSource('pages/Monitoring.tsx')
        expect(src).toContain('conn-banner conn-banner-error')
        expect(src).toContain("t('common.connectionError'")
    })

    it('does not use mon-error-banner class', () => {
        const src = readSource('pages/Monitoring.tsx')
        expect(src).not.toContain('mon-error-banner')
    })

    it('does not have retry button in error banners', () => {
        const src = readSource('pages/Monitoring.tsx')
        expect(src).not.toContain('mon-retry-btn')
    })
})

describe('Unified error banner CSS class — conn-banner', () => {
    const pagesToCheck = [
        { file: 'pages/Home.tsx', name: 'Home' },
        { file: 'pages/History.tsx', name: 'History' },
        { file: 'pages/Files.tsx', name: 'Files' },
        { file: 'pages/Inbox.tsx', name: 'Inbox' },
        { file: 'pages/Agents.tsx', name: 'Agents' },
        { file: 'pages/ScheduledActions.tsx', name: 'ScheduledActions' },
        { file: 'pages/Monitoring.tsx', name: 'Monitoring' },
    ]

    for (const { file, name } of pagesToCheck) {
        it(`${name} uses conn-banner class for error display`, () => {
            const src = readSource(file)
            expect(src).toContain('conn-banner conn-banner-error')
        })
    }

    it('App.css defines conn-banner base class', () => {
        const css = readSource('App.css')
        expect(css).toContain('.conn-banner')
        expect(css).toContain('.conn-banner-error')
        expect(css).toContain('.conn-banner-warning')
    })

    it('no page uses inline styles for error banners', () => {
        for (const { file } of pagesToCheck) {
            const src = readSource(file)
            // Should NOT have inline red background for error banners
            const inlineErrorBanner = src.match(
                /background:\s*['"]?rgba\(239,\s*68,\s*68/
            )
            expect(inlineErrorBanner).toBeNull()
        }
    })
})

describe('Error banner position — after page header, before content', () => {
    it('History: conn-banner appears before search-container', () => {
        const src = readSource('pages/History.tsx')
        const bannerPos = src.indexOf('conn-banner conn-banner-error')
        const searchPos = src.indexOf('search-container')
        expect(bannerPos).toBeLessThan(searchPos)
    })

    it('Files: conn-banner appears before search-container', () => {
        const src = readSource('pages/Files.tsx')
        const bannerPos = src.indexOf('conn-banner conn-banner-error')
        const searchPos = src.indexOf('search-container')
        expect(bannerPos).toBeLessThan(searchPos)
    })

    it('Inbox: conn-banner appears before inbox-toolbar', () => {
        const src = readSource('pages/Inbox.tsx')
        const bannerPos = src.indexOf('conn-banner conn-banner-error')
        const toolbarPos = src.indexOf('inbox-toolbar')
        expect(bannerPos).toBeLessThan(toolbarPos)
    })

    it('Monitoring: page-level conn-banner appears before config-tabs', () => {
        const src = readSource('pages/Monitoring.tsx')
        // Find the page-level conn-banner (in the Monitoring component, not in tabs)
        const exportPos = src.indexOf('export default function Monitoring')
        const monitoringBody = src.slice(exportPos)
        const bannerPos = monitoringBody.indexOf('conn-banner conn-banner-error')
        const tabsPos = monitoringBody.indexOf('config-tabs')
        expect(bannerPos).toBeLessThan(tabsPos)
    })
})

describe('GoosedContext — timeout and error propagation', () => {
    it('uses AbortSignal.timeout for agent fetch', () => {
        const src = readSource('contexts/GoosedContext.tsx')
        expect(src).toContain('AbortSignal.timeout(5000)')
    })

    it('sets isConnected to false on error', () => {
        const src = readSource('contexts/GoosedContext.tsx')
        expect(src).toContain('setIsConnected(false)')
    })

    it('exposes error in context value', () => {
        const src = readSource('contexts/GoosedContext.tsx')
        expect(src).toMatch(/value=\{\{[^}]*error/)
    })
})
