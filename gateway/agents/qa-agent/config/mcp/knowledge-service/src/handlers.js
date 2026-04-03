const KNOWLEDGE_SERVICE_URL = process.env.KNOWLEDGE_SERVICE_URL || 'http://127.0.0.1:8092'
const KNOWLEDGE_DEFAULT_SOURCE_ID = process.env.KNOWLEDGE_DEFAULT_SOURCE_ID || 'src_ac8da09a7cfd'
const KNOWLEDGE_REQUEST_TIMEOUT_MS = parseInt(process.env.KNOWLEDGE_REQUEST_TIMEOUT_MS || '15000', 10)
const KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW = 2

const API_PREFIX = '/knowledge'

export const tools = [
  {
    name: 'search',
    description: 'Search knowledge chunks. Uses the configured default source when sourceIds is omitted.',
    inputSchema: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'Search query text.',
        },
        sourceIds: {
          type: 'array',
          description: 'Optional source IDs. Defaults to the configured QA knowledge source.',
          items: { type: 'string' },
        },
        documentIds: {
          type: 'array',
          description: 'Optional document IDs to narrow the search.',
          items: { type: 'string' },
        },
        topK: {
          type: 'number',
          description: 'Optional result size. Defaults to 8.',
          minimum: 1,
          maximum: 20,
        },
      },
      required: ['query'],
    },
  },
  {
    name: 'fetch',
    description: 'Fetch a knowledge chunk by chunkId, with optional neighbor chunks.',
    inputSchema: {
      type: 'object',
      properties: {
        chunkId: {
          type: 'string',
          description: 'Chunk ID returned from search.',
        },
        includeNeighbors: {
          type: 'boolean',
          description: 'Whether to include adjacent chunks.',
        },
        neighborWindow: {
          type: 'number',
          description: 'Neighbor window size when includeNeighbors is true. Defaults to 1.',
          minimum: 1,
          maximum: KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW,
        },
      },
      required: ['chunkId'],
    },
  },
]

function normalizeSourceIds(sourceIds) {
  if (Array.isArray(sourceIds) && sourceIds.length > 0) {
    return sourceIds.filter(Boolean)
  }
  return KNOWLEDGE_DEFAULT_SOURCE_ID ? [KNOWLEDGE_DEFAULT_SOURCE_ID] : []
}

function createTimeoutSignal() {
  return AbortSignal.timeout(Number.isFinite(KNOWLEDGE_REQUEST_TIMEOUT_MS) ? KNOWLEDGE_REQUEST_TIMEOUT_MS : 15000)
}

async function ks(path, init) {
  const response = await fetch(`${KNOWLEDGE_SERVICE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
    signal: createTimeoutSignal(),
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`Knowledge service ${path} returned ${response.status}: ${text}`)
  }

  return response.json()
}

export async function handleSearch(args) {
  const query = args.query?.trim()
  if (!query) {
    throw new Error('search.query is required')
  }

  const body = {
    query,
    sourceIds: normalizeSourceIds(args.sourceIds),
    documentIds: args.documentIds || [],
    topK: args.topK ?? 8,
  }

  const result = await ks(`${API_PREFIX}/search`, {
    method: 'POST',
    body: JSON.stringify(body),
  })

  return JSON.stringify(result, null, 2)
}

export async function handleFetch(args) {
  const chunkId = args.chunkId?.trim()
  if (!chunkId) {
    throw new Error('fetch.chunkId is required')
  }

  const neighborWindow = args.neighborWindow ?? 1
  if (!Number.isInteger(neighborWindow) || neighborWindow < 1 || neighborWindow > KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW) {
    throw new Error(`fetch.neighborWindow must be an integer between 1 and ${KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW}`)
  }

  const params = new URLSearchParams()
  params.set('includeNeighbors', String(Boolean(args.includeNeighbors)))
  params.set('neighborWindow', String(neighborWindow))
  params.set('includeMarkdown', 'true')
  params.set('includeRawText', 'true')

  const result = await ks(`${API_PREFIX}/fetch/${encodeURIComponent(chunkId)}?${params.toString()}`)
  return JSON.stringify(result, null, 2)
}

export async function dispatch(name, args = {}) {
  switch (name) {
    case 'search':
      return handleSearch(args)
    case 'fetch':
      return handleFetch(args)
    default:
      throw new Error(`Unknown tool: ${name}`)
  }
}
