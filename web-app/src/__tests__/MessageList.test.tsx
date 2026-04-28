import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import type { ComponentProps } from 'react'
import MessageList from '../app/platform/chat/MessageList'
import { UserProvider } from '../app/platform/providers/UserContext'
import { PreviewProvider } from '../app/platform/providers/PreviewContext'
import i18n from '../i18n'
import type { ChatMessage } from '../types/message'

function renderMessageList(messages: ChatMessage[], props: Partial<ComponentProps<typeof MessageList>> = {}) {
    return render(
        <I18nextProvider i18n={i18n}>
            <UserProvider>
                <MessageList messages={messages} {...props} />
            </UserProvider>
        </I18nextProvider>
    )
}

function renderMessageListWithPreview(messages: ChatMessage[], props: Partial<ComponentProps<typeof MessageList>> = {}) {
    return render(
        <I18nextProvider i18n={i18n}>
            <UserProvider>
                <PreviewProvider>
                    <MessageList messages={messages} {...props} />
                </PreviewProvider>
            </UserProvider>
        </I18nextProvider>
    )
}

describe('MessageList tool error rendering', () => {
    const originalScrollIntoView = Element.prototype.scrollIntoView
    const originalRequestAnimationFrame = window.requestAnimationFrame
    const originalCancelAnimationFrame = window.cancelAnimationFrame

    beforeEach(() => {
        Element.prototype.scrollIntoView = () => {}
        window.requestAnimationFrame = ((callback: FrameRequestCallback) => {
            callback(0)
            return 1
        }) as typeof window.requestAnimationFrame
        window.cancelAnimationFrame = (() => {}) as typeof window.cancelAnimationFrame
    })

    afterEach(() => {
        Element.prototype.scrollIntoView = originalScrollIntoView
        window.requestAnimationFrame = originalRequestAnimationFrame
        window.cancelAnimationFrame = originalCancelAnimationFrame
        vi.unstubAllGlobals()
    })

    it('renders tool steps as error when toolResult.isError is true', () => {

        const messages: ChatMessage[] = [
            {
                id: 'assistant-tool-request',
                role: 'assistant',
                content: [
                    {
                        type: 'toolRequest',
                        id: 'tool-1',
                        toolCall: {
                            status: 'completed',
                            value: {
                                name: 'developer__extension_manager',
                                arguments: {
                                    action: 'enable',
                                    extension_name: 'control_center',
                                },
                            },
                        },
                    },
                ],
            },
            {
                id: 'assistant-tool-response',
                role: 'assistant',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'tool-1',
                        toolResult: {
                            isError: true,
                            value: {
                                content: [
                                    {
                                        type: 'text',
                                        text: 'Extension operation failed',
                                    },
                                ],
                            },
                        },
                    },
                ],
            },
        ]

        const { container } = renderMessageList(messages)
        const errorNode = container.querySelector('.process-step-node.error')
        expect(errorNode).toBeTruthy()
    })

    it('scrolls to bottom again when a resumed session changes with the same message count', async () => {
        const scrollContainer = document.createElement('div')
        Object.defineProperty(scrollContainer, 'scrollHeight', { configurable: true, value: 600 })
        Object.defineProperty(scrollContainer, 'clientHeight', { configurable: true, value: 200 })
        Object.defineProperty(scrollContainer, 'scrollTop', {
            configurable: true,
            get: () => 0,
        })
        scrollContainer.scrollTo = vi.fn()

        const firstSessionMessages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'First session' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Reply one' }],
            },
        ]

        const secondSessionMessages: ChatMessage[] = [
            {
                id: 'user-2',
                role: 'user',
                content: [{ type: 'text', text: 'Second session' }],
            },
            {
                id: 'assistant-2',
                role: 'assistant',
                content: [{ type: 'text', text: 'Reply two' }],
            },
        ]

        const view = renderMessageList(firstSessionMessages, {
            agentId: 'agent-a',
            sessionId: 'session-a',
            scrollContainerRef: { current: scrollContainer },
        })

        await waitFor(() => {
            expect(scrollContainer.scrollTo).toHaveBeenCalledTimes(1)
        })

        view.rerender(
            <I18nextProvider i18n={i18n}>
                <UserProvider>
                    <MessageList
                        messages={secondSessionMessages}
                        agentId="agent-a"
                        sessionId="session-b"
                        scrollContainerRef={{ current: scrollContainer }}
                    />
                </UserProvider>
            </I18nextProvider>
        )

        await waitFor(() => {
            expect(scrollContainer.scrollTo).toHaveBeenCalledTimes(2)
        })
    })

    it('falls back to the document scroll root when the provided message container does not overflow', async () => {
        const scrollContainer = document.createElement('div')
        Object.defineProperty(scrollContainer, 'scrollHeight', { configurable: true, value: 600 })
        Object.defineProperty(scrollContainer, 'clientHeight', { configurable: true, value: 600 })
        Object.defineProperty(scrollContainer, 'scrollTop', {
            configurable: true,
            get: () => 0,
        })
        scrollContainer.scrollTo = vi.fn()

        const scrollRoot = document.createElement('div')
        Object.defineProperty(scrollRoot, 'scrollHeight', { configurable: true, value: 1200 })
        Object.defineProperty(scrollRoot, 'clientHeight', { configurable: true, value: 600 })
        let rootScrollTop = 0
        Object.defineProperty(scrollRoot, 'scrollTop', {
            configurable: true,
            get: () => rootScrollTop,
            set: (value: number) => { rootScrollTop = value },
        })
        scrollRoot.scrollTo = vi.fn(({ top }: ScrollToOptions) => {
            rootScrollTop = Number(top ?? 0)
        })

        const originalScrollingElement = Object.getOwnPropertyDescriptor(document, 'scrollingElement')
        Object.defineProperty(document, 'scrollingElement', {
            configurable: true,
            value: scrollRoot,
        })

        const messages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'Hello' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Hi there' }],
            },
        ]

        renderMessageList(messages, {
            agentId: 'agent-a',
            sessionId: 'session-a',
            scrollContainerRef: { current: scrollContainer },
        })

        await waitFor(() => {
            expect(scrollContainer.scrollTo).not.toHaveBeenCalled()
            expect(scrollRoot.scrollTo).toHaveBeenCalledTimes(1)
        })

        if (originalScrollingElement) {
            Object.defineProperty(document, 'scrollingElement', originalScrollingElement)
        } else {
            // @ts-expect-error test cleanup for configurable property
            delete document.scrollingElement
        }
    })

    it('falls back to the document scroll root when no message container is provided', async () => {
        const scrollRoot = document.createElement('div')
        Object.defineProperty(scrollRoot, 'scrollHeight', { configurable: true, value: 1200 })
        Object.defineProperty(scrollRoot, 'clientHeight', { configurable: true, value: 600 })
        let rootScrollTop = 0
        Object.defineProperty(scrollRoot, 'scrollTop', {
            configurable: true,
            get: () => rootScrollTop,
            set: (value: number) => { rootScrollTop = value },
        })
        scrollRoot.scrollTo = vi.fn(({ top }: ScrollToOptions) => {
            rootScrollTop = Number(top ?? 0)
        })

        const originalScrollingElement = Object.getOwnPropertyDescriptor(document, 'scrollingElement')
        Object.defineProperty(document, 'scrollingElement', {
            configurable: true,
            value: scrollRoot,
        })

        const messages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'Hello' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Hi there' }],
            },
        ]

        renderMessageList(messages, {
            agentId: 'agent-a',
            sessionId: 'session-a',
        })

        await waitFor(() => {
            expect(scrollRoot.scrollTo).toHaveBeenCalledTimes(1)
        })

        if (originalScrollingElement) {
            Object.defineProperty(document, 'scrollingElement', originalScrollingElement)
        } else {
            // @ts-expect-error test cleanup for configurable property
            delete document.scrollingElement
        }
    })

    it('renders and persists output file capsules from a live OutputFiles event', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({ entries: {} }) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        view.rerender(
            <I18nextProvider i18n={i18n}>
                <UserProvider>
                    <PreviewProvider>
                        <MessageList
                            messages={messages}
                            agentId="universal-agent"
                            sessionId="session-1"
                            outputFilesEvent={{
                                sessionId: 'session-1',
                                files: [{
                                    path: 'goose-intro.md',
                                    name: 'goose-intro.md',
                                    ext: 'md',
                                    rootId: 'workingDir',
                                    displayPath: 'goose-intro.md',
                                }],
                            }}
                        />
                    </PreviewProvider>
                </UserProvider>
            </I18nextProvider>
        )

        await waitFor(() => {
            expect(view.container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('goose-intro.md')).toBeTruthy()
        })

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledWith(
                expect.stringContaining('/agents/universal-agent/file-capsules'),
                expect.objectContaining({
                    method: 'POST',
                    body: expect.stringContaining('"messageId":"assistant-final"'),
                })
            )
        })
    })

    it('attaches late OutputFiles to the assistant message with the matching request id', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({ entries: {} }) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-a',
                role: 'assistant',
                content: [{ type: 'text', text: 'First done' }],
                metadata: { requestId: 'req-a' },
            },
            {
                id: 'assistant-b',
                role: 'assistant',
                content: [{ type: 'text', text: 'Second done' }],
                metadata: { requestId: 'req-b' },
            },
        ]

        renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-a',
                files: [{
                    path: 'first-report.md',
                    name: 'first-report.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'first-report.md',
                }],
            },
        })

        await waitFor(() => {
            const postCall = fetchMock.mock.calls.find(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )
            expect(postCall).toBeTruthy()
            const body = JSON.parse(String(postCall?.[1]?.body))
            expect(body.messageId).toBe('assistant-a')
        })
    })

    it('does not attach request-scoped OutputFiles when no assistant request id matches', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({ entries: {} }) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-latest',
                role: 'assistant',
                content: [{ type: 'text', text: 'Latest done' }],
                metadata: { requestId: 'req-latest' },
            },
        ]

        renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-missing',
                files: [{
                    path: 'missing-request.md',
                    name: 'missing-request.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'missing-request.md',
                }],
            },
        })

        await waitFor(() => {
            expect(fetchMock.mock.calls.some(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )).toBe(false)
        })
        expect(screen.queryByText('missing-request.md')).toBeNull()
    })

    it('restores output file capsules from persisted session entries', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        entries: {
                            'assistant-final': [{
                                path: 'goose-intro.md',
                                name: 'goose-intro.md',
                                ext: 'md',
                                rootId: 'workingDir',
                                displayPath: 'goose-intro.md',
                            }],
                        },
                    }),
                }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('goose-intro.md')).toBeTruthy()
        })
    })

    it('restores output file capsules when the persisted id belongs to a merged final answer', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        entries: {
                            'gen-1777386391-Z5uapXjSllauI4F0BQjr': [{
                                path: 'example.md',
                                name: 'example.md',
                                ext: 'md',
                                rootId: 'workingDir',
                                displayPath: 'example.md',
                            }],
                        },
                    }),
                }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'msg_20260428_2_1f25c68c-3454-4d42-aa0a-2c611a8eb1d9',
                role: 'user',
                content: [{ type: 'text', text: '随便输出一个 md 文件' }],
            },
            {
                id: 'gen-1777386384-TVNvBJlFddfstgvMjdrn',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '用户要求输出一个 md 文件', signature: '' },
                    { type: 'text', text: '我来为你创建一个简单的 markdown 文件：' },
                ],
            },
            {
                id: 'msg_8150af22-1207-4cbe-befd-8458b13c15b4',
                role: 'assistant',
                content: [{ type: 'thinking', thinking: '准备写入文件', signature: '' }],
            },
            {
                id: 'msg_67b4c79b-41bf-44eb-ab8d-ddd4e9275bb9',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '调用写文件工具', signature: '' },
                    {
                        type: 'toolRequest',
                        id: 'call_ec5140b3a82046f29905a5e5',
                        toolCall: {
                            status: 'success',
                            value: {
                                name: 'write',
                                arguments: { path: 'example.md' },
                            },
                        },
                    },
                ],
            },
            {
                id: 'msg_3763c20b-8ac6-4148-b3c9-a730f177caff',
                role: 'user',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'call_ec5140b3a82046f29905a5e5',
                        toolResult: {
                            status: 'success',
                            value: {
                                content: [{ type: 'text', text: 'Created example.md' }],
                            },
                        },
                    },
                ],
            },
            {
                id: 'gen-1777386391-Z5uapXjSllauI4F0BQjr',
                role: 'assistant',
                content: [{ type: 'text', text: '已创建示例 Markdown 文件：example.md' }],
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: '20260428_2',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('example.md')).toBeTruthy()
        })
    })

    it('merges live output files with existing persisted capsules for the same message', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
            }
            if (url.includes('/file-capsules')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        entries: {
                            'assistant-final': [{
                                path: 'existing.md',
                                name: 'existing.md',
                                ext: 'md',
                                rootId: 'workingDir',
                                displayPath: 'existing.md',
                            }],
                        },
                    }),
                }) as Promise<Response>
            }
            return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        await waitFor(() => {
            expect(screen.getByText('existing.md')).toBeTruthy()
        })

        view.rerender(
            <I18nextProvider i18n={i18n}>
                <UserProvider>
                    <PreviewProvider>
                        <MessageList
                            messages={messages}
                            agentId="universal-agent"
                            sessionId="session-1"
                            outputFilesEvent={{
                                sessionId: 'session-1',
                                files: [{
                                    path: 'new.md',
                                    name: 'new.md',
                                    ext: 'md',
                                    rootId: 'workingDir',
                                    displayPath: 'new.md',
                                }],
                            }}
                        />
                    </PreviewProvider>
                </UserProvider>
            </I18nextProvider>
        )

        await waitFor(() => {
            expect(screen.getByText('existing.md')).toBeTruthy()
            expect(screen.getByText('new.md')).toBeTruthy()
        })

        await waitFor(() => {
            const postCall = fetchMock.mock.calls.find(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )
            expect(postCall).toBeTruthy()
            const body = JSON.parse(String(postCall?.[1]?.body))
            expect(body.files.map((file: { name: string }) => file.name)).toEqual(['existing.md', 'new.md'])
        })
    })

    it('keeps live output file capsules when a stale persisted load resolves later', async () => {
        let resolvePersistedLoad: ((response: Response) => void) | null = null
        const persistedLoad = new Promise<Response>(resolve => {
            resolvePersistedLoad = resolve
        })
        const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input)
            if (url.includes('/config')) {
                return Promise.resolve({ ok: false }) as Promise<Response>
            }
            if (url.includes('/file-capsules') && init?.method === 'POST') {
                return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
            }
            if (url.includes('/file-capsules')) {
                return persistedLoad
            }
            return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        view.rerender(
            <I18nextProvider i18n={i18n}>
                <UserProvider>
                    <PreviewProvider>
                        <MessageList
                            messages={messages}
                            agentId="universal-agent"
                            sessionId="session-1"
                            outputFilesEvent={{
                                sessionId: 'session-1',
                                files: [{
                                    path: 'aaa.md',
                                    name: 'aaa.md',
                                    ext: 'md',
                                    rootId: 'workingDir',
                                    displayPath: 'aaa.md',
                                }],
                            }}
                        />
                    </PreviewProvider>
                </UserProvider>
            </I18nextProvider>
        )

        await waitFor(() => {
            expect(screen.getByText('aaa.md')).toBeTruthy()
        })

        await act(async () => {
            resolvePersistedLoad?.({
                ok: true,
                json: async () => ({
                    entries: {
                        'assistant-final': [{
                            path: 'old.md',
                            name: 'old.md',
                            ext: 'md',
                            rootId: 'workingDir',
                            displayPath: 'old.md',
                        }],
                    },
                }),
            } as Response)
            await persistedLoad
        })

        await waitFor(() => {
            expect(screen.getByText('aaa.md')).toBeTruthy()
            expect(screen.queryByText('old.md')).toBeNull()
        })
    })

})
