import OperationIntelligencePage from './pages/OperationIntelligencePage'
import type { AppModule } from '../../platform/module-types'

const operationIntelligenceModule: AppModule = {
    id: 'operation-intelligence',
    owner: 'platform',
    routes: [
        { id: 'operation-intelligence.index', path: '/operation-intelligence', component: OperationIntelligencePage, access: 'authenticated' },
    ],
    navItems: [
        {
            id: 'operation-intelligence.nav',
            type: 'route',
            group: 'business',
            order: 11,
            titleKey: 'sidebar.operationIntelligence',
            icon: 'businessIntelligence',
            routeId: 'operation-intelligence.index',
        },
    ],
}

export default operationIntelligenceModule
