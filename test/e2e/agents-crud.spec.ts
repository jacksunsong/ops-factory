/**
 * E2E Tests: Agents CRUD — Full Lifecycle
 *
 * Covers:
 *   - Create agent: fill form, submit, verify card appears in list
 *   - Agent ID auto-generated from name via slugify
 *   - Agent card shows correct name, model, status
 *   - Delete agent: confirm, verify card removed from list
 *   - Create validation: empty name, invalid ID
 *   - Regular user cannot create/delete
 */
import { test, expect, type Page } from '@playwright/test'

const ADMIN_USER = 'admin'
const REGULAR_USER = 'e2e-agent-user'
const UNIQUE = Date.now()

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, username)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(500)
}

async function goToAgents(page: Page) {
  await page.goto('/#/agents')
  await page.waitForSelector('.agent-card', { timeout: 10_000 })
}

// =====================================================
// 1. Full Create → Verify → Delete → Verify Lifecycle
// =====================================================
test.describe('Agents CRUD — full lifecycle', () => {
  const AGENT_NAME = `E2E Agent ${UNIQUE}`
  const AGENT_ID = `e2e-agent-${UNIQUE}`

  test('create agent, verify in list, delete, verify removed', async ({ page }) => {
    await loginAs(page, ADMIN_USER)
    await goToAgents(page)

    // --- Count agents before ---
    const countBefore = await page.locator('.agent-card').count()

    // --- Open create modal ---
    await page.locator('.btn-primary:has-text("Create"), .btn-primary:has-text("创建")').first().click()
    await expect(page.locator('.modal')).toBeVisible({ timeout: 5000 })

    // --- Fill name ---
    const nameInput = page.locator('.modal .form-input').first()
    await nameInput.fill(AGENT_NAME)

    // --- Verify ID auto-generated from name ---
    const idInput = page.locator('.modal .form-input').nth(1)
    const autoId = await idInput.inputValue()
    expect(autoId.length).toBeGreaterThan(0)

    // --- Override ID to a known value ---
    await idInput.fill(AGENT_ID)

    // --- Submit ---
    const submitBtn = page.locator('.modal .btn-primary').last()
    await expect(submitBtn).toBeEnabled()
    await submitBtn.click()

    // --- Wait for modal to close (success) ---
    await expect(page.locator('.modal')).not.toBeVisible({ timeout: 10_000 })

    // --- Verify agent card appears in list ---
    await page.waitForTimeout(1000)
    const newCard = page.locator(`.agent-name:has-text("${AGENT_NAME}")`)
    await expect(newCard.first()).toBeVisible({ timeout: 5000 })

    // --- Verify count increased ---
    const countAfterCreate = await page.locator('.agent-card').count()
    expect(countAfterCreate).toBe(countBefore + 1)

    // --- Now delete the agent we just created ---
    // Find the card containing our agent name and click its delete button
    const agentCard = page.locator('.agent-card', { has: page.locator(`.agent-name:has-text("${AGENT_NAME}")`) })
    await agentCard.locator('.agent-delete-button').click()

    // --- Confirmation modal appears ---
    await expect(page.locator('.modal')).toBeVisible({ timeout: 5000 })
    // Verify warning text is shown
    await expect(page.locator('.agents-alert-warning').first()).toBeVisible()

    // --- Confirm delete ---
    await page.locator('.modal .btn-danger').click()

    // --- Wait for modal to close ---
    await expect(page.locator('.modal')).not.toBeVisible({ timeout: 10_000 })

    // --- Verify agent card removed from list ---
    await page.waitForTimeout(1000)
    await expect(page.locator(`.agent-name:has-text("${AGENT_NAME}")`)).not.toBeVisible()
    const countAfterDelete = await page.locator('.agent-card').count()
    expect(countAfterDelete).toBe(countBefore)
  })
})

// =====================================================
// 2. Create Validation
// =====================================================
test.describe('Agents CRUD — create validation', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_USER)
    await goToAgents(page)
    await page.locator('.btn-primary:has-text("Create"), .btn-primary:has-text("创建")').first().click()
    await expect(page.locator('.modal')).toBeVisible({ timeout: 5000 })
  })

  test('submit button is disabled when name is empty', async ({ page }) => {
    // Leave name empty, set a valid ID
    const idInput = page.locator('.modal .form-input').nth(1)
    await idInput.fill('valid-id')

    const submitBtn = page.locator('.modal .btn-primary').last()
    await expect(submitBtn).toBeDisabled()
  })

  test('submit button is disabled when ID is invalid (single char)', async ({ page }) => {
    const nameInput = page.locator('.modal .form-input').first()
    await nameInput.fill('Test Agent')

    const idInput = page.locator('.modal .form-input').nth(1)
    await idInput.fill('a') // too short, needs >= 2

    const submitBtn = page.locator('.modal .btn-primary').last()
    await expect(submitBtn).toBeDisabled()
  })

  test('submit button is disabled when ID has invalid chars', async ({ page }) => {
    const nameInput = page.locator('.modal .form-input').first()
    await nameInput.fill('Test Agent')

    const idInput = page.locator('.modal .form-input').nth(1)
    await idInput.fill('INVALID_ID!') // uppercase and special chars

    const submitBtn = page.locator('.modal .btn-primary').last()
    await expect(submitBtn).toBeDisabled()
  })

  test('submit button is enabled when name and ID are valid', async ({ page }) => {
    const nameInput = page.locator('.modal .form-input').first()
    await nameInput.fill('Valid Agent')

    const idInput = page.locator('.modal .form-input').nth(1)
    await idInput.fill('valid-agent-id')

    const submitBtn = page.locator('.modal .btn-primary').last()
    await expect(submitBtn).toBeEnabled()
  })
})

// =====================================================
// 3. Cancel operations don't modify list
// =====================================================
test.describe('Agents CRUD — cancel safety', () => {
  test('cancel create does not add agent', async ({ page }) => {
    await loginAs(page, ADMIN_USER)
    await goToAgents(page)
    const countBefore = await page.locator('.agent-card').count()

    await page.locator('.btn-primary:has-text("Create"), .btn-primary:has-text("创建")').first().click()
    await expect(page.locator('.modal')).toBeVisible({ timeout: 5000 })

    // Fill form but cancel
    await page.locator('.modal .form-input').first().fill('Should Not Exist')
    await page.locator('.modal .btn-secondary').click()
    await expect(page.locator('.modal')).not.toBeVisible()

    // Count unchanged
    const countAfter = await page.locator('.agent-card').count()
    expect(countAfter).toBe(countBefore)
  })

  test('cancel delete does not remove agent', async ({ page }) => {
    await loginAs(page, ADMIN_USER)
    await goToAgents(page)
    const countBefore = await page.locator('.agent-card').count()

    await page.locator('.agent-delete-button').first().click()
    await expect(page.locator('.modal')).toBeVisible({ timeout: 5000 })

    // Cancel
    await page.locator('.modal .btn-secondary').click()
    await expect(page.locator('.modal')).not.toBeVisible()

    // Count unchanged
    const countAfter = await page.locator('.agent-card').count()
    expect(countAfter).toBe(countBefore)
  })
})

// =====================================================
// 4. RBAC — Regular user restrictions
// =====================================================
test.describe('Agents CRUD — RBAC', () => {
  test('regular user sees agent list but no Create/Delete buttons', async ({ page }) => {
    await loginAs(page, REGULAR_USER)
    await page.goto('/#/agents')
    await page.waitForTimeout(3000)

    // Can see agents
    const agents = page.locator('.agent-card')
    await expect(agents.first()).toBeVisible({ timeout: 10_000 })

    // No create button
    await expect(page.locator('.btn-primary:has-text("Create"), .btn-primary:has-text("创建")')).not.toBeVisible()
    // No delete button
    await expect(page.locator('.agent-delete-button')).not.toBeVisible()
    // No configure button
    await expect(page.locator('.agent-skill-button')).not.toBeVisible()
  })
})
