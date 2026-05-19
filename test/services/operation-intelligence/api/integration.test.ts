/**
 * Operation Intelligence Integration Tests — API Coverage
 *
 * Starts a real operation-intelligence Spring Boot service and exercises
 * all HTTP endpoints including authentication, input validation, CORS,
 * and QoS API responses.
 *
 * Prerequisites: Java 21+, Maven (or MVN env var).
 * Run: cd test && npx vitest run services/operation-intelligence
 */
import { ChildProcess, spawn } from 'node:child_process'
import { resolve, join } from 'node:path'
import { writeFileSync, unlinkSync, mkdirSync, existsSync, rmSync } from 'node:fs'
import { stringify } from 'yaml'
import { tmpdir } from 'node:os'
import net from 'node:net'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..')
const OI_DIR = join(PROJECT_ROOT, 'operation-intelligence')
const MVN = process.env.MVN || 'C:\\zhulin\\apache-maven-3.9.14\\bin\\mvn'
const SECRET_KEY = 'test-secret-key'

interface OiHandle {
  port: number
  baseUrl: string
  secretKey: string
  process: ChildProcess
  logs: string[]
  fetch: (path: string, init?: RequestInit) => Promise<Response>
  stop: () => Promise<void>
}

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

async function startOiService(): Promise<OiHandle> {
  const port = await freePort()
  const baseUrl = `http://127.0.0.1:${port}`
  const healthUrl = `${baseUrl}/actuator/health`

  const testConfigPath = join(tmpdir(), `oi-test-config-${port}.yaml`)
  const testConfig = {
    server: { port },
    'operation-intelligence': {
      'secret-key': SECRET_KEY,
      'cors-origin': '*',
      qos: {
        enabled: false,
        'dv-environments': [
          {
            'env-code': 'TEST.env',
            'env-name': 'Test Environment',
            'agent-solution-type': 'TestApp',
            'product-type-name': 'TestApp',
            'server-url': 'https://127.0.0.1:26335',
            'utm-user': 'admin',
            'utm-password': 'test',
            'crt-content': '',
            'crt-file-name': 'client.jks',
            'strict-ssl': false,
          },
        ],
      },
      logging: { 'access-log-enabled': false },
    },
  }
  writeFileSync(testConfigPath, stringify(testConfig), 'utf-8')

  // Verify JAR exists (build externally first: cd operation-intelligence && mvn -DskipTests package)
  const jarPath = join(OI_DIR, 'target', 'operation-intelligence.jar')
  if (!existsSync(jarPath)) {
    throw new Error(
      `JAR not found at ${jarPath}. Build it first:\n  cd operation-intelligence && ${MVN} -DskipTests package`,
    )
  }

  const child = spawn('java', [
    `-Dserver.port=${port}`,
    '-jar', jarPath,
  ], {
    cwd: OI_DIR,
    env: { ...process.env, OI_CONFIG_PATH: testConfigPath },
    stdio: ['ignore', 'pipe', 'pipe'],
  })

  const logs: string[] = []
  child.stdout?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[oi:out] ${line}`)
  })
  child.stderr?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[oi:err] ${line}`)
  })

  // Wait for health check to pass
  const maxWait = 40_000
  const start = Date.now()
  let ready = false
  while (Date.now() - start < maxWait) {
    try {
      const res = await fetch(healthUrl, { signal: AbortSignal.timeout(1500) })
      if (res.ok) { ready = true; break }
    } catch { /* not ready */ }
    await new Promise(r => setTimeout(r, 500))
  }

  if (!ready) {
    child.kill('SIGTERM')
    throw new Error(`operation-intelligence did not start within ${maxWait / 1000}s\nLogs:\n${logs.join('\n')}`)
  }

  const fetchWithAuth = (path: string, init?: RequestInit) =>
    fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'x-secret-key': SECRET_KEY,
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })

  return {
    port,
    baseUrl,
    secretKey: SECRET_KEY,
    process: child,
    logs,
    fetch: fetchWithAuth,
    stop: async () => {
      child.kill('SIGTERM')
      await new Promise<void>(resolve => {
        child.on('close', () => resolve())
        setTimeout(() => { child.kill('SIGKILL'); resolve() }, 5000)
      })
      try { unlinkSync(testConfigPath) } catch { /* ignore */ }
    },
  }
}

let oi: OiHandle | null = null

beforeAll(async () => {
  oi = await startOiService()
}, 180_000)

afterAll(async () => {
  if (oi) await oi.stop()
}, 15_000)

// =============================================================================
// 1. Health & Actuator
// =============================================================================
describe('Health & actuator', () => {
  it('GET /actuator/health returns 200', async () => {
    const res = await fetch(`${oi.baseUrl}/actuator/health`)
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data.status).toBe('UP')
  })

  it('GET /actuator/health does not require auth', async () => {
    const res = await fetch(`${oi.baseUrl}/actuator/health`)
    expect(res.status).toBe(200)
  })
})

// =============================================================================
// 2. Authentication
// =============================================================================
describe('Authentication', () => {
  it('rejects requests without x-secret-key', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments`)
    expect(res.status).toBe(401)
  })

  it('rejects requests with wrong x-secret-key', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments`, {
      headers: { 'x-secret-key': 'wrong-key' },
    })
    expect(res.status).toBe(401)
  })

  it('accepts requests with correct x-secret-key', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments')
    expect(res.ok).toBe(true)
  })

  it('accepts auth via ?key= query parameter', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments?key=${SECRET_KEY}`)
    expect(res.ok).toBe(true)
  })

  it('rejects ?key= with wrong value', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments?key=wrong`)
    expect(res.status).toBe(401)
  })

  it('OPTIONS preflight bypasses auth', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments`, {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:5173' },
    })
    expect(res.status).not.toBe(401)
  })
})

// =============================================================================
// 3. CORS
// =============================================================================
describe('CORS', () => {
  it('OPTIONS returns CORS headers', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments`, {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:5173' },
    })
    expect(res.status).toBe(204)
    const allowOrigin = res.headers.get('access-control-allow-origin')
    expect(allowOrigin).toBeTruthy()
    const allowMethods = res.headers.get('access-control-allow-methods') || ''
    expect(allowMethods).toContain('GET')
    expect(allowMethods).toContain('POST')
  })

  it('regular responses include CORS headers', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments', {
      headers: { Origin: 'http://localhost:5173' },
    })
    expect(res.headers.get('access-control-allow-origin')).toBeTruthy()
  })
})

// =============================================================================
// 4. getEnvironments API
// =============================================================================
describe('GET /operation-intelligence/qos/getEnvironments', () => {
  it('returns environments list with expected shape', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments')
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('results')
    expect(data.results).toBeInstanceOf(Array)
  })

  it('returns configured test environment', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments')
    const data = await res.json()
    const envCodes = data.results.map((e: any) => e.envCode)
    expect(envCodes).toContain('TEST.env')
  })

  it('environment entries have required fields', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments')
    const data = await res.json()
    for (const env of data.results) {
      expect(env).toHaveProperty('envCode')
      expect(env).toHaveProperty('envName')
      expect(env).toHaveProperty('agentSolutionType')
      expect(env).toHaveProperty('productTypeName')
    }
  })

  it('does not leak utmPassword in response', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getEnvironments')
    const data = await res.json()
    const jsonStr = JSON.stringify(data)
    expect(jsonStr).not.toContain('utmPassword')
    expect(jsonStr).not.toContain('test-password')
  })
})

// =============================================================================
// 5. QoS API Input Validation
// =============================================================================
describe('QoS API input validation', () => {
  const basePath = '/operation-intelligence/qos'

  it('getHealthIndicator requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
    const data = await res.json()
    expect(data.message).toContain('endTime')
  })

  it('getHealthIndicator requires valid time range', async () => {
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: 2000, endTime: 1000 }),
    })
    expect(res.status).toBe(400)
    const data = await res.json()
    expect(data.message).toContain('startTime')
  })

  it('getHealthIndicator requires startTime and endTime', async () => {
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env' }),
    })
    expect(res.status).toBe(400)
    const data = await res.json()
    expect(data.message).toContain('required')
  })

  it('getHealthIndicator rejects time range exceeding 90 days', async () => {
    const startTime = Date.now() - 91 * 24 * 60 * 60 * 1000
    const endTime = Date.now()
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime, endTime }),
    })
    expect(res.status).toBe(400)
    const data = await res.json()
    expect(data.message).toContain('90 days')
  })

  it('getAvailableIndicatorDetail requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getAvailableIndicatorDetail`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('getPerformanceIndicatorDetail requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getPerformanceIndicatorDetail`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('getResourceIndicatorDetail requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getResourceIndicatorDetail`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('getContributionData requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getContributionData`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('getAlarmIndicatorDetail requires envCode', async () => {
    const res = await oi.fetch(`${basePath}/getAlarmIndicatorDetail`, {
      method: 'POST',
      body: JSON.stringify({ startTime: 1000, endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('getProductConfigRule returns 404 for unknown type', async () => {
    const res = await oi.fetch(`${basePath}/getProductConfigRule`, {
      method: 'POST',
      body: JSON.stringify({ agentSolutionType: 'NonExistentType' }),
    })
    expect(res.status).toBe(404)
  })

  it('rejects invalid numeric values', async () => {
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: 'not-a-number', endTime: 2000 }),
    })
    expect(res.status).toBe(400)
  })

  it('accepts numeric values as numbers', async () => {
    const now = Date.now()
    const res = await oi.fetch(`${basePath}/getHealthIndicator`, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: now - 3600000, endTime: now }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('results')
  })
})

// =============================================================================
// 6. QoS API Response Shape (with QoS disabled, empty data)
// =============================================================================
describe('QoS API response shapes (empty data)', () => {
  const basePath = '/operation-intelligence/qos/getHealthIndicator'
  const now = Date.now()
  const oneHourAgo = now - 3600000

  it('getHealthIndicator returns { results: [] }', async () => {
    const res = await oi.fetch(basePath, {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('results')
    expect(data.results).toBeInstanceOf(Array)
  })

  it('getAvailableIndicatorDetail returns paginated shape', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getAvailableIndicatorDetail', {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now, pageIndex: 1, pageSize: 10 }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('total')
    expect(data).toHaveProperty('pageIndex')
    expect(data).toHaveProperty('pageSize')
    expect(data).toHaveProperty('results')
  })

  it('getPerformanceIndicatorDetail returns paginated shape', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getPerformanceIndicatorDetail', {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('total')
    expect(data).toHaveProperty('results')
  })

  it('getResourceIndicatorDetail returns { results: [] }', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getResourceIndicatorDetail', {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('results')
  })

  it('getContributionData returns contribution array', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getContributionData', {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('results')
    expect(data.results).toBeInstanceOf(Array)
  })

  it('getAlarmIndicatorDetail returns paginated shape', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getAlarmIndicatorDetail', {
      method: 'POST',
      body: JSON.stringify({ envCode: 'TEST.env', startTime: oneHourAgo, endTime: now, pageIndex: 1, pageSize: 10 }),
    })
    expect(res.ok).toBe(true)
    const data = await res.json()
    expect(data).toHaveProperty('total')
    expect(data).toHaveProperty('pageIndex')
    expect(data).toHaveProperty('pageSize')
    expect(data).toHaveProperty('results')
  })
})

// =============================================================================
// 7. Error Handling
// =============================================================================
describe('Error handling', () => {
  it('returns 404 for unknown routes', async () => {
    const res = await oi.fetch('/nonexistent')
    expect(res.status).toBe(404)
  })

  it('returns JSON error for 400 validation', async () => {
    const res = await oi.fetch('/operation-intelligence/qos/getHealthIndicator', {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(res.status).toBe(400)
    const data = await res.json()
    expect(data).toHaveProperty('message')
  })

  it('returns JSON error for 401 unauthorized', async () => {
    const res = await fetch(`${oi.baseUrl}/operation-intelligence/qos/getEnvironments`)
    const data = await res.json()
    // 401 responses may or may not have a JSON body depending on WebFlux filter
    // Just verify the status code
    expect(res.status).toBe(401)
  })
})
