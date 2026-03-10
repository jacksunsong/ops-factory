import { describe, it, expect } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Static analysis tests: verify that alert() has been fully removed
 * from the target files and replaced with showToast().
 */

const SRC_DIR = path.resolve(__dirname, '..')

function readSource(relativePath: string): string {
    return fs.readFileSync(path.join(SRC_DIR, relativePath), 'utf-8')
}

describe('alert() removal — static analysis', () => {
    const filesToCheck = [
        'pages/Home.tsx',
        'pages/History.tsx',
        'components/ChatInput.tsx',
    ]

    for (const file of filesToCheck) {
        it(`${file} does not contain window.alert or bare alert()`, () => {
            const src = readSource(file)
            // Match alert( but not showToast calls or string literals containing "alert"
            const alertCalls = src.match(/(?<!\w)alert\s*\(/g)
            expect(alertCalls).toBeNull()
        })

        it(`${file} imports useToast`, () => {
            const src = readSource(file)
            expect(src).toContain("import { useToast } from '../contexts/ToastContext'")
        })

        it(`${file} calls showToast`, () => {
            const src = readSource(file)
            expect(src).toContain('showToast(')
        })
    }
})

describe('showToast replacement correctness', () => {
    it('Home.tsx uses showToast("error", ...) for session creation failure', () => {
        const src = readSource('pages/Home.tsx')
        expect(src).toContain("showToast('error', t('home.failedToCreateSession'")
    })

    it('History.tsx uses showToast("error", ...) for delete failure', () => {
        const src = readSource('pages/History.tsx')
        expect(src).toContain("showToast('error', t('errors.deleteFailed'))")
    })

    it('ChatInput.tsx uses showToast("warning", ...) for image upload limits', () => {
        const src = readSource('components/ChatInput.tsx')
        expect(src).toContain("showToast('warning', t('chat.imageUploadNotEnabled'))")
        expect(src).toContain("showToast('warning', t('chat.maxImagesAllowed'")
        expect(src).toContain("showToast('warning', t('chat.maxFilesAllowed'")
    })
})

describe('Voice error display in ChatInput', () => {
    it('ChatInput.tsx destructures voiceError from useVoiceInput', () => {
        const src = readSource('components/ChatInput.tsx')
        expect(src).toContain('error: voiceError')
    })

    it('ChatInput.tsx has useEffect for voiceError toast', () => {
        const src = readSource('components/ChatInput.tsx')
        expect(src).toContain('voiceError')
        expect(src).toContain("t('errors.micPermissionDenied')")
        expect(src).toContain("t('errors.voiceError'")
    })
})

describe('FilePreview copy failure toast', () => {
    it('FilePreview.tsx imports useToast', () => {
        const src = readSource('components/FilePreview.tsx')
        expect(src).toContain("import { useToast } from '../contexts/ToastContext'")
    })

    it('FilePreview.tsx shows toast on copy failure', () => {
        const src = readSource('components/FilePreview.tsx')
        expect(src).toContain("showToast('error', t('errors.copyFailed'))")
    })
})
