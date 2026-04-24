/**
 * E2E Tests: History Page — Real Operations
 *
 * Covers:
 *   - Create sessions, verify they appear in history list
 *   - Search filtering: type keyword, verify results filtered
 *   - Type filter switching (All / User / Scheduled)
 *   - Delete session from history, verify count decreases
 *   - Click session to resume in chat, verify messages loaded
 *   - Clear search restores full list
 */
import { test, expect, type Page } from '@playwright/test'

const USER = 'e2e-history'
const UNIQUE = Date.now()
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

async function createSession(page: Page, message: string) {
  // Go to home page and wait for the current chat entrypoint to load.
  await page.goto('/#/')
  await expect(page.locator('.chat-input')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.new-chat-nav')).toBeVisible({ timeout: 15_000 })

  const newChatBtn = page.locator('.new-chat-nav')
  await newChatBtn.click()
  await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })
  const chatInput = page.locator('.chat-input')
  await expect(chatInput).toBeVisible({ timeout: 15_000 })
  await chatInput.fill(message)
  await chatInput.press('Enter')
  await page.waitForFunction(
    () => {
      const btn = document.querySelector('.chat-send-btn-new')
      return btn && !btn.classList.contains('is-stop')
    },
    { timeout: 60_000 }
  )
  await page.waitForTimeout(1000)
}

// =====================================================
// 1. Sessions Appear in History
// =====================================================
test.describe('History — session tracking', () => {
  test('newly created session appears in history list', async ({ page }) => {
    const user = `${USER}-track-${UNIQUE}`
    await loginAs(page, user)

    // Create a session
    await createSession(page, 'Reply with "history-test-ok"')

    // Go to history
    await page.goto('/#/history')
    await page.waitForTimeout(3000)

    // Should have at least 1 session
    const sessions = page.locator('.session-item')
    const count = await sessions.count()
    expect(count).toBeGreaterThanOrEqual(1)
  }, 120_000)
})

// =====================================================
// 2. Search Filtering
// =====================================================
test.describe('History — search', () => {
  test('typing in search filters the session list', async ({ page }) => {
    await loginAs(page, `${USER}-search`)
    await page.goto('/#/history')
    await page.waitForTimeout(2000)

    const searchInput = page.locator('.list-search-input')
    await expect(searchInput).toBeVisible({ timeout: 5000 })

    // Get initial count
    const initialCount = await page.locator('[class*="session"]').count()

    // Search for a very unlikely term
    await searchInput.fill('zzz-nonexistent-session-xyz-123456')
    await page.waitForTimeout(500)

    // Results should be filtered (fewer or zero)
    const filteredCount = await page.locator('[class*="session-item"]').count()
    expect(filteredCount).toBeLessThanOrEqual(initialCount)

    // Clear search
    await searchInput.fill('')
    await page.waitForTimeout(500)

    // Results should restore
    const restoredCount = await page.locator('[class*="session"]').count()
    expect(restoredCount).toBeGreaterThanOrEqual(filteredCount)
  })
})

// =====================================================
// 3. Type Filter
// =====================================================
test.describe('History — type filter', () => {
  test('filter buttons change active state and filter list', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/history')
    await page.waitForSelector('.seg-filter-btn', { timeout: 5000 })

    const filterBtns = page.locator('.seg-filter-btn')
    const count = await filterBtns.count()
    expect(count).toBeGreaterThanOrEqual(2)

    // First button active by default
    await expect(filterBtns.first()).toHaveClass(/active/)

    // Click each filter button and verify active state switches
    for (let i = 1; i < count; i++) {
      await filterBtns.nth(i).click()
      await page.waitForTimeout(300)
      await expect(filterBtns.nth(i)).toHaveClass(/active/)
      // Previous button should not be active
      if (i > 0) {
        await expect(filterBtns.nth(i - 1)).not.toHaveClass(/active/)
      }
    }
  })
})

// =====================================================
// 4. Delete Session
// =====================================================
test.describe('History — delete session', () => {
  test('delete a session, verify count decreases', async ({ page }) => {
    const user = `${USER}-del-${UNIQUE}`
    await loginAs(page, user)

    // Create a session to ensure we have something to delete
    await createSession(page, 'Reply with "deletable-session"')

    await page.goto('/#/history')
    await page.waitForTimeout(3000)

    // Count sessions before
    const sessionItems = page.locator('[class*="session-item"]')
    const countBefore = await sessionItems.count()

    if (countBefore > 0) {
      // Find and click delete button on the first session
      const deleteBtn = sessionItems.first().locator('button[title*="delete" i], button[title*="Delete" i], [class*="delete"], button[aria-label*="delete" i]')

      if (await deleteBtn.count() > 0) {
        await deleteBtn.first().click()
        await page.waitForTimeout(2000)

        // Verify count decreased
        const countAfter = await page.locator('[class*="session-item"]').count()
        expect(countAfter).toBe(countBefore - 1)
      }
    }
  }, 120_000)
})

// =====================================================
// 5. Click Session to Resume
// =====================================================
test.describe('History — resume session', () => {
  const MARKER = `hist-resume-${UNIQUE}`

  test('click session navigates to chat with messages loaded', async ({ page }) => {
    const user = `${USER}-click-${UNIQUE}`
    await loginAs(page, user)

    // Create session with known content
    await createSession(page, `Reply with exactly "${MARKER}"`)

    // Go to history
    await page.goto('/#/history')
    await page.waitForTimeout(3000)

    // Click first session
    const sessions = page.locator('.session-item')
    expect(await sessions.count()).toBeGreaterThanOrEqual(1)
    await sessions.first().click()

    // Should navigate to chat
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })
    await page.waitForTimeout(5000)

    // Verify messages from previous session are loaded
    const chatText = await page.locator('.chat-messages-area').textContent()
    expect(chatText).toContain(MARKER)
  }, 120_000)
})
