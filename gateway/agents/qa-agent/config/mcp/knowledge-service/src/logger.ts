import { appendFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

const LOG_ROOT = process.env.GOOSE_PATH_ROOT || process.cwd()
const LOG_DIR = join(LOG_ROOT, 'logs', 'mcp')
export const LOG_FILE_PATH = join(LOG_DIR, 'knowledge_service.log')

type LogDetails = Record<string, unknown>

mkdirSync(LOG_DIR, { recursive: true })

function sanitizeValue(value: unknown): unknown {
  if (value instanceof Error) {
    return {
      name: value.name,
      message: value.message,
      stack: value.stack,
    }
  }

  if (value === undefined) return undefined

  try {
    return JSON.parse(JSON.stringify(value))
  } catch {
    return String(value)
  }
}

export function log(level: 'INFO' | 'ERROR', event: string, details: LogDetails = {}): void {
  const payload: LogDetails = {
    ts: new Date().toISOString(),
    level,
    service: 'knowledge_service',
    event,
  }

  for (const [key, value] of Object.entries(details)) {
    const sanitized = sanitizeValue(value)
    if (sanitized !== undefined) payload[key] = sanitized
  }

  const line = JSON.stringify(payload)
  console.error(line)
  appendFileSync(LOG_FILE_PATH, `${line}\n`, 'utf8')
}

export function logInfo(event: string, details?: LogDetails): void {
  log('INFO', event, details)
}

export function logError(event: string, details?: LogDetails): void {
  log('ERROR', event, details)
}
