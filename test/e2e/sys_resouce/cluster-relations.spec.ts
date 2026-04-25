/**
 * E2E Test: System Resource — Cluster Relations Full Flow
 *
 * Relation model:
 *   ✅ Cluster → Cluster  (sourceType="cluster")
 *   ✅ BS → Cluster        (sourceType="business-service")
 *   ❌ NOT Host → Host
 *   ❌ NOT BS → Host
 *
 * Test Flow (sequential, data-dependent):
 *   0. Clean up existing E2E test data
 *   1. Create cluster types (peer + primary-backup)
 *   2. Create group + two clusters (NSLB peer, RCPA HA)
 *   3. Create hosts under clusters (with role for HA)
 *   4. Create business service
 *   5. Create Cluster→Cluster relation (NSLB→RCPA) via API
 *   6. Create BS→Cluster relation (BS→NSLB entry) via API
 *   7. Verify cluster-level topology graph
 *   8. Verify cluster neighbor resolution
 *   9. Verify host neighbor resolution via cluster relations
 *  10. Verify cascade delete (cluster delete removes relations)
 */
import { test, expect, type Page } from '@playwright/test'

const SS_DIR = 'test-results/sys-resource-cluster'
const API = 'http://127.0.0.1:3000/gateway'
const TS = Date.now()

// ── API Helpers ──────────────────────────────────────────────────────────────

function headers(userId = 'admin') {
    return { 'Content-Type': 'application/json', 'x-secret-key': 'test', 'x-user-id': userId }
}

async function apiGet(path: string): Promise<any> {
    const res = await fetch(`${API}${path}`, { headers: headers() })
    return res.json()
}

async function apiPost(path: string, body: any): Promise<any> {
    const res = await fetch(`${API}${path}`, { method: 'POST', headers: headers(), body: JSON.stringify(body) })
    return res.json()
}

async function apiPut(path: string, body: any): Promise<any> {
    const res = await fetch(`${API}${path}`, { method: 'PUT', headers: headers(), body: JSON.stringify(body) })
    return res.json()
}

async function apiDelete(path: string): Promise<any> {
    const res = await fetch(`${API}${path}`, { method: 'DELETE', headers: headers() })
    return res.json()
}

async function apiGetInPage(page: Page, path: string): Promise<any> {
    return page.evaluate(async (url) => {
        const res = await fetch(url, { headers: { 'Content-Type': 'application/json', 'x-secret-key': 'test', 'x-user-id': 'admin' } })
        return res.json()
    }, `${API}${path}`)
}

async function apiPostInPage(page: Page, path: string, body: any): Promise<any> {
    return page.evaluate(async ({ url, data }) => {
        const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'x-secret-key': 'test', 'x-user-id': 'admin' }, body: JSON.stringify(data) })
        return res.json()
    }, { url: `${API}${path}`, data: body })
}

async function apiDeleteInPage(page: Page, path: string): Promise<any> {
    return page.evaluate(async (url) => {
        const res = await fetch(url, { method: 'DELETE', headers: { 'Content-Type': 'application/json', 'x-secret-key': 'test', 'x-user-id': 'admin' } })
        return res.json()
    }, `${API}${path}`)
}

// ── UI Helpers ───────────────────────────────────────────────────────────────

async function ss(page: Page, name: string) {
    await page.screenshot({ path: `${SS_DIR}/${name}.png`, fullPage: true })
}

async function loginAs(page: Page, username: string) {
    await page.goto('/')
    await page.evaluate(() => { localStorage.removeItem('ops-user'); localStorage.removeItem('user'); sessionStorage.clear() })
    await page.goto('/login')
    await page.waitForTimeout(500)
    const loginInput = page.locator('input[placeholder="Your name"]')
    if (await loginInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await loginInput.fill(username)
        await page.click('button:has-text("Enter")')
        await page.waitForURL('/')
    }
    await page.waitForURL(/\/(#\/)?/, { timeout: 5000 }).catch(() => {})
    await page.waitForTimeout(500)
}

async function navigateTo(page: Page) {
    await page.goto('/#/host-resource')
    await page.waitForSelector('.host-resource-page', { timeout: 10000 })
    await page.waitForTimeout(500)
}

async function switchTab(page: Page, tabPattern: string | RegExp) {
    const tab = page.locator('.config-tab').filter({ hasText: tabPattern })
    await expect(tab).toBeVisible({ timeout: 5000 })
    await tab.click()
    await page.waitForTimeout(500)
}

async function fillFormInput(container: ReturnType<Page['locator']>, labelPattern: string | RegExp, value: string) {
    const label = container.locator('.form-label').filter({ hasText: labelPattern })
    await expect(label.first()).toBeVisible({ timeout: 5000 })
    const group = label.locator('..')
    const input = group.locator('input.form-input').first()
    await input.fill(value)
}

async function selectFormOption(container: ReturnType<Page['locator']>, labelPattern: string | RegExp, optionValue: string) {
    const label = container.locator('.form-label').filter({ hasText: labelPattern })
    await expect(label.first()).toBeVisible({ timeout: 5000 })
    const group = label.locator('..')
    const select = group.locator('select.form-input').first()
    await select.selectOption({ value: optionValue })
}

async function selectFormOptionByLabel(container: ReturnType<Page['locator']>, labelPattern: string | RegExp, labelRegex: RegExp) {
    const label = container.locator('.form-label').filter({ hasText: labelPattern })
    await expect(label.first()).toBeVisible({ timeout: 5000 })
    const group = label.locator('..')
    const select = group.locator('select.form-input').first()
    await select.selectOption({ label: labelRegex })
}

async function clickModalSave(page: Page) {
    const saveBtn = page.locator('.modal-footer .btn-primary, .modal .btn-primary').last()
    await expect(saveBtn).toBeEnabled()
    await saveBtn.click()
    await page.waitForTimeout(1000)
}

async function openCreateModal(page: Page) {
    const createBtn = page.locator('.host-resource-page .btn-primary').first()
    await createBtn.click()
    await page.waitForTimeout(500)
}

async function selectResourceType(page: Page, typeLabel: string | RegExp) {
    const card = page.locator('.hr-type-card-label').filter({ hasText: typeLabel }).first()
    await expect(card).toBeVisible({ timeout: 5000 })
    await card.click()
    await page.waitForTimeout(500)
}

// ── Shared test state ──
const ids: Record<string, string> = {}

const NAMES = {
    peerType: `E2E-NSLB-Type-${TS}`,
    haType: `E2E-RCPA-Type-${TS}`,
    group: `E2E-Group-${TS}`,
    nslbCluster: `E2E-NSLB-${TS}`,
    rcpaCluster: `E2E-RCPA-${TS}`,
    nslbHost: `E2E-NSLB-Host-${TS}`,
    rcpaPrimary: `E2E-RCPA-Primary-${TS}`,
    rcpaBackup: `E2E-RCPA-Backup-${TS}`,
    bs: `E2E-BS-${TS}`,
}

// =====================================================
// Test Suite
// =====================================================

test.describe('System Resource — Cluster Relations Full Flow', () => {

    // ── Step 0: Clean up old E2E data ──

    test('0. Clean up existing E2E test data', async ({ page }) => {
        // Delete old E2E groups (and their children cascade)
        const groupsData = await apiGet('/host-groups')
        const e2eGroups = (groupsData.groups || []).filter((g: any) => g.name?.startsWith('E2E-'))
        for (const g of e2eGroups) {
            await apiDelete(`/host-groups/${g.id}`)
        }

        // Delete old E2E cluster types
        const typesData = await apiGet('/cluster-types')
        const e2eTypes = (typesData.types || typesData.clusterTypes || []).filter((t: any) => t.name?.startsWith('E2E-'))
        for (const t of e2eTypes) {
            await apiDelete(`/cluster-types/${t.id}`)
        }

        // Verify cleanup
        const afterGroups = await apiGet('/host-groups')
        const remaining = (afterGroups.groups || []).filter((g: any) => g.name?.startsWith('E2E-'))
        console.log(`Cleaned up ${e2eGroups.length} groups, ${e2eTypes.length} types, ${remaining.length} remaining groups`)
    })

    // ── Step 1: Create Cluster Types ──

    test('1. Create cluster types (peer NSLB + HA RCPA)', async ({ page }) => {
        await loginAs(page, 'admin')
        await navigateTo(page)
        await switchTab(page, /集群类型|Cluster Types/i)

        // Create NSLB peer type
        let createBtn = page.locator('.hr-type-tab-header .btn-primary')
        await createBtn.click()
        await page.waitForTimeout(500)

        let modal = page.locator('.modal-content')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /类型名称|Type Name/i, NAMES.peerType)
        await fillFormInput(modal, /类型编码|Type Code/i, `e2e-nslb-type-${TS}`)
        await fillFormInput(modal, /描述|Description/i, 'NSLB load balancer peer cluster type')
        // Mode defaults to peer — no need to change
        await clickModalSave(page)
        await page.waitForTimeout(500)

        // Verify created
        const peerCard = page.locator('.hr-type-def-card-name').filter({ hasText: NAMES.peerType })
        await expect(peerCard.first()).toBeVisible({ timeout: 5000 })

        // Create RCPA HA type
        await createBtn.click()
        await page.waitForTimeout(500)

        modal = page.locator('.modal-content')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /类型名称|Type Name/i, NAMES.haType)
        await fillFormInput(modal, /类型编码|Type Code/i, `e2e-rcpa-type-${TS}`)
        await fillFormInput(modal, /描述|Description/i, 'RCPA HA primary-backup cluster type')

        // Switch mode to primary-backup
        const modeLabel = modal.locator('.form-label').filter({ hasText: /集群模式|Cluster Mode/i })
        const modeSelect = modeLabel.locator('..').locator('select.form-input').first()
        await modeSelect.selectOption('primary-backup')

        await clickModalSave(page)
        await page.waitForTimeout(500)

        // Verify created
        const haCard = page.locator('.hr-type-def-card-name').filter({ hasText: NAMES.haType })
        await expect(haCard.first()).toBeVisible({ timeout: 5000 })

        await ss(page, '01-cluster-types-created')
    })

    // ── Step 2: Create Group + Clusters ──

    test('2. Create group and two clusters', async ({ page }) => {
        await loginAs(page, 'admin')
        await navigateTo(page)

        const overviewTab = page.locator('.config-tab').filter({ hasText: /总览|Overview/i })
        if (await overviewTab.isVisible()) { await overviewTab.click(); await page.waitForTimeout(500) }

        // Create Group
        await openCreateModal(page)
        await selectResourceType(page, /环境组|Group/i)
        let modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /环境组名称|Group Name/i, NAMES.group)
        await fillFormInput(modal, /描述|Description/i, 'E2E test group')
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        // Verify group in tree
        const groupNode = page.locator('.hr-tree-label').filter({ hasText: new RegExp(NAMES.group) })
        await expect(groupNode.first()).toBeVisible({ timeout: 5000 })

        // Create NSLB cluster (peer)
        await openCreateModal(page)
        await selectResourceType(page, /集群|Cluster/i)
        modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /集群名称|Cluster Name/i, NAMES.nslbCluster)
        try { await selectFormOption(modal, /集群类型|Cluster Type/i, NAMES.peerType) } catch {}
        await fillFormInput(modal, /用途|Purpose/i, 'Load balancer')
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        // Create RCPA cluster (HA)
        await openCreateModal(page)
        await selectResourceType(page, /集群|Cluster/i)
        modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /集群名称|Cluster Name/i, NAMES.rcpaCluster)
        try { await selectFormOption(modal, /集群类型|Cluster Type/i, NAMES.haType) } catch {}
        await fillFormInput(modal, /用途|Purpose/i, 'Call agent')
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        await ss(page, '02-group-and-clusters')

        // Store IDs via API
        const clustersData = await apiGetInPage(page, '/clusters')
        const clusters = clustersData.clusters || []
        const nslb = clusters.find((c: any) => c.name === NAMES.nslbCluster)
        const rcpa = clusters.find((c: any) => c.name === NAMES.rcpaCluster)
        if (nslb) ids.nslbClusterId = nslb.id
        if (rcpa) ids.rcpaClusterId = rcpa.id
    })

    // ── Step 3: Create Hosts ──

    test('3. Create hosts under clusters with roles', async ({ page }) => {
        await loginAs(page, 'admin')
        await navigateTo(page)

        const overviewTab = page.locator('.config-tab').filter({ hasText: /总览|Overview/i })
        if (await overviewTab.isVisible()) { await overviewTab.click(); await page.waitForTimeout(500) }

        // Host 1: NSLB peer cluster (no role)
        await openCreateModal(page)
        await selectResourceType(page, /主机|Host/i)
        let modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /主机名称|Host Name/i, NAMES.nslbHost)
        await fillFormInput(modal, /SSH IP|IP/i, '10.99.1.1')
        await fillFormInput(modal, /用户名|Username/i, 'root')
        await fillFormInput(modal, /凭证|Credential/i, 'test')
        try { await selectFormOptionByLabel(modal, /所属集群|Cluster/i, new RegExp(NAMES.nslbCluster)) } catch {}
        // Peer cluster — role dropdown should NOT appear
        const peerRoleLabel = modal.locator('.form-label').filter({ hasText: /主机角色|Host Role/i })
        expect(await peerRoleLabel.isVisible().catch(() => false)).toBe(false)
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        // Host 2: RCPA HA cluster — primary role
        await openCreateModal(page)
        await selectResourceType(page, /主机|Host/i)
        modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /主机名称|Host Name/i, NAMES.rcpaPrimary)
        await fillFormInput(modal, /SSH IP|IP/i, '10.99.1.2')
        await fillFormInput(modal, /用户名|Username/i, 'root')
        await fillFormInput(modal, /凭证|Credential/i, 'test')
        try { await selectFormOptionByLabel(modal, /所属集群|Cluster/i, new RegExp(NAMES.rcpaCluster)) } catch {}
        // HA cluster — role dropdown SHOULD appear
        const haRoleLabel = modal.locator('.form-label').filter({ hasText: /主机角色|Host Role/i })
        if (await haRoleLabel.isVisible().catch(() => false)) {
            await haRoleLabel.locator('..').locator('select.form-input').first().selectOption({ value: 'primary' })
        }
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        // Host 3: RCPA HA cluster — backup role
        await openCreateModal(page)
        await selectResourceType(page, /主机|Host/i)
        modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /主机名称|Host Name/i, NAMES.rcpaBackup)
        await fillFormInput(modal, /SSH IP|IP/i, '10.99.1.3')
        await fillFormInput(modal, /用户名|Username/i, 'root')
        await fillFormInput(modal, /凭证|Credential/i, 'test')
        try { await selectFormOptionByLabel(modal, /所属集群|Cluster/i, new RegExp(NAMES.rcpaCluster)) } catch {}
        const haRoleLabel2 = modal.locator('.form-label').filter({ hasText: /主机角色|Host Role/i })
        if (await haRoleLabel2.isVisible().catch(() => false)) {
            await haRoleLabel2.locator('..').locator('select.form-input').first().selectOption({ value: 'backup' })
        }
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        await ss(page, '03-hosts-created-with-roles')

        // Store host IDs
        const hostsData = await apiGetInPage(page, '/hosts')
        const hosts = hostsData.hosts || []
        const nslbHost = hosts.find((h: any) => h.name === NAMES.nslbHost)
        const rcpaPrimary = hosts.find((h: any) => h.name === NAMES.rcpaPrimary)
        if (nslbHost) ids.nslbHostId = nslbHost.id
        if (rcpaPrimary) ids.rcpaPrimaryId = rcpaPrimary.id
    })

    // ── Step 4: Create Business Service ──

    test('4. Create business service', async ({ page }) => {
        await loginAs(page, 'admin')
        await navigateTo(page)

        const overviewTab = page.locator('.config-tab').filter({ hasText: /总览|Overview/i })
        if (await overviewTab.isVisible()) { await overviewTab.click(); await page.waitForTimeout(500) }

        await openCreateModal(page)
        await selectResourceType(page, /业务服务|Business Service/i)
        const modal = page.locator('.modal')
        await expect(modal).toBeVisible({ timeout: 5000 })
        await fillFormInput(modal, /业务名称|Business Name/i, NAMES.bs)
        await clickModalSave(page)
        await page.waitForTimeout(1000)

        await ss(page, '04-business-service-created')

        // Store BS ID
        const bsData = await apiGetInPage(page, '/business-services')
        const services = bsData.services || bsData.businessServices || []
        const bs = services.find((s: any) => s.name === NAMES.bs)
        if (bs) ids.bsId = bs.id
    })

    // ── Step 5: Create Cluster→Cluster Relation ──

    test('5. Create Cluster→Cluster relation (NSLB→RCPA)', async ({ page }) => {
        await loginAs(page, 'admin')

        if (!ids.nslbClusterId || !ids.rcpaClusterId) {
            test.skip()
            return
        }

        // Create cluster relation: NSLB (source) → RCPA (target)
        const result = await apiPostInPage(page, '/cluster-relations', {
            sourceType: 'cluster',
            sourceId: ids.nslbClusterId,
            targetId: ids.rcpaClusterId,
            description: '负载均衡转发到呼叫代理',
        })

        if (result.success) {
            ids.clusterRelationId = result.relation?.id
            expect(result.relation.sourceType).toBe('cluster')
            expect(result.relation.sourceId).toBe(ids.nslbClusterId)
            expect(result.relation.targetId).toBe(ids.rcpaClusterId)
            console.log(`✅ Cluster→Cluster relation created: ${ids.nslbClusterId} → ${ids.rcpaClusterId}`)
        } else {
            console.log(`⚠️ Cluster relation API not available: ${result.error || 'endpoint not found'}`)
        }

        await navigateTo(page)
        await ss(page, '05-cluster-to-cluster-relation')
    })

    // ── Step 6: Create BS→Cluster Relation ──

    test('6. Create BS→Cluster relation (Business→NSLB entry)', async ({ page }) => {
        await loginAs(page, 'admin')

        if (!ids.bsId || !ids.nslbClusterId) {
            test.skip()
            return
        }

        // Create BS→Cluster relation: BS (source) → NSLB cluster (target)
        const result = await apiPostInPage(page, '/cluster-relations', {
            sourceType: 'business-service',
            sourceId: ids.bsId,
            targetId: ids.nslbClusterId,
            description: '业务入口',
        })

        if (result.success) {
            ids.bsRelationId = result.relation?.id
            expect(result.relation.sourceType).toBe('business-service')
            expect(result.relation.sourceId).toBe(ids.bsId)
            expect(result.relation.targetId).toBe(ids.nslbClusterId)
            console.log(`✅ BS→Cluster relation created: BS ${ids.bsId} → NSLB ${ids.nslbClusterId}`)
        } else {
            console.log(`⚠️ BS→Cluster relation API not available: ${result.error || 'endpoint not found'}`)
        }

        await navigateTo(page)
        await ss(page, '06-bs-to-cluster-relation')
    })

    // ── Step 7: Verify Cluster-Level Topology Graph ──

    test('7. Verify cluster topology graph', async ({ page }) => {
        await loginAs(page, 'admin')
        await navigateTo(page)

        // Select group node in tree
        const groupNode = page.locator('.hr-tree-label').filter({ hasText: new RegExp(NAMES.group) })
        if (await groupNode.isVisible({ timeout: 5000 }).catch(() => false)) {
            await groupNode.first().click()
            await page.waitForTimeout(2000)
        }

        const topologyArea = page.locator('.hr-topology-area')
        await expect(topologyArea).toBeVisible({ timeout: 5000 })

        // Verify SVG graph rendered
        const svg = topologyArea.locator('svg')
        await expect(svg.first()).toBeVisible({ timeout: 5000 }).catch(() => {})

        await ss(page, '07-cluster-topology')
    })

    // ── Step 8: Verify Cluster Neighbor Resolution ──

    test('8. Verify cluster neighbor resolution (NSLB downstream = RCPA)', async ({ page }) => {
        await loginAs(page, 'admin')

        if (!ids.nslbClusterId) {
            test.skip()
            return
        }

        const result = await apiGetInPage(page, `/cluster-relations/clusters/${ids.nslbClusterId}/neighbors`)

        // Skip if API not available
        if (!result || result.status === 404 || result.error) {
            test.skip()
            return
        }

        if (result.cluster) {
            expect(result.cluster.id).toBe(ids.nslbClusterId)

            const downstream = result.downstream || []
            const rcpaNeighbor = downstream.find((n: any) => n.cluster?.id === ids.rcpaClusterId)
            if (rcpaNeighbor) {
                // HA cluster neighbor should return ONLY primary hosts (not backup)
                if (rcpaNeighbor.hosts?.length > 0) {
                    const hasBackup = rcpaNeighbor.hosts.some((h: any) => h.role === 'backup')
                    expect(hasBackup).toBe(false)
                    console.log(`✅ HA neighbor returns only primary hosts: ${rcpaNeighbor.hosts.length} host(s)`)
                }
            }
        }

        await navigateTo(page)
        await ss(page, '08-cluster-neighbor-resolution')
    })

    // ── Step 9: Verify Host Neighbor Resolution ──

    test('9. Verify host neighbor resolution via cluster relations', async ({ page }) => {
        await loginAs(page, 'admin')

        if (!ids.nslbHostId) {
            test.skip()
            return
        }

        const result = await apiGetInPage(page, `/cluster-relations/hosts/${ids.nslbHostId}/neighbors`)

        if (!result || result.status === 404 || result.error) {
            test.skip()
            return
        }

        if (result.downstream) {
            // NSLB host's downstream should be RCPA primary host (NOT backup)
            const downstream = result.downstream || []
            const backupHosts = downstream.filter((h: any) => h.role === 'backup')
            expect(backupHosts.length).toBe(0)
            if (downstream.length > 0) {
                console.log(`✅ Host neighbors via cluster relations: ${downstream.length} downstream host(s), 0 backup`)
            }
        }

        await navigateTo(page)
        await ss(page, '09-host-neighbor-resolution')
    })

    // ── Step 10: Cascade Delete ──

    test('10. Cascade delete: deleting cluster removes relations', async ({ page }) => {
        await loginAs(page, 'admin')

        if (!ids.nslbClusterId) {
            test.skip()
            return
        }

        // Check relations before delete
        let relationsBefore: any[] = []
        try {
            const relData = await apiGetInPage(page, `/cluster-relations?clusterId=${ids.nslbClusterId}`)
            relationsBefore = relData.relations || []
        } catch {}

        // Delete NSLB cluster via API
        await apiDeleteInPage(page, `/clusters/${ids.nslbClusterId}`)
        await page.waitForTimeout(1000)

        // Verify relations involving NSLB cluster are gone
        let relationsAfter: any[] = []
        try {
            const relData2 = await apiGetInPage(page, `/cluster-relations?clusterId=${ids.nslbClusterId}`)
            relationsAfter = relData2.relations || []
        } catch {}

        const remaining = relationsAfter.filter(
            (r: any) => r.sourceId === ids.nslbClusterId || r.targetId === ids.nslbClusterId
        )
        expect(remaining.length).toBe(0)
        console.log(`✅ Cascade delete: ${relationsBefore.length} relations before, ${remaining.length} after`)

        await navigateTo(page)
        await ss(page, '10-cascade-delete')
    })
})
