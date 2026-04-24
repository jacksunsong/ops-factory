/**
 * E2E Tests: Home Page — Real Operations
 *
 * Covers:
 *   - Home chat input renders with model and agent selectors
 *   - Agent selector opens and can choose an agent
 *   - Sending from home creates a chat session and receives a response
 */
import { test, expect, type Page } from '@playwright/test'

const USER = 'e2e-home-user'

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, username)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(500)
}

test.describe('Home — chat entrypoint', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER)
  })

  test('renders chat input, model info, and selected agent', async ({ page }) => {
    await expect(page.locator('.chat-input')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.toolbar-model-info')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.agent-selector-trigger')).toBeVisible({ timeout: 15_000 })
  })

  test('agent selector opens and shows available agents', async ({ page }) => {
    const trigger = page.locator('.agent-selector-trigger')
    await expect(trigger).toBeVisible({ timeout: 15_000 })
    await trigger.click()
    await expect(page.locator('.agent-dropdown')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.agent-option').first()).toBeVisible({ timeout: 5000 })
  })

  test('send message from home and receive a response', async ({ page }) => {
    const chatInput = page.locator('.chat-input')
    await expect(chatInput).toBeVisible({ timeout: 15_000 })
    await page.waitForFunction(
      () => {
        const input = document.querySelector('.chat-input') as HTMLTextAreaElement
        return input && !input.disabled
      },
      { timeout: 15_000 }
    )

    await chatInput.fill('Reply with exactly: "home-session-ok"')
    const sendBtn = page.locator('.chat-send-btn-new')
    await sendBtn.click()

    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })
    await page.waitForFunction(
      () => {
        const btn = document.querySelector('.chat-send-btn-new')
        return btn && !btn.classList.contains('is-stop')
      },
      { timeout: 60_000 }
    )

    const messageText = await page.locator('.chat-messages-area').textContent()
    expect(messageText).toContain('home-session-ok')
  }, 120_000)
})
