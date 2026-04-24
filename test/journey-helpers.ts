/**
 * Shared test utilities for user journey and stress tests.
 *
 * WebClient simulates the exact API call sequences the React web app performs,
 * so tests exercise the same code paths real users hit.
 */
import type { GatewayHandle } from './helpers.js'

// ─── Types ───────────────────────────────────────────────────────────────────

export interface ParsedEvent {
  type: string
  message?: any
  token_state?: any
  error?: string
  [key: string]: any
}

export interface ToolCallInfo {
  name: string
  input?: any
}

export interface SseResult {
  events: ParsedEvent[]
  textContent: string
  toolCalls: ToolCallInfo[]
  hasFinish: boolean
  hasError: boolean
  errorMessages: string[]
  durationMs: number
}

export interface UserReport {
  userId: string
  rounds: number
  successes: number
  failures: number
  errors: string[]
  responseTimes: number[]
  averageResponseTime: number
  maxResponseTime: number
}

export interface StressReport {
  testName: string
  totalUsers: number
  totalRounds: number
  overallSuccessRate: number
  userReports: UserReport[]
}

// ─── SSE parsing ─────────────────────────────────────────────────────────────

/** Build a goosed-compatible user Message */
export function makeUserMessage(text: string) {
  return {
    role: 'user',
    created: Math.floor(Date.now() / 1000),
    content: [{ type: 'text', text }],
    metadata: { userVisible: true, agentVisible: true },
  }
}

/** Parse SSE body text into structured events */
export function parseSseStream(body: string): ParsedEvent[] {
  return body
    .split('\n\n')
    .map(chunk => chunk.trim())
    .filter(Boolean)
    .flatMap(chunk => {
      const data = chunk
        .split('\n')
        .filter(line => line.startsWith('data:'))
        .map(line => line.replace(/^data:\s*/, ''))
        .join('\n')
      if (!data) return []
      try {
        return [JSON.parse(data) as ParsedEvent]
      } catch {
        return []
      }
    })
}

/** Extract concatenated text content from Message events */
function extractTextContent(events: ParsedEvent[]): string {
  return events
    .filter(e => e.type === 'Message' && e.message)
    .flatMap(e => (e.message.content || []) as Array<{ type: string; text?: string }>)
    .filter(c => c.type === 'text' && typeof c.text === 'string')
    .map(c => c.text || '')
    .join('')
}

/** Extract tool call info from Message events */
function extractToolCalls(events: ParsedEvent[]): ToolCallInfo[] {
  const tools: ToolCallInfo[] = []
  for (const event of events) {
    if (event.type !== 'Message' || !event.message?.content) continue
    for (const item of event.message.content) {
      if (item.type === 'toolUse' || item.type === 'tool_use') {
        tools.push({ name: item.name || item.toolName || 'unknown', input: item.input })
      }
    }
  }
  return tools
}

/** Build a full SseResult from raw SSE body */
export function buildSseResult(body: string, startTime: number): SseResult {
  const events = parseSseStream(body)
  const errorEvents = events.filter(e => e.type === 'Error')
  return {
    events,
    textContent: extractTextContent(events),
    toolCalls: extractToolCalls(events),
    hasFinish: events.some(e => e.type === 'Finish'),
    hasError: errorEvents.length > 0,
    errorMessages: errorEvents.map(e => e.error || JSON.stringify(e)),
    durationMs: Date.now() - startTime,
  }
}

// ─── WebClient ───────────────────────────────────────────────────────────────

/**
 * Simulates a web app user's interactions with the gateway.
 * Each method mirrors the API call sequence the React frontend performs.
 */
export class WebClient {
  constructor(
    private gw: GatewayHandle,
    public readonly userId: string,
    private agentId: string,
  ) {}

  /** Home page: list available agents */
  async listAgents(): Promise<any[]> {
    const res = await this.gw.fetchAs(this.userId, '/agents')
    if (!res.ok) throw new Error(`listAgents failed: ${res.status}`)
    const data = await res.json()
    return data.agents || data
  }

  /**
   * Start a new chat session (web app: Home → "New Chat").
   * Follows the canonical goosed flow: start → resume(load_model_and_extensions).
   * Returns the session ID.
   */
  async startNewChat(): Promise<string> {
    // Step 1: POST /agent/start
    const startRes = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/agent/start`,
      { method: 'POST', body: JSON.stringify({}) },
    )
    if (!startRes.ok) {
      const text = await startRes.text()
      throw new Error(`start failed (${startRes.status}): ${text}`)
    }
    const session = await startRes.json()
    const sessionId = session.id as string

    // Step 2: POST /agent/resume with load_model_and_extensions
    const resumeRes = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/agent/resume`,
      {
        method: 'POST',
        body: JSON.stringify({ session_id: sessionId, load_model_and_extensions: true }),
      },
    )
    if (!resumeRes.ok) {
      const text = await resumeRes.text()
      throw new Error(`resume after start failed (${resumeRes.status}): ${text}`)
    }

    return sessionId
  }

  /** Resume an existing session (web app: History → click session) */
  async resumeChat(sessionId: string): Promise<void> {
    const res = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/agent/resume`,
      {
        method: 'POST',
        body: JSON.stringify({ session_id: sessionId, load_model_and_extensions: true }),
      },
    )
    if (!res.ok) {
      const text = await res.text()
      throw new Error(`resumeChat failed (${res.status}): ${text}`)
    }
  }

  /**
   * Send a message and wait for the full SSE response (web app: Chat → send).
   * Returns structured SseResult with parsed events, text, tool calls, timing.
   */
  async sendMessage(sessionId: string, text: string, timeoutMs = 90_000): Promise<SseResult> {
    const startTime = Date.now()
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    const requestId = crypto.randomUUID()
    const events: ParsedEvent[] = []
    try {
      const eventsRes = await this.gw.fetchAs(
        this.userId,
        `/agents/${this.agentId}/sessions/${encodeURIComponent(sessionId)}/events`,
        {
          method: 'GET',
          signal: controller.signal,
        },
      )
      if (!eventsRes.ok || !eventsRes.body) {
        const body = await eventsRes.text()
        throw new Error(`events failed (${eventsRes.status}): ${body}`)
      }

      const submitRes = await this.gw.fetchAs(
        this.userId,
        `/agents/${this.agentId}/sessions/${encodeURIComponent(sessionId)}/reply`,
        {
          method: 'POST',
          body: JSON.stringify({
            request_id: requestId,
            user_message: makeUserMessage(text),
          }),
        },
      )
      if (!submitRes.ok) {
        const body = await submitRes.text()
        throw new Error(`submit failed (${submitRes.status}): ${body}`)
      }

      const reader = eventsRes.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
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
          const event = JSON.parse(data) as ParsedEvent
          const eventRequestId = event.chat_request_id || event.request_id
          if (eventRequestId && eventRequestId !== requestId) continue
          if (event.type === 'ActiveRequests' || event.type === 'Ping') continue
          events.push(event)
          if (event.type === 'Finish' || event.type === 'Error') {
            controller.abort()
            return {
              events,
              textContent: extractTextContent(events),
              toolCalls: extractToolCalls(events),
              hasFinish: events.some(e => e.type === 'Finish'),
              hasError: events.some(e => e.type === 'Error'),
              errorMessages: events.filter(e => e.type === 'Error').map(e => e.error || JSON.stringify(e)),
              durationMs: Date.now() - startTime,
            }
          }
        }
      }

      return {
        events,
        textContent: extractTextContent(events),
        toolCalls: extractToolCalls(events),
        hasFinish: events.some(e => e.type === 'Finish'),
        hasError: events.some(e => e.type === 'Error'),
        errorMessages: events.filter(e => e.type === 'Error').map(e => e.error || JSON.stringify(e)),
        durationMs: Date.now() - startTime,
      }
    } catch (err: any) {
      if (err.name === 'AbortError') {
        if (!events.some(e => e.type === 'Finish')) {
          await this.stopGeneration(sessionId, requestId)
        }
        return {
          events,
          textContent: extractTextContent(events),
          toolCalls: extractToolCalls(events),
          hasFinish: events.some(e => e.type === 'Finish'),
          hasError: !events.some(e => e.type === 'Finish'),
          errorMessages: events.some(e => e.type === 'Finish') ? [] : [`Timeout after ${timeoutMs}ms`],
          durationMs: Date.now() - startTime,
        }
      }
      throw err
    } finally {
      clearTimeout(timer)
    }
  }

  /** Stop generation (web app: click stop button) */
  async stopGeneration(sessionId: string, requestId: string): Promise<void> {
    await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/sessions/${encodeURIComponent(sessionId)}/cancel`,
      {
        method: 'POST',
        body: JSON.stringify({ request_id: requestId }),
      },
    ).catch(() => {
      // Ignore errors — goosed may have already finished
    })
  }

  /** History page: list all sessions for this agent */
  async listSessions(): Promise<any[]> {
    const res = await this.gw.fetchAs(this.userId, `/agents/${this.agentId}/sessions`)
    if (!res.ok) throw new Error(`listSessions failed: ${res.status}`)
    const data = await res.json()
    return data.sessions || []
  }

  /** Session detail */
  async getSession(sessionId: string): Promise<any> {
    const res = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/sessions/${sessionId}`,
    )
    if (!res.ok) throw new Error(`getSession failed: ${res.status}`)
    return res.json()
  }

  /** Delete a session */
  async deleteSession(sessionId: string): Promise<void> {
    const res = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/sessions/${sessionId}`,
      { method: 'DELETE' },
    )
    if (!res.ok) throw new Error(`deleteSession failed: ${res.status}`)
  }

  /** Upload a file (web app: chat file attachment) */
  async uploadFile(
    sessionId: string,
    fileName: string,
    content: string | Buffer,
    mimeType: string,
  ): Promise<any> {
    const boundary = '----TestBoundary' + Date.now()
    const bodyParts = [
      `--${boundary}\r\n`,
      `Content-Disposition: form-data; name="sessionId"\r\n\r\n`,
      `${sessionId}\r\n`,
      `--${boundary}\r\n`,
      `Content-Disposition: form-data; name="file"; filename="${fileName}"\r\n`,
      `Content-Type: ${mimeType}\r\n\r\n`,
      `${content}\r\n`,
      `--${boundary}--\r\n`,
    ].join('')

    const res = await this.gw.fetchAs(
      this.userId,
      `/agents/${this.agentId}/files/upload`,
      {
        method: 'POST',
        headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
        body: bodyParts,
      },
    )
    if (!res.ok) {
      const text = await res.text()
      throw new Error(`uploadFile failed (${res.status}): ${text}`)
    }
    return res.json()
  }

  /** File browser page: list all files for this agent */
  async listFiles(): Promise<any[]> {
    const res = await this.gw.fetchAs(this.userId, `/agents/${this.agentId}/files`)
    if (!res.ok) throw new Error(`listFiles failed: ${res.status}`)
    const data = await res.json()
    return data.files || data
  }
}

// ─── Stress test utilities ───────────────────────────────────────────────────

const SIMPLE_PROMPTS = [
  (n: number) => `What is ${n} times ${n}? Reply with just the number.`,
  (n: number) => `Reply with only the word "round-${n}-ok".`,
  (n: number) => `Count from 1 to ${Math.min(n, 5)} separated by commas.`,
]

const TOOL_PROMPTS = [
  'List the files in the current directory.',
  'Run the command: echo "stress test ok"',
  'Show the current working directory path.',
]

/**
 * Run a multi-round conversation for a single user.
 * Used by stress tests to drive concurrent user sessions.
 */
export async function runUserConversation(
  client: WebClient,
  rounds: number,
  opts?: { toolCallEveryN?: number; roundTimeoutMs?: number },
): Promise<UserReport> {
  const report: UserReport = {
    userId: client.userId,
    rounds,
    successes: 0,
    failures: 0,
    errors: [],
    responseTimes: [],
    averageResponseTime: 0,
    maxResponseTime: 0,
  }

  const timeoutMs = opts?.roundTimeoutMs ?? 90_000

  let sessionId: string
  try {
    sessionId = await client.startNewChat()
  } catch (err: any) {
    report.failures = rounds
    report.errors.push(`Failed to start session: ${err.message}`)
    return report
  }

  for (let round = 1; round <= rounds; round++) {
    let prompt: string
    if (opts?.toolCallEveryN && round % opts.toolCallEveryN === 0) {
      prompt = TOOL_PROMPTS[(round / opts.toolCallEveryN - 1) % TOOL_PROMPTS.length]
    } else {
      const fn = SIMPLE_PROMPTS[(round - 1) % SIMPLE_PROMPTS.length]
      prompt = fn(round)
    }

    try {
      const result = await client.sendMessage(sessionId, prompt, timeoutMs)
      report.responseTimes.push(result.durationMs)

      if (result.hasFinish && !result.hasError) {
        report.successes++
      } else {
        report.failures++
        const reason = result.hasError
          ? result.errorMessages.join('; ')
          : 'No Finish event'
        report.errors.push(`Round ${round}: ${reason}`)
      }
    } catch (err: any) {
      report.failures++
      report.errors.push(`Round ${round}: ${err.message}`)
      report.responseTimes.push(timeoutMs)
    }
  }

  if (report.responseTimes.length > 0) {
    report.averageResponseTime =
      report.responseTimes.reduce((a, b) => a + b, 0) / report.responseTimes.length
    report.maxResponseTime = Math.max(...report.responseTimes)
  }

  return report
}

/** Build aggregated stress report from individual user reports */
export function buildStressReport(testName: string, userReports: UserReport[]): StressReport {
  const totalRounds = userReports.reduce((s, r) => s + r.rounds, 0)
  const totalSuccesses = userReports.reduce((s, r) => s + r.successes, 0)
  return {
    testName,
    totalUsers: userReports.length,
    totalRounds,
    overallSuccessRate: totalRounds > 0 ? totalSuccesses / totalRounds : 0,
    userReports,
  }
}

/** Format stress report as a readable table for console output */
export function formatStressReport(report: StressReport): string {
  const lines: string[] = []
  const w = 56
  lines.push('╔' + '═'.repeat(w) + '╗')
  lines.push('║ ' + `Stress Test: ${report.testName}`.padEnd(w - 1) + '║')
  lines.push('╠' + '═'.repeat(w) + '╣')

  const successPct = (report.overallSuccessRate * 100).toFixed(0)
  const totalSuccesses = report.userReports.reduce((s, r) => s + r.successes, 0)
  lines.push(
    '║ ' +
      `Users: ${report.totalUsers} | Rounds/user: ${report.totalRounds / report.totalUsers} | Total: ${report.totalRounds}`.padEnd(w - 1) +
      '║',
  )
  lines.push(
    '║ ' +
      `Success rate: ${successPct}% (${totalSuccesses}/${report.totalRounds})`.padEnd(w - 1) +
      '║',
  )
  lines.push('╠' + '─'.repeat(w) + '╣')
  lines.push(
    '║ ' + 'User'.padEnd(14) + '| Pass | Fail | Avg(s) | Max(s)'.padEnd(w - 15) + '║',
  )

  for (const u of report.userReports) {
    const avg = (u.averageResponseTime / 1000).toFixed(1)
    const max = (u.maxResponseTime / 1000).toFixed(1)
    const row = `${u.userId.padEnd(14)}| ${String(u.successes).padStart(4)} | ${String(u.failures).padStart(4)} | ${avg.padStart(6)} | ${max.padStart(6)}`
    lines.push('║ ' + row.padEnd(w - 1) + '║')
  }

  lines.push('╚' + '═'.repeat(w) + '╝')

  // Print errors if any
  const allErrors = report.userReports.flatMap(r => r.errors.map(e => `  [${r.userId}] ${e}`))
  if (allErrors.length > 0) {
    lines.push('')
    lines.push('Errors:')
    for (const e of allErrors.slice(0, 20)) {
      lines.push(e)
    }
    if (allErrors.length > 20) {
      lines.push(`  ... and ${allErrors.length - 20} more`)
    }
  }

  return lines.join('\n')
}
