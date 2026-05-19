/**
 * Operation Intelligence Frontend Structure Tests
 *
 * Static analysis tests that verify the frontend module structure,
 * boundaries, API integration, i18n alignment, chart colors,
 * and component imports without requiring a running app.
 */
import { describe, expect, it } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import { resolve, join } from 'node:path'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..', '..')
const WEB_APP = join(PROJECT_ROOT, 'web-app')
const MODULE_DIR = join(WEB_APP, 'src', 'app', 'modules', 'operation-intelligence')

function read(relativePath: string): string {
  return readFileSync(resolve(WEB_APP, relativePath), 'utf-8')
}

function readFile(absPath: string): string {
  return readFileSync(absPath, 'utf-8')
}

// =============================================================================
// 1. Module Registration & Route
// =============================================================================
describe('module registration', () => {
  it('registers an admin route and sidebar item', () => {
    const src = readFile(join(MODULE_DIR, 'module.ts'))

    expect(src).toContain("id: 'operation-intelligence'")
    expect(src).toContain("path: '/operation-intelligence'")
    expect(src).toContain("titleKey: 'sidebar.operationIntelligence'")
    expect(src).toContain("icon: 'businessIntelligence'")
    expect(src).toContain("access: 'authenticated'")
  })

  it('imports page component from correct relative path', () => {
    const src = readFile(join(MODULE_DIR, 'module.ts'))
    expect(src).toContain("./pages/OperationIntelligencePage")
  })

  it('module file exists at expected location', () => {
    expect(existsSync(join(MODULE_DIR, 'module.ts'))).toBe(true)
  })
})

// =============================================================================
// 2. Component Files & Structure
// =============================================================================
describe('component file structure', () => {
  const expectedComponents = [
    'OperationIntelligenceChart',
    'OperationIntelligenceFilters',
    'DimensionScoreCards',
    'ContributionAnalysis',
    'IndicatorDetailTable',
    'AlarmDetailTable',
    'TopologyView',
  ]

  for (const name of expectedComponents) {
    it(`${name}.tsx exists`, () => {
      expect(existsSync(join(MODULE_DIR, 'components', `${name}.tsx`))).toBe(true)
    })
  }

  it('page file exists', () => {
    expect(existsSync(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))).toBe(true)
  })

  it('chart-colors style module exists', () => {
    expect(existsSync(join(MODULE_DIR, 'styles', 'chart-colors.ts'))).toBe(true)
  })

  it('CSS file exists', () => {
    expect(existsSync(join(MODULE_DIR, 'styles', 'operation-intelligence.css'))).toBe(true)
  })
})

// =============================================================================
// 3. Platform Boundary — no cross-module imports
// =============================================================================
describe('platform boundary compliance', () => {
  const filesToCheck = [
    'pages/OperationIntelligencePage.tsx',
    'components/OperationIntelligenceChart.tsx',
    'components/OperationIntelligenceFilters.tsx',
    'components/DimensionScoreCards.tsx',
    'components/ContributionAnalysis.tsx',
    'components/IndicatorDetailTable.tsx',
    'components/AlarmDetailTable.tsx',
    'components/TopologyView.tsx',
  ]

  for (const file of filesToCheck) {
    it(`${file} does not import other modules`, () => {
      const src = readFile(join(MODULE_DIR, file))
      expect(src).not.toMatch(/from ['"].*\/modules\/(?!operation-intelligence)[^/]/)
    })

    it(`${file} imports from platform via relative ../../../platform path`, () => {
      const src = readFile(join(MODULE_DIR, file))
      const platformImports = src.match(/from ['"](\.\.\/)+platform/g) || []
      for (const imp of platformImports) {
        expect(imp).toContain('../../../platform')
      }
    })
  }
})

// =============================================================================
// 4. Shared UI Primitives Usage
// =============================================================================
describe('shared UI primitives usage', () => {
  it('page uses PageHeader, SectionCard, AnalyticsTableCard, ChartHeaderLegend', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain("../../../platform/ui/primitives/PageHeader")
    expect(src).toContain("../../../platform/ui/primitives/SectionCard")
    expect(src).toContain("../../../platform/ui/primitives/AnalyticsTableCard")
    expect(src).toContain("../../../platform/ui/primitives/ChartHeaderLegend")
  })

  it('filters use FilterSelect and Button from platform', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'OperationIntelligenceFilters.tsx'))
    expect(src).toContain("../../../platform/ui/filters/FilterSelect")
    expect(src).toContain("../../../platform/ui/primitives/Button")
  })

  it('DimensionScoreCards uses StatCard from platform', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'DimensionScoreCards.tsx'))
    expect(src).toContain("../../../platform/ui/primitives/StatCard")
  })

  it('ContributionAnalysis uses PieDistributionCard and SectionCard', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'ContributionAnalysis.tsx'))
    expect(src).toContain("../../../platform/ui/primitives/PieDistributionCard")
    expect(src).toContain("../../../platform/ui/primitives/SectionCard")
  })

  it('IndicatorDetailTable uses Pagination from platform', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'IndicatorDetailTable.tsx'))
    expect(src).toContain("../../../platform/ui/primitives/Pagination")
  })

  it('AlarmDetailTable uses Pagination from platform', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'AlarmDetailTable.tsx'))
    expect(src).toContain("../../../platform/ui/primitives/Pagination")
  })
})

// =============================================================================
// 5. API Integration
// =============================================================================
describe('API service integration', () => {
  it('API service routes to operation-intelligence directly, not through gateway', () => {
    const src = read('src/services/operationIntelligenceAPI.ts')
    expect(src).toContain('OPERATION_INTELLIGENCE_SERVICE_URL')
    expect(src).toContain('runtime.OPERATION_INTELLIGENCE_SECRET_KEY')
    expect(src).toContain("'x-secret-key': runtime.OPERATION_INTELLIGENCE_SECRET_KEY")
    expect(src).not.toContain('GATEWAY_URL')
    expect(src).not.toContain('GATEWAY_SECRET_KEY')
  })

  it('API service covers all QoS endpoints', () => {
    const src = read('src/services/operationIntelligenceAPI.ts')
    expect(src).toContain('/qos/getHealthIndicator')
    expect(src).toContain('/qos/getResourceIndicatorDetail')
    expect(src).toContain('/qos/getContributionData')
    expect(src).toContain('/qos/getProductConfigRule')
    expect(src).toContain('/qos/getEnvironments')
    expect(src).toContain('getIndicatorDetail')
  })

  it('component code references detail endpoints by full path', () => {
    const dimSrc = readFile(join(MODULE_DIR, 'components', 'DimensionScoreCards.tsx'))
    expect(dimSrc).toContain('/qos/getAvailableIndicatorDetail')
    expect(dimSrc).toContain('/qos/getPerformanceIndicatorDetail')

    const indSrc = readFile(join(MODULE_DIR, 'components', 'IndicatorDetailTable.tsx'))
    expect(indSrc).toContain('/qos/getAvailableIndicatorDetail')
    expect(indSrc).toContain('/qos/getPerformanceIndicatorDetail')

    const alarmSrc = readFile(join(MODULE_DIR, 'components', 'AlarmDetailTable.tsx'))
    expect(alarmSrc).toContain('/qos/getAlarmIndicatorDetail')
  })

  it('runtime config exports operation-intelligence URL and secret', () => {
    const src = read('src/config/runtime.ts')
    expect(src).toContain('operationIntelligenceServiceUrl')
    expect(src).toContain('operationIntelligenceSecretKey')
    expect(src).toContain("const OPERATION_INTELLIGENCE_PATH_PREFIX = '/operation-intelligence'")
    expect(src).toContain('OPERATION_INTELLIGENCE_SERVICE_URL')
    expect(src).toContain('runtime.OPERATION_INTELLIGENCE_SECRET_KEY')
  })

  it('config.json and config.json.example declare service URL and secret', () => {
    const config = JSON.parse(read('../web-app/config.json')) as Record<string, unknown>
    const example = JSON.parse(read('../web-app/config.json.example')) as Record<string, unknown>

    expect(config.operationIntelligenceServiceUrl).toBe('http://127.0.0.1:8096')
    expect(config.operationIntelligenceSecretKey).toBeTruthy()
    expect(example.operationIntelligenceServiceUrl).toBe('http://127.0.0.1:8096')
    expect(example.operationIntelligenceSecretKey).toBeTruthy()
  })
})

// =============================================================================
// 6. Types
// =============================================================================
describe('TypeScript types', () => {
  it('HealthIndicatorPoint has timestamp and value fields', () => {
    const src = read('src/types/operationIntelligence.ts')
    expect(src).toContain('timestamp')
    expect(src).toContain('value')
    expect(src).toContain('HealthIndicatorPoint')
    expect(src).toContain('HealthIndicatorResponse')
    expect(src).toContain('IndicatorDetailResponse')
  })

  it('API service uses correct types from types file', () => {
    const apiSrc = read('src/services/operationIntelligenceAPI.ts')
    expect(apiSrc).toContain("from '../types/operationIntelligence'")
    expect(apiSrc).toContain('HealthIndicatorResponse')
    expect(apiSrc).toContain('IndicatorDetailResponse')
  })
})

// =============================================================================
// 7. i18n Alignment
// =============================================================================
describe('i18n alignment', () => {
  const en = JSON.parse(read('src/i18n/en.json'))
  const zh = JSON.parse(read('src/i18n/zh.json'))

  it('sidebar labels exist in both locales', () => {
    expect(en.sidebar.operationIntelligence).toBe('Operation Intelligence')
    expect(zh.sidebar.operationIntelligence).toBe('智能运维')
  })

  it('operationIntelligence keys are aligned between en and zh', () => {
    const enKeys = Object.keys(en.operationIntelligence).sort()
    const zhKeys = Object.keys(zh.operationIntelligence).sort()
    expect(enKeys).toEqual(zhKeys)
  })

  it('essential copy keys exist in both locales', () => {
    const requiredKeys = [
      'title', 'subtitle', 'availability', 'performance', 'resource',
      'chart', 'loading', 'noData', 'noDataShort', 'loadFailed',
      'refresh', 'environment', 'product', 'timeRange',
      'availabilityDetail', 'performanceDetail', 'alarmDetail',
      'good', 'warning', 'orange', 'critical',
      'topology', 'topologySubtitle', 'healthScore',
      'contributionDetail', 'detailSubtitle',
    ]
    for (const key of requiredKeys) {
      expect(en.operationIntelligence[key], `en.operationIntelligence.${key} missing`).toBeTruthy()
      expect(zh.operationIntelligence[key], `zh.operationIntelligence.${key} missing`).toBeTruthy()
    }
  })

  it('time range options exist in both locales', () => {
    const timeKeys = ['last15Min', 'lastHour', 'last2Hours', 'last12Hours', 'last24Hours', 'last48Hours']
    for (const key of timeKeys) {
      expect(en.operationIntelligence[key]).toBeTruthy()
      expect(zh.operationIntelligence[key]).toBeTruthy()
    }
  })

  it('table column headers exist in both locales', () => {
    const tableKeys = [
      'timestamp', 'indicatorName', 'dn', 'score',
      'successRatio', 'successCount', 'totalCount',
      'avgResTime', 'minResTime', 'maxResTime',
      'alarmName', 'severity', 'count', 'alarmDesc', 'alarmDetail',
    ]
    for (const key of tableKeys) {
      expect(en.operationIntelligence[key], `en.operationIntelligence.${key} missing`).toBeTruthy()
      expect(zh.operationIntelligence[key], `zh.operationIntelligence.${key} missing`).toBeTruthy()
    }
  })
})

// =============================================================================
// 8. Chart Colors
// =============================================================================
describe('chart colors', () => {
  it('exports CHART_COLORS with required keys', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'chart-colors.ts'))
    expect(src).toContain('availability')
    expect(src).toContain('performance')
    expect(src).toContain('resource')
    expect(src).toContain('good')
    expect(src).toContain('warning')
    expect(src).toContain('orange')
    expect(src).toContain('critical')
    expect(src).toContain('neutral')
  })

  it('exports CHART_COLORS_LIGHT with translucent variants', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'chart-colors.ts'))
    expect(src).toContain('CHART_COLORS_LIGHT')
    expect(src).toContain('hexToRgba')
  })

  it('all colors are valid hex values', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'chart-colors.ts'))
    const hexMatches = src.match(/'#[0-9a-fA-F]{6}'/g) || []
    expect(hexMatches.length).toBeGreaterThanOrEqual(5)
  })
})

// =============================================================================
// 9. Page Component Integration
// =============================================================================
describe('page component integration', () => {
  it('page imports all expected child components', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('OperationIntelligenceChart')
    expect(src).toContain('DimensionScoreCards')
    expect(src).toContain('IndicatorDetailTable')
    expect(src).toContain('AlarmDetailTable')
    expect(src).toContain('OperationIntelligenceFilters')
    expect(src).toContain('ContributionAnalysis')
    expect(src).toContain('TopologyView')
  })

  it('page uses i18n via useTranslation', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('useTranslation')
  })

  it('page accesses userId from UserContext', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('useUser')
    expect(src).toContain('userId')
  })

  it('page auto-refreshes health data on interval', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('setInterval')
    expect(src).toContain('clearInterval')
    expect(src).toContain('60000')
  })

  it('page renders detail tabs with role="tablist" for accessibility', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('role="tablist"')
    expect(src).toContain('role="tab"')
    expect(src).toContain('aria-selected')
    expect(src).toContain('aria-label')
  })

  it('page shows error banner with i18n key', () => {
    const src = readFile(join(MODULE_DIR, 'pages', 'OperationIntelligencePage.tsx'))
    expect(src).toContain('conn-banner-error')
    expect(src).toContain('loadFailedWithReason')
  })
})

// =============================================================================
// 10. Component-specific Checks
// =============================================================================
describe('component-specific checks', () => {
  it('OperationIntelligenceChart renders loading and empty states', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'OperationIntelligenceChart.tsx'))
    expect(src).toContain('loading')
    expect(src).toContain('points.length === 0')
    expect(src).toContain('noData')
  })

  it('OperationIntelligenceChart uses CHART_COLORS_LIGHT for mark areas', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'OperationIntelligenceChart.tsx'))
    expect(src).toContain('CHART_COLORS_LIGHT')
    expect(src).toContain('markArea')
  })

  it('OperationIntelligenceFilters fetches environments on mount', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'OperationIntelligenceFilters.tsx'))
    expect(src).toContain('getEnvironments(userId)')
    expect(src).toContain('getProductConfigRule')
  })

  it('OperationIntelligenceFilters exposes product, environment, and time range filters', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'OperationIntelligenceFilters.tsx'))
    expect(src).toContain("t('operationIntelligence.product')")
    expect(src).toContain("t('operationIntelligence.environment')")
    expect(src).toContain("t('operationIntelligence.timeRange')")
  })

  it('DimensionScoreCards fetches all three dimensions', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'DimensionScoreCards.tsx'))
    expect(src).toContain('getAvailableIndicatorDetail')
    expect(src).toContain('getPerformanceIndicatorDetail')
    expect(src).toContain('getResourceIndicatorDetail')
  })

  it('ContributionAnalysis fetches contribution data', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'ContributionAnalysis.tsx'))
    expect(src).toContain('getContributionData')
    expect(src).toContain('contribution')
  })

  it('IndicatorDetailTable switches endpoint based on type A vs P', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'IndicatorDetailTable.tsx'))
    expect(src).toContain('/qos/getAvailableIndicatorDetail')
    expect(src).toContain('/qos/getPerformanceIndicatorDetail')
  })

  it('AlarmDetailTable calls alarm endpoint', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'AlarmDetailTable.tsx'))
    expect(src).toContain('/qos/getAlarmIndicatorDetail')
  })

  it('TopologyView shows health score with status label', () => {
    const src = readFile(join(MODULE_DIR, 'components', 'TopologyView.tsx'))
    expect(src).toContain('healthScore')
    expect(src).toContain('statusColor')
    expect(src).toContain('statusLabel')
    expect(src).toContain('0.9')
    expect(src).toContain('0.7')
    expect(src).toContain('0.5')
  })
})

// =============================================================================
// 11. CSS Quality
// =============================================================================
describe('CSS quality', () => {
  it('no !important declarations', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'operation-intelligence.css'))
    expect(src).not.toContain('!important')
  })

  it('does not redefine platform-level component classes', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'operation-intelligence.css'))
    expect(src).not.toContain('.score-card')
    expect(src).not.toContain('.operation-intelligence-filters button')
  })

  it('uses scoped operation-intelligence class prefix', () => {
    const src = readFile(join(MODULE_DIR, 'styles', 'operation-intelligence.css'))
    expect(src).toContain('.operation-intelligence-')
  })
})

// =============================================================================
// 12. No Legacy Health-Curve References
// =============================================================================
describe('no legacy references', () => {
  const allFiles = [
    'pages/OperationIntelligencePage.tsx',
    'components/OperationIntelligenceChart.tsx',
    'components/OperationIntelligenceFilters.tsx',
    'components/DimensionScoreCards.tsx',
    'components/ContributionAnalysis.tsx',
    'components/IndicatorDetailTable.tsx',
    'components/AlarmDetailTable.tsx',
    'components/TopologyView.tsx',
  ]

  for (const file of allFiles) {
    it(`${file} has no healthCurve references`, () => {
      const src = readFile(join(MODULE_DIR, file))
      expect(src).not.toContain('healthCurve')
      expect(src).not.toContain('HealthCurve')
    })
  }
})
