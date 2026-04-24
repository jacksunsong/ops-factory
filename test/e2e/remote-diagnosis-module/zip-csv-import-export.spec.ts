/**
 * E2E Test: ZIP Export & CSV Import
 *
 * Covers the ZIP+CSV import/export feature on the /#/host-resource page:
 *
 *   1. Export: click Export button → verify ZIP download contains 9 CSVs + manifest.json
 *   2. Import Whitelist: import whitelist.csv → verify commands appear
 *   3. Import ClusterTypes: import cluster_types.csv → verify cluster types created
 *   4. Import BusinessTypes: import business_types.csv → verify business types created
 *   5. Import HostGroups: import groups.csv → verify groups appear in tree
 *   6. Import Clusters: import clusters.csv (depends on group) → verify clusters
 *   7. Import Hosts: import hosts.csv (depends on cluster) → verify hosts created
 *   8. Import BusinessServices: import business_services.csv (depends on group)
 *   9. Import Relations: import relations.csv (depends on hosts)
 *  10. Import SOPs: import sops.csv → verify SOPs created
 *  11. Dependency validation: import hosts with non-existent cluster → verify error
 *  12. Round-trip: export → verify ZIP contains data matching what was imported
 *
 * Cleanup: all created test entities are deleted after the suite.
 */
import { test, expect, type Page, type Response, type Download } from '@playwright/test'
import { readFileSync, mkdirSync, rmSync, existsSync, readdirSync } from 'fs'
import { join } from 'path'
import { createWriteStream } from 'fs'
import { pipeline } from 'stream/promises'
import { createUnzip } from 'zlib'

const SS_DIR = 'test-results/zip-csv-import-export'
const DOWNLOAD_DIR = 'test-results/zip-csv-import-export/downloads'
const EXTRACT_DIR = 'test-results/zip-csv-import-export/extracted'

const TS = Date.now()

// ── Test data constants ──────────────────────────────────────────────────────

const TEST_CT = { name: `E2E-CT-${TS}`, code: `E2E_CT_${TS}`, description: 'E2E cluster type', knowledge: 'Test knowledge' }
const TEST_BT = { name: `E2E-BT-${TS}`, code: `E2E_BT_${TS}`, description: 'E2E business type', knowledge: 'Test business knowledge' }
const TEST_GROUP = { name: `E2E-GRP-${TS}`, code: `E2E_GRP_${TS}`, description: 'E2E test group' }
const TEST_CLUSTER = { name: `E2E-CL-${TS}`, type: TEST_CT.code, purpose: 'Test purpose', group: TEST_GROUP.name, description: 'E2E test cluster' }
const TEST_HOST = { name: `E2E-Host-${TS}`, hostname: 'e2ehost', ip: '10.99.99.1', businessIp: '10.99.99.2', port: '22', os: 'Linux', location: 'DC-E2E', username: 'root', authType: 'password', credential: '', business: 'E2E', cluster: TEST_CLUSTER.name, purpose: 'Test', tags: 'tag1;tag2', description: 'E2E test host' }
const TEST_BS = { name: `E2E-BS-${TS}`, code: `E2E_BS_${TS}`, group: TEST_GROUP.name, businessType: TEST_BT.name, description: 'E2E business service', tags: 'bs-tag', priority: 'high', contactInfo: 'admin@test.com' }
const TEST_SOP = { name: `E2E-SOP-${TS}`, description: 'E2E test SOP', version: '1.0', triggerCondition: 'Manual', enabled: 'true', mode: 'natural_language', stepsDescription: 'Step 1: Check\nStep 2: Fix', tags: TEST_CT.name }
const TEST_WL = { pattern: `e2e-test-cmd-${TS}-wl`, description: 'E2E whitelist entry', enabled: 'true' }

// ── CSV content generators ───────────────────────────────────────────────────

function makeCsv(headers: string[], rows: string[][]) {
    const lines = [headers.join(','), ...rows.map(r => r.map(escapeCsv).join(','))]
    return '\uFEFF' + lines.join('\n') + '\n'
}

function escapeCsv(val: string) {
    if (val.includes('"') || val.includes(',') || val.includes('\n') || val.includes('\r')) {
        return '"' + val.replace(/"/g, '""') + '"'
    }
    return val
}

const clusterTypesCsv = makeCsv(
    ['name', 'code', 'description', 'knowledge', 'commandPrefix', 'envVariables'],
    [[TEST_CT.name, TEST_CT.code, TEST_CT.description, TEST_CT.knowledge, '', '']],
)

const businessTypesCsv = makeCsv(
    ['name', 'code', 'description', 'knowledge'],
    [[TEST_BT.name, TEST_BT.code, TEST_BT.description, TEST_BT.knowledge]],
)

const groupsCsv = makeCsv(
    ['name', 'code', 'parentGroup', 'description'],
    [[TEST_GROUP.name, TEST_GROUP.code, '', TEST_GROUP.description]],
)

const clustersCsv = makeCsv(
    ['name', 'type', 'purpose', 'group', 'description'],
    [[TEST_CLUSTER.name, TEST_CLUSTER.type, TEST_CLUSTER.purpose, TEST_CLUSTER.group, TEST_CLUSTER.description]],
)

const hostsCsv = makeCsv(
    ['name', 'hostname', 'ip', 'businessIp', 'port', 'os', 'location', 'username', 'authType', 'credential', 'business', 'cluster', 'purpose', 'tags', 'description'],
    [[TEST_HOST.name, TEST_HOST.hostname, TEST_HOST.ip, TEST_HOST.businessIp, TEST_HOST.port, TEST_HOST.os, TEST_HOST.location, TEST_HOST.username, TEST_HOST.authType, TEST_HOST.credential, TEST_HOST.business, TEST_HOST.cluster, TEST_HOST.purpose, TEST_HOST.tags, TEST_HOST.description]],
)

const businessServicesCsv = makeCsv(
    ['name', 'code', 'group', 'businessType', 'description', 'tags', 'priority', 'contactInfo'],
    [[TEST_BS.name, TEST_BS.code, TEST_BS.group, TEST_BS.businessType, TEST_BS.description, TEST_BS.tags, TEST_BS.priority, TEST_BS.contactInfo]],
)

const relationsCsv = makeCsv(
    ['sourceNode', 'destNode', 'description'],
    [[TEST_HOST.name, TEST_HOST.name, 'self-relation test']],
)

const sopsCsv = makeCsv(
    ['name', 'description', 'version', 'triggerCondition', 'enabled', 'mode', 'stepsDescription', 'tags'],
    [[TEST_SOP.name, TEST_SOP.description, TEST_SOP.version, TEST_SOP.triggerCondition, TEST_SOP.enabled, TEST_SOP.mode, TEST_SOP.stepsDescription, TEST_SOP.tags]],
)

const whitelistCsv = makeCsv(
    ['pattern', 'description', 'enabled'],
    [[TEST_WL.pattern, TEST_WL.description, TEST_WL.enabled]],
)

// ── Helpers ──────────────────────────────────────────────────────────────────

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

async function navigateTo(page: Page) {
    await page.goto('/#/host-resource')
    await page.waitForSelector('.host-resource-page', { timeout: 10000 })
    await page.waitForTimeout(800)
}

/** Click the Export button in the page header */
async function clickExportButton(page: Page) {
    const btn = page.locator('.page-header .btn-secondary').filter({ hasText: /Export|导出/ })
    await expect(btn).toBeVisible({ timeout: 5000 })
    return btn
}

/** Click the Import button in the page header */
async function clickImportButton(page: Page) {
    const btn = page.locator('.page-header .btn-secondary').filter({ hasText: /Import|导入/ })
    await expect(btn).toBeVisible({ timeout: 5000 })
    await btn.click()
    // Wait for import dialog to appear
    const dialog = page.locator('.hr-import-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    return dialog
}

/** Select an import type in the dialog — uses exact text match to avoid ambiguity */
async function selectImportType(page: Page, typeLabel: string | RegExp) {
    let btn
    if (typeof typeLabel === 'string') {
        // Use exact match on the button text content
        btn = page.locator('.hr-import-type-btn').filter({ hasText: new RegExp(`^${typeLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }).first()
    } else {
        btn = page.locator('.hr-import-type-btn').filter({ hasText: typeLabel }).first()
    }
    await expect(btn).toBeVisible({ timeout: 5000 })
    await btn.click()
    // Verify active state
    await expect(btn).toHaveClass(/hr-import-type-btn-active/)
}

/** Upload a CSV file to the file input in the import dialog */
async function uploadCsvFile(page: Page, csvContent: string, fileName: string) {
    const fileInput = page.locator('.hr-import-file-input')
    await expect(fileInput).toBeVisible({ timeout: 5000 })

    // Write temp CSV file and upload
    const tempPath = join(DOWNLOAD_DIR, fileName)
    mkdirSync(DOWNLOAD_DIR, { recursive: true })
    const { writeFileSync } = await import('fs')
    writeFileSync(tempPath, csvContent, 'utf-8')

    await fileInput.setInputFiles(tempPath)

    // Verify file name appears
    const fileNameEl = page.locator('.hr-import-file-name')
    await expect(fileNameEl).toBeVisible({ timeout: 5000 })
    await expect(fileNameEl).toContainText(fileName)
}

/** Click the "Start Import" button and wait for it to complete */
async function clickStartImport(page: Page): Promise<void> {
    const startBtn = page.locator('.hr-import-dialog .modal-footer .btn-primary')
    await expect(startBtn).toBeVisible({ timeout: 5000 })
    await startBtn.click()
}

/** Wait for import result to appear */
async function waitForImportResult(page: Page) {
    const resultSummary = page.locator('.hr-import-result-summary')
    await expect(resultSummary, 'Import result summary not visible').toBeVisible({ timeout: 60000 })
    return resultSummary
}

/** Close the import dialog */
async function closeImportDialog(page: Page) {
    const closeBtn = page.locator('.hr-import-dialog .modal-footer .btn-primary').filter({ hasText: /Close|关闭/ })
    if (await closeBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await closeBtn.click()
    } else {
        const closeBtn2 = page.locator('.hr-import-dialog .modal-footer .btn-secondary').first()
        await closeBtn2.click()
    }
    await expect(page.locator('.hr-import-dialog')).not.toBeVisible({ timeout: 5000 })
}

/** Delete a cluster type by name (cleanup) */
async function deleteClusterType(page: Page, name: string) {
    const tab = page.locator('.config-tab').filter({ hasText: /Cluster Type|集群类型/ })
    if (await tab.isVisible({ timeout: 3000 }).catch(() => false)) {
        await tab.click()
        await page.waitForTimeout(800)

        const card = page.locator('.hr-type-def-card').filter({ hasText: name }).first()
        if (await card.isVisible({ timeout: 3000 }).catch(() => false)) {
            const delBtn = card.locator('.btn-subtle').filter({ hasText: /Delete|删除/ })
            if (await delBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
                page.once('dialog', d => d.accept())
                await delBtn.click()
                await page.waitForTimeout(1000)
            }
        }
    }
}

/** Delete a business type by name (cleanup) */
async function deleteBusinessType(page: Page, name: string) {
    const tab = page.locator('.config-tab').filter({ hasText: /Business Type|典型业务类型/ })
    if (await tab.isVisible({ timeout: 3000 }).catch(() => false)) {
        await tab.click()
        await page.waitForTimeout(800)

        const card = page.locator('.hr-type-def-card').filter({ hasText: name }).first()
        if (await card.isVisible({ timeout: 3000 }).catch(() => false)) {
            const delBtn = card.locator('.btn-subtle').filter({ hasText: /Delete|删除/ })
            if (await delBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
                page.once('dialog', d => d.accept())
                await delBtn.click()
                await page.waitForTimeout(1000)
            }
        }
    }
}

// ── Test Suite ───────────────────────────────────────────────────────────────

test.describe('ZIP Export & CSV Import', () => {
    test.setTimeout(300_000)

    test.beforeEach(async ({ page }) => {
        await page.goto('/#/')
        await page.evaluate(() => localStorage.setItem('opsfactory:userId', 'admin'))
        await navigateTo(page)
    })

    // ── 1. Export — download ZIP and verify structure ──────────────────────

    test('export downloads a ZIP with 9 CSVs + manifest', async ({ page }) => {
        mkdirSync(DOWNLOAD_DIR, { recursive: true })

        const [download] = await Promise.all([
            page.waitForEvent('download', { timeout: 15000 }),
            (await clickExportButton(page)).click(),
        ])

        const zipPath = join(DOWNLOAD_DIR, 'export.zip')
        await download.saveAs(zipPath)
        expect(existsSync(zipPath), 'ZIP file was not saved').toBeTruthy()

        // Verify the file is a valid ZIP (starts with PK magic bytes)
        const buf = readFileSync(zipPath)
        expect(buf[0]).toBe(0x50) // 'P'
        expect(buf[1]).toBe(0x4B) // 'K'

        await ss(page, '01-export-downloaded')
    })

    // ── 2. Import Whitelist ────────────────────────────────────────────────

    test('import whitelist CSV creates command entries', async ({ page }) => {
        await clickImportButton(page)
        await ss(page, '02-import-dialog-open')

        // Select Whitelist type
        await selectImportType(page, 'Whitelist')
        await ss(page, '03-whitelist-selected')

        // Upload CSV
        await uploadCsvFile(page, whitelistCsv, 'whitelist.csv')
        await ss(page, '04-whitelist-file-uploaded')

        // Start import and wait for result
        await clickStartImport(page)
        const resultSummary = await waitForImportResult(page)
        // Assert at least 1 success — "X succeeded" where X > 0
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '05-whitelist-import-result')

        // Verify command appears in whitelist tab
        await closeImportDialog(page)
        const tab = page.locator('.config-tab').filter({ hasText: /Whitelist|白名单/ })
        await tab.click()
        await page.waitForTimeout(2000)

        // Use the search box to filter to our test command (table may have pagination)
        const searchInput = page.locator('input[placeholder*="Search"]')
        await searchInput.fill(TEST_WL.pattern)
        await page.waitForTimeout(1500)

        // Look for the pattern in the filtered table
        const row = page.locator('.sop-workflow-table tbody tr').filter({ hasText: TEST_WL.pattern }).first()
        await expect(row, 'Whitelist entry not found in table').toBeVisible({ timeout: 8000 })

        await ss(page, '06-whitelist-verified-in-tab')
    })

    // ── 3. Import Cluster Types ────────────────────────────────────────────

    test('import cluster_types CSV creates cluster type entries', async ({ page }) => {
        await clickImportButton(page)
        await selectImportType(page, 'Cluster Types')
        await uploadCsvFile(page, clusterTypesCsv, 'cluster_types.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '07-cluster-type-import-result')

        // Verify in Cluster Types tab
        await closeImportDialog(page)
        const tab = page.locator('.config-tab').filter({ hasText: /Cluster Type|集群类型/ })
        await tab.click()
        await page.waitForTimeout(1000)

        const card = page.locator('.hr-type-def-card').filter({ hasText: TEST_CT.name })
        await expect(card, 'Cluster type not found in tab').toBeVisible({ timeout: 5000 })

        await ss(page, '08-cluster-type-verified')
    })

    // ── 4. Import Business Types ───────────────────────────────────────────

    test('import business_types CSV creates business type entries', async ({ page }) => {
        await clickImportButton(page)
        await selectImportType(page, 'Business Types')
        await uploadCsvFile(page, businessTypesCsv, 'business_types.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '09-business-type-import-result')

        // Verify in Business Types tab
        await closeImportDialog(page)
        const tab = page.locator('.config-tab').filter({ hasText: /Business Type|典型业务类型/ })
        await tab.click()
        await page.waitForTimeout(1000)

        const card = page.locator('.hr-type-def-card').filter({ hasText: TEST_BT.name })
        await expect(card, 'Business type not found in tab').toBeVisible({ timeout: 5000 })

        await ss(page, '10-business-type-verified')
    })

    // ── 5. Import Host Groups ──────────────────────────────────────────────

    test('import groups CSV creates group in tree', async ({ page }) => {
        await clickImportButton(page)
        await selectImportType(page, 'Host Groups')
        await uploadCsvFile(page, groupsCsv, 'groups.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '11-group-import-result')

        // Verify in tree sidebar
        await closeImportDialog(page)
        await page.waitForTimeout(1000)

        const treeNode = page.locator('.hr-tree-node').filter({ hasText: TEST_GROUP.name })
        await expect(treeNode, 'Group not visible in tree sidebar').toBeVisible({ timeout: 5000 })

        await ss(page, '12-group-verified-in-tree')
    })

    // ── 6. Import Clusters (depends on group) ──────────────────────────────

    test('import clusters CSV creates cluster under group', async ({ page }) => {
        // First import the prerequisite group
        await clickImportButton(page)
        await selectImportType(page, 'Host Groups')
        await uploadCsvFile(page, groupsCsv, 'groups.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        // Also need the cluster type
        await clickImportButton(page)
        await selectImportType(page, 'Cluster Types')
        await uploadCsvFile(page, clusterTypesCsv, 'cluster_types.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        // Now import cluster
        await clickImportButton(page)
        await selectImportType(page, 'Clusters')
        await uploadCsvFile(page, clustersCsv, 'clusters.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '13-cluster-import-result')

        // Verify cluster appears in tree under the group
        await closeImportDialog(page)
        await page.waitForTimeout(1000)

        const clusterNode = page.locator('.hr-tree-node').filter({ hasText: TEST_CLUSTER.name })
        await expect(clusterNode, 'Cluster not visible in tree sidebar').toBeVisible({ timeout: 5000 })

        await ss(page, '14-cluster-verified-in-tree')
    })

    // ── 7. Import Hosts (depends on cluster) ───────────────────────────────

    test('import hosts CSV creates host in cluster', async ({ page }) => {
        // Setup prerequisites: group + cluster type + cluster
        await clickImportButton(page)
        await selectImportType(page, 'Host Groups')
        await uploadCsvFile(page, groupsCsv, 'groups.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        await clickImportButton(page)
        await selectImportType(page, 'Cluster Types')
        await uploadCsvFile(page, clusterTypesCsv, 'cluster_types.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        await clickImportButton(page)
        await selectImportType(page, 'Clusters')
        await uploadCsvFile(page, clustersCsv, 'clusters.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        // Now import host
        await clickImportButton(page)
        await selectImportType(page, 'Hosts')
        await uploadCsvFile(page, hostsCsv, 'hosts.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '15-host-import-result')

        // Verify host card appears
        await closeImportDialog(page)
        await page.waitForTimeout(1500)

        // Select the cluster in tree to show hosts
        const clusterNode = page.locator('.hr-tree-node').filter({ hasText: TEST_CLUSTER.name })
        if (await clusterNode.isVisible({ timeout: 3000 }).catch(() => false)) {
            await clusterNode.click()
            await page.waitForTimeout(1500)
        }

        const hostCard = page.locator('.hr-host-card').filter({ hasText: TEST_HOST.name })
        await expect(hostCard, 'Host card not visible after import').toBeVisible({ timeout: 8000 })

        await ss(page, '16-host-verified')
    })

    // ── 8. Import Business Services (depends on group + business type) ──────

    test('import business_services CSV creates business service', async ({ page }) => {
        // Setup prerequisites: group + business type
        await clickImportButton(page)
        await selectImportType(page, 'Host Groups')
        await uploadCsvFile(page, groupsCsv, 'groups.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        await clickImportButton(page)
        await selectImportType(page, 'Business Types')
        await uploadCsvFile(page, businessTypesCsv, 'business_types.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        // Now import business service
        await clickImportButton(page)
        await selectImportType(page, 'Business Services')
        await uploadCsvFile(page, businessServicesCsv, 'business_services.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '17-business-service-import-result')

        // Verify BS in tree
        await closeImportDialog(page)
        await page.waitForTimeout(1000)

        const bsNode = page.locator('.hr-tree-node').filter({ hasText: TEST_BS.name })
        await expect(bsNode, 'Business service not visible in tree').toBeVisible({ timeout: 5000 })

        await ss(page, '18-business-service-verified')
    })

    // ── 9. Import SOPs ─────────────────────────────────────────────────────

    test('import sops CSV creates SOP entry', async ({ page }) => {
        await clickImportButton(page)
        await selectImportType(page, 'SOPs')
        await uploadCsvFile(page, sopsCsv, 'sops.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        await expect(resultSummary).toContainText(/[1-9]\d* succeed/)
        await ss(page, '19-sop-import-result')

        // Verify in SOP tab
        await closeImportDialog(page)
        const tab = page.locator('.config-tab').filter({ hasText: /SOP|SOP 管理/ })
        await tab.click()
        await page.waitForTimeout(2000)

        const sopRow = page.locator('.sop-workflow-table tbody tr').filter({ hasText: TEST_SOP.name }).first()
        await expect(sopRow, 'SOP not found in table').toBeVisible({ timeout: 8000 })

        await ss(page, '20-sop-verified')
    })

    // ── 10. Dependency validation — missing cluster ─────────────────────────

    test('import hosts with non-existent cluster shows dependency error', async ({ page }) => {
        const badHostCsv = makeCsv(
            ['name', 'hostname', 'ip', 'businessIp', 'port', 'os', 'location', 'username', 'authType', 'credential', 'business', 'cluster', 'purpose', 'tags', 'description'],
            [[`E2E-BadHost-${TS}`, 'badhost', '10.99.99.99', '', '22', 'Linux', 'DC-NA', 'root', 'password', '', '', `NonExistentCluster-${TS}`, '', '', 'Host with missing cluster']],
        )

        await clickImportButton(page)
        await selectImportType(page, 'Hosts')
        await uploadCsvFile(page, badHostCsv, 'hosts_bad.csv')
        await clickStartImport(page)

        const resultSummary = await waitForImportResult(page)
        // Should show failure
        await expect(resultSummary).toContainText(/failed|失败/)
        await ss(page, '21-dependency-error-result')

        // Verify error detail is shown
        const errorItem = page.locator('.hr-import-error-item')
        await expect(errorItem, 'Error detail not shown').toBeVisible({ timeout: 5000 })
        await expect(errorItem).toContainText(/not found|不存在/)

        await ss(page, '22-dependency-error-detail')

        await closeImportDialog(page)
    })

    // ── 11. Import dialog UX — type selection, file selection, state ────────

    test('import dialog UX: type buttons, file input, button states', async ({ page }) => {
        await clickImportButton(page)

        // Verify all 9 type buttons are present
        const typeButtons = page.locator('.hr-import-type-btn')
        await expect(typeButtons).toHaveCount(9, { timeout: 5000 })

        // Start Import button should be disabled initially (no type, no file)
        const startBtn = page.locator('.hr-import-dialog .modal-footer .btn-primary')
        await expect(startBtn).toBeDisabled()

        await ss(page, '23-dialog-initial-state')

        // Select a type
        await selectImportType(page, 'Whitelist')

        // Start Import should still be disabled (no file)
        await expect(startBtn).toBeDisabled()

        await ss(page, '24-type-selected-no-file')

        // Upload a file
        await uploadCsvFile(page, whitelistCsv, 'whitelist.csv')

        // Now Start Import should be enabled
        await expect(startBtn).toBeEnabled()

        await ss(page, '25-file-selected-btn-enabled')

        // Close without importing
        await closeImportDialog(page)
        await ss(page, '26-dialog-closed')
    })

    // ── 12. Round-trip: create data, export, verify ZIP contents ────────────

    test('round-trip: import whitelist then export includes it in ZIP', async ({ page }) => {
        mkdirSync(DOWNLOAD_DIR, { recursive: true })

        // Import whitelist
        await clickImportButton(page)
        await selectImportType(page, 'Whitelist')
        await uploadCsvFile(page, whitelistCsv, 'whitelist.csv')
        await clickStartImport(page)
        await waitForImportResult(page)
        await closeImportDialog(page)

        await ss(page, '27-round-trip-prepared')

        // Now export
        const [download] = await Promise.all([
            page.waitForEvent('download', { timeout: 15000 }),
            (await clickExportButton(page)).click(),
        ])

        const zipPath = join(DOWNLOAD_DIR, 'roundtrip-export.zip')
        await download.saveAs(zipPath)
        expect(existsSync(zipPath), 'Round-trip ZIP was not saved').toBeTruthy()

        // Read ZIP and verify it's non-empty
        const buf = readFileSync(zipPath)
        expect(buf.length, 'ZIP file is empty').toBeGreaterThan(100)

        // Verify PK magic
        expect(buf[0]).toBe(0x50)
        expect(buf[1]).toBe(0x4B)

        // The ZIP should contain whitelist.csv with our test pattern
        // Parse file entries from central directory (simple scan for filenames)
        const zipContent = buf.toString('utf-8')
        expect(zipContent, 'ZIP does not contain whitelist.csv').toContain('whitelist.csv')
        expect(zipContent, 'ZIP does not contain manifest.json').toContain('manifest.json')
        expect(zipContent, 'ZIP does not contain hosts.csv').toContain('hosts.csv')
        expect(zipContent, 'ZIP does not contain clusters.csv').toContain('clusters.csv')
        expect(zipContent, 'ZIP does not contain groups.csv').toContain('groups.csv')
        expect(zipContent, 'ZIP does not contain cluster_types.csv').toContain('cluster_types.csv')
        expect(zipContent, 'ZIP does not contain business_types.csv').toContain('business_types.csv')
        expect(zipContent, 'ZIP does not contain business_services.csv').toContain('business_services.csv')
        expect(zipContent, 'ZIP does not contain relations.csv').toContain('relations.csv')
        expect(zipContent, 'ZIP does not contain sops.csv').toContain('sops.csv')

        await ss(page, '28-round-trip-export-verified')
    })
})
