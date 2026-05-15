import { describe, expect, it, vi, afterEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import ToolCallDisplay from '../app/platform/chat/ToolCallDisplay'
import i18n from '../i18n'

function renderToolCall(result: unknown) {
    return render(
        <I18nextProvider i18n={i18n}>
            <ToolCallDisplay name="knowledge__search" result={result} />
        </I18nextProvider>
    )
}

describe('ToolCallDisplay output copy', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('copies the full tool output even when the displayed output is truncated', async () => {
        const longText = `prefix-${'x'.repeat(50_200)}-tail`
        const writeText = vi.fn().mockResolvedValue(undefined)
        vi.stubGlobal('navigator', {
            ...navigator,
            clipboard: { writeText },
        })

        renderToolCall({ content: [{ type: 'text', text: longText }] })

        fireEvent.click(screen.getByText('Output'))

        expect(screen.getByText(/\.\.\. \[truncated\]/)).toBeTruthy()
        fireEvent.click(screen.getByRole('button', { name: /Copy output|复制输出/ }))

        await waitFor(() => {
            expect(writeText).toHaveBeenCalledWith(JSON.stringify({ content: [{ type: 'text', text: longText }] }, null, 2))
        })
    })
})
