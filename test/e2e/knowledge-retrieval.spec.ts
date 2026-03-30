import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

const KNOWLEDGE_URL = process.env.KNOWLEDGE_SERVICE_URL || 'http://127.0.0.1:8092'

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="Your name"]', 'admin')
  await page.click('button:has-text("Enter")')
  await page.waitForURL('/')
}

async function createSource(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${KNOWLEDGE_URL}/ops-knowledge/sources`, {
    data: {
      name: `e2e-knowledge-${Date.now()}`,
      description: 'retrieval e2e',
    },
  })
  expect(response.ok()).toBeTruthy()
  const json = await response.json()
  return json.id as string
}

async function uploadMarkdown(request: APIRequestContext, sourceId: string) {
  const response = await request.post(`${KNOWLEDGE_URL}/ops-knowledge/sources/${sourceId}/documents:ingest`, {
    multipart: {
      files: {
        name: 'itsm-deployment.md',
        mimeType: 'text/markdown',
        buffer: Buffer.from(`
# ITSM 部署方案

ITSM 部署在 itsm-01 和 itsm-02 两台服务器上。
运维智能体平台部署在 EulerOS 2 SP12 x86 环境。
        `.trim(), 'utf-8'),
      },
    },
  })

  expect(response.ok()).toBeTruthy()
}

async function deleteSource(request: APIRequestContext, sourceId: string) {
  await request.delete(`${KNOWLEDGE_URL}/ops-knowledge/sources/${sourceId}`)
}

test.describe('Knowledge retrieval compare', () => {
  test('runs ITSM compare search successfully from the UI', async ({ page, request }) => {
    const sourceId = await createSource(request)
    const consoleErrors: string[] = []

    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text())
      }
    })

    try {
      await uploadMarkdown(request, sourceId)
      await loginAsAdmin(page)
      await page.goto(`/knowledge/${sourceId}?tab=retrieval`)
      await page.waitForLoadState('networkidle')

      await page.fill('#knowledge-retrieval-query', 'ITSM')
      await page.getByRole('button', { name: /Run Test|测试/ }).click()

      await expect(page.getByText(/部署方案/).first()).toBeVisible({ timeout: 15000 })
      await expect(page.getByText(/Comparison|结果对比/)).toBeVisible()
      await expect(page.getByText(/当前条件下没有召回结果。|No retrieval results under the current conditions./)).toHaveCount(0)

      expect(consoleErrors.filter(text => text.includes('/ops-knowledge/search/compare 404'))).toHaveLength(0)
    } finally {
      try {
        await deleteSource(request, sourceId)
      } catch {
        // Best-effort cleanup: request context can already be torn down after a fatal failure.
      }
    }
  })
})
