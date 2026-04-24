/**
 * E2E Tests: Sidebar & Settings — Real Operations
 *
 * Covers:
 *   - Sidebar collapse/expand: verify nav text hidden/shown
 *   - New Chat button: navigates to /chat and creates new session
 *   - Language switch: change to Chinese, verify UI text changed, switch back to English
 *   - Logout: verify localStorage is cleared and the app returns home
 *   - Settings modal: verify tabs work, user info displayed
 */
import { test, expect, type Page } from '@playwright/test'

const USER = 'e2e-sidebar'

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
// 1. Sidebar Collapse / Expand
// =====================================================
test.describe('Sidebar — collapse/expand', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER)
  })

  test('collapse hides nav text, expand restores it', async ({ page }) => {
    // Expanded: logo text visible
    await expect(page.locator('.sidebar-logo-text')).toBeVisible()

    // Collapse
    await page.locator('.sidebar-toggle-btn').click()
    await page.waitForTimeout(300)
    await expect(page.locator('.sidebar')).toHaveClass(/collapsed/)
    await expect(page.locator('.sidebar-logo-text')).not.toBeVisible()

    // Username should be hidden too
    await expect(page.locator('.sidebar-user-name')).not.toBeVisible()

    // Expand
    await page.locator('.sidebar-toggle-btn').click()
    await page.waitForTimeout(300)
    await expect(page.locator('.sidebar')).not.toHaveClass(/collapsed/)
    await expect(page.locator('.sidebar-logo-text')).toBeVisible()
    await expect(page.locator('.sidebar-user-name')).toBeVisible()
  })

  test('navigation still works when collapsed', async ({ page }) => {
    // Collapse
    await page.locator('.sidebar-toggle-btn').click()
    await page.waitForTimeout(300)

    // Click history nav link (should still work by icon)
    await page.getByRole('link', { name: 'History' }).click()
    await expect(page).toHaveURL(/\/#\/history$/)
  })
})

// =====================================================
// 2. New Chat Button
// =====================================================
test.describe('Sidebar — New Chat', () => {
  test('New Chat button navigates to /chat and creates fresh session', async ({ page }) => {
    await loginAs(page, USER)

    // Wait for the current home chat entrypoint to load.
    await page.waitForSelector('.chat-input', { timeout: 15_000 })

    // Navigate to history using sidebar link (avoids full page reload)
    await page.getByRole('link', { name: 'History' }).click()
    await page.waitForURL(/\/#\/history$/)
    await page.waitForTimeout(1000)

    // Click New Chat — this calls the API to create a session then navigates
    const newChatBtn = page.locator('.new-chat-nav')
    await expect(newChatBtn).toBeVisible()
    await newChatBtn.click()

    // Should navigate to /chat (with sessionId query param) after session creation
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })

    // Chat input should be visible and empty (fresh session)
    const chatInput = page.locator('.chat-input')
    await expect(chatInput).toBeVisible({ timeout: 15_000 })
    const value = await chatInput.inputValue()
    expect(value).toBe('')
  })
})

// =====================================================
// 3. Language Switch — Verify UI Text Changes
// =====================================================
test.describe('Settings — language switch', () => {
  test('switch to Chinese, verify sidebar text changes, switch back to English', async ({ page }) => {
    await loginAs(page, USER)

    // --- Verify English UI initially ---
    const homeLink = page.getByRole('link', { name: 'Home' })
    const homeText = await homeLink.textContent()
    expect(homeText).toContain('Home')

    // --- Open settings ---
    await page.locator('.sidebar-user-btn').first().click()
    await expect(page.locator('.settings-modal')).toBeVisible({ timeout: 5000 })

    // General tab (first tab)
    await page.locator('.settings-nav-item').first().click()
    await page.waitForTimeout(300)

    // --- Switch to Chinese ---
    const langSelect = page.locator('.settings-select')
    await langSelect.selectOption('zh')
    await page.waitForTimeout(500)

    // Close settings
    await page.locator('.settings-nav-close').click()
    await page.waitForTimeout(500)

    // --- Verify UI text changed to Chinese ---
    const homeTextZh = await page.locator('.sidebar-nav').textContent()
    // Should now contain Chinese text (首页) instead of "Home"
    expect(homeTextZh).not.toContain('Home')
    expect(homeTextZh!.trim().length).toBeGreaterThan(0)

    // --- Switch back to English ---
    await page.locator('.sidebar-user-btn').first().click()
    await expect(page.locator('.settings-modal')).toBeVisible({ timeout: 5000 })
    await page.locator('.settings-nav-item').first().click()
    await page.waitForTimeout(300)
    await langSelect.selectOption('en')
    await page.waitForTimeout(500)
    await page.locator('.settings-nav-close').click()
    await page.waitForTimeout(500)

    // --- Verify English restored ---
    const homeTextEn = await page.locator('.sidebar-nav').textContent()
    expect(homeTextEn).toContain('Home')
  })

  test('language setting persists after page reload', async ({ page }) => {
    await loginAs(page, USER)

    // Switch to Chinese
    await page.locator('.sidebar-user-btn').first().click()
    await page.locator('.settings-nav-item').first().click()
    await page.locator('.settings-select').selectOption('zh')
    await page.waitForTimeout(500)
    await page.locator('.settings-nav-close').click()

    // Reload page
    await page.reload()
    await page.waitForTimeout(1000)

    // Should still be in Chinese
    const homeText = await page.locator('.sidebar-nav').textContent()
    expect(homeText).not.toContain('Home')

    // Switch back to English for cleanup
    await page.locator('.sidebar-user-btn').first().click()
    await page.locator('.settings-nav-item').first().click()
    await page.locator('.settings-select').selectOption('en')
    await page.waitForTimeout(500)
    await page.locator('.settings-nav-close').click()
  })
})

// =====================================================
// 4. Logout — Verify Redirect & State Cleared
// =====================================================
test.describe('Settings — logout', () => {
  test('logout clears user state and returns home', async ({ page }) => {
    await loginAs(page, USER)

    // Open settings → User tab → Logout
    await page.locator('.sidebar-user-btn').first().click()
    await expect(page.locator('.settings-modal')).toBeVisible({ timeout: 5000 })
    await page.locator('.settings-nav-item').last().click()
    await page.waitForTimeout(300)

    // Verify username shown before logout
    await expect(page.locator('.settings-username')).toContainText(USER)

    // Click logout
    await page.locator('.btn.btn-secondary').last().click()

    // The dedicated login page is gone; logout clears persisted identity and returns home.
    await page.waitForURL(/\/#\/?$/)
    const storedUserId = await page.evaluate(() => localStorage.getItem('opsfactory:userId'))
    expect(storedUserId).toBeNull()

    // A fresh app load falls back to the default runtime user instead of a login page.
    await page.goto('/#/')
    await expect(page.locator('.sidebar-user-btn')).toBeVisible({ timeout: 5000 })
  })
})

// =====================================================
// 5. Settings Modal — Tab Navigation
// =====================================================
test.describe('Settings — modal behavior', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER)
    await page.locator('.sidebar-user-btn').first().click()
    await expect(page.locator('.settings-modal')).toBeVisible({ timeout: 5000 })
  })

  test('General tab shows language selector', async ({ page }) => {
    await page.locator('.settings-nav-item').first().click()
    await expect(page.locator('.settings-select')).toBeVisible()

    // Should have at least 2 options (en, zh)
    const options = page.locator('.settings-select option')
    const count = await options.count()
    expect(count).toBeGreaterThanOrEqual(2)
  })

  test('User tab shows user info and avatar', async ({ page }) => {
    await page.locator('.settings-nav-item').last().click()
    await page.waitForTimeout(300)

    await expect(page.locator('.settings-avatar')).toBeVisible()
    await expect(page.locator('.settings-username')).toContainText(USER)
    await expect(page.locator('.settings-logout-btn')).toBeVisible()
  })

  test('close modal with X button', async ({ page }) => {
    await page.locator('.settings-nav-close').click()
    await expect(page.locator('.settings-modal')).not.toBeVisible()
  })

  test('close modal by clicking overlay', async ({ page }) => {
    await page.locator('.settings-overlay').click({ position: { x: 10, y: 10 } })
    await expect(page.locator('.settings-modal')).not.toBeVisible()
  })
})

// =====================================================
// 6. Sidebar User Section
// =====================================================
test.describe('Sidebar — user section', () => {
  test('shows correct username and avatar', async ({ page }) => {
    await loginAs(page, USER)

    await expect(page.locator('.sidebar-user-avatar')).toBeVisible()
    await expect(page.locator('.sidebar-user-name')).toContainText(USER)
  })
})
