import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeConfigure from '../pages/KnowledgeConfigure'

const showToast = vi.fn()
const searchRequests: Array<Record<string, unknown>> = []

vi.mock('react-i18next', () => ({
    initReactI18next: {
        type: '3rdParty',
        init: () => {},
    },
    useTranslation: () => ({
        t: (key: string, params?: Record<string, unknown>) => {
            if (params?.name) return `${key}:${String(params.name)}`
            if (params?.error) return `${key}:${String(params.error)}`
            return key
        },
    }),
}))

vi.mock('../contexts/ToastContext', () => ({
    useToast: () => ({
        showToast,
    }),
}))

vi.mock('../contexts/PreviewContext', () => ({
    usePreview: () => ({
        openInlinePreview: vi.fn(),
        previewFile: null,
        closePreview: vi.fn(),
    }),
}))

vi.mock('../config/runtime', () => ({
    KNOWLEDGE_SERVICE_URL: 'http://127.0.0.1:8092',
}))

const baseSource = {
    id: 'src_001',
    name: '产品文档库',
    description: '产品手册与 FAQ',
    status: 'ACTIVE',
    storageMode: 'MANAGED',
    indexProfileId: 'ip_default',
    retrievalProfileId: 'rp_default',
    createdAt: '2026-03-25T10:00:00Z',
    updatedAt: '2026-03-25T10:00:00Z',
}

const detailByChunkId = {
    chk_hyb_001: {
        chunkId: 'chk_hyb_001',
        documentId: 'doc_hybrid',
        sourceId: 'src_001',
        title: '混合结论',
        titlePath: ['召回评估', '混合结论'],
        text: '混合检索把语义相关和关键词命中放在一起，整体排序更均衡。',
        markdown: '混合检索把语义相关和关键词命中放在一起。',
        keywords: ['hybrid', 'rank'],
        pageFrom: 6,
        pageTo: 6,
        previousChunkId: null,
        nextChunkId: null,
    },
    chk_sem_001: {
        chunkId: 'chk_sem_001',
        documentId: 'doc_semantic',
        sourceId: 'src_001',
        title: '语义覆盖',
        titlePath: ['召回评估', '语义覆盖'],
        text: '向量召回覆盖更多语义近邻段落，适合问题表达不稳定的场景。',
        markdown: '向量召回覆盖更多语义近邻段落。',
        keywords: ['semantic', 'embedding'],
        pageFrom: 3,
        pageTo: 3,
        previousChunkId: null,
        nextChunkId: null,
    },
    chk_lex_001: {
        chunkId: 'chk_lex_001',
        documentId: 'doc_lexical',
        sourceId: 'src_001',
        title: '精确命中',
        titlePath: ['召回评估', '精确命中'],
        text: '关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。该段落适合验证词项召回。',
        markdown: '关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。',
        keywords: ['Qwen3-32B', 'keyword'],
        pageFrom: 8,
        pageTo: 8,
        previousChunkId: null,
        nextChunkId: null,
    },
} as const

describe('KnowledgeConfigure retrieval tab', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        searchRequests.length = 0
        window.localStorage.clear()

        vi.stubGlobal('fetch', vi.fn((input: string | URL | Request, init?: RequestInit) => {
            const url = String(input)
            const method = init?.method ?? 'GET'

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => baseSource,
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001/stats')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        sourceId: 'src_001',
                        documentCount: 12,
                        indexedDocumentCount: 10,
                        failedDocumentCount: 1,
                        processingDocumentCount: 1,
                        chunkCount: 234,
                        userEditedChunkCount: 3,
                        lastIngestionAt: '2026-03-25T12:30:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/capabilities')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        retrievalModes: ['semantic', 'lexical', 'hybrid'],
                        fusionModes: ['rrf'],
                        chunkModes: ['hierarchical'],
                        expandModes: ['ordinal_neighbors'],
                        analyzers: ['smartcn'],
                        editableChunkFields: ['title', 'keywords', 'text'],
                        featureFlags: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride: false,
                        },
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/system/defaults')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        ingest: {
                            maxFileSizeMb: 100,
                            allowedContentTypes: ['application/pdf', 'text/markdown'],
                            deduplication: 'sha256',
                            skipExistingByDefault: true,
                        },
                        chunking: {
                            mode: 'hierarchical',
                            targetTokens: 512,
                            overlapTokens: 64,
                            respectHeadings: true,
                            keepTablesWhole: true,
                        },
                        retrieval: {
                            mode: 'semantic',
                            lexicalTopK: 50,
                            semanticTopK: 50,
                            finalTopK: 3,
                            fusionMode: 'rrf',
                            rrfK: 60,
                        },
                        features: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride: false,
                        },
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/index/ip_default')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'ip_default',
                        name: '默认索引配置',
                        config: {
                            chunking: { mode: 'hierarchical', targetTokens: 512 },
                        },
                        createdAt: '2026-03-24T10:00:00Z',
                        updatedAt: '2026-03-24T10:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'rp_default',
                        name: '默认召回配置',
                        config: {
                            retrieval: { mode: 'semantic', fusionMode: 'rrf' },
                            result: { finalTopK: 3, snippetLength: 180 },
                        },
                        createdAt: '2026-03-24T10:00:00Z',
                        updatedAt: '2026-03-24T10:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/documents?sourceId=src_001&page=1&pageSize=100')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items: [
                            {
                                id: 'doc_hybrid',
                                sourceId: 'src_001',
                                name: '混合检索总结.pdf',
                                contentType: 'application/pdf',
                                title: '混合检索总结',
                                status: 'INDEXED',
                                indexStatus: 'INDEXED',
                                fileSizeBytes: 1024,
                                chunkCount: 8,
                                userEditedChunkCount: 0,
                                createdAt: '2026-03-25T10:00:00Z',
                                updatedAt: '2026-03-25T10:05:00Z',
                            },
                            {
                                id: 'doc_semantic',
                                sourceId: 'src_001',
                                name: '向量召回评估.pdf',
                                contentType: 'application/pdf',
                                title: '向量召回评估',
                                status: 'INDEXED',
                                indexStatus: 'INDEXED',
                                fileSizeBytes: 2048,
                                chunkCount: 6,
                                userEditedChunkCount: 0,
                                createdAt: '2026-03-25T10:00:00Z',
                                updatedAt: '2026-03-25T10:05:00Z',
                            },
                            {
                                id: 'doc_lexical',
                                sourceId: 'src_001',
                                name: '关键词命中说明.pdf',
                                contentType: 'application/pdf',
                                title: '关键词命中说明',
                                status: 'INDEXED',
                                indexStatus: 'INDEXED',
                                fileSizeBytes: 2048,
                                chunkCount: 4,
                                userEditedChunkCount: 0,
                                createdAt: '2026-03-25T10:00:00Z',
                                updatedAt: '2026-03-25T10:05:00Z',
                            },
                        ],
                        page: 1,
                        pageSize: 100,
                        total: 3,
                    }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search')) {
                const requestBody = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>
                const override = requestBody.override as Record<string, unknown>
                const mode = String(override.mode)
                searchRequests.push(requestBody)

                const responseByMode = {
                    hybrid: {
                        query: requestBody.query,
                        total: 1,
                        hits: [
                            {
                                chunkId: 'chk_hyb_001',
                                documentId: 'doc_hybrid',
                                sourceId: 'src_001',
                                title: '混合结论',
                                titlePath: ['召回评估', '混合结论'],
                                snippet: '混合检索把语义相关和关键词命中放在一起，整体排序更均衡。',
                                score: 0.92,
                                lexicalScore: 0.74,
                                semanticScore: 0.88,
                                fusionScore: 0.92,
                                pageFrom: 6,
                                pageTo: 6,
                            },
                        ],
                    },
                    semantic: {
                        query: requestBody.query,
                        total: 1,
                        hits: [
                            {
                                chunkId: 'chk_sem_001',
                                documentId: 'doc_semantic',
                                sourceId: 'src_001',
                                title: '语义覆盖',
                                titlePath: ['召回评估', '语义覆盖'],
                                snippet: '向量召回覆盖更多语义近邻段落，适合问题表达不稳定的场景。',
                                score: 0.81,
                                lexicalScore: 0.32,
                                semanticScore: 0.81,
                                fusionScore: 0.81,
                                pageFrom: 3,
                                pageTo: 3,
                            },
                        ],
                    },
                    lexical: {
                        query: requestBody.query,
                        total: 1,
                        hits: [
                            {
                                chunkId: 'chk_lex_001',
                                documentId: 'doc_lexical',
                                sourceId: 'src_001',
                                title: '精确命中',
                                titlePath: ['召回评估', '精确命中'],
                                snippet: '关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。',
                                score: 0.76,
                                lexicalScore: 0.76,
                                semanticScore: 0.24,
                                fusionScore: 0.76,
                                pageFrom: 8,
                                pageTo: 8,
                            },
                        ],
                    },
                } as const

                return Promise.resolve({
                    ok: true,
                    json: async () => responseByMode[mode as keyof typeof responseByMode],
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/fetch/')) {
                const match = url.match(/\/ops-knowledge\/fetch\/([^?]+)/)
                const chunkId = match?.[1] as keyof typeof detailByChunkId | undefined

                if (chunkId && detailByChunkId[chunkId]) {
                    return Promise.resolve({
                        ok: true,
                        json: async () => detailByChunkId[chunkId],
                    } as Response)
                }
            }

            return Promise.resolve({
                ok: false,
                status: 404,
                json: async () => ({ message: 'not found' }),
            } as Response)
        }))
    })

    it('supports compare mode, single-mode tuning, and inline detail inspection', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')
        expect(screen.getByRole('button', { name: 'knowledge.retrievalViewCompare' })).toBeInTheDocument()

        const queryInput = screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }) as HTMLTextAreaElement

        fireEvent.change(queryInput, {
            target: { value: 'qwen3' },
        })
        expect(queryInput.value).toBe('qwen3')
        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'knowledge.retrievalRun' })).not.toBeDisabled()
        })

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await screen.findByText('混合检索总结.pdf')
        expect(await screen.findByText('向量召回评估.pdf')).toBeInTheDocument()
        expect(await screen.findByText('关键词命中说明.pdf')).toBeInTheDocument()

        await waitFor(() => {
            expect(searchRequests).toHaveLength(3)
        })

        expect(searchRequests.map(request => (request.override as Record<string, unknown>).mode)).toEqual([
            'hybrid',
            'semantic',
            'lexical',
        ])

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalModeLexical' }))
        fireEvent.change(screen.getByLabelText('knowledge.retrievalTopKLabel'), {
            target: { value: '5' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(searchRequests).toHaveLength(4)
        })

        expect(searchRequests.at(-1)?.topK).toBe(5)
        expect((searchRequests.at(-1)?.override as Record<string, unknown>).mode).toBe('lexical')

        fireEvent.click(screen.getAllByText('关键词命中说明.pdf')[0])
        expect(await screen.findByText('关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。该段落适合验证词项召回。')).toBeInTheDocument()
        expect(screen.getByText('knowledge.retrievalDetailMetadata')).toBeInTheDocument()

        const rawHistory = window.localStorage.getItem('opsfactory:knowledge:retrieval-history:src_001:v1')
        expect(rawHistory).toContain('qwen3')
        expect(rawHistory).toContain('"viewMode":"compare"')
        expect(rawHistory).toContain('"viewMode":"lexical"')
    })
})
