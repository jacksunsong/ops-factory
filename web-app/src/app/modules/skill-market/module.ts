import SkillMarketPage from './pages/SkillMarketPage'
import type { AppModule } from '../../platform/module-types'

const skillMarketModule: AppModule = {
    id: 'skill-market',
    owner: 'platform',
    routes: [
        { id: 'skill-market.index', path: '/skill-market', component: SkillMarketPage, access: 'authenticated' },
    ],
    navItems: [
        {
            id: 'skill-market.nav',
            type: 'route',
            group: 'config',
            order: 12,
            titleKey: 'sidebar.skillMarket',
            icon: 'skillMarket',
            routeId: 'skill-market.index',
        },
    ],
}

export default skillMarketModule
