/**
 * LLM service availability tests.
 *
 * 1. Direct API test: reads universal-agent config and calls the LLM endpoint directly.
 * 2. goosed test: spawns a real goosed process, creates a session, submits a session reply,
 *    and verifies the LLM responds through the session event stream.
 */
import { ChildProcess, spawn } from 'node:child_process'
import { resolve, join } from 'node:path'
import { readFile, mkdtemp, rm, mkdir, writeFile, cp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { randomUUID } from 'node:crypto'
import net from 'node:net'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { sleep } from './helpers.js'

// Trust goosed's self-signed TLS cert for fetch calls in this test process
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'

const PROJECT_ROOT = resolve(import.meta.dirname, '..')
const AGENT_CONFIG_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'universal-agent', 'config')

interface AgentConfig {
  provider: string
  model: string
  host: string
  apiKey: string
}

interface CustomProviderJson {
  name: string
  api_key_env: string
  base_url: string
}

/** Simple YAML key-value parser (top-level only). */
function parseSimpleYaml(content: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const line of content.split('\n')) {
    const match = line.match(/^(\w+):\s*['"]?(.+?)['"]?\s*$/)
    if (match) result[match[1]] = match[2]
  }
  return result
}

/** Build chat completions URL, avoiding double /v1 when base_url already includes it. */
function chatCompletionsUrl(host: string): string {
  const base = host.replace(/\/+$/, '')
  return base.endsWith('/v1')
    ? `${base}/chat/completions`
    : `${base}/v1/chat/completions`
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

let agentConfig: AgentConfig

beforeAll(async () => {
  const configContent = await readFile(join(AGENT_CONFIG_DIR, 'config.yaml'), 'utf-8')
  const secretsContent = await readFile(join(AGENT_CONFIG_DIR, 'secrets.yaml'), 'utf-8')
  const cfg = parseSimpleYaml(configContent)
  const secrets = parseSimpleYaml(secretsContent)

  const provider = cfg.GOOSE_PROVIDER || 'openai'

  // Resolve host and apiKey: custom provider reads from JSON, standard provider from config/secrets
  let host = cfg.OPENAI_HOST || 'https://api.openai.com'
  let apiKey = secrets.OPENAI_API_KEY || ''

  if (provider.startsWith('custom_')) {
    const providerJsonPath = join(AGENT_CONFIG_DIR, 'custom_providers', `${provider}.json`)
    try {
      const providerJson: CustomProviderJson = JSON.parse(await readFile(providerJsonPath, 'utf-8'))
      host = providerJson.base_url || host
      apiKey = secrets[providerJson.api_key_env] || apiKey
    } catch (err) {
      console.warn(`Failed to read custom provider config: ${providerJsonPath}`, err)
    }
  }

  agentConfig = { provider, model: cfg.GOOSE_MODEL || '', host, apiKey }
  console.log(`LLM config: provider=${agentConfig.provider}, model=${agentConfig.model}, host=${agentConfig.host}`)
})

// ─── Test 1: Direct LLM API call ───

describe('Direct LLM API', () => {
  it('config is valid', () => {
    expect(agentConfig.model).toBeTruthy()
    expect(agentConfig.host).toBeTruthy()
    expect(agentConfig.apiKey).toBeTruthy()
    expect(agentConfig.apiKey).toMatch(/^sk-/)
  })

  it('LLM endpoint responds', async () => {
    const res = await fetch(chatCompletionsUrl(agentConfig.host), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${agentConfig.apiKey}`,
      },
      body: JSON.stringify({
        model: agentConfig.model,
        messages: [{ role: 'user', content: 'Reply with exactly: ok' }],
        max_tokens: 10,
        temperature: 0,
      }),
      signal: AbortSignal.timeout(30_000),
    })

    expect(res.status).toBe(200)
    const data = await res.json()
    expect(data.choices?.length).toBeGreaterThan(0)
    const msg = data.choices[0].message
    // Some models (e.g. reasoning models) may return content=null with reasoning in a separate field
    const hasContent = msg?.content || msg?.reasoning
    expect(hasContent).toBeTruthy()
    console.log(`Direct API response: content="${msg.content}", has_reasoning=${!!msg.reasoning}`)
  }, 60_000)
})

// ─── Test 2: Through goosed ───

describe('goosed LLM integration', () => {
  let goosed: ChildProcess | null = null
  let port: number
  let baseUrl: string
  let tmpDir: string
  const logs: string[] = []
  const SECRET = 'test-llm-health'

  beforeAll(async () => {
    port = await freePort()
    // goosed 1.27+ defaults to TLS; use https + trust-all
    baseUrl = `https://127.0.0.1:${port}`
    tmpDir = await mkdtemp(join(tmpdir(), 'llm-health-goosed-'))

    // goosed reads config from $GOOSE_PATH_ROOT/config/config.yaml
    // Write provider/model config so goosed can resolve the provider on new sessions
    const gooseConfigDir = join(tmpDir, 'config')
    await mkdir(gooseConfigDir, { recursive: true })
    await writeFile(join(gooseConfigDir, 'config.yaml'), [
      `GOOSE_PROVIDER: ${agentConfig.provider}`,
      `GOOSE_MODEL: ${agentConfig.model}`,
    ].join('\n'))

    // Copy custom_providers dir so goosed can resolve custom provider configs
    const customProvidersDir = join(AGENT_CONFIG_DIR, 'custom_providers')
    try {
      await cp(customProvidersDir, join(gooseConfigDir, 'custom_providers'), { recursive: true })
    } catch { /* no custom providers */ }

    // Read all secrets as env vars for goosed (supports custom provider key names)
    const secretsContent = await readFile(join(AGENT_CONFIG_DIR, 'secrets.yaml'), 'utf-8')
    const secretsEnv = parseSimpleYaml(secretsContent)

    const env: Record<string, string> = {
      ...process.env as Record<string, string>,
      ...secretsEnv,
      // Agent config as env vars (same as InstanceManager.buildEnvironment)
      GOOSE_PROVIDER: agentConfig.provider,
      GOOSE_MODEL: agentConfig.model,
      OPENAI_HOST: agentConfig.host,
      OPENAI_API_KEY: agentConfig.apiKey,
      GOOSE_MODE: 'auto',
      GOOSE_CONTEXT_LIMIT: '65536',
      GOOSE_MAX_TOKENS: '4096',
      GOOSE_TEMPERATURE: '0.2',
      // goosed runtime env
      GOOSE_PORT: String(port),
      GOOSE_HOST: '127.0.0.1',
      GOOSE_SERVER__SECRET_KEY: SECRET,
      GOOSE_PATH_ROOT: tmpDir,
      GOOSE_DISABLE_KEYRING: '1',
      GOOSE_TELEMETRY_ENABLED: 'false',
      // Trust self-signed cert from goosed
      NODE_TLS_REJECT_UNAUTHORIZED: '0',
    }

    const goosedBin = process.env.GOOSED_BIN || 'goosed'
    goosed = spawn(goosedBin, ['agent'], {
      cwd: tmpDir,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
    })

    goosed.stdout?.on('data', (d: Buffer) => logs.push(`[out] ${d.toString().trim()}`))
    goosed.stderr?.on('data', (d: Buffer) => logs.push(`[err] ${d.toString().trim()}`))

    // Wait for goosed to be ready
    const maxWait = 15_000
    const start = Date.now()
    let ready = false
    while (Date.now() - start < maxWait) {
      try {
        const res = await fetch(`${baseUrl}/status`, { signal: AbortSignal.timeout(1000) })
        if (res.ok) { ready = true; break }
      } catch { /* not ready */ }
      await sleep(300)
    }
    if (!ready) {
      console.error('goosed logs:', logs.join('\n'))
      throw new Error('goosed failed to start')
    }
    console.log(`goosed ready on port ${port}`)
  }, 30_000)

  afterAll(async () => {
    if (goosed) {
      goosed.kill('SIGTERM')
      await sleep(1500)
      if (!goosed.killed) goosed.kill('SIGKILL')
    }
    try { await rm(tmpDir, { recursive: true, force: true }) } catch { /* ignore */ }
  })

  const authHeaders = {
    'Content-Type': 'application/json',
    'X-Secret-Key': SECRET,
  }

  it('goosed /status is healthy', async () => {
    const res = await fetch(`${baseUrl}/status`, { signal: AbortSignal.timeout(5000) })
    expect(res.ok).toBe(true)
  })

  it('goosed replies via LLM through session events', async () => {
    // 1. Create a session with provider configured via recipe settings
    //    goosed requires provider_name on the session for new sessions
    const startRes = await fetch(`${baseUrl}/agent/start`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        working_dir: tmpDir,
        recipe: {
          version: '1.0.0',
          title: 'LLM Health Test',
          description: 'Test LLM connectivity',
          instructions: 'You are a helpful assistant. Keep responses concise.',
          settings: {
            goose_provider: agentConfig.provider,
            goose_model: agentConfig.model,
          },
        },
      }),
      signal: AbortSignal.timeout(10_000),
    })
    if (!startRes.ok) {
      const errBody = await startRes.text()
      console.error(`agent/start failed (${startRes.status}): ${errBody}`)
    }
    expect(startRes.ok).toBe(true)
    const { id: sessionId } = await startRes.json() as { id: string }
    console.log(`Session created: ${sessionId}`)

    // Wait for background extension loading to complete
    await sleep(1000)

    // 2. Resume the session to load the provider and extensions (start → resume → reply)
    const resumeRes = await fetch(`${baseUrl}/agent/resume`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        session_id: sessionId,
        load_model_and_extensions: true,
      }),
      signal: AbortSignal.timeout(10_000),
    })
    if (!resumeRes.ok) {
      const errBody = await resumeRes.text()
      console.error(`agent/resume failed (${resumeRes.status}): ${errBody}`)
    }
    expect(resumeRes.ok).toBe(true)
    console.log('Session resumed')

    // Wait for provider + extensions to initialize
    await sleep(2000)

    // 3. Subscribe to session events and submit a reply.
    const userMessage = {
      role: 'user',
      created: Math.floor(Date.now() / 1000),
      content: [{ type: 'text', text: 'Reply with exactly one word: hello' }],
      metadata: { userVisible: true, agentVisible: true },
    }
    const requestId = randomUUID()
    const eventsController = new AbortController()

    const eventsRes = await fetch(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/events`, {
      method: 'GET',
      headers: authHeaders,
      signal: eventsController.signal,
    })
    expect(eventsRes.ok).toBe(true)
    expect(eventsRes.headers.get('content-type')).toContain('text/event-stream')

    const replyRes = await fetch(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/reply`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ request_id: requestId, user_message: userMessage }),
      signal: AbortSignal.timeout(60_000),
    })

    expect(replyRes.ok).toBe(true)

    // 4. Consume SSE stream until this request finishes.
    const events: Array<Record<string, any>> = []
    const reader = eventsRes.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const timeoutAt = Date.now() + 60_000
    try {
      while (Date.now() < timeoutAt) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let separatorIndex
        while ((separatorIndex = buffer.indexOf('\n\n')) >= 0) {
          const eventBlock = buffer.slice(0, separatorIndex)
          buffer = buffer.slice(separatorIndex + 2)
          const data = eventBlock
            .split('\n')
            .filter(line => line.startsWith('data:'))
            .map(line => line.replace(/^data:\s*/, ''))
            .join('\n')
          if (!data) continue
          let event: Record<string, any>
          try { event = JSON.parse(data) } catch { continue }
          const eventRequestId = event.chat_request_id || event.request_id
          if (eventRequestId && eventRequestId !== requestId) continue
          if (event.type === 'ActiveRequests' || event.type === 'Ping') continue
          events.push(event)
          if (event.type === 'Finish' || event.type === 'Error') {
            eventsController.abort()
            break
          }
        }
        if (events.some(event => event.type === 'Finish' || event.type === 'Error')) break
      }
    } finally {
      eventsController.abort()
    }

    // 5. Extract assistant text from Message events
    const assistantText = events
      .filter(e => e.type === 'Message' && e.message)
      .flatMap(e => (e.message.content || []) as Array<{ type: string; text?: string }>)
      .filter(c => c.type === 'text' && typeof c.text === 'string')
      .map(c => c.text || '')
      .join('')

    console.log(`goosed LLM response: "${assistantText}"`)

    // Dump goosed logs for debugging
    console.log('goosed logs:', logs.slice(-10).join('\n'))

    // Check for errors in SSE events
    const errors = events.filter(e => e.type === 'Error')
    if (errors.length > 0) {
      console.error('SSE errors:', JSON.stringify(errors))
    }
    expect(errors).toHaveLength(0)

    expect(assistantText.length).toBeGreaterThan(0)
    expect(assistantText.toLowerCase()).toContain('hello')
  }, 90_000)
})
