import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Message from '../components/Message'
import type { ChatMessage } from '../types/message'

vi.mock('../contexts/PreviewContext', () => ({
    usePreview: () => ({
        openPreview: vi.fn(),
        isPreviewable: () => false,
    }),
}))

describe('Message thinking panel', () => {
    const buildStreamingMessage = (thinking: string): ChatMessage => ({
        id: 'assistant-1',
        role: 'assistant',
        content: [
            { type: 'reasoning', text: thinking },
        ],
    })

    it('keeps the thinking panel open while streaming, then auto-collapses when streaming ends', async () => {
        const { rerender } = render(
            <Message message={buildStreamingMessage('first chunk')} isStreaming />
        )

        const header = screen.getByRole('button', { name: '推理过程' })
        expect(header).toBeDisabled()
        expect(document.querySelector('.process-thinking-content')).toBeInTheDocument()

        rerender(
            <Message message={buildStreamingMessage('first chunk')} isStreaming={false} />
        )

        await waitFor(() => {
            expect(screen.getByRole('button', { name: '推理过程' })).not.toBeDisabled()
            expect(document.querySelector('.process-thinking-content')).not.toBeInTheDocument()
        })

        fireEvent.click(screen.getByRole('button', { name: '推理过程' }))
        expect(document.querySelector('.process-thinking-content')).toBeInTheDocument()
    })

    it('auto-scrolls the thinking content to the latest streamed text', async () => {
        const { rerender, container } = render(
            <Message message={buildStreamingMessage('chunk 1')} isStreaming />
        )

        const content = container.querySelector('.process-thinking-content') as HTMLDivElement
        expect(content).toBeInTheDocument()

        Object.defineProperty(content, 'clientHeight', {
            configurable: true,
            value: 100,
        })
        Object.defineProperty(content, 'scrollHeight', {
            configurable: true,
            value: 320,
        })
        Object.defineProperty(content, 'scrollTop', {
            configurable: true,
            writable: true,
            value: 0,
        })

        rerender(
            <Message message={buildStreamingMessage('chunk 1\n\nchunk 2\n\nchunk 3')} isStreaming />
        )

        await waitFor(() => {
            expect(content.scrollTop).toBe(320)
        })
    })
})
