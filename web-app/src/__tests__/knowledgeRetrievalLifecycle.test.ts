import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())

function read(path: string): string {
    return readFileSync(resolve(ROOT, path), 'utf-8')
}

describe('knowledge retrieval test lifecycle', () => {
    it('keeps retrieval test state in the current configure-page session only', () => {
        const retrievalTabSource = read('src/app/modules/knowledge/components/KnowledgeRetrievalTab.tsx')
        const configurePageSource = read('src/app/modules/knowledge/pages/KnowledgeConfigurePage.tsx')

        expect(retrievalTabSource).not.toContain('localStorage')
        expect(retrievalTabSource).not.toContain('sessionStorage')
        expect(configurePageSource).toContain('hasOpenedRetrievalTab')
        expect(configurePageSource).toContain("hidden={activeTab !== 'retrieval'}")
        expect(configurePageSource).toContain('key={source.id}')
    })
})
