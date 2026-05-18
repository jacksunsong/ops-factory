import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())

function read(path: string): string {
    return readFileSync(resolve(ROOT, path), 'utf-8')
}

describe('operation intelligence frontend structure', () => {
    it('registers an authenticated route and sidebar item', () => {
        const moduleSource = read('src/app/modules/operation-intelligence/module.ts')

        expect(moduleSource).toContain("id: 'operation-intelligence'")
        expect(moduleSource).toContain("path: '/operation-intelligence'")
        expect(moduleSource).toContain("titleKey: 'sidebar.operationIntelligence'")
        expect(moduleSource).toContain("icon: 'businessIntelligence'")
        expect(moduleSource).toContain("access: 'authenticated'")
    })

    it('declares runtime URL and secret configuration in code and examples', () => {
        const runtimeSource = read('src/config/runtime.ts')
        const config = JSON.parse(read('../web-app/config.json')) as Record<string, unknown>
        const example = JSON.parse(read('../web-app/config.json.example')) as Record<string, unknown>

        expect(runtimeSource).toContain('operationIntelligenceServiceUrl')
        expect(runtimeSource).toContain('operationIntelligenceSecretKey')
        expect(runtimeSource).toContain("const OPERATION_INTELLIGENCE_PATH_PREFIX = '/operation-intelligence'")
        expect(runtimeSource).toContain('OPERATION_INTELLIGENCE_SERVICE_URL')
        expect(runtimeSource).toContain('OPERATION_INTELLIGENCE_SECRET_KEY')
        expect(config.operationIntelligenceServiceUrl).toBe('http://127.0.0.1:8096')
        expect(config.operationIntelligenceSecretKey).toBeTruthy()
        expect(example.operationIntelligenceServiceUrl).toBe('http://127.0.0.1:8096')
        expect(example.operationIntelligenceSecretKey).toBeTruthy()
    })

    it('routes all API calls to operation-intelligence with secret headers', () => {
        const apiSource = read('src/services/operationIntelligenceAPI.ts')

        expect(apiSource).toContain('OPERATION_INTELLIGENCE_SERVICE_URL')
        expect(apiSource).toContain('OPERATION_INTELLIGENCE_SECRET_KEY')
        expect(apiSource).toContain("'x-secret-key': runtime.OPERATION_INTELLIGENCE_SECRET_KEY")
        expect(apiSource).toContain('/qos/getHealthIndicator')
        expect(apiSource).toContain('/qos/getResourceIndicatorDetail')
        expect(apiSource).toContain('/qos/getContributionData')
        expect(apiSource).toContain('/qos/getProductConfigRule')
        expect(apiSource).toContain('/qos/getEnvironments')
        expect(apiSource).not.toContain('GATEWAY_URL')
        expect(apiSource).not.toContain('GATEWAY_SECRET_KEY')
    })

    it('keeps operation-intelligence user-facing copy aligned in both locales', () => {
        const en = JSON.parse(read('src/i18n/en.json'))
        const zh = JSON.parse(read('src/i18n/zh.json'))

        expect(en.sidebar.operationIntelligence).toBe('Operation Intelligence')
        expect(zh.sidebar.operationIntelligence).toBe('智能运维')
        expect(Object.keys(en.operationIntelligence).sort()).toEqual(Object.keys(zh.operationIntelligence).sort())
        expect(en.operationIntelligence.title).toBeTruthy()
        expect(zh.operationIntelligence.title).toBeTruthy()
    })

    it('uses module-local files without depending on the removed health-curve module', () => {
        const pageSource = read('src/app/modules/operation-intelligence/pages/OperationIntelligencePage.tsx')
        const chartSource = read('src/app/modules/operation-intelligence/components/OperationIntelligenceChart.tsx')
        const filtersSource = read('src/app/modules/operation-intelligence/components/OperationIntelligenceFilters.tsx')

        expect(pageSource).toContain('operationIntelligenceAPI')
        expect(pageSource).toContain('OperationIntelligenceChart')
        expect(pageSource).toContain('OperationIntelligenceFilters')
        expect(pageSource).not.toContain('healthCurve')
        expect(chartSource).not.toContain('HealthCurve')
        expect(filtersSource).not.toContain('HealthCurve')
    })

    it('uses shared UI primitives instead of feature-local chrome', () => {
        const pageSource = read('src/app/modules/operation-intelligence/pages/OperationIntelligencePage.tsx')
        const filtersSource = read('src/app/modules/operation-intelligence/components/OperationIntelligenceFilters.tsx')
        const scoreCardsSource = read('src/app/modules/operation-intelligence/components/DimensionScoreCards.tsx')
        const contributionSource = read('src/app/modules/operation-intelligence/components/ContributionAnalysis.tsx')
        const styles = read('src/app/modules/operation-intelligence/styles/operation-intelligence.css')

        expect(pageSource).toContain("../../../platform/ui/primitives/PageHeader")
        expect(pageSource).toContain("../../../platform/ui/primitives/SectionCard")
        expect(pageSource).toContain("../../../platform/ui/primitives/AnalyticsTableCard")
        expect(pageSource).toContain("../../../platform/ui/primitives/ChartHeaderLegend")
        expect(filtersSource).toContain("../../../platform/ui/filters/FilterSelect")
        expect(filtersSource).toContain("../../../platform/ui/primitives/Button")
        expect(filtersSource).toContain('operation-intelligence-control-panel')
        expect(filtersSource).toContain('operation-intelligence-score-chip')
        expect(scoreCardsSource).toContain("../../../platform/ui/primitives/StatCard")
        expect(contributionSource).toContain("../../../platform/ui/primitives/PieDistributionCard")
        expect(styles).not.toContain('!important')
        expect(styles).not.toContain('.score-card')
        expect(styles).not.toContain('.operation-intelligence-filters button')
    })
})
