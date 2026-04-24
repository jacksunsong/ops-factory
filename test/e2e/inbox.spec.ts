/**
 * E2E Tests: Inbox Page — Real Operations
 *
 * Covers:
 *   - Page rendering with unread count
 *   - Mark All Read button clears unread count
 *   - Dismiss button removes session from inbox
 *   - Open button navigates to chat with correct session
 *   - Sidebar badge reflects unread count
 *   - Empty state when no unread sessions
 *   - Sessions grouped by agent
 */
import { test, expect, type Page } from '@playwright/test'

const USER = 'e2e-inbox'

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, username)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(500)
}

// =====================================================
// 1. Page Rendering & Unread Count
// =====================================================
test.describe('Inbox — rendering', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER)
  })

  test('renders page title and unread count', async ({ page }) => {
    await page.goto('/#/inbox')
    await expect(page.locator('.page-title')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.inbox-count')).toBeVisible()

    // Unread count should contain a number
    const countText = await page.locator('.inbox-count').textContent()
    expect(countText).toMatch(/\d+/)
  })

  test('renders toolbar with actions', async ({ page }) => {
    await page.goto('/#/inbox')
    await expect(page.locator('.inbox-toolbar')).toBeVisible({ timeout: 5000 })
  })
})

// =====================================================
// 2. Empty State vs Session List
// =====================================================
test.describe('Inbox — content display', () => {
  test('shows session items or empty state', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    const hasItems = await page.locator('.inbox-item').count() > 0
    const hasEmpty = await page.locator('.empty-state').isVisible()

    expect(hasItems || hasEmpty).toBeTruthy()

    if (hasEmpty) {
      await expect(page.locator('.empty-state-title')).toBeVisible()
    }

    if (hasItems) {
      // Session items should have title and metadata
      const first = page.locator('.inbox-item').first()
      await expect(first.locator('.inbox-item-title')).toBeVisible()
      await expect(first.locator('.inbox-item-meta')).toBeVisible()
    }
  })
})

// =====================================================
// 3. Open Session → Navigate to Chat
// =====================================================
test.describe('Inbox — open session', () => {
  test('Open button navigates to chat with session loaded', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    const items = page.locator('.inbox-item')
    if (await items.count() > 0) {
      const openBtn = items.first().locator('.btn-primary:has-text("Open"), .btn-primary:has-text("打开")')
      if (await openBtn.count() > 0) {
        await openBtn.click()

        // Should navigate to chat with sessionId
        await expect(page).toHaveURL(/\/chat/)
        await page.waitForTimeout(3000)

        // Chat area should have content (messages from the session)
        const chatArea = page.locator('.chat-messages-area')
        await expect(chatArea).toBeVisible({ timeout: 10_000 })
      }
    }
  })
})

// =====================================================
// 4. Dismiss Session
// =====================================================
test.describe('Inbox — dismiss', () => {
  test('Dismiss button removes session from inbox list', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    const items = page.locator('.inbox-item')
    const countBefore = await items.count()

    if (countBefore > 0) {
      const dismissBtn = items.first().locator('.btn-secondary:has-text("Dismiss"), .btn-secondary:has-text("忽略")')
      if (await dismissBtn.count() > 0) {
        await dismissBtn.click()
        await page.waitForTimeout(1000)

        // Count should decrease
        const countAfter = await page.locator('.inbox-item').count()
        expect(countAfter).toBe(countBefore - 1)

        // Unread count in header should update
        const countText = await page.locator('.inbox-count').textContent()
        expect(countText).toContain(String(countAfter))
      }
    }
  })
})

// =====================================================
// 5. Mark All Read
// =====================================================
test.describe('Inbox — mark all read', () => {
  test('Mark All Read clears all items and updates count to 0', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    const items = page.locator('.inbox-item')
    if (await items.count() > 0) {
      const markAllBtn = page.locator('button:has-text("Mark All Read"), button:has-text("全部已读")')
      if (await markAllBtn.count() > 0) {
        await markAllBtn.click()
        await page.waitForTimeout(1000)

        // All items should be removed
        const countAfter = await page.locator('.inbox-item').count()
        expect(countAfter).toBe(0)

        // Unread count should show 0
        const countText = await page.locator('.inbox-count').textContent()
        expect(countText).toContain('0')

        // Empty state should appear
        await expect(page.locator('.empty-state')).toBeVisible()
      }
    }
  })
})

// =====================================================
// 6. Sidebar Badge
// =====================================================
test.describe('Inbox — sidebar badge', () => {
  test('sidebar badge count matches inbox header count', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    // Get inbox header count
    const headerCount = await page.locator('.inbox-count').textContent()
    const unreadNum = parseInt(headerCount?.match(/\d+/)?.[0] || '0')

    // Check sidebar badge
    const badge = page.locator('.sidebar-badge')
    if (unreadNum > 0) {
      await expect(badge).toBeVisible()
      const badgeText = await badge.textContent()
      expect(parseInt(badgeText || '0')).toBe(unreadNum)
    } else {
      // Badge should not be visible when count is 0
      await expect(badge).not.toBeVisible()
    }
  })
})

// =====================================================
// 7. Sessions Grouped by Agent
// =====================================================
test.describe('Inbox — grouping', () => {
  test('sessions are grouped under agent headings', async ({ page }) => {
    await loginAs(page, USER)
    await page.goto('/#/inbox')
    await page.waitForTimeout(3000)

    const groups = page.locator('.inbox-group')
    if (await groups.count() > 0) {
      // Each group has a title (agent ID)
      const firstTitle = await groups.first().locator('.inbox-group-title').textContent()
      expect(firstTitle!.trim().length).toBeGreaterThan(0)

      // Each group has at least one session item
      const items = groups.first().locator('.inbox-item')
      expect(await items.count()).toBeGreaterThanOrEqual(1)
    }
  })
})
