/**
 * CORS integration tests — starts a real Java gateway and verifies
 * Access-Control-Allow-Origin behavior end-to-end.
 */
import { ChildProcess, spawn } from 'node:child_process'
import { resolve, join } from 'node:path'
import net from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { sleep } from './helpers.js'

const PROJECT_ROOT = resolve(import.meta.dirname, '..')
const GATEWAY_DIR = join(PROJECT_ROOT, 'gateway')
const SECRET_KEY = 'test-cors-key'

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const srv = net.createServer()
    srv.listen(0, '127.0.0.1', () => {
      const port = (srv.address() as net.AddressInfo).port
      srv.close(() => resolve(port))
    })
    srv.on('error', reject)
  })
}

interface GatewayInstance {
  port: number
  baseUrl: string
  process: ChildProcess
  logs: string[]
  stop: () => Promise<void>
}

async function startGatewayWithCors(corsOrigin: string): Promise<GatewayInstance> {
  const port = await freePort()
  const baseUrl = `http://127.0.0.1:${port}`

  const jarPath = join(GATEWAY_DIR, 'gateway-service', 'target', 'gateway-service.jar')
  const libDir = join(GATEWAY_DIR, 'gateway-service', 'target', 'lib')
  const log4jConfig = join(GATEWAY_DIR, 'gateway-service', 'target', 'resources', 'log4j2.xml')

  const javaArgs = [
    `-Dloader.path=${libDir}`,
    `-Dserver.port=${port}`,
    '-Dserver.address=127.0.0.1',
    `-Dgateway.secret-key=${SECRET_KEY}`,
    `-Dgateway.goosed-bin=${process.env.GOOSED_BIN || 'goosed'}`,
    `-Dgateway.paths.project-root=${PROJECT_ROOT}`,
    `-Dgateway.cors-origin=${corsOrigin}`,
    `-Dlogging.config=file:${log4jConfig}`,
    '-jar', jarPath,
  ]

  const child = spawn('java', javaArgs, {
    cwd: GATEWAY_DIR,
    stdio: ['ignore', 'pipe', 'pipe'],
  })

  const logs: string[] = []
  child.stdout?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[gw:out] ${line}`)
  })
  child.stderr?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[gw:err] ${line}`)
  })

  // Wait for gateway to respond
  const maxWait = 30_000
  const start = Date.now()
  while (Date.now() - start < maxWait) {
    try {
      const res = await fetch(`${baseUrl}/status`, {
        headers: { 'x-secret-key': SECRET_KEY },
        signal: AbortSignal.timeout(2000),
      })
      if (res.ok) break
    } catch {
      // not ready
    }
    await sleep(500)
  }

  // Verify it's up
  const res = await fetch(`${baseUrl}/status`, {
    headers: { 'x-secret-key': SECRET_KEY },
    signal: AbortSignal.timeout(3000),
  })
  if (!res.ok) {
    console.error('Gateway logs:', logs.join('\n'))
    child.kill('SIGKILL')
    throw new Error(`Gateway failed to start (HTTP ${res.status})`)
  }

  return {
    port,
    baseUrl,
    process: child,
    logs,
    stop: async () => {
      child.kill('SIGTERM')
      await sleep(3000)
      if (!child.killed) child.kill('SIGKILL')
      await sleep(500)
    },
  }
}

// ====================== Tests with exact-match CORS ======================

describe('CORS with exact origin', () => {
  let gw: GatewayInstance

  beforeAll(async () => {
    gw = await startGatewayWithCors('http://app.example.com')
  }, 60_000)

  afterAll(async () => {
    await gw?.stop()
  })

  it('matching origin returns Access-Control-Allow-Origin', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://app.example.com',
      },
    })
    expect(res.ok).toBe(true)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://app.example.com')
    expect(res.headers.get('Vary')).toBe('Origin')
  })

  it('non-matching origin omits Access-Control-Allow-Origin', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://evil.com',
      },
    })
    expect(res.ok).toBe(true)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })

  it('private network origin is NOT auto-allowed (regression)', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://192.168.1.5:5173',
      },
    })
    expect(res.ok).toBe(true)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })

  it('localhost:5173 is NOT auto-allowed (regression)', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://127.0.0.1:5173',
      },
    })
    expect(res.ok).toBe(true)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })

  it('OPTIONS preflight with matching origin returns 204', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      method: 'OPTIONS',
      headers: {
        'Origin': 'http://app.example.com',
      },
    })
    expect(res.status).toBe(204)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://app.example.com')
    expect(res.headers.get('Access-Control-Allow-Methods')).toContain('GET')
  })

  it('OPTIONS preflight with non-matching origin returns 403', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      method: 'OPTIONS',
      headers: {
        'Origin': 'http://evil.com',
      },
    })
    expect(res.status).toBe(403)
  })

  it('no Origin header omits ACAO (non-CORS request)', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: { 'x-secret-key': SECRET_KEY },
    })
    expect(res.ok).toBe(true)
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })
}, 90_000)

// ====================== Tests with multi-value CORS ======================

describe('CORS with multiple origins', () => {
  let gw: GatewayInstance

  beforeAll(async () => {
    gw = await startGatewayWithCors('http://a.com,http://b.com,http://c.com')
  }, 60_000)

  afterAll(async () => {
    await gw?.stop()
  })

  it('first origin matches', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://a.com',
      },
    })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://a.com')
  })

  it('third origin matches', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://c.com',
      },
    })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://c.com')
  })

  it('unlisted origin rejected', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://d.com',
      },
    })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeNull()
  })
}, 90_000)

// ====================== Tests with wildcard CORS ======================

describe('CORS with wildcard', () => {
  let gw: GatewayInstance

  beforeAll(async () => {
    gw = await startGatewayWithCors('*')
  }, 60_000)

  afterAll(async () => {
    await gw?.stop()
  })

  it('any origin is reflected back', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://anything.example.com:9999',
      },
    })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://anything.example.com:9999')
  })

  it('private network origin allowed in wildcard mode', async () => {
    const res = await fetch(`${gw.baseUrl}/status`, {
      headers: {
        'x-secret-key': SECRET_KEY,
        'Origin': 'http://10.0.0.1:5173',
      },
    })
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('http://10.0.0.1:5173')
  })
}, 90_000)
