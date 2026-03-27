import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeConfigure from '../pages/KnowledgeConfigure'

const showToast = vi.fn()
const openPreview = vi.fn()
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
        openPreview,
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

let downloadOriginalResponse: () => Promise<Response>

describe('KnowledgeConfigure page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        let indexProfileDetail = {
            id: 'ip_default',
            name: '默认索引配置',
            config: {
                convert: { engine: 'tika' },
                analysis: { language: 'zh', indexAnalyzer: 'smartcn', queryAnalyzer: 'smartcn' },
                chunking: { mode: 'hierarchical', targetTokens: 512 },
                indexing: {
                    titleBoost: 4,
                    titlePathBoost: 2.5,
                    keywordBoost: 2,
                    contentBoost: 1,
                    bm25: { k1: 1.2, b: 0.75 },
                },
            },
            createdAt: '2026-03-24T10:00:00Z',
            updatedAt: '2026-03-24T10:00:00Z',
        }
        let retrievalProfileDetail = {
            id: 'rp_default',
            name: '默认召回配置',
            config: {
                retrieval: { mode: 'hybrid', lexicalTopK: 50, semanticTopK: 50, rrfK: 60 },
                result: { finalTopK: 10, snippetLength: 180 },
            },
            createdAt: '2026-03-24T10:00:00Z',
            updatedAt: '2026-03-24T10:00:00Z',
        }
        let documents = [
            {
                id: 'doc_001',
                sourceId: 'src_001',
                name: 'runbook.pdf',
                contentType: 'application/pdf',
                title: 'Runbook PDF',
                status: 'INDEXED',
                indexStatus: 'INDEXED',
                fileSizeBytes: 1024,
                chunkCount: 8,
                userEditedChunkCount: 0,
                createdAt: '2026-03-25T10:00:00Z',
                updatedAt: '2026-03-25T10:05:00Z',
            },
        ]
        downloadOriginalResponse = () => Promise.resolve({
            ok: true,
            headers: new Headers({
                'Content-Disposition': 'attachment; filename="runbook.pdf"',
            }),
            blob: async () => new Blob(['pdf-bytes']),
        } as Response)
        Object.assign(globalThis.URL, {
            createObjectURL: vi.fn(() => 'blob:runbook'),
            revokeObjectURL: vi.fn(),
        })

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
                    json: async () => indexProfileDetail,
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => retrievalProfileDetail,
                } as Response)
            }

            if (method === 'PATCH' && url.endsWith('/ops-knowledge/profiles/index/ip_default')) {
                const body = JSON.parse(String(init?.body || '{}'))
                indexProfileDetail = {
                    ...indexProfileDetail,
                    name: body.name ?? indexProfileDetail.name,
                    config: {
                        ...indexProfileDetail.config,
                        ...body.config,
                    },
                    updatedAt: '2026-03-25T13:10:00Z',
                }
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'ip_default',
                        name: indexProfileDetail.name,
                        updatedAt: indexProfileDetail.updatedAt,
                    }),
                } as Response)
            }

            if (method === 'PATCH' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                const body = JSON.parse(String(init?.body || '{}'))
                retrievalProfileDetail = {
                    ...retrievalProfileDetail,
                    name: body.name ?? retrievalProfileDetail.name,
                    config: {
                        ...retrievalProfileDetail.config,
                        ...body.config,
                    },
                    updatedAt: '2026-03-25T13:12:00Z',
                }
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'rp_default',
                        name: retrievalProfileDetail.name,
                        updatedAt: retrievalProfileDetail.updatedAt,
                    }),
                } as Response)
            }

            if (method === 'PATCH' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        ...baseSource,
                        name: '产品文档库 v2',
                        status: 'DISABLED',
                        retrievalProfileId: 'rp_strict',
                        updatedAt: '2026-03-25T13:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/sources/src_001:rebuild')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        jobId: 'job_001',
                        sourceId: 'src_001',
                        status: 'SUCCEEDED',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/documents?sourceId=src_001&page=1&pageSize=100')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items: documents,
                        page: 1,
                        pageSize: 100,
                        total: documents.length,
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/documents/doc_001/artifacts')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        documentId: 'doc_001',
                        markdown: true,
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/documents/doc_001/preview')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        documentId: 'doc_001',
                        title: 'Runbook PDF',
                        markdownPreview: '# Runbook PDF',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/documents/doc_001/original')) {
                return downloadOriginalResponse()
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/sources/src_001/documents:ingest')) {
                documents = [
                    {
                        id: 'doc_002',
                        sourceId: 'src_001',
                        name: 'guide.pdf',
                        contentType: 'application/pdf',
                        title: 'Guide PDF',
                        status: 'UPLOADED',
                        indexStatus: 'PENDING',
                        fileSizeBytes: 2048,
                        chunkCount: 0,
                        userEditedChunkCount: 0,
                        createdAt: '2026-03-25T10:06:00Z',
                        updatedAt: '2026-03-25T10:06:00Z',
                    },
                    ...documents,
                ]

                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        documentCount: 1,
                    }),
                } as Response)
            }

            if (method === 'PATCH' && url.endsWith('/ops-knowledge/documents/doc_001')) {
                const payload = JSON.parse(String(init?.body ?? '{}')) as { title?: string }
                documents = documents.map(document => (
                    document.id === 'doc_001'
                        ? {
                            ...document,
                            title: payload.title ?? document.title,
                            updatedAt: '2026-03-25T10:10:00Z',
                        }
                        : document
                ))
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'doc_001',
                        updated: true,
                        updatedAt: '2026-03-25T10:10:00Z',
                    }),
                } as Response)
            }

            if (method === 'DELETE' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        sourceId: 'src_001',
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

    it('renders basic info and saves edited source settings', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByRole('heading', { name: '产品文档库' })
        expect(screen.getByText('knowledge.tabBasicInfo')).toBeInTheDocument()
        expect(screen.getByText('knowledge.basicInfoTitle')).toBeInTheDocument()
        expect(screen.getByText('234')).toBeInTheDocument()
        expect(screen.queryByText('knowledge.sourceId')).not.toBeInTheDocument()
        expect(screen.queryByText('knowledge.statusLabel')).not.toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.editBasicInfo' }))
        await screen.findByText('knowledge.editBasicInfoTitle')

        fireEvent.change(screen.getByLabelText('knowledge.name'), {
            target: { value: '产品文档库 v2' },
        })
        fireEvent.change(screen.getByLabelText('knowledge.description'), {
            target: { value: '新的描述' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'common.save' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.saveSuccess:产品文档库 v2')
        })

        expect(await screen.findByRole('heading', { name: '产品文档库 v2' })).toBeInTheDocument()

        const fetchMock = vi.mocked(fetch)
        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8092/ops-knowledge/sources/src_001',
            expect.objectContaining({
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
            }),
        )
    })

    it('deletes the current source and navigates back to the knowledge list', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001']}>
                <Routes>
                    <Route path="/knowledge" element={<div>knowledge-list</div>} />
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByRole('heading', { name: '产品文档库' })
        fireEvent.click(screen.getByRole('button', { name: 'common.delete' }))
        await screen.findByRole('heading', { name: 'knowledge.deleteTitle' })

        fireEvent.click(screen.getAllByRole('button', { name: 'common.delete' }).at(-1)!)

        await screen.findByText('knowledge-list')
        expect(showToast).toHaveBeenCalledWith('success', 'knowledge.deleteSuccess:产品文档库')
    })

    it('submits a source rebuild request from the maintenance panel', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByRole('heading', { name: '产品文档库' })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.rebuildAction' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.rebuildSuccess:产品文档库')
        })

        expect(fetch).toHaveBeenCalledWith(
            'http://127.0.0.1:8092/ops-knowledge/sources/src_001:rebuild',
            expect.objectContaining({
                method: 'POST',
            }),
        )
    })

    it('renders config editors and saves profile changes', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=config']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.currentBindingsTitle')
        expect(screen.getByText('knowledge.indexProfileEditorTitle')).toBeInTheDocument()
        expect(screen.getByText('knowledge.retrievalProfileEditorTitle')).toBeInTheDocument()
        expect(screen.getAllByText('默认索引配置').length).toBeGreaterThan(0)
        expect(screen.getAllByText('默认召回配置').length).toBeGreaterThan(0)
        expect(screen.getAllByRole('button', { name: 'knowledge.editConfig' }).length).toBe(2)
        expect(screen.getAllByText('knowledge.configSourceBoundProfile').length).toBe(2)
        expect(screen.getAllByText('knowledge.configSourceLabel').length).toBeGreaterThan(0)

        fireEvent.click(screen.getAllByRole('button', { name: 'knowledge.editConfig' })[0])
        await screen.findByDisplayValue('默认索引配置')

        fireEvent.change(screen.getByDisplayValue('默认索引配置'), {
            target: { value: '索引配置 v2' },
        })
        fireEvent.change(screen.getByDisplayValue('4'), {
            target: { value: '5' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.saveConfig' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.configSaveSuccess')
        })

        expect(fetch).toHaveBeenCalledWith(
            'http://127.0.0.1:8092/ops-knowledge/profiles/index/ip_default',
            expect.objectContaining({
                method: 'PATCH',
            }),
        )
    })

    it('renders documents tab with upload modal', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        expect(screen.getByText('runbook.pdf')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.docUpload' }))
        await screen.findByText('knowledge.uploadTitle:产品文档库')
    })

    it('keeps the upload modal open after a successful import until the user closes it', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.docUpload' }))
        await screen.findByText('knowledge.uploadTitle:产品文档库')

        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement | null
        expect(fileInput).not.toBeNull()

        fireEvent.change(fileInput!, {
            target: {
                files: [new File(['pdf'], 'guide.pdf', { type: 'application/pdf' })],
            },
        })

        expect(await screen.findByText('guide.pdf')).toBeInTheDocument()
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.uploadStart' }))

        await waitFor(() => {
            expect(fetch).toHaveBeenCalledWith(
                'http://127.0.0.1:8092/ops-knowledge/sources/src_001/documents:ingest',
                expect.objectContaining({
                    method: 'POST',
                    body: expect.any(FormData),
                }),
            )
        })

        await screen.findAllByText('knowledge.uploadItemCompleted')
        expect(screen.getByRole('button', { name: 'knowledge.uploadClose' })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'knowledge.uploadContinue' })).toBeInTheDocument()
        expect(screen.getByText('Guide PDF')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.uploadClose' }))

        await waitFor(() => {
            expect(screen.queryByText('knowledge.uploadTitle:产品文档库')).not.toBeInTheDocument()
        })
    })

    it('shows the renamed document title after a successful rename', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        expect(screen.getByText('Runbook PDF')).toBeInTheDocument()
        expect(screen.getByText('runbook.pdf')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.docRename' }))
        await screen.findByText('knowledge.renameDocumentTitle')

        fireEvent.change(screen.getByLabelText('knowledge.docDisplayTitle'), {
            target: { value: 'Release Notes' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'common.save' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith('success', 'knowledge.renameDocumentSuccess:Release Notes')
        })

        expect(await screen.findByText('Release Notes')).toBeInTheDocument()
        expect(screen.getByText('runbook.pdf')).toBeInTheDocument()

        const fetchMock = vi.mocked(fetch)
        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8092/ops-knowledge/documents/doc_001',
            expect.objectContaining({
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: 'Release Notes' }),
            }),
        )
    })

    it('downloads the original document via the backend download endpoint', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.docDownload' }))

        await waitFor(() => {
            expect(fetch).toHaveBeenCalledWith('http://127.0.0.1:8092/ops-knowledge/documents/doc_001/original')
        })
        expect(globalThis.URL.createObjectURL).toHaveBeenCalled()
        expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith('blob:runbook')
    })

    it('loads markdown preview in the document side panel', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        fireEvent.click(screen.getByRole('button', { name: 'files.preview' }))

        await waitFor(() => {
            expect(fetch).toHaveBeenCalledWith('http://127.0.0.1:8092/ops-knowledge/documents/doc_001/preview')
        })

        expect(openPreview).toHaveBeenCalledWith(expect.objectContaining({
            name: 'Runbook PDF',
            path: 'knowledge-document:doc_001',
            type: 'md',
            previewKind: 'markdown',
            downloadUrl: 'http://127.0.0.1:8092/ops-knowledge/documents/doc_001/original',
        }))
    })

    it('shows a toast when the original download endpoint is unavailable', async () => {
        downloadOriginalResponse = () => Promise.resolve({
            ok: false,
            status: 404,
            statusText: 'Not Found',
            headers: new Headers({
                'Content-Type': 'text/html',
            }),
        } as Response)

        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=documents']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.documentsTabTitle')
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.docDownload' }))

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith(
                'error',
                'knowledge.downloadDocumentFailed:runbook.pdf:knowledge.downloadDocumentNotAvailable'
            )
        })
    })
})
