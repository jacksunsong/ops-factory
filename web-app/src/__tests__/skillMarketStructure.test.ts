import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())

function read(path: string): string {
    return readFileSync(resolve(ROOT, path), 'utf-8')
}

describe('skill market frontend structure', () => {
    it('registers a first-level admin route and sidebar item', () => {
        const moduleSource = read('src/app/modules/skill-market/module.ts')

        expect(moduleSource).toContain("id: 'skill-market'")
        expect(moduleSource).toContain("path: '/skill-market'")
        expect(moduleSource).toContain("titleKey: 'sidebar.skillMarket'")
        expect(moduleSource).toContain("icon: 'skillMarket'")
        expect(moduleSource).toContain("access: 'admin'")
    })

    it('declares runtime URL configuration for the service', () => {
        const runtimeSource = read('src/config/runtime.ts')
        const config = JSON.parse(read('../web-app/config.json')) as Record<string, unknown>

        expect(runtimeSource).toContain('skillMarketServiceUrl')
        expect(runtimeSource).toContain("const SKILL_MARKET_PATH_PREFIX = '/skill-market'")
        expect(runtimeSource).toContain('SKILL_MARKET_SERVICE_URL')
        expect(config.skillMarketServiceUrl).toBe('http://127.0.0.1:8095')
    })

    it('keeps user-facing copy in both locales', () => {
        const en = JSON.parse(read('src/i18n/en.json'))
        const zh = JSON.parse(read('src/i18n/zh.json'))

        expect(en.sidebar.skillMarket).toBe('Skill Market')
        expect(zh.sidebar.skillMarket).toBe('技能市场')
        expect(en.skillMarket.importSkill).toBeTruthy()
        expect(zh.skillMarket.importSkill).toBeTruthy()
        expect(en.skillMarket.descriptionPlaceholder).toBeTruthy()
        expect(zh.skillMarket.descriptionPlaceholder).toBeTruthy()
        expect(Object.keys(en.skillMarket).sort()).toEqual(Object.keys(zh.skillMarket).sort())
        expect(en.skillMarket.defaultInstructions).toContain('---\nname:')
        expect(zh.skillMarket.defaultInstructions).toContain('---\nname:')
    })

    it('agent skills page installs from skill market instead of mock capability market', () => {
        const agentPage = read('src/app/modules/agents/pages/AgentConfigurePage.tsx')
        const skillSection = read('src/app/modules/agents/components/skill/SkillSection.tsx')
        const drawerSource = read('src/app/modules/agents/components/skill/SkillMarketDrawer.tsx')
        const hookSource = read('src/app/modules/agents/hooks/useAgentSkillMarket.ts')

        expect(agentPage).toContain('SkillMarketDrawer')
        expect(agentPage).toContain('setIsSkillMarketOpen(true)')
        expect(agentPage).not.toContain("handleBrowseMarket('skill')")
        expect(skillSection).toContain('onBrowseMarket')
        expect(skillSection).toContain("t('market.browseMarket')")
        expect(drawerSource).toContain('useAgentSkillMarket')
        expect(drawerSource).toContain('onInstalled')
        expect(hookSource).toContain('SKILL_MARKET_SERVICE_URL')
        expect(hookSource).toContain('/skills/install')
        expect(skillSection).not.toContain('AddSkillFromMarketModal')
    })

    it('uses platform list and resource card primitives for the market page', () => {
        const pageSource = read('src/app/modules/skill-market/pages/SkillMarketPage.tsx')
        const cssSource = read('src/app/modules/skill-market/styles/skill-market.css')

        expect(pageSource).toContain('ListWorkbench')
        expect(pageSource).toContain('ListToolbar')
        expect(pageSource).toContain('ListSearchInput')
        expect(pageSource).toContain('ListResultsMeta')
        expect(pageSource).toContain('CardGrid')
        expect(pageSource).toContain('ResourceCard')
        expect(pageSource).toContain('className="empty-state"')
        expect(pageSource).not.toContain('skill-market-search')
        expect(pageSource).not.toContain('skill-market-state')
        expect(pageSource).not.toContain('skill-market-card')
        expect(cssSource).not.toContain('.skill-market-search')
        expect(cssSource).not.toContain('.skill-market-state')
        expect(cssSource).not.toContain('.skill-market-card')
    })

    it('uses a larger create modal with a standard skill template', () => {
        const pageSource = read('src/app/modules/skill-market/pages/SkillMarketPage.tsx')
        const cssSource = read('src/app/modules/skill-market/styles/skill-market.css')

        expect(pageSource).toContain('modal modal-wide skill-market-editor-modal')
        expect(pageSource).toContain("t('skillMarket.defaultInstructions')")
        expect(pageSource).toContain('skill-market-instructions-input')
        expect(cssSource).toContain('.skill-market-editor-modal .modal-body')
        expect(cssSource).toContain('min-height: min(520px, 48vh)')
        expect(read('src/app/platform/styles/UIPrimitives.css')).toContain('font: inherit')
        expect(read('src/app/platform/styles/UIPrimitives.css')).toContain('line-height: 1.5')
    })

    it('supports edit and delete confirmation actions', () => {
        const pageSource = read('src/app/modules/skill-market/pages/SkillMarketPage.tsx')
        const hookSource = read('src/app/modules/skill-market/hooks/useSkillMarket.ts')

        expect(pageSource).toContain('DeleteSkillDialog')
        expect(pageSource).toContain('SkillFormDialog')
        expect(pageSource).toContain("mode=\"edit\"")
        expect(pageSource).toContain('handleEdit')
        expect(hookSource).toContain('fetchSkill')
        expect(hookSource).toContain('updateSkill')
        expect(hookSource).toContain("method: 'PUT'")
        expect(pageSource).toContain("mode === 'create'")
        expect(pageSource).toContain('skill-market-editor-meta')
        expect(pageSource).toContain('skillMarket.directoryName')
    })

    it('imports by direct file selection without an intermediate modal', () => {
        const pageSource = read('src/app/modules/skill-market/pages/SkillMarketPage.tsx')
        const cssSource = read('src/app/modules/skill-market/styles/skill-market.css')

        expect(pageSource).toContain('importInputRef')
        expect(pageSource).toContain('type="file"')
        expect(pageSource).toContain('void handleImportFile(file)')
        expect(pageSource).toContain('skillMarket.importDuplicateSkill')
        expect(pageSource).not.toContain('ImportSkillDialog')
        expect(pageSource).not.toContain('skillIdOptional')
        expect(pageSource).not.toContain('zipFile')
        expect(cssSource).toContain('.skill-market-import-input')
    })
})
