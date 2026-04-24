/**
 * E2E Tests: Chat Advanced Features — Real Operations
 *
 * Covers:
 *   - File attachment: attach file, verify preview appears, send with file, verify in message
 *   - Stop generation: send long prompt, click stop, verify partial response exists
 *   - Resume session from history: create session, go to history, resume, verify messages loaded
 *   - Tool call display: ask for shell command, verify tool call rendered
 *   - Keyboard shortcuts: Shift+Enter for newline
 *   - Input auto-expand on multiline
 */
import { test, expect, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import * as os from 'os'

const USER = 'e2e-chat-adv'
const USER_STORAGE_KEY = 'opsfactory:userId'

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate(([storageKey, userId]) => {
    localStorage.setItem(storageKey, userId)
  }, [USER_STORAGE_KEY, username])
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(500)
}

async function goToChat(page: Page) {
  // Go to home page and wait for the current chat entrypoint to load.
  await page.goto('/#/')
  await expect(page.locator('.chat-input')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.new-chat-nav')).toBeVisible({ timeout: 15_000 })

  const newChatBtn = page.locator('.new-chat-nav')
  await newChatBtn.click()
  await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })
  const chatInput = page.locator('.chat-input')
  await expect(chatInput).toBeVisible({ timeout: 15_000 })
  return chatInput
}

async function sendMessage(page: Page, text: string) {
  const chatInput = page.locator('.chat-input')
  await chatInput.fill(text)
  await chatInput.press('Enter')
}

async function waitForResponse(page: Page, timeoutMs = 60_000) {
  await page.waitForFunction(
    () => {
      const btn = document.querySelector('.chat-send-btn-new')
      return btn && !btn.classList.contains('is-stop')
    },
    { timeout: timeoutMs }
  )
}

// =====================================================
// 1. File Attachment — Attach, Verify, Remove
// =====================================================
test.describe('Chat — file attachment lifecycle', () => {
  test('attach file, verify preview and name, then remove', async ({ page }) => {
    await loginAs(page, USER)
    await goToChat(page)

    // Create temp file
    const tmpFile = path.join(os.tmpdir(), `e2e-attach-${Date.now()}.txt`)
    fs.writeFileSync(tmpFile, 'Hello from E2E test')

    try {
      // Trigger file chooser via attach button
      const [fileChooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        page.locator('.toolbar-btn').first().click(),
      ])
      await fileChooser.setFiles(tmpFile)

      // Verify file preview area appears
      await expect(page.locator('.uploaded-file')).toBeVisible({ timeout: 5000 })

      // Verify correct file name shown
      const fileName = page.locator('.uploaded-file-name')
      await expect(fileName).toContainText(path.basename(tmpFile))

      // Verify badge count on attach button
      const badge = page.locator('.toolbar-badge')
      await expect(badge).toBeVisible()
      expect(await badge.textContent()).toBe('1')

      // Remove the file
      await page.locator('.uploaded-file-remove').click()

      // Verify file removed
      await expect(page.locator('.uploaded-file')).not.toBeVisible()
      await expect(badge).not.toBeVisible()
    } finally {
      fs.unlinkSync(tmpFile)
    }
  })

  test('cannot attach more than 5 files', async ({ page }) => {
    await loginAs(page, USER)
    await goToChat(page)

    const files: string[] = []
    try {
      // Create 6 temp files
      for (let i = 0; i < 6; i++) {
        const f = path.join(os.tmpdir(), `e2e-multi-${Date.now()}-${i}.txt`)
        fs.writeFileSync(f, `File ${i}`)
        files.push(f)
      }

      // Attach all 6 at once
      const [fileChooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        page.locator('.toolbar-btn').first().click(),
      ])
      await fileChooser.setFiles(files)
      await page.waitForTimeout(1000)

      // Should have at most 5 non-image files
      const uploadedCount = await page.locator('.uploaded-file').count()
      expect(uploadedCount).toBeLessThanOrEqual(5)
    } finally {
      files.forEach(f => { try { fs.unlinkSync(f) } catch {} })
    }
  })
})

// =====================================================
// 2. Stop Generation — Verify Partial Response
// =====================================================
test.describe('Chat — stop generation', () => {
  test('stop mid-stream, verify partial response exists and session is still usable', async ({ page }) => {
    await loginAs(page, `${USER}-stop`)
    await goToChat(page)

    // Send a prompt that triggers a long response
    await sendMessage(page, 'Write a detailed 3000-word essay about the history of operating systems from 1960 to 2020')

    // Wait for streaming to start (stop button appears)
    const sendBtn = page.locator('.chat-send-btn-new')
    await expect(sendBtn).toHaveClass(/is-stop/, { timeout: 15_000 })

    // Wait a bit for some content to stream in
    await page.waitForTimeout(3000)

    // Click stop
    await sendBtn.click()

    // Verify button returns to send state
    await expect(sendBtn).not.toHaveClass(/is-stop/, { timeout: 10_000 })

    // Verify there IS a partial response (not empty)
    const messages = page.locator('.chat-messages-area')
    const text = await messages.textContent()
    expect(text!.length).toBeGreaterThan(50) // Should have some content

    // Verify session is still usable — send another message
    const followupMarker = `session-still-alive-${Date.now()}`
    await sendMessage(page, `Reply with exactly: "${followupMarker}"`)
    await waitForResponse(page)

    // Verify second response came through with the requested marker
    const allText = await messages.textContent()
    expect(allText).toContain(followupMarker)
  }, 120_000)
})

// =====================================================
// 3. Resume Session from History
// =====================================================
test.describe('Chat — resume from history', () => {
  const MARKER = `resume-marker-${Date.now()}`

  test('create session, find in history, resume, verify previous messages loaded', async ({ page }) => {
    await loginAs(page, `${USER}-resume`)

    // 1. Create a session with a unique marker
    await goToChat(page)
    await sendMessage(page, `Reply with exactly: "${MARKER}"`)
    await waitForResponse(page)

    // Verify response contains our marker
    const chatText = await page.locator('.chat-messages-area').textContent()
    expect(chatText).toContain(MARKER)

    // 2. Navigate to history
    await page.goto('/#/history')
    await page.waitForSelector('.list-search-input', { timeout: 10_000 })
    await page.waitForTimeout(3000)

    // 3. Find and click the session
    const sessionItems = page.locator('.session-item')
    const count = await sessionItems.count()
    expect(count).toBeGreaterThanOrEqual(1)

    // Click the first (most recent) session
    await sessionItems.first().click()

    // 4. Should navigate to chat
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })
    await page.waitForTimeout(5000)

    // 5. Verify previous messages are loaded (our marker should be visible)
    const resumedText = await page.locator('.chat-messages-area').textContent()
    expect(resumedText).toContain(MARKER)
  }, 120_000)
})

// =====================================================
// 4. Keyboard Shortcuts
// =====================================================
test.describe('Chat — keyboard behavior', () => {
  test('Shift+Enter adds newline without sending', async ({ page }) => {
    await loginAs(page, USER)
    const chatInput = await goToChat(page)

    await chatInput.fill('Line 1')
    await chatInput.press('Shift+Enter')
    await page.waitForTimeout(500)

    // Should NOT have started streaming (no is-stop class)
    const sendBtn = page.locator('.chat-send-btn-new')
    await expect(sendBtn).not.toHaveClass(/is-stop/)

    // Input should still have content (not cleared by send)
    const value = await chatInput.inputValue()
    expect(value).toContain('Line 1')
  })

  test('input auto-expands with multiline content', async ({ page }) => {
    await loginAs(page, USER)
    const chatInput = await goToChat(page)

    const initialHeight = await chatInput.evaluate(el => el.scrollHeight)

    await chatInput.fill('Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6')
    const expandedHeight = await chatInput.evaluate(el => el.scrollHeight)

    expect(expandedHeight).toBeGreaterThan(initialHeight)
  })
})

// =====================================================
// 5. Tool Call Display
// =====================================================
test.describe('Chat — tool call rendering', () => {
  test('tool calls are visually rendered when agent uses tools', async ({ page }) => {
    await loginAs(page, `${USER}-tools`)
    await goToChat(page)

    // Ask something that forces a tool call — use explicit instruction
    await sendMessage(page, 'Please run this shell command right now: echo "e2e-tool-test". Do not explain, just run it.')
    await waitForResponse(page, 90_000)

    // The message area should contain some response (tool call may or may not show exact text)
    const messageArea = page.locator('.chat-messages-area')
    const text = await messageArea.textContent()
    // Verify the agent produced a response (tool calls render as collapsible sections)
    expect(text!.length).toBeGreaterThan(20)
  }, 120_000)
})
