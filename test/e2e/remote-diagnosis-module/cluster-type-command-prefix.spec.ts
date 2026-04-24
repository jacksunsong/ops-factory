/**
 * E2E Test: Cluster Type — commandPrefix & envVariables CRUD
 *
 * Covers the two new fields on the Cluster Types tab of /#/host-resource:
 *   1. Create  — add a cluster type with commandPrefix + envVariables
 *   2. Read    — verify the type card appears, API response contains new fields
 *   3. Edit    — open edit modal, verify pre-filled values, update them
 *   4. Add env var rows — click "+ Add Variable", fill key/value, save
 *   5. Remove env var rows — delete a row, save, verify
 *   6. Clear fields — set commandPrefix to empty, remove all vars
 *   7. Delete  — remove the cluster type
 *
 * Every step takes a screenshot and validates API responses.
 */
import { test, expect, type Page, type Response } from '@playwright/test'

const SS_DIR = 'test-results/cluster-type-command-prefix'
const TS = Date.now()
const TEST_TYPE = {
    name: `E2E-CT-${TS}`,
    code: `e2e-ct-${TS}`,
    description: 'E2E test cluster type for commandPrefix + envVariables',
    color: '#6366f1',
    knowledge: 'Test knowledge for command prefix feature',
    commandPrefix: 'sudo -u nslb',
    envVariables: [
        { key: 'NSLB_HOME', value: '/opt/nslb' },
        { key: 'NSLB_USER', value: 'nslb' },
    ],
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function ss(page: Page, name: string) {
    await page.screenshot({ path: `${SS_DIR}/${name}.png`, fullPage: true })
}

async function waitForApi(
    page: Page,
    predicate: (r: Response) => boolean,
    timeout = 15000,
): Promise<Response> {
    return page.waitForResponse(predicate, { timeout })
}

async function navigateToClusterTypesTab(page: Page) {
    await page.goto('/#/host-resource')
    await page.waitForSelector('.config-tabs', { timeout: 10000 })
    await page.waitForTimeout(500)

    const tab = page.locator('.config-tab').filter({ hasText: /集群类型|Cluster Types/ })
    await expect(tab).toBeVisible({ timeout: 5000 })
    await tab.click()
    await page.waitForTimeout(800)

    // Wait for the tab content to render
    await page.waitForSelector('.hr-type-tab-content', { timeout: 5000 })
}

async function getModal(page: Page) {
    const modal = page.locator('.modal-content')
    await expect(modal).toBeVisible({ timeout: 5000 })
    return modal
}

/** Fill a text input or textarea by its .form-label text */
async function fillByLabel(
    container: ReturnType<Page['locator']>,
    labelPattern: string,
    value: string,
) {
    const label = container.locator('.form-label').filter({ hasText: new RegExp(labelPattern) })
    const group = label.locator('..')
    // Try textarea first, fall back to input
    const textarea = group.locator('textarea.form-input').first()
    const hasTextarea = await textarea.count()
    if (hasTextarea > 0) {
        await textarea.fill(value)
    } else {
        await group.locator('input.form-input').first().fill(value)
    }
}

/** Get the text input by its .form-label text — returns the locator */
function getInputByLabel(
    container: ReturnType<Page['locator']>,
    labelPattern: string,
) {
    const label = container.locator('.form-label').filter({ hasText: new RegExp(labelPattern) })
    const group = label.locator('..')
    return group.locator('input.form-input').first()
}

/** Get the textarea by its .form-label text */
function getTextareaByLabel(
    container: ReturnType<Page['locator']>,
    labelPattern: string,
) {
    const label = container.locator('.form-label').filter({ hasText: new RegExp(labelPattern) })
    const group = label.locator('..')
    return group.locator('textarea.form-input').first()
}

/** Find a TypeCard by name */
function findTypeCard(page: Page, name: string) {
    return page.locator('.hr-type-def-card').filter({ hasText: name }).first()
}

// ---------------------------------------------------------------------------
// Shared state
// ---------------------------------------------------------------------------

let createdTypeId = ''

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Cluster Type — commandPrefix & envVariables', () => {
    test.setTimeout(180_000)

    test.beforeEach(async ({ page }) => {
        await page.goto('/#/')
        await page.evaluate(() => localStorage.setItem('opsfactory:userId', 'admin'))
        await navigateToClusterTypesTab(page)
        await ss(page, '00-cluster-types-tab')
    })

    // ── 1. Create cluster type with commandPrefix + envVariables ────────

    test('create cluster type with command prefix and env variables', async ({ page }) => {
        // Click "+ New Cluster Type"
        const addBtn = page.locator('.hr-type-tab-header .btn-primary')
        await expect(addBtn).toBeVisible({ timeout: 5000 })
        await addBtn.click()

        const modal = await getModal(page)
        await ss(page, '01-create-modal-open')

        // Fill basic fields
        await fillByLabel(modal, /Type Name|类型名称/, TEST_TYPE.name)
        await fillByLabel(modal, /Type Code|类型编码/, TEST_TYPE.code)
        await fillByLabel(modal, /Description|描述/, TEST_TYPE.description)
        await fillByLabel(modal, /Knowledge|常识/, TEST_TYPE.knowledge)

        // Fill the new commandPrefix field
        const prefixInput = getInputByLabel(modal, /Command Prefix|命令前缀/)
        await prefixInput.fill(TEST_TYPE.commandPrefix)
        await ss(page, '02-command-prefix-filled')

        // Add 2 env variable rows
        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')

        // Click "+ Add Variable" twice
        const addVarBtn = envGroup.locator('.btn-secondary').filter({ hasText: /\+|添加变量|Add Variable/ })
        await addVarBtn.click()
        await page.waitForTimeout(200)
        await addVarBtn.click()
        await page.waitForTimeout(200)

        await ss(page, '03-env-var-rows-added')

        // Fill first env var row (2 inputs + × button per row)
        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(2, { timeout: 3000 })

        // Row 0: key & value
        const row0Inputs = varRows.nth(0).locator('input.form-input')
        await row0Inputs.nth(0).fill(TEST_TYPE.envVariables[0].key)
        await row0Inputs.nth(1).fill(TEST_TYPE.envVariables[0].value)

        // Row 1: key & value
        const row1Inputs = varRows.nth(1).locator('input.form-input')
        await row1Inputs.nth(0).fill(TEST_TYPE.envVariables[1].key)
        await row1Inputs.nth(1).fill(TEST_TYPE.envVariables[1].value)

        await ss(page, '04-all-fields-filled')

        // Save and capture API call
        const createApi = waitForApi(page, r =>
            r.url().includes('/cluster-types') &&
            r.request().method() === 'POST' &&
            !r.url().includes('/cluster-types/'),
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await createApi

        expect(resp.ok(), `Create cluster type failed: ${resp.status()}`).toBeTruthy()
        const body = await resp.json()
        expect(body.success, `API returned success:false`).toBe(true)
        expect(body.clusterType, `Response missing clusterType`).toBeDefined()

        createdTypeId = body.clusterType.id

        // Verify API response contains the new fields
        expect(body.clusterType.commandPrefix).toBe(TEST_TYPE.commandPrefix)
        expect(body.clusterType.envVariables).toHaveLength(2)
        expect(body.clusterType.envVariables[0].key).toBe('NSLB_HOME')
        expect(body.clusterType.envVariables[0].value).toBe('/opt/nslb')
        expect(body.clusterType.envVariables[1].key).toBe('NSLB_USER')
        expect(body.clusterType.envVariables[1].value).toBe('nslb')

        await expect(page.locator('.modal-overlay')).not.toBeVisible({ timeout: 3000 })
        await ss(page, '05-after-create')

        // Verify type card appears in grid
        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not visible after create').toBeVisible({ timeout: 5000 })
        await ss(page, '06-type-card-visible')
    })

    // ── 2. Edit — verify pre-filled values and update ───────────────────

    test('edit cluster type — pre-filled values and update', async ({ page }) => {
        test.skip(!createdTypeId, 'No cluster type created yet')

        // Find the type card and click Edit
        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not found').toBeVisible({ timeout: 5000 })

        await card.locator('.btn-secondary').filter({ hasText: /Edit|编辑/ }).first().click()
        const modal = await getModal(page)
        await ss(page, '07-edit-modal-open')

        // Verify commandPrefix is pre-filled
        const prefixInput = getInputByLabel(modal, /Command Prefix|命令前缀/)
        await expect(prefixInput).toHaveValue(TEST_TYPE.commandPrefix, { timeout: 3000 })

        // Verify env vars are pre-filled (2 rows)
        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')
        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(2, { timeout: 3000 })

        // Check first row values
        const row0Inputs = varRows.nth(0).locator('input.form-input')
        await expect(row0Inputs.nth(0)).toHaveValue('NSLB_HOME')
        await expect(row0Inputs.nth(1)).toHaveValue('/opt/nslb')

        // Check second row values
        const row1Inputs = varRows.nth(1).locator('input.form-input')
        await expect(row1Inputs.nth(1)).toHaveValue('nslb')

        await ss(page, '08-prefilled-values-verified')

        // Update commandPrefix
        await prefixInput.clear()
        await prefixInput.fill('sudo -u appuser')

        // Update first env var value
        await row0Inputs.nth(1).clear()
        await row0Inputs.nth(1).fill('/opt/app')

        await ss(page, '09-values-updated')

        // Save
        const updateApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'PUT',
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await updateApi

        expect(resp.ok(), `Update cluster type failed: ${resp.status()}`).toBeTruthy()
        const body = await resp.json()
        expect(body.success, `API returned success:false`).toBe(true)

        // Verify updated values in API response
        expect(body.clusterType.commandPrefix).toBe('sudo -u appuser')
        expect(body.clusterType.envVariables[0].value).toBe('/opt/app')

        await expect(page.locator('.modal-overlay')).not.toBeVisible({ timeout: 3000 })
        await ss(page, '10-after-edit')
    })

    // ── 3. Add an additional env var row ────────────────────────────────

    test('add additional env variable row', async ({ page }) => {
        test.skip(!createdTypeId, 'No cluster type created yet')

        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not found').toBeVisible({ timeout: 5000 })

        await card.locator('.btn-secondary').filter({ hasText: /Edit|编辑/ }).first().click()
        const modal = await getModal(page)

        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')

        // Click "+ Add Variable" to add a 3rd row
        const addVarBtn = envGroup.locator('.btn-secondary').filter({ hasText: /\+|添加变量|Add Variable/ })
        await addVarBtn.click()
        await page.waitForTimeout(200)

        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(3, { timeout: 3000 })

        // Fill the new 3rd row
        const row2Inputs = varRows.nth(2).locator('input.form-input')
        await row2Inputs.nth(0).fill('APP_PORT')
        await row2Inputs.nth(1).fill('8080')

        await ss(page, '11-third-var-added')

        const updateApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'PUT',
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await updateApi

        expect(resp.ok(), `Update failed after adding var`).toBeTruthy()
        const body = await resp.json()
        expect(body.clusterType.envVariables).toHaveLength(3)
        expect(body.clusterType.envVariables[2].key).toBe('APP_PORT')
        expect(body.clusterType.envVariables[2].value).toBe('8080')

        await ss(page, '12-after-add-var')
    })

    // ── 4. Remove an env var row ────────────────────────────────────────

    test('remove an env variable row', async ({ page }) => {
        test.skip(!createdTypeId, 'No cluster type created yet')

        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not found').toBeVisible({ timeout: 5000 })

        await card.locator('.btn-secondary').filter({ hasText: /Edit|编辑/ }).first().click()
        const modal = await getModal(page)

        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')
        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(3, { timeout: 3000 })

        // Click the × button on the second row (NSLB_USER) to remove it
        await varRows.nth(1).locator('.btn-secondary').filter({ hasText: '×' }).click()
        await page.waitForTimeout(200)

        // Should now have 2 rows
        await expect(varRows).toHaveCount(2, { timeout: 3000 })

        await ss(page, '13-var-row-removed')

        const updateApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'PUT',
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await updateApi

        expect(resp.ok(), `Update failed after removing var`).toBeTruthy()
        const body = await resp.json()
        expect(body.clusterType.envVariables).toHaveLength(2)
        // First var should still be NSLB_HOME, second should now be APP_PORT
        expect(body.clusterType.envVariables[0].key).toBe('NSLB_HOME')
        expect(body.clusterType.envVariables[1].key).toBe('APP_PORT')

        await ss(page, '14-after-remove-var')
    })

    // ── 5. Clear commandPrefix and all env vars ─────────────────────────

    test('clear commandPrefix and remove all env variables', async ({ page }) => {
        test.skip(!createdTypeId, 'No cluster type created yet')

        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not found').toBeVisible({ timeout: 5000 })

        await card.locator('.btn-secondary').filter({ hasText: /Edit|编辑/ }).first().click()
        const modal = await getModal(page)

        // Clear commandPrefix
        const prefixInput = getInputByLabel(modal, /Command Prefix|命令前缀/)
        await prefixInput.clear()

        // Remove all env var rows
        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')
        const removeBtns = envGroup.locator('div[style*="display: flex"] .btn-secondary').filter({ hasText: '×' })
        const btnCount = await removeBtns.count()

        for (let i = btnCount - 1; i >= 0; i--) {
            await removeBtns.nth(i).click()
            await page.waitForTimeout(100)
        }

        // Verify no var rows remain
        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(0, { timeout: 3000 })

        await ss(page, '15-fields-cleared')

        const updateApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'PUT',
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await updateApi

        expect(resp.ok(), `Update failed after clearing fields`).toBeTruthy()
        const body = await resp.json()

        // commandPrefix should be empty string, envVariables should be empty array
        expect(body.clusterType.commandPrefix).toBe('')
        expect(body.clusterType.envVariables).toHaveLength(0)

        await ss(page, '16-after-clear')
    })

    // ── 6. Re-set fields for final verification then delete ─────────────

    test('re-set commandPrefix and env vars, then delete', async ({ page }) => {
        test.skip(!createdTypeId, 'No cluster type created yet')

        const card = findTypeCard(page, TEST_TYPE.name)
        await expect(card, 'Type card not found').toBeVisible({ timeout: 5000 })

        await card.locator('.btn-secondary').filter({ hasText: /Edit|编辑/ }).first().click()
        const modal = await getModal(page)

        // Set commandPrefix back
        const prefixInput = getInputByLabel(modal, /Command Prefix|命令前缀/)
        await prefixInput.fill('sudo -u root')

        // Add one env var
        const envLabel = modal.locator('.form-label').filter({ hasText: /Environment Variables|环境变量/ })
        const envGroup = envLabel.locator('..')
        const addVarBtn = envGroup.locator('.btn-secondary').filter({ hasText: /\+|添加变量|Add Variable/ })
        await addVarBtn.click()
        await page.waitForTimeout(200)

        const varRows = envGroup.locator('div[style*="display: flex"]')
        await expect(varRows).toHaveCount(1, { timeout: 3000 })
        const rowInputs = varRows.nth(0).locator('input.form-input')
        await rowInputs.nth(0).fill('ROOT_HOME')
        await rowInputs.nth(1).fill('/root')

        await ss(page, '17-reset-fields')

        const updateApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'PUT',
        )
        await modal.locator('.modal-footer .btn-primary').click()
        const resp = await updateApi

        expect(resp.ok(), `Update failed on re-set`).toBeTruthy()
        const body = await resp.json()
        expect(body.clusterType.commandPrefix).toBe('sudo -u root')
        expect(body.clusterType.envVariables).toHaveLength(1)
        expect(body.clusterType.envVariables[0].key).toBe('ROOT_HOME')

        await expect(page.locator('.modal-overlay')).not.toBeVisible({ timeout: 3000 })
        await ss(page, '18-after-reset')

        // Now delete the cluster type
        const deleteCard = findTypeCard(page, TEST_TYPE.name)
        await expect(deleteCard, 'Type card not found for deletion').toBeVisible({ timeout: 5000 })

        page.once('dialog', d => d.accept())

        const delApi = waitForApi(page, r =>
            r.url().includes(`/cluster-types/${createdTypeId}`) && r.request().method() === 'DELETE',
        ).catch(() => null)

        await deleteCard.locator('.btn-secondary').filter({ hasText: /Delete|删除/ }).first().click()
        const delResp = await delApi

        if (delResp) {
            expect(delResp.ok(), `Delete cluster type failed: ${delResp.status()}`).toBeTruthy()
        }

        await page.waitForTimeout(1000)

        // Verify card is gone
        const cardAfterDelete = findTypeCard(page, TEST_TYPE.name)
        await expect(cardAfterDelete, 'Type card should be gone after deletion')
            .not.toBeVisible({ timeout: 3000 })

        await ss(page, '19-type-deleted')
    })
})
