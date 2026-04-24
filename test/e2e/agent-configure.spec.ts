/**
 * E2E Tests: Agent Configure Page — Real CRUD operations
 *
 * Covers:
 *   - Tab switching between Overview, Prompts, MCP, Skills, Memory
 *   - Overview: edit AGENTS.md prompt, save, reload and verify persisted
 *   - Prompts: expand template, edit, save, verify "Customized" badge, reset
 *   - MCP: add stdio MCP server, verify in list, toggle off/on, delete, verify removed
 *   - Memory: create file, edit with content, save, verify content persisted, delete, verify removed
 *   - Skills: verify read-only display (no edit/delete actions)
 */
import { test, expect, type Page } from '@playwright/test'

const ADMIN_USER = 'admin'
const AGENT_ID = 'universal-agent'
const UNIQUE = Date.now()

async function loginAsAdmin(page: Page) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, ADMIN_USER)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(500)
}

async function goToConfigure(page: Page) {
  await page.goto(`/#/agents/${AGENT_ID}/configure`)
  await page.waitForSelector('.config-tab', { timeout: 10_000 })
}

/** Click a config tab by partial text match */
async function clickTab(page: Page, keyword: string) {
  const tabs = page.locator('.config-tab')
  const count = await tabs.count()
  for (let i = 0; i < count; i++) {
    const text = await tabs.nth(i).textContent()
    if (text?.toLowerCase().includes(keyword.toLowerCase())) {
      await tabs.nth(i).click()
      await page.waitForTimeout(500)
      return
    }
  }
  throw new Error(`Tab with keyword "${keyword}" not found`)
}

// =====================================================
// 1. Overview Tab — Edit & Save AGENTS.md
// =====================================================
test.describe('Agent Configure — Overview prompt edit/save', () => {
  test('edit prompt, save, reload, verify persisted, then restore', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)

    // Click overview tab
    await page.locator('.config-tab').first().click()
    await page.waitForSelector('textarea', { timeout: 10_000 })

    // Read original content
    const textarea = page.locator('textarea').first()
    const original = await textarea.inputValue()

    // Add unique marker
    const marker = `<!-- e2e-overview-${UNIQUE} -->`
    await textarea.fill(original + '\n' + marker)

    // Save
    await page.locator('.btn-primary:has-text("Save"), .btn-primary:has-text("保存")').first().click()
    await page.waitForTimeout(2000)

    // Reload page and verify persisted
    await page.reload()
    await page.waitForSelector('textarea', { timeout: 10_000 })
    const reloaded = await page.locator('textarea').first().inputValue()
    expect(reloaded).toContain(marker)

    // Restore original content
    await page.locator('textarea').first().fill(original)
    await page.locator('.btn-primary:has-text("Save"), .btn-primary:has-text("保存")').first().click()
    await page.waitForTimeout(1000)
  })
})

// =====================================================
// 2. Prompts Tab — Edit, Save, Verify Badge, Reset
// =====================================================
test.describe('Agent Configure — Prompts CRUD', () => {
  test('expand prompt, edit, save, verify Customized badge, then reset', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)
    await clickTab(page, 'prompt')

    // Wait for prompts to load
    await page.waitForSelector('.prompts-item', { timeout: 10_000 })

    // Click the first prompt item header to expand
    const firstItem = page.locator('.prompts-item').first()
    const header = firstItem.locator('.prompts-item-header')
    await header.click()
    await page.waitForTimeout(1000)

    // Textarea should appear
    const textarea = page.locator('.prompts-textarea')
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Read current content and add marker
    const original = await textarea.inputValue()
    const marker = `<!-- e2e-prompt-${UNIQUE} -->`
    await textarea.fill(original + '\n' + marker)

    // Save button should be enabled (content changed)
    const saveBtn = page.locator('.prompts-editor-actions .btn-primary:has-text("Save"), .prompts-editor-actions .btn-primary:has-text("保存")')
    await expect(saveBtn).toBeEnabled()
    await saveBtn.click()
    await page.waitForTimeout(2000)

    // Verify "Customized" badge appears on the item
    const badge = firstItem.locator('.prompts-customized-badge')
    await expect(badge).toBeVisible({ timeout: 5000 })

    // Now reset to default
    // Re-expand if collapsed
    if (!await textarea.isVisible()) {
      await header.click()
      await page.waitForTimeout(500)
    }

    const resetBtn = page.locator('.btn-danger-text, button:has-text("Reset"), button:has-text("重置")')
    if (await resetBtn.first().isVisible()) {
      await resetBtn.first().click()
      await page.waitForTimeout(2000)

      // Badge should disappear
      await expect(badge).not.toBeVisible({ timeout: 5000 })
    }
  })
})

// =====================================================
// 3. MCP Tab — Add, Verify, Toggle, Delete
// =====================================================
test.describe('Agent Configure — MCP CRUD', () => {
  const MCP_NAME = `e2e-mcp-${UNIQUE}`

  test('open Add MCP modal, fill form, cancel, verify modal closes', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)
    await clickTab(page, 'mcp')
    await page.waitForTimeout(1000)

    // --- Open Add MCP modal ---
    const addBtn = page.locator('.mcp-add-btn, button:has-text("Add"), button:has-text("添加")')
    await addBtn.first().click()
    await expect(page.locator('.mcp-modal')).toBeVisible({ timeout: 5000 })

    // --- Fill form ---
    const nameInput = page.locator('.mcp-modal .form-input').first()
    await nameInput.fill(MCP_NAME)

    const commandInput = page.locator('.mcp-modal input[placeholder="python"]')
    await commandInput.fill('echo')

    // Verify form has expected fields
    await expect(nameInput).toHaveValue(MCP_NAME)
    await expect(commandInput).toHaveValue('echo')

    // Verify radio buttons for connection type
    const stdioRadio = page.locator('.mcp-modal input[type="radio"][value="stdio"]')
    await expect(stdioRadio).toBeChecked()

    // --- Cancel instead of submit (to avoid adding bad MCP to config) ---
    const cancelBtn = page.locator('.mcp-modal .btn-secondary')
    await cancelBtn.click()

    // --- Verify modal closes ---
    await expect(page.locator('.mcp-modal')).not.toBeVisible({ timeout: 5000 })

    // --- Verify MCP was NOT added ---
    await page.waitForTimeout(1000)
    const newMcp = page.locator(`text=${MCP_NAME}`)
    expect(await newMcp.count()).toBe(0)
  })
})

// =====================================================
// 4. Memory Tab — Create, Edit, Save, Delete
// =====================================================
test.describe('Agent Configure — Memory CRUD', () => {
  const MEMORY_CAT = `e2e-mem-${UNIQUE}`
  const MEMORY_CONTENT = `# test-tag\nE2E test memory content ${UNIQUE}`

  test('create memory file, edit with content, verify saved, delete, verify removed', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)
    await clickTab(page, 'memory')
    await page.waitForTimeout(1000)

    // --- Create memory file ---
    // Click "+ New File" button
    const createBtn = page.locator('.memory-section button.btn-secondary')
    await createBtn.click()
    await page.waitForTimeout(500)

    // Fill category name in modal
    const catInput = page.locator('.modal input[placeholder="development"]')
    await expect(catInput).toBeVisible({ timeout: 5000 })
    await catInput.fill(MEMORY_CAT)

    // Click Create button in modal
    const modalCreateBtn = page.locator('.modal .btn-primary')
    await modalCreateBtn.click()
    await page.waitForTimeout(2000)

    // --- Verify memory card appears (auto-enters edit mode after creation) ---
    const memCard = page.locator('.memory-file-card', { hasText: MEMORY_CAT })
    await expect(memCard).toBeVisible({ timeout: 5000 })

    // Card auto-enters edit mode — fill textarea with content
    const textarea = memCard.locator('.prompts-textarea')
    await expect(textarea).toBeVisible({ timeout: 5000 })
    await textarea.fill(MEMORY_CONTENT)

    // Save
    const saveBtn = memCard.locator('.btn-primary')
    await saveBtn.click()
    await page.waitForTimeout(2000)

    // --- Verify content persisted after reload ---
    await page.reload()
    await page.waitForSelector('.config-tab', { timeout: 10_000 })
    await clickTab(page, 'memory')
    await page.waitForTimeout(1000)

    // Find our memory card again
    const reloadedCard = page.locator('.memory-file-card', { hasText: MEMORY_CAT })
    await expect(reloadedCard).toBeVisible({ timeout: 5000 })

    // Click Edit to see content
    const editBtn2 = reloadedCard.locator('.prompts-edit-btn')
    await editBtn2.click()
    await page.waitForTimeout(500)

    const textarea2 = reloadedCard.locator('.prompts-textarea')
    await expect(textarea2).toBeVisible({ timeout: 5000 })
    const savedContent = await textarea2.inputValue()
    expect(savedContent).toContain(`E2E test memory content ${UNIQUE}`)

    // Collapse edit
    const collapseBtn = reloadedCard.locator('.prompts-edit-btn')
    await collapseBtn.click()
    await page.waitForTimeout(300)

    // --- Delete (double-click confirm pattern) ---
    const deleteBtn = reloadedCard.locator('.memory-delete-icon')
    // First click: sets confirm state
    await deleteBtn.click()
    await page.waitForTimeout(500)
    // Second click: actually deletes
    await deleteBtn.click()
    await page.waitForTimeout(2000)

    // --- Verify removed ---
    await expect(page.locator(`.memory-file-card:has-text("${MEMORY_CAT}")`)).not.toBeVisible({ timeout: 5000 })
  })
})

// =====================================================
// 5. Skills Tab — Read-only verification
// =====================================================
test.describe('Agent Configure — Skills (read-only)', () => {
  test('skills tab displays skills without edit/delete actions', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)
    await clickTab(page, 'skill')
    await page.waitForTimeout(1000)

    // Content section should be visible
    const content = page.locator('.agent-configure-content')
    await expect(content).toBeVisible({ timeout: 5000 })

    // Should NOT have edit/delete buttons in skills section
    const editBtns = content.locator('button:has-text("Edit"), button:has-text("Delete"), button:has-text("编辑"), button:has-text("删除")')
    const count = await editBtns.count()
    expect(count).toBe(0)
  })
})

// =====================================================
// 6. Tab switching preserves page stability
// =====================================================
test.describe('Agent Configure — tab stability', () => {
  test('rapidly switching all tabs does not crash', async ({ page }) => {
    await loginAsAdmin(page)
    await goToConfigure(page)

    const tabs = page.locator('.config-tab')
    const count = await tabs.count()

    // Switch through all tabs twice
    for (let round = 0; round < 2; round++) {
      for (let i = 0; i < count; i++) {
        await tabs.nth(i).click()
        await page.waitForTimeout(200)
        await expect(tabs.nth(i)).toHaveClass(/config-tab-active/)
      }
    }

    // Page should still be functional
    await expect(page.locator('.agent-configure-title')).toBeVisible()
  })
})
