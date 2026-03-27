import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeConfigure from '../pages/KnowledgeConfigure'

const showToast = vi.fn()
const compareRequests: Array<Record<string, unknown>> = []
let allowRequestOverride = true

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
        compareRequests.length = 0
        allowRequestOverride = true
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
                        chunkModes: ['hierarchical'],
                        expandModes: ['ordinal_neighbors'],
                        analyzers: ['smartcn'],
                        editableChunkFields: ['title', 'keywords', 'text'],
                        featureFlags: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride,
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
                            rrfK: 60,
                        },
                        features: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride,
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
                            retrieval: { mode: 'semantic', lexicalTopK: 50, semanticTopK: 50, rrfK: 60 },
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

            if (method === 'POST' && url.endsWith('/ops-knowledge/search/compare')) {
                const requestBody = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>
                compareRequests.push(requestBody)

                if (requestBody.query === 'multi-hit') {
                    return Promise.resolve({
                        ok: true,
                        json: async () => ({
                            query: requestBody.query,
                            fetchedTopK: 64,
                            hybrid: {
                                total: 2,
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
                                    {
                                        chunkId: 'chk_hyb_002',
                                        documentId: 'doc_hybrid',
                                        sourceId: 'src_001',
                                        title: '混合补充',
                                        titlePath: ['召回评估', '混合补充'],
                                        snippet: '第二条混合结果用于验证本地展示数量裁剪。',
                                        score: 0.62,
                                        lexicalScore: 0.44,
                                        semanticScore: 0.61,
                                        fusionScore: 0.62,
                                        pageFrom: 7,
                                        pageTo: 7,
                                    },
                                ],
                            },
                            semantic: {
                                total: 2,
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
                                    {
                                        chunkId: 'chk_sem_002',
                                        documentId: 'doc_semantic',
                                        sourceId: 'src_001',
                                        title: '语义补充',
                                        titlePath: ['召回评估', '语义补充'],
                                        snippet: '第二条语义结果用于验证前端本地过滤。',
                                        score: 0.52,
                                        lexicalScore: 0.12,
                                        semanticScore: 0.52,
                                        fusionScore: 0.52,
                                        pageFrom: 4,
                                        pageTo: 4,
                                    },
                                ],
                            },
                            lexical: {
                                total: 2,
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
                                    {
                                        chunkId: 'chk_lex_002',
                                        documentId: 'doc_lexical',
                                        sourceId: 'src_001',
                                        title: '关键词补充',
                                        titlePath: ['召回评估', '关键词补充'],
                                        snippet: '第二条关键词结果用于验证展示数量变化。',
                                        score: 0.45,
                                        lexicalScore: 0.45,
                                        semanticScore: 0.11,
                                        fusionScore: 0.45,
                                        pageFrom: 9,
                                        pageTo: 9,
                                    },
                                ],
                            },
                        }),
                    } as Response)
                }

                if (requestBody.query === 'identical-zero') {
                    return Promise.resolve({
                        ok: true,
                        json: async () => ({
                            query: requestBody.query,
                            fetchedTopK: 64,
                            hybrid: {
                                total: 1,
                                hits: [
                                    {
                                        chunkId: 'chk_shared_001',
                                        documentId: 'doc_hybrid',
                                        sourceId: 'src_001',
                                        title: '共享结果',
                                        titlePath: ['召回评估', '共享结果'],
                                        snippet: '三种模式都返回了同一个 chunk，用来验证比较模式告警。',
                                        score: 0.61,
                                        lexicalScore: 0.61,
                                        semanticScore: 0,
                                        fusionScore: 0.61,
                                        pageFrom: 2,
                                        pageTo: 2,
                                    },
                                ],
                            },
                            semantic: {
                                total: 1,
                                hits: [
                                    {
                                        chunkId: 'chk_shared_001',
                                        documentId: 'doc_hybrid',
                                        sourceId: 'src_001',
                                        title: '共享结果',
                                        titlePath: ['召回评估', '共享结果'],
                                        snippet: '三种模式都返回了同一个 chunk，用来验证比较模式告警。',
                                        score: 0.61,
                                        lexicalScore: 0.61,
                                        semanticScore: 0,
                                        fusionScore: 0.61,
                                        pageFrom: 2,
                                        pageTo: 2,
                                    },
                                ],
                            },
                            lexical: {
                                total: 1,
                                hits: [
                                    {
                                        chunkId: 'chk_shared_001',
                                        documentId: 'doc_hybrid',
                                        sourceId: 'src_001',
                                        title: '共享结果',
                                        titlePath: ['召回评估', '共享结果'],
                                        snippet: '三种模式都返回了同一个 chunk，用来验证比较模式告警。',
                                        score: 0.61,
                                        lexicalScore: 0.61,
                                        semanticScore: 0,
                                        fusionScore: 0.61,
                                        pageFrom: 2,
                                        pageTo: 2,
                                    },
                                ],
                            },
                        }),
                    } as Response)
                }

                if (requestBody.query === 'no-hit') {
                    return Promise.resolve({
                        ok: true,
                        json: async () => ({
                            query: requestBody.query,
                            fetchedTopK: 64,
                            hybrid: { total: 0, hits: [] },
                            semantic: { total: 0, hits: [] },
                            lexical: { total: 0, hits: [] },
                        }),
                    } as Response)
                }

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        query: requestBody.query,
                        fetchedTopK: 64,
                        hybrid: {
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
                    }),
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

    it('supports compare mode, test-only tuning, and inline detail inspection', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        const queryInput = screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }) as HTMLTextAreaElement

        fireEvent.change(queryInput, {
            target: { value: 'qwen3' },
        })
        expect(queryInput.value).toBe('qwen3')
        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'knowledge.retrievalRun' })).not.toBeDisabled()
        })

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        expect(await screen.findAllByText('混合检索总结.pdf')).toHaveLength(1)
        expect(await screen.findByText('向量召回评估.pdf')).toBeInTheDocument()
        expect(await screen.findByText('关键词命中说明.pdf')).toBeInTheDocument()

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        expect(compareRequests[0]?.modes).toEqual(['hybrid', 'semantic', 'lexical'])
        expect(screen.getByRole('button', { name: 'knowledge.retrievalRunCurrent' })).toBeDisabled()

        fireEvent.change(screen.getByLabelText('knowledge.retrievalDisplayCountLabel'), {
            target: { value: '5' },
        })
        expect(compareRequests).toHaveLength(1)
        expect(screen.getByRole('button', { name: 'knowledge.retrievalRunCurrent' })).toBeDisabled()

        fireEvent.click(screen.getAllByText('关键词命中说明.pdf')[0])
        expect(await screen.findByText('关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。该段落适合验证词项召回。')).toBeInTheDocument()
        expect(screen.getByText('knowledge.retrievalDetailMetadata')).toBeInTheDocument()

        const rawHistory = window.localStorage.getItem('opsfactory:knowledge:retrieval-history:src_001:v1')
        expect(rawHistory).toContain('qwen3')
        expect(rawHistory).not.toContain('"displayCount"')
        expect(rawHistory).not.toContain('"scoreThreshold"')
    })

    it('falls back to the bound mode when request override is disabled', async () => {
        allowRequestOverride = false

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')
        expect(screen.queryByRole('button', { name: 'knowledge.retrievalViewCompare' })).not.toBeInTheDocument()

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'qwen3' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        expect(compareRequests[0]?.retrievalProfileId).toBe('rp_default')
        expect(compareRequests[0]?.modes).toEqual(['semantic'])
    })

    it('filters compare results locally without calling the backend again', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'qwen3 threshold' },
        })

        const [semanticToggle, lexicalToggle] = screen.getAllByRole('checkbox') as HTMLInputElement[]
        expect(lexicalToggle.checked).toBe(true)
        expect(semanticToggle.checked).toBe(true)
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        fireEvent.change(screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel'), {
            target: { value: '0.95' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel'), {
            target: { value: '0.95' },
        })

        expect(compareRequests).toHaveLength(1)
        expect(await screen.findAllByText('knowledge.retrievalNoResultsThreshold')).toHaveLength(2)
        expect(screen.getByText('混合检索总结.pdf')).toBeInTheDocument()
    })

    it('restores locally filtered semantic results when semantic threshold is turned off without calling the backend again', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'qwen3 threshold toggle' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        fireEvent.change(screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel'), {
            target: { value: '0.95' },
        })

        expect(await screen.findAllByText('knowledge.retrievalNoResultsThreshold')).toHaveLength(1)

        const semanticToggle = (screen.getAllByRole('checkbox') as HTMLInputElement[])[0]
        fireEvent.click(semanticToggle)

        expect(compareRequests).toHaveLength(1)
        expect(await screen.findByText('混合检索总结.pdf')).toBeInTheDocument()
        expect(screen.getByText('向量召回评估.pdf')).toBeInTheDocument()
        expect(screen.getByText('关键词命中说明.pdf')).toBeInTheDocument()
    })

    it('updates displayed result count locally without calling the backend again', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'multi-hit' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        expect(await screen.findByText('混合补充')).toBeInTheDocument()
        expect(screen.getByText('语义补充')).toBeInTheDocument()
        expect(screen.getByText('关键词补充')).toBeInTheDocument()

        fireEvent.change(screen.getByLabelText('knowledge.retrievalDisplayCountLabel'), {
            target: { value: '1' },
        })

        expect(compareRequests).toHaveLength(1)
        await waitFor(() => {
            expect(screen.queryByText('混合补充')).not.toBeInTheDocument()
            expect(screen.queryByText('语义补充')).not.toBeInTheDocument()
            expect(screen.queryByText('关键词补充')).not.toBeInTheDocument()
        })
    })

    it('re-enables test and issues a new compare request when the query changes', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        const queryInput = screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' })

        fireEvent.change(queryInput, { target: { value: 'first-query' } })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })
        expect(screen.getByRole('button', { name: 'knowledge.retrievalRunCurrent' })).toBeDisabled()

        fireEvent.change(queryInput, { target: { value: 'second-query' } })

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'knowledge.retrievalRun' })).not.toBeDisabled()
        })

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(2)
        })
        expect(compareRequests[1]?.query).toBe('second-query')
    })

    it('shows compare diagnostics when all modes return identical zero-semantic results', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'identical-zero' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        expect(await screen.findByText('knowledge.retrievalCompareWarningSemanticInactive')).toBeInTheDocument()
        expect(screen.getAllByText('knowledge.retrievalLexicalScoreLabel 0.61')).toHaveLength(1)
        expect(screen.getAllByText('knowledge.retrievalSemanticScoreLabel 0.00')).toHaveLength(1)
        expect(screen.getAllByText('knowledge.retrievalFusionScoreLabel 0.61')).toHaveLength(1)
    })

    it('replays retrieval history without overriding the current local filter settings', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')
        const [semanticToggle, lexicalToggle] = screen.getAllByRole('checkbox') as HTMLInputElement[]
        expect(lexicalToggle.checked).toBe(true)
        expect(semanticToggle.checked).toBe(true)
        expect((screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel') as HTMLInputElement).value).toBe('0.3')
        expect((screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel') as HTMLInputElement).value).toBe('0.3')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'history-check' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel'), {
            target: { value: '0.77' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'different query' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel'), {
            target: { value: '0.33' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel'), {
            target: { value: '0.66' },
        })

        fireEvent.click(screen.getByRole('button', { name: /history-check/i }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        expect((screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel') as HTMLInputElement).value).toBe('0.33')
        expect((screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel') as HTMLInputElement).value).toBe('0.66')
        expect((screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }) as HTMLTextAreaElement).value).toBe('history-check')
    })

    it('replays cached history for the same query without calling compare again', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'cached-history' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'different-query' },
        })

        fireEvent.click(screen.getByRole('button', { name: /cached-history/i }))

        await waitFor(() => {
            expect((screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }) as HTMLTextAreaElement).value).toBe('cached-history')
        })
        expect(compareRequests).toHaveLength(1)
        expect(screen.getByRole('button', { name: 'knowledge.retrievalRunCurrent' })).toBeDisabled()
    })

    it('restores cached compare results and local filter settings after leaving and re-entering the retrieval tab', async () => {
        const firstRender = render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'persisted-query' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        expect(await screen.findByText('混合检索总结.pdf')).toBeInTheDocument()

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        fireEvent.change(screen.getByLabelText('knowledge.retrievalDisplayCountLabel'), {
            target: { value: '1' },
        })
        const lexicalPersistToggle = (screen.getAllByRole('checkbox') as HTMLInputElement[])[1]
        fireEvent.click(lexicalPersistToggle)
        fireEvent.change(screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel'), {
            target: { value: '0.95' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel'), {
            target: { value: '0.88' },
        })

        firstRender.unmount()

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        expect((screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }) as HTMLTextAreaElement).value).toBe('persisted-query')
        expect(screen.getByText('混合检索总结.pdf')).toBeInTheDocument()
        expect(screen.getByText('关键词命中说明.pdf')).toBeInTheDocument()
        expect(screen.getByText('knowledge.retrievalNoResultsThreshold')).toBeInTheDocument()
        expect((screen.getByLabelText('knowledge.retrievalDisplayCountLabel') as HTMLInputElement).value).toBe('1')
        expect((screen.getAllByRole('checkbox') as HTMLInputElement[])[1].checked).toBe(false)
        expect((screen.getAllByRole('checkbox') as HTMLInputElement[])[0].checked).toBe(true)
        expect((screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel') as HTMLInputElement).value).toBe('0.95')
        expect((screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel') as HTMLInputElement).value).toBe('0.88')
        expect(compareRequests).toHaveLength(1)
        expect(screen.getByRole('button', { name: 'knowledge.retrievalRunCurrent' })).toBeDisabled()
    })

    it('keeps test-only controls available when request override is disabled', async () => {
        allowRequestOverride = false

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        const [semanticDisabledToggle, lexicalDisabledToggle] = screen.getAllByRole('checkbox') as HTMLInputElement[]
        expect(lexicalDisabledToggle).not.toBeDisabled()
        expect(semanticDisabledToggle).not.toBeDisabled()
        expect(screen.getByLabelText('knowledge.retrievalLexicalThresholdLabel')).not.toBeDisabled()
        expect(screen.getByLabelText('knowledge.retrievalSemanticThresholdLabel')).not.toBeDisabled()
    })

    it('shows backend empty-state results without client-side filtering', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'no-hit' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await waitFor(() => {
            expect(compareRequests).toHaveLength(1)
        })

        expect(await screen.findAllByText('knowledge.retrievalNoResults')).toHaveLength(3)
    })

    it('falls back to legacy search requests when compare endpoint is unavailable', async () => {
        compareRequests.length = 0
        vi.stubGlobal('fetch', vi.fn((input: string | URL | Request, init?: RequestInit) => {
            const url = String(input)
            const method = init?.method ?? 'GET'

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({ ok: true, json: async () => baseSource } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001/stats')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    sourceId: 'src_001', documentCount: 12, indexedDocumentCount: 10, failedDocumentCount: 1, processingDocumentCount: 1, chunkCount: 234, userEditedChunkCount: 3, lastIngestionAt: '2026-03-25T12:30:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/capabilities')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    retrievalModes: ['semantic', 'lexical', 'hybrid'],
                    chunkModes: ['hierarchical'],
                    expandModes: ['ordinal_neighbors'],
                    analyzers: ['smartcn'],
                    editableChunkFields: ['title', 'keywords', 'text'],
                    featureFlags: { allowChunkEdit: true, allowChunkDelete: true, allowExplain: true, allowRequestOverride: true },
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/system/defaults')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    retrieval: { mode: 'semantic', lexicalTopK: 50, semanticTopK: 50, finalTopK: 3, rrfK: 60 },
                    chunking: { mode: 'hierarchical', targetTokens: 512, overlapTokens: 64, respectHeadings: true, keepTablesWhole: true },
                    ingest: { maxFileSizeMb: 100, allowedContentTypes: ['application/pdf'], deduplication: 'sha256', skipExistingByDefault: true },
                    features: { allowChunkEdit: true, allowChunkDelete: true, allowExplain: true, allowRequestOverride: true },
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/index/ip_default')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    id: 'ip_default', name: '默认索引配置', config: { chunking: { mode: 'hierarchical', targetTokens: 512 } }, createdAt: '2026-03-24T10:00:00Z', updatedAt: '2026-03-24T10:00:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    id: 'rp_default', name: '默认召回配置', config: { retrieval: { mode: 'semantic', lexicalTopK: 50, semanticTopK: 50, rrfK: 60 }, result: { finalTopK: 3, snippetLength: 180 } }, createdAt: '2026-03-24T10:00:00Z', updatedAt: '2026-03-24T10:00:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/documents?sourceId=src_001&page=1&pageSize=100')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items: [
                            { id: 'doc_hybrid', sourceId: 'src_001', name: '混合检索总结.pdf', contentType: 'application/pdf', title: '混合检索总结', status: 'INDEXED', indexStatus: 'INDEXED', fileSizeBytes: 1024, chunkCount: 8, userEditedChunkCount: 0, createdAt: '2026-03-25T10:00:00Z', updatedAt: '2026-03-25T10:05:00Z' },
                            { id: 'doc_semantic', sourceId: 'src_001', name: '向量召回评估.pdf', contentType: 'application/pdf', title: '向量召回评估', status: 'INDEXED', indexStatus: 'INDEXED', fileSizeBytes: 2048, chunkCount: 6, userEditedChunkCount: 0, createdAt: '2026-03-25T10:00:00Z', updatedAt: '2026-03-25T10:05:00Z' },
                            { id: 'doc_lexical', sourceId: 'src_001', name: '关键词命中说明.pdf', contentType: 'application/pdf', title: '关键词命中说明', status: 'INDEXED', indexStatus: 'INDEXED', fileSizeBytes: 2048, chunkCount: 4, userEditedChunkCount: 0, createdAt: '2026-03-25T10:00:00Z', updatedAt: '2026-03-25T10:05:00Z' },
                        ],
                        page: 1,
                        pageSize: 100,
                        total: 3,
                    }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search/compare')) {
                compareRequests.push(JSON.parse(String(init?.body || '{}')) as Record<string, unknown>)
                return Promise.resolve({
                    ok: false,
                    status: 404,
                    json: async () => ({ message: 'not found' }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search')) {
                const body = JSON.parse(String(init?.body || '{}')) as Record<string, any>
                const mode = body.override?.mode as string
                const hit = mode === 'hybrid'
                    ? {
                        chunkId: 'chk_hyb_001', documentId: 'doc_hybrid', sourceId: 'src_001', title: '混合结论', titlePath: ['召回评估', '混合结论'], snippet: '混合检索把语义相关和关键词命中放在一起，整体排序更均衡。', score: 0.92, lexicalScore: 0.74, semanticScore: 0.88, fusionScore: 0.92, pageFrom: 6, pageTo: 6,
                    }
                    : mode === 'semantic'
                        ? {
                            chunkId: 'chk_sem_001', documentId: 'doc_semantic', sourceId: 'src_001', title: '语义覆盖', titlePath: ['召回评估', '语义覆盖'], snippet: '向量召回覆盖更多语义近邻段落，适合问题表达不稳定的场景。', score: 0.81, lexicalScore: 0.32, semanticScore: 0.81, fusionScore: 0.81, pageFrom: 3, pageTo: 3,
                        }
                        : {
                            chunkId: 'chk_lex_001', documentId: 'doc_lexical', sourceId: 'src_001', title: '精确命中', titlePath: ['召回评估', '精确命中'], snippet: '关键词检索优先命中包含 Qwen3-32B 精确词项的结论段落。', score: 0.76, lexicalScore: 0.76, semanticScore: 0.24, fusionScore: 0.76, pageFrom: 8, pageTo: 8,
                        }
                return Promise.resolve({
                    ok: true,
                    json: async () => ({ query: body.query, total: 1, hits: [hit] }),
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/fetch/')) {
                const match = url.match(/\/ops-knowledge\/fetch\/([^?]+)/)
                const chunkId = match?.[1] as keyof typeof detailByChunkId | undefined
                if (chunkId && detailByChunkId[chunkId]) {
                    return Promise.resolve({ ok: true, json: async () => detailByChunkId[chunkId] } as Response)
                }
            }

            return Promise.resolve({ ok: false, status: 404, json: async () => ({ message: 'not found' }) } as Response)
        }))

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'ITSM' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        expect(await screen.findByText('混合检索总结.pdf')).toBeInTheDocument()
        expect(screen.getByText('向量召回评估.pdf')).toBeInTheDocument()
        expect(screen.getByText('关键词命中说明.pdf')).toBeInTheDocument()
        expect(compareRequests).toHaveLength(1)
    })

    it('shows backend compare errors directly without falling back to legacy search', async () => {
        compareRequests.length = 0
        vi.stubGlobal('fetch', vi.fn((input: string | URL | Request, init?: RequestInit) => {
            const url = String(input)
            const method = init?.method ?? 'GET'

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({ ok: true, json: async () => baseSource } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001/stats')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    sourceId: 'src_001', documentCount: 12, indexedDocumentCount: 10, failedDocumentCount: 1, processingDocumentCount: 1, chunkCount: 234, userEditedChunkCount: 3, lastIngestionAt: '2026-03-25T12:30:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/capabilities')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    retrievalModes: ['semantic', 'lexical', 'hybrid'],
                    chunkModes: ['hierarchical'],
                    expandModes: ['ordinal_neighbors'],
                    analyzers: ['smartcn'],
                    editableChunkFields: ['title', 'keywords', 'text'],
                    featureFlags: { allowChunkEdit: true, allowChunkDelete: true, allowExplain: true, allowRequestOverride: true },
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/system/defaults')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    retrieval: { mode: 'semantic', lexicalTopK: 50, semanticTopK: 50, finalTopK: 3, rrfK: 60 },
                    chunking: { mode: 'hierarchical', targetTokens: 512, overlapTokens: 64, respectHeadings: true, keepTablesWhole: true },
                    ingest: { maxFileSizeMb: 100, allowedContentTypes: ['application/pdf'], deduplication: 'sha256', skipExistingByDefault: true },
                    features: { allowChunkEdit: true, allowChunkDelete: true, allowExplain: true, allowRequestOverride: true },
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/index/ip_default')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    id: 'ip_default', name: '默认索引配置', config: { chunking: { mode: 'hierarchical', targetTokens: 512 } }, createdAt: '2026-03-24T10:00:00Z', updatedAt: '2026-03-24T10:00:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                return Promise.resolve({ ok: true, json: async () => ({
                    id: 'rp_default', name: '默认召回配置', config: { retrieval: { mode: 'semantic', lexicalTopK: 50, semanticTopK: 50, rrfK: 60 }, result: { finalTopK: 3, snippetLength: 180 } }, createdAt: '2026-03-24T10:00:00Z', updatedAt: '2026-03-24T10:00:00Z',
                }) } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/documents?sourceId=src_001&page=1&pageSize=100')) {
                return Promise.resolve({ ok: true, json: async () => ({ items: [], page: 1, pageSize: 100, total: 0 }) } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search/compare')) {
                compareRequests.push(JSON.parse(String(init?.body || '{}')) as Record<string, unknown>)
                return Promise.resolve({
                    ok: false,
                    status: 500,
                    json: async () => ({ message: 'Embedding dimension mismatch' }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search')) {
                throw new Error('legacy search should not be called for non-404 compare errors')
            }

            return Promise.resolve({ ok: false, status: 404, json: async () => ({ message: 'not found' }) } as Response)
        }))

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByRole('textbox', { name: 'knowledge.retrievalQueryLabel' }), {
            target: { value: 'ITSM' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        expect(await screen.findByText('common.connectionError:Embedding dimension mismatch')).toBeInTheDocument()
        expect(showToast).toHaveBeenCalledWith('error', 'Embedding dimension mismatch')
        expect(compareRequests).toHaveLength(1)
    })
})
