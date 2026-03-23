import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MessageList from '../components/MessageList'
import type { ChatMessage } from '../types/message'

Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: vi.fn(),
})

vi.mock('react-i18next', () => ({
    useTranslation: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('../contexts/UserContext', () => ({
    useUser: () => ({
        userId: 'admin',
    }),
}))

vi.mock('../contexts/PreviewContext', () => ({
    usePreview: () => ({
        openPreview: vi.fn(),
        isPreviewable: () => false,
    }),
}))

describe('MessageList tool chain rendering', () => {
    it('merges consecutive assistant tool messages into a single rendered chain', () => {
        const messages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: '诊断一下当前系统的健康状态' }],
            },
            {
                id: 'assistant-plan',
                role: 'assistant',
                content: [{ type: 'reasoning', text: '我需要先检查平台状态。' }],
            },
            {
                id: 'assistant-tool-1',
                role: 'assistant',
                content: [
                    { type: 'reasoning', text: '先调用平台状态工具。' },
                    {
                        type: 'toolRequest',
                        id: 'tool-1',
                        toolCall: {
                            status: 'success',
                            value: { name: 'platform-monitor__get_platform_status', arguments: {} },
                        },
                    },
                ],
            },
            {
                id: 'tool-response-1',
                role: 'user',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'tool-1',
                        toolResult: {
                            status: 'success',
                            value: { content: [{ type: 'text', text: '{"ok":true}' }] },
                        },
                    },
                ],
            },
            {
                id: 'assistant-tool-2',
                role: 'assistant',
                content: [
                    { type: 'reasoning', text: '再调用代理状态工具。' },
                    {
                        type: 'toolRequest',
                        id: 'tool-2',
                        toolCall: {
                            status: 'success',
                            value: { name: 'platform-monitor__get_agents_status', arguments: {} },
                        },
                    },
                ],
            },
            {
                id: 'tool-response-2',
                role: 'user',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'tool-2',
                        toolResult: {
                            status: 'success',
                            value: { content: [{ type: 'text', text: '{"agents":[]}' }] },
                        },
                    },
                ],
            },
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: '诊断完成。' }],
            },
        ]

        const { container } = render(<MessageList messages={messages} />)

        expect(container.querySelectorAll('.message.user')).toHaveLength(1)
        expect(container.querySelectorAll('.message.assistant')).toHaveLength(2)
        expect(screen.getAllByText('推理过程')).toHaveLength(2)
        expect(screen.getByText('Get platform status')).toBeInTheDocument()
        expect(screen.getByText('Get agents status')).toBeInTheDocument()
        expect(screen.getByText('诊断完成。')).toBeInTheDocument()
    })
})
