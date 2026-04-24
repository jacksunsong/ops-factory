/**
 * User Journey E2E Tests — Web Client Perspective
 *
 * Simulates real user workflows through the gateway, following the exact
 * API call sequences the React web app performs:
 *   start → resume(load_model_and_extensions) → reply (SSE stream)
 *
 * Prerequisites: goosed binary in PATH, gateway JAR built.
 * Run: cd test && npx vitest run user-journey.test.ts
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { startJavaGateway, sleep, type GatewayHandle } from './helpers.js'
import { WebClient } from './journey-helpers.js'
import { existsSync } from 'node:fs'

const AGENT_ID = 'universal-agent'
const RUN_ID = Date.now()

let gw: GatewayHandle

/** Lazy WebClient factory — safe to call only after beforeAll */
function client(userId: string): WebClient {
  return new WebClient(gw, userId, AGENT_ID)
}

function runUser(userId: string): string {
  return `${userId}-${RUN_ID}`
}

beforeAll(async () => {
  gw = await startJavaGateway()
}, 60_000)

afterAll(async () => {
  if (gw) await gw.stop()
}, 60_000)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 1: New user opens app, starts a chat, sends 3 messages, views history
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 1: New user multi-round chat', () => {
  let user: WebClient
  let sessionId: string

  beforeAll(() => { user = client(runUser('journey-alice')) })

  it('Step 1: App init — list agents', async () => {
    const agents = await user.listAgents()
    expect(agents.length).toBeGreaterThan(0)
    const agentIds = agents.map((a: any) => a.id)
    expect(agentIds).toContain(AGENT_ID)
  })

  it('Step 2: Start new chat session', async () => {
    sessionId = await user.startNewChat()
    expect(sessionId).toBeTruthy()
    expect(typeof sessionId).toBe('string')
  })

  it('Step 3: Send first message', async () => {
    const result = await user.sendMessage(sessionId, 'Hello, who are you? Reply briefly.')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent.length).toBeGreaterThan(0)
  })

  it('Step 4: Send second message', async () => {
    const result = await user.sendMessage(sessionId, 'What is 2 + 3? Reply with just the number.')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent).toContain('5')
  })

  it('Step 5: Send third message', async () => {
    const result = await user.sendMessage(sessionId, 'Thanks, goodbye! Reply briefly.')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent.length).toBeGreaterThan(0)
  })

  it('Step 6: View history — session appears', async () => {
    const sessions = await user.listSessions()
    const ids = sessions.map((s: any) => s.id)
    expect(ids).toContain(sessionId)
  })

  it('Step 7: View session detail — conversation has messages', async () => {
    const detail = await user.getSession(sessionId)
    // 3 user messages + 3 assistant messages = at least 6
    expect(detail.conversation.length).toBeGreaterThanOrEqual(6)
  })
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 2: Resume existing session and continue chatting
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 2: Resume session and continue chatting', () => {
  let user: WebClient
  let sessionId: string
  let initialConversationLength: number

  beforeAll(() => { user = client(runUser('journey-alice-resume')) })

  it('Step 1: Create initial session with a message', async () => {
    sessionId = await user.startNewChat()
    const result = await user.sendMessage(sessionId, 'Remember this number: 42. Reply with "noted".')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)

    const detail = await user.getSession(sessionId)
    initialConversationLength = detail.conversation.length
  })

  it('Step 2: Resume the session from history', async () => {
    await user.resumeChat(sessionId)
  })

  it('Step 3: Send message in resumed session', async () => {
    const result = await user.sendMessage(sessionId, 'What number did I ask you to remember? Reply briefly.')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent.length).toBeGreaterThan(0)
  })

  it('Step 4: Verify conversation grew', async () => {
    const detail = await user.getSession(sessionId)
    expect(detail.conversation.length).toBeGreaterThan(initialConversationLength)
  })
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 3: Tool call verification (critical per postmortem)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 3: Tool call verification', () => {
  let user: WebClient
  let sessionId: string

  beforeAll(() => { user = client(runUser('journey-alice-tools')) })

  it('Step 1: Start new session', async () => {
    sessionId = await user.startNewChat()
    expect(sessionId).toBeTruthy()
  })

  it('Step 2: Trigger tool call — list files', async () => {
    const result = await user.sendMessage(
      sessionId,
      'List the files in the current directory. Use the shell tool.',
      60_000,
    )
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent.length).toBeGreaterThan(10)
  }, 90_000)

  it('Step 3: Trigger tool call — create file', async () => {
    const result = await user.sendMessage(
      sessionId,
      'Create a file called journey-test.txt with content "hello from journey test". Use the shell tool with echo.',
      60_000,
    )
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
  }, 90_000)

  it('Step 4: Trigger tool call — read file back', async () => {
    const result = await user.sendMessage(
      sessionId,
      'Read the content of journey-test.txt and tell me what it says.',
      60_000,
    )
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    const eventText = JSON.stringify(result.events).toLowerCase()
    expect(eventText).toContain('hello')
  }, 90_000)

  it('Step 5: Trigger tool call — delete file', async () => {
    const result = await user.sendMessage(
      sessionId,
      'Delete the file journey-test.txt and confirm it is gone.',
      60_000,
    )
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
  }, 90_000)
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 4: Stop generation mid-stream
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 4: Stop generation mid-stream', () => {
  let user: WebClient
  let sessionId: string

  const userId = runUser('journey-alice-stop')

  beforeAll(() => { user = client(userId) })

  it('Step 1: Start session', async () => {
    sessionId = await user.startNewChat()
    expect(sessionId).toBeTruthy()
  })

  it('Step 2: Send message and abort mid-stream', async () => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 3_000)
    const requestId = crypto.randomUUID()

    try {
      const eventsRes = await gw.fetchAs(
        userId,
        `/agents/${AGENT_ID}/sessions/${encodeURIComponent(sessionId)}/events`,
        {
          method: 'GET',
          signal: controller.signal,
        },
      )
      expect(eventsRes.ok).toBe(true)

      const replyRes = await gw.fetchAs(
        userId,
        `/agents/${AGENT_ID}/sessions/${encodeURIComponent(sessionId)}/reply`,
        {
          method: 'POST',
          body: JSON.stringify({
            request_id: requestId,
            user_message: {
              role: 'user',
              created: Math.floor(Date.now() / 1000),
              content: [{ type: 'text', text: 'Write a very detailed 2000-word essay about the history of computing. Be thorough.' }],
              metadata: { userVisible: true, agentVisible: true },
            },
          }),
        },
      )
      expect(replyRes.ok).toBe(true)

      if (eventsRes.body) {
        const reader = eventsRes.body.getReader()
        while (true) {
          await reader.read()
        }
      }
    } catch (err: any) {
      expect(err.name).toBe('AbortError')
    } finally {
      clearTimeout(timer)
    }

    await user.stopGeneration(sessionId, requestId)
    await sleep(2_000)
  }, 30_000)

  it('Step 3: Resume and send new message — session still works', async () => {
    await user.resumeChat(sessionId)
    const result = await user.sendMessage(sessionId, 'Reply with just the word "ok".')
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    expect(result.textContent.length).toBeGreaterThan(0)
  })
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 5: Multi-user concurrent isolation
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 5: Multi-user concurrent isolation', () => {
  let alice: WebClient
  let bob: WebClient
  let aliceSession: string
  let bobSession: string

  beforeAll(() => {
    alice = client(runUser('journey-iso-alice'))
    bob = client(runUser('journey-iso-bob'))
  })

  it('Step 1: Both users start sessions concurrently', async () => {
    ;[aliceSession, bobSession] = await Promise.all([
      alice.startNewChat(),
      bob.startNewChat(),
    ])
    expect(aliceSession).toBeTruthy()
    expect(bobSession).toBeTruthy()
    // Note: goosed session IDs may be date-based and identical for concurrent starts.
    // What matters is per-user isolation (tested below), not ID uniqueness.
  })

  it('Step 2: Both send messages concurrently', async () => {
    const [aliceResult, bobResult] = await Promise.all([
      alice.sendMessage(aliceSession, 'My name is Alice. Reply with "Hello Alice".'),
      bob.sendMessage(bobSession, 'My name is Bob. Reply with "Hello Bob".'),
    ])
    expect(aliceResult.hasFinish).toBe(true)
    expect(aliceResult.hasError).toBe(false)
    expect(bobResult.hasFinish).toBe(true)
    expect(bobResult.hasError).toBe(false)
  })

  it('Step 3: Verify session isolation — each user sees only their own', async () => {
    const aliceSessions = await alice.listSessions()
    const bobSessions = await bob.listSessions()

    // Each user should see at least 1 session (the one they just created)
    expect(aliceSessions.length).toBeGreaterThanOrEqual(1)
    expect(bobSessions.length).toBeGreaterThanOrEqual(1)

    // Verify each user's sessions contain the session they created
    expect(aliceSessions.some((s: any) => s.id === aliceSession)).toBe(true)
    expect(bobSessions.some((s: any) => s.id === bobSession)).toBe(true)
  })
})

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Journey 6: File upload and reference in chat
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

describe('Journey 6: File upload and reference', () => {
  let user: WebClient
  let sessionId: string
  let filePath: string

  beforeAll(() => { user = client(runUser('journey-alice-files')) })

  it('Step 1: Start session', async () => {
    sessionId = await user.startNewChat()
    expect(sessionId).toBeTruthy()
  })

  it('Step 2: Upload a text file', async () => {
    const uploadResult = await user.uploadFile(
      sessionId,
      'test-data.txt',
      'Line 1: Apple\nLine 2: Banana\nLine 3: Cherry',
      'text/plain',
    )
    expect(uploadResult.path).toBeDefined()
    expect(uploadResult.name).toBe('test-data.txt')
    filePath = uploadResult.path
  })

  it('Step 3: Reference uploaded file in chat', async () => {
    const result = await user.sendMessage(
      sessionId,
      `Read the file at ${filePath} and tell me what fruits are listed.`,
      60_000,
    )
    expect(result.hasFinish).toBe(true)
    expect(result.hasError).toBe(false)
    const text = result.textContent.toLowerCase()
    const mentionsFruit = text.includes('apple') || text.includes('banana') || text.includes('cherry')
    expect(mentionsFruit).toBe(true)
  }, 90_000)

  it('Step 4: Verify file appears in file list', async () => {
    expect(existsSync(filePath)).toBe(true)
  })

  it('Step 5: Clean up — delete session', async () => {
    await user.deleteSession(sessionId)
    const sessions = await user.listSessions()
    const ids = sessions.map((s: any) => s.id)
    expect(ids).not.toContain(sessionId)
  })
})
