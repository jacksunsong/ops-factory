import { execFile as execFileCallback, spawn } from 'node:child_process'
import { readFile, realpath, stat } from 'node:fs/promises'
import { promisify } from 'node:util'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { logError, logInfo } from './logger.js'

const execFile = promisify(execFileCallback)
const CONFIG_FILE_PATH = fileURLToPath(new URL('../../../config.yaml', import.meta.url))
const CONFIG_DIR = path.dirname(CONFIG_FILE_PATH)
const DEFAULT_ROOT_DIR = '../data'
const DEFAULT_FIND_LIMIT = 100
const DEFAULT_SEARCH_LIMIT = 50
const DEFAULT_READ_WINDOW = 120
const MAX_FIND_LIMIT = 500
const MAX_SEARCH_LIMIT = 200
const MAX_READ_WINDOW = 200
const MAX_READ_OUTPUT_CHARS = 24_000
const COMMAND_TIMEOUT_MS = 20_000
const MAX_STDERR_CHARS = 16_000

type ToolArgs = Record<string, unknown>
type SearchEngine = 'rg' | 'grep'

interface RootDirContext {
  rootDir: string
  exists: boolean
}

interface ScopeContext extends RootDirContext {
  scopePath: string
}

interface ReadableFileContext {
  rootDir: string
  filePath: string
}

interface LineCommandResult {
  lines: string[]
  stderr: string
  code: number
  truncated: boolean
}

interface SearchHit {
  path: string
  line: number
  column: number | null
  preview: string
}

let searchEnginePromise: Promise<SearchEngine> | undefined

export const tools = [
  {
    name: 'find_files',
    description: 'Find files under the configured root directory.',
    inputSchema: {
      type: 'object',
      properties: {
        pathPrefix: {
          type: 'string',
          description: 'Optional relative subdirectory under rootDir.',
        },
        glob: {
          type: 'string',
          description: 'Optional file name glob. Prefer the configured knowledge artifact type before probing unrelated file types.',
        },
        limit: {
          type: 'number',
          description: 'Optional maximum number of files to return.',
          minimum: 1,
          maximum: MAX_FIND_LIMIT,
        },
      },
    },
  },
  {
    name: 'search_content',
    description: 'Search text content under the configured root directory.',
    inputSchema: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'Search query text.',
        },
        pathPrefix: {
          type: 'string',
          description: 'Optional relative subdirectory under rootDir.',
        },
        glob: {
          type: 'string',
          description: 'Optional file name glob to limit searched files, for example *.md for knowledge documents.',
        },
        regex: {
          type: 'boolean',
          description: 'Whether query should be treated as a regex.',
        },
        caseSensitive: {
          type: 'boolean',
          description: 'Whether search should be case-sensitive.',
        },
        limit: {
          type: 'number',
          description: 'Optional maximum number of hits to return.',
          minimum: 1,
          maximum: MAX_SEARCH_LIMIT,
        },
      },
      required: ['query'],
    },
  },
  {
    name: 'read_file',
    description: 'Read a file or a specific line range under the configured root directory. Results are capped to keep context small; truncated responses include the returned end line and next startLine.',
    inputSchema: {
      type: 'object',
      properties: {
        path: {
          type: 'string',
          description: 'Absolute path returned by a previous tool call.',
        },
        startLine: {
          type: 'number',
          minimum: 1,
        },
        endLine: {
          type: 'number',
          minimum: 1,
        },
      },
      required: ['path'],
    },
  },
]

function clamp(value: unknown, min: number, max: number, fallback: number): number {
  const number = typeof value === 'number' && Number.isFinite(value) ? Math.floor(value) : fallback
  return Math.max(min, Math.min(max, number))
}

function normalizeGlob(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }

  const glob = value.trim()
  const segments = glob.split(/[\\/]+/)
  if (
    glob.includes('\0') ||
    glob.startsWith('!') ||
    path.isAbsolute(glob) ||
    segments.includes('..')
  ) {
    throw new Error(`Invalid glob pattern: ${glob}`)
  }

  return glob
}

export function extractConfiguredRootDir(content: string): string | null {
  const parsed = YAML.parse(content)
  const rootDir = parsed?.extensions?.['knowledge-cli']?.['x-opsfactory']?.scope?.rootDir
  if (typeof rootDir === 'string' && rootDir.trim()) {
    return rootDir.trim()
  }

  return null
}

async function readConfiguredRootDir(): Promise<string> {
  if (process.env.QA_CLI_ROOT_DIR?.trim()) {
    return process.env.QA_CLI_ROOT_DIR.trim()
  }

  try {
    const content = await readFile(CONFIG_FILE_PATH, 'utf8')
    const rootDir = extractConfiguredRootDir(content)
    if (rootDir) {
      return rootDir
    }
  } catch {
    // fall through to default
  }

  return DEFAULT_ROOT_DIR
}

async function getRootDirContext(): Promise<RootDirContext> {
  const configured = await readConfiguredRootDir()
  const resolved = path.isAbsolute(configured) ? configured : path.resolve(CONFIG_DIR, configured)

  try {
    const realRoot = await realpath(resolved)
    return {
      rootDir: realRoot,
      exists: true,
    }
  } catch {
    return {
      rootDir: resolved,
      exists: false,
    }
  }
}

function isWithinRoot(rootDir: string, candidatePath: string): boolean {
  const relative = path.relative(rootDir, candidatePath)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

async function resolveScopePath(pathPrefix: unknown): Promise<ScopeContext> {
  const root = await getRootDirContext()
  const prefix = typeof pathPrefix === 'string' ? pathPrefix.trim() : ''
  const candidate = prefix ? path.resolve(root.rootDir, prefix) : root.rootDir

  if (!isWithinRoot(root.rootDir, candidate)) {
    throw new Error(`Path escapes configured rootDir: ${candidate}`)
  }

  try {
    const realCandidate = await realpath(candidate)
    if (!isWithinRoot(root.rootDir, realCandidate)) {
      throw new Error(`Resolved path escapes configured rootDir: ${realCandidate}`)
    }
    return {
      rootDir: root.rootDir,
      scopePath: realCandidate,
      exists: true,
    }
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('Resolved path escapes configured rootDir:')) {
      throw error
    }

    return {
      rootDir: root.rootDir,
      scopePath: candidate,
      exists: false,
    }
  }
}

async function resolveReadableFile(filePath: unknown): Promise<ReadableFileContext> {
  const root = await getRootDirContext()
  if (!root.exists) {
    throw new Error(`Configured rootDir does not exist: ${root.rootDir}`)
  }

  if (typeof filePath !== 'string' || !filePath.trim()) {
    throw new Error('read_file.path is required')
  }

  const candidate = path.isAbsolute(filePath)
    ? path.resolve(filePath)
    : path.resolve(root.rootDir, filePath)

  const realFile = await realpath(candidate)
  if (!isWithinRoot(root.rootDir, realFile)) {
    throw new Error(`File escapes configured rootDir: ${realFile}`)
  }

  const stats = await stat(realFile)
  if (!stats.isFile()) {
    throw new Error(`Path is not a file: ${realFile}`)
  }

  return {
    rootDir: root.rootDir,
    filePath: realFile,
  }
}

async function runCommandLines(command: string, args: string[], limit: number): Promise<LineCommandResult> {
  logInfo('command_started', { command, mode: 'stream', limit, argCount: args.length })

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      stdio: ['ignore', 'pipe', 'pipe'],
    })

    const lines: string[] = []
    let stdoutRemainder = ''
    let stderr = ''
    let truncated = false
    let timedOut = false
    let settled = false

    const finish = (result: LineCommandResult) => {
      if (settled) return
      settled = true
      clearTimeout(timeout)
      logInfo('command_succeeded', {
        command,
        mode: 'stream',
        lines: result.lines.length,
        truncated: result.truncated,
      })
      resolve(result)
    }

    const fail = (error: unknown) => {
      if (settled) return
      settled = true
      clearTimeout(timeout)
      reject(error)
    }

    const stopEarly = () => {
      if (truncated) return
      truncated = true
      child.kill()
    }

    const pushLine = (line: string) => {
      if (lines.length < limit) {
        lines.push(line.replace(/\r$/, ''))
      } else {
        stopEarly()
      }
    }

    const timeout = setTimeout(() => {
      timedOut = true
      child.kill()
    }, COMMAND_TIMEOUT_MS)

    child.stdout?.on('data', (chunk: Buffer) => {
      const data = stdoutRemainder + chunk.toString('utf8')
      const parts = data.split('\n')
      stdoutRemainder = parts.pop() ?? ''
      for (const part of parts) {
        pushLine(part)
        if (truncated) break
      }
    })

    child.stderr?.on('data', (chunk: Buffer) => {
      if (stderr.length < MAX_STDERR_CHARS) {
        stderr = (stderr + chunk.toString('utf8')).slice(0, MAX_STDERR_CHARS)
      }
    })

    child.on('error', fail)
    child.on('close', (code) => {
      if (!truncated && !timedOut && stdoutRemainder) {
        pushLine(stdoutRemainder)
      }

      if (timedOut) {
        fail(new Error(`${command} timed out after ${COMMAND_TIMEOUT_MS / 1000}s. Try a narrower pathPrefix, glob, or query.`))
        return
      }

      finish({
        lines,
        stderr,
        code: truncated ? 0 : code ?? 0,
        truncated,
      })
    })
  })
}

async function getSearchEngine(): Promise<SearchEngine> {
  if (!searchEnginePromise) {
    searchEnginePromise = (async () => {
      try {
        await execFile('rg', ['--version'], { maxBuffer: 1024 * 1024 })
        return 'rg'
      } catch {
        return 'grep'
      }
    })()
  }

  return searchEnginePromise
}

function parseRgLine(line: string): SearchHit | null {
  const match = /^(.*?):(\d+):(\d+):(.*)$/.exec(line)
  if (!match) return null
  return {
    path: match[1],
    line: Number.parseInt(match[2], 10),
    column: Number.parseInt(match[3], 10),
    preview: match[4]?.trim() || '',
  }
}

function parseGrepLine(line: string): SearchHit | null {
  const match = /^(.*?):(\d+):(.*)$/.exec(line)
  if (!match) return null
  return {
    path: match[1],
    line: Number.parseInt(match[2], 10),
    column: null,
    preview: match[3]?.trim() || '',
  }
}

function isSearchHit(hit: SearchHit | null): hit is SearchHit {
  return hit !== null
}

export function summarizeToolArgs(args: ToolArgs): Record<string, unknown> {
  return {
    keys: Object.keys(args).sort(),
    hasQuery: typeof args.query === 'string' && args.query.length > 0,
    queryLength: typeof args.query === 'string' ? args.query.length : undefined,
    hasPath: typeof args.path === 'string' && args.path.length > 0,
    hasPathPrefix: typeof args.pathPrefix === 'string' && args.pathPrefix.length > 0,
    glob: typeof args.glob === 'string' ? args.glob : undefined,
    limit: typeof args.limit === 'number' ? args.limit : undefined,
  }
}

function formatReadContent(lines: string[], startLine: number): string {
  return lines
    .map((line, index) => `${String(startLine + index).padStart(4, ' ')}  ${line}`)
    .join('\n')
}

function fitReadContentToCharLimit(lines: string[], startLine: number): {
  content: string
  lines: string[]
  truncatedByChars: boolean
} {
  let selected = lines
  let content = formatReadContent(selected, startLine)
  let truncatedByChars = false

  while (selected.length > 1 && content.length > MAX_READ_OUTPUT_CHARS) {
    selected = selected.slice(0, -1)
    content = formatReadContent(selected, startLine)
    truncatedByChars = true
  }

  if (content.length > MAX_READ_OUTPUT_CHARS) {
    content = content.slice(0, MAX_READ_OUTPUT_CHARS)
    truncatedByChars = true
  }

  return {
    content,
    lines: selected,
    truncatedByChars,
  }
}

export async function handleFindFiles(args: ToolArgs = {}): Promise<string> {
  const scope = await resolveScopePath(args.pathPrefix)
  const limit = clamp(args.limit, 1, MAX_FIND_LIMIT, DEFAULT_FIND_LIMIT)
  const glob = normalizeGlob(args.glob)

  if (!scope.exists) {
    return JSON.stringify({ rootDir: scope.rootDir, files: [], total: 0, truncated: false }, null, 2)
  }

  const engine = await getSearchEngine()
  let result: LineCommandResult

  if (engine === 'rg') {
    const commandArgs = ['--files', '--hidden', '--no-ignore']
    if (glob) commandArgs.push('--glob', glob)
    commandArgs.push(scope.scopePath)
    result = await runCommandLines('rg', commandArgs, limit)
  } else {
    const commandArgs = [scope.scopePath, '-type', 'f']
    if (glob) commandArgs.push('-name', glob)
    result = await runCommandLines('find', commandArgs, limit)
  }

  if (result.code > 1 || (engine !== 'rg' && result.code > 0)) {
    throw new Error(result.stderr?.trim() || `find_files failed with code ${result.code}`)
  }

  const lines = result.lines
    .map(line => line.trim())
    .filter(Boolean)

  const files = []
  for (const filePath of lines) {
    const absolutePath = path.isAbsolute(filePath)
      ? filePath
      : path.resolve(scope.scopePath, filePath)
    const fileStat = await stat(absolutePath)
    files.push({
      path: absolutePath,
      type: fileStat.isFile() ? 'file' : 'other',
      size: fileStat.size,
      mtime: new Date(fileStat.mtimeMs).toISOString(),
    })
  }

  return JSON.stringify({
    rootDir: scope.rootDir,
    files,
    total: files.length,
    truncated: result.truncated,
  }, null, 2)
}

export async function handleSearchContent(args: ToolArgs = {}): Promise<string> {
  const query = typeof args.query === 'string' ? args.query.trim() : ''
  if (!query) {
    throw new Error('search_content.query is required')
  }

  const scope = await resolveScopePath(args.pathPrefix)
  const limit = clamp(args.limit, 1, MAX_SEARCH_LIMIT, DEFAULT_SEARCH_LIMIT)
  const glob = normalizeGlob(args.glob)

  if (!scope.exists) {
    return JSON.stringify({ rootDir: scope.rootDir, hits: [], total: 0, engine: 'none', truncated: false }, null, 2)
  }

  const engine = await getSearchEngine()
  let result: LineCommandResult

  if (engine === 'rg') {
    const commandArgs = ['-n', '--no-heading', '--with-filename', '--column', '--max-columns', '500', '--hidden', '--no-ignore']
    if (glob) commandArgs.push('--glob', glob)
    if (!args.caseSensitive) commandArgs.push('-i')
    if (!args.regex) commandArgs.push('-F')
    commandArgs.push('-e', query, scope.scopePath)
    result = await runCommandLines('rg', commandArgs, limit)
  } else {
    const commandArgs = ['-R', '-n', '-I']
    if (glob) commandArgs.push(`--include=${glob}`)
    if (!args.caseSensitive) commandArgs.push('-i')
    if (!args.regex) commandArgs.push('-F')
    commandArgs.push('--', query, scope.scopePath)
    result = await runCommandLines('grep', commandArgs, limit)
  }

  if (result.code && result.code > 1) {
    throw new Error(result.stderr?.trim() || `search_content failed with code ${result.code}`)
  }

  const parser = engine === 'rg' ? parseRgLine : parseGrepLine
  const hits = result.lines
    .map(line => parser(line.trim()))
    .filter(isSearchHit)

  return JSON.stringify({
    rootDir: scope.rootDir,
    hits,
    total: hits.length,
    engine,
    truncated: result.truncated,
  }, null, 2)
}

export async function handleReadFile(args: ToolArgs = {}): Promise<string> {
  const { filePath } = await resolveReadableFile(args.path)
  const buffer = await readFile(filePath)
  if (buffer.includes(0)) {
    throw new Error(`Binary files are not supported: ${filePath}`)
  }

  const content = buffer.toString('utf8')
  const lines = content.split(/\r?\n/)
  const totalLines = lines.length

  const requestedStart = clamp(args.startLine, 1, totalLines || 1, 1)
  const requestedEnd = Number.isFinite(args.endLine)
    ? clamp(args.endLine, requestedStart, totalLines || requestedStart, requestedStart)
    : Math.min(totalLines, requestedStart + DEFAULT_READ_WINDOW - 1)
  const cappedEnd = Math.min(requestedEnd, requestedStart + MAX_READ_WINDOW - 1)
  const selected = lines.slice(requestedStart - 1, cappedEnd)
  const fitted = fitReadContentToCharLimit(selected, requestedStart)
  const returnedEndLine = requestedStart + fitted.lines.length - 1
  const truncatedByLines = cappedEnd < requestedEnd
  const truncatedByChars = fitted.truncatedByChars
  const truncated = truncatedByLines || truncatedByChars
  const truncatedReason = truncatedByLines
    ? 'line_limit'
    : truncatedByChars
      ? 'char_limit'
      : null
  const nextStartLine = truncated && returnedEndLine < totalLines ? returnedEndLine + 1 : null
  const message = truncated
    ? [
        `内容已被截断：请求到第 ${requestedEnd} 行，实际返回到第 ${returnedEndLine} 行。`,
        nextStartLine ? `如需继续读取，请用 startLine=${nextStartLine}。` : null,
      ].filter(Boolean).join(' ')
    : undefined

  return JSON.stringify({
    path: filePath,
    startLine: requestedStart,
    endLine: returnedEndLine,
    requestedEndLine: requestedEnd,
    totalLines,
    truncated,
    truncatedReason,
    nextStartLine,
    message,
    content: fitted.content,
  }, null, 2)
}

export async function dispatch(name: string, args: ToolArgs = {}): Promise<string> {
  const startedAt = Date.now()
  logInfo('tool_dispatch_started', { tool: name, args: summarizeToolArgs(args) })

  try {
    let result

    switch (name) {
      case 'find_files':
        result = await handleFindFiles(args)
        break
      case 'search_content':
        result = await handleSearchContent(args)
        break
      case 'read_file':
        result = await handleReadFile(args)
        break
      default:
        throw new Error(`Unknown tool: ${name}`)
    }

    logInfo('tool_dispatch_succeeded', {
      tool: name,
      durationMs: Date.now() - startedAt,
    })
    return result
  } catch (error) {
    logError('tool_dispatch_failed', {
      tool: name,
      args: summarizeToolArgs(args ?? {}),
      durationMs: Date.now() - startedAt,
      error,
    })
    throw error
  }
}
