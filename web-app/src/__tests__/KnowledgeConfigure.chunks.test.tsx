import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeConfigure from '../pages/KnowledgeConfigure'

const showToast = vi.fn()

vi.mock('react-i18next', () => ({
    initReactI18next: {
        type: '3rdParty',
        init: () => {},
    },
    useTranslation: () => ({
        t: (key: string, params?: Record<string, unknown>) => {
            if (params?.name && params?.error) return `${key}:${String(params.name)}:${String(params.error)}`
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
        previewFile: null,
        isLoading: false,
        error: null,
        openPreview: vi.fn(),
        closePreview: vi.fn(),
        isPreviewable: () => true,
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

type TestDocument = {
    id: string
    sourceId: string
    name: string
    contentType: string
    title: string
    status: string
    indexStatus: string
    fileSizeBytes: number
    createdAt: string
    updatedAt: string
}

type TestChunk = {
    id: string
    documentId: string
    sourceId: string
    ordinal: number
    title: string | null
    titlePath: string[]
    keywords: string[]
    text: string
    markdown: string
    pageFrom: number | null
    pageTo: number | null
    tokenCount: number
    editStatus: string
    updatedBy: string | null
    createdAt: string
    updatedAt: string
}

function buildDocumentResponse(document: TestDocument, chunks: TestChunk[]) {
    const documentChunks = chunks.filter(chunk => chunk.documentId === document.id)

    return {
        ...document,
        chunkCount: documentChunks.length,
        userEditedChunkCount: documentChunks.filter(chunk => chunk.editStatus === 'USER_EDITED').length,
    }
}

function buildChunkSummary(chunk: TestChunk) {
    return {
        id: chunk.id,
        documentId: chunk.documentId,
        sourceId: chunk.sourceId,
        ordinal: chunk.ordinal,
        title: chunk.title,
        titlePath: chunk.titlePath,
        keywords: chunk.keywords,
        snippet: chunk.text.slice(0, 180),
        pageFrom: chunk.pageFrom,
        pageTo: chunk.pageTo,
        tokenCount: chunk.tokenCount,
        editStatus: chunk.editStatus,
        updatedAt: chunk.updatedAt,
    }
}

function buildChunkDetail(chunk: TestChunk) {
    return {
        ...chunk,
        textLength: chunk.text.length,
    }
}

describe('KnowledgeConfigure chunks tab', () => {
    beforeEach(() => {
        vi.clearAllMocks()

        let documents: TestDocument[] = [
            {
                id: 'doc_001',
                sourceId: 'src_001',
                name: 'runbook.pdf',
                contentType: 'application/pdf',
                title: 'Runbook PDF',
                status: 'INDEXED',
                indexStatus: 'INDEXED',
                fileSizeBytes: 1024,
                createdAt: '2026-03-25T10:00:00Z',
                updatedAt: '2026-03-25T10:05:00Z',
            },
            {
                id: 'doc_002',
                sourceId: 'src_001',
                name: 'faq.md',
                contentType: 'text/markdown',
                title: 'FAQ',
                status: 'INDEXED',
                indexStatus: 'INDEXED',
                fileSizeBytes: 2048,
                createdAt: '2026-03-25T11:00:00Z',
                updatedAt: '2026-03-25T11:05:00Z',
            },
        ]

        let chunks: TestChunk[] = [
            {
                id: 'chk_001',
                documentId: 'doc_001',
                sourceId: 'src_001',
                ordinal: 1,
                title: 'Initial Chunk',
                titlePath: ['Initial Chunk'],
                keywords: ['runbook', 'incident'],
                text: 'Initial chunk content for the runbook document.',
                markdown: 'Initial chunk content for the runbook document.',
                pageFrom: 1,
                pageTo: 1,
                tokenCount: 8,
                editStatus: 'SYSTEM_GENERATED',
                updatedBy: 'system',
                createdAt: '2026-03-25T10:05:00Z',
                updatedAt: '2026-03-25T10:05:00Z',
            },
            {
                id: 'chk_002',
                documentId: 'doc_002',
                sourceId: 'src_001',
                ordinal: 1,
                title: 'FAQ Chunk',
                titlePath: ['FAQ Chunk'],
                keywords: ['faq'],
                text: 'FAQ chunk content for the markdown document.',
                markdown: 'FAQ chunk content for the markdown document.',
                pageFrom: 2,
                pageTo: 2,
                tokenCount: 7,
                editStatus: 'USER_EDITED',
                updatedBy: 'system',
                createdAt: '2026-03-25T11:05:00Z',
                updatedAt: '2026-03-25T11:05:00Z',
            },
        ]

        const buildStats = () => ({
            sourceId: 'src_001',
            documentCount: documents.length,
            indexedDocumentCount: documents.length,
            failedDocumentCount: 0,
            processingDocumentCount: 0,
            chunkCount: chunks.length,
            userEditedChunkCount: chunks.filter(chunk => chunk.editStatus === 'USER_EDITED').length,
            lastIngestionAt: '2026-03-25T12:30:00Z',
        })

        vi.stubGlobal('fetch', vi.fn((input: string | URL | Request, init?: RequestInit) => {
            const url = String(input)
            const method = init?.method ?? 'GET'
            const parsedUrl = new URL(url, 'http://127.0.0.1')

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => baseSource,
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001/stats')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => buildStats(),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/capabilities')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        retrievalModes: ['lexical', 'hybrid'],
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
                            mode: 'hybrid',
                            lexicalTopK: 50,
                            semanticTopK: 50,
                            finalTopK: 10,
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
                            retrieval: { mode: 'hybrid', lexicalTopK: 50, semanticTopK: 50, rrfK: 60 },
                        },
                        createdAt: '2026-03-24T10:00:00Z',
                        updatedAt: '2026-03-24T10:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && parsedUrl.pathname.endsWith('/ops-knowledge/documents')) {
                const sourceId = parsedUrl.searchParams.get('sourceId')
                const items = documents
                    .filter(document => !sourceId || document.sourceId === sourceId)
                    .map(document => buildDocumentResponse(document, chunks))

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items,
                        page: 1,
                        pageSize: 100,
                        total: items.length,
                    }),
                } as Response)
            }

            if (method === 'GET' && /\/ops-knowledge\/documents\/[^/]+\/artifacts$/.test(parsedUrl.pathname)) {
                const documentId = parsedUrl.pathname.split('/').at(-2) || ''
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        documentId,
                        markdown: true,
                    }),
                } as Response)
            }

            if (method === 'GET' && parsedUrl.pathname.endsWith('/ops-knowledge/chunks')) {
                const sourceId = parsedUrl.searchParams.get('sourceId')
                const documentId = parsedUrl.searchParams.get('documentId')
                const items = chunks
                    .filter(chunk => (!sourceId || chunk.sourceId === sourceId) && (!documentId || chunk.documentId === documentId))
                    .map(buildChunkSummary)

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items,
                        page: 1,
                        pageSize: 100,
                        total: items.length,
                    }),
                } as Response)
            }

            if (method === 'GET' && /\/ops-knowledge\/chunks\/[^/]+$/.test(parsedUrl.pathname)) {
                const chunkId = parsedUrl.pathname.split('/').at(-1) || ''
                const chunk = chunks.find(item => item.id === chunkId)
                if (!chunk) {
                    return Promise.resolve({
                        ok: false,
                        status: 404,
                        json: async () => ({ message: 'not found' }),
                    } as Response)
                }

                return Promise.resolve({
                    ok: true,
                    json: async () => buildChunkDetail(chunk),
                } as Response)
            }

            if (method === 'POST' && /\/ops-knowledge\/documents\/[^/]+\/chunks$/.test(parsedUrl.pathname)) {
                const documentId = parsedUrl.pathname.split('/').at(-2) || ''
                const payload = JSON.parse(String(init?.body ?? '{}')) as {
                    ordinal: number
                    title?: string | null
                    titlePath?: string[]
                    keywords?: string[]
                    text?: string
                    markdown?: string
                    pageFrom?: number | null
                    pageTo?: number | null
                }
                const newId = `chk_${String(chunks.length + 1).padStart(3, '0')}`

                chunks = [
                    ...chunks,
                    {
                        id: newId,
                        documentId,
                        sourceId: 'src_001',
                        ordinal: payload.ordinal,
                        title: payload.title ?? null,
                        titlePath: payload.titlePath ?? [],
                        keywords: payload.keywords ?? [],
                        text: payload.text ?? '',
                        markdown: payload.markdown ?? payload.text ?? '',
                        pageFrom: payload.pageFrom ?? null,
                        pageTo: payload.pageTo ?? null,
                        tokenCount: 10,
                        editStatus: 'USER_EDITED',
                        updatedBy: 'system',
                        createdAt: '2026-03-25T13:00:00Z',
                        updatedAt: '2026-03-25T13:00:00Z',
                    },
                ]

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: newId,
                        documentId,
                        reembedded: true,
                        reindexed: true,
                        editStatus: 'USER_EDITED',
                        updatedAt: '2026-03-25T13:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'PATCH' && /\/ops-knowledge\/chunks\/[^/]+$/.test(parsedUrl.pathname)) {
                const chunkId = parsedUrl.pathname.split('/').at(-1) || ''
                const payload = JSON.parse(String(init?.body ?? '{}')) as {
                    keywords?: string[]
                    text?: string
                    markdown?: string
                }

                chunks = chunks.map(chunk => chunk.id === chunkId
                    ? {
                        ...chunk,
                        keywords: payload.keywords ?? chunk.keywords,
                        text: payload.text ?? chunk.text,
                        markdown: payload.markdown ?? chunk.markdown,
                        editStatus: 'USER_EDITED',
                        updatedAt: '2026-03-25T13:10:00Z',
                    }
                    : chunk
                )

                const updatedChunk = chunks.find(chunk => chunk.id === chunkId)!

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: chunkId,
                        documentId: updatedChunk.documentId,
                        reembedded: true,
                        reindexed: true,
                        editStatus: 'USER_EDITED',
                        updatedAt: updatedChunk.updatedAt,
                    }),
                } as Response)
            }

            if (method === 'DELETE' && /\/ops-knowledge\/chunks\/[^/]+$/.test(parsedUrl.pathname)) {
                const chunkId = parsedUrl.pathname.split('/').at(-1) || ''
                chunks = chunks.filter(chunk => chunk.id !== chunkId)

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        chunkId,
                        deleted: true,
                    }),
                } as Response)
            }

            return Promise.resolve({
                ok: false,
                status: 404,
                json: async () => ({ message: 'not found' }),
            } as Response)
        }))
    })

    it('jumps from a document row into the chunks tab with document filtering', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        fireEvent.click(screen.getAllByRole('button', { name: 'knowledge.docViewChunks' })[0])

        await screen.findByText('knowledge.chunksTabTitle')
        expect(screen.getByText('Initial Chunk')).toBeInTheDocument()
        expect(screen.queryByText('FAQ Chunk')).not.toBeInTheDocument()
        expect(screen.getAllByText('Runbook PDF').length).toBeGreaterThan(0)
    })

    it('creates, edits, and deletes chunks from the chunks tab', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=chunks']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.chunksTabTitle')
        expect(screen.getByText('Initial Chunk')).toBeInTheDocument()
        expect(screen.getByText('FAQ Chunk')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: /Initial Chunk/ }))
        await screen.findByDisplayValue('Initial chunk content for the runbook document.')

        fireEvent.change(screen.getByLabelText('knowledge.chunkKeywordsLabel'), {
            target: { value: 'manual-keyword, incident-custom' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.chunkContentTitle'), {
            target: { value: 'Updated runbook content with manual edits.' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'common.save' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.chunkSaveSuccess')
        })

        await screen.findByDisplayValue('Updated runbook content with manual edits.')
        expect(vi.mocked(fetch)).toHaveBeenCalledWith(
            'http://127.0.0.1:8092/ops-knowledge/chunks/chk_001',
            expect.objectContaining({
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    keywords: ['runbook', 'incident', 'manual-keyword', 'incident-custom'],
                    text: 'Updated runbook content with manual edits.',
                    markdown: 'Updated runbook content with manual edits.',
                }),
            }),
        )

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.chunkCreate' }))
        await screen.findByText('knowledge.chunkCreateTitle')

        fireEvent.change(screen.getByLabelText('knowledge.chunkDocumentLabel'), {
            target: { value: 'doc_001' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.chunkKeywordsLabel'), {
            target: { value: 'manual-only-term' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.chunkContentTitle'), {
            target: { value: 'Manual validation chunk content for operators.' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'common.save' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.chunkCreateSuccess')
        })

        expect(await screen.findByDisplayValue('Manual validation chunk content for operators.')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'common.delete' }))
        await screen.findByText('knowledge.chunkDeleteTitle')
        fireEvent.click(screen.getAllByRole('button', { name: 'common.delete' }).at(-1)!)

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.chunkDeleteSuccess')
        })

        expect(screen.queryByDisplayValue('Manual validation chunk content for operators.')).not.toBeInTheDocument()
    })
})
