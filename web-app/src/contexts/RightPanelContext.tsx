import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type MarketTab = 'all' | 'mcp' | 'skill'

interface RightPanelContextType {
    isMarketOpen: boolean
    marketActiveTab: MarketTab
    openMarket: (tab?: MarketTab) => void
    closeMarket: () => void
    setMarketActiveTab: (tab: MarketTab) => void
}

const RightPanelContext = createContext<RightPanelContextType | null>(null)

export function RightPanelProvider({ children }: { children: ReactNode }) {
    const [isMarketOpen, setIsMarketOpen] = useState(false)
    const [marketActiveTab, setMarketActiveTab] = useState<MarketTab>('all')

    const openMarket = useCallback((tab: MarketTab = 'all') => {
        setMarketActiveTab(tab)
        setIsMarketOpen(true)
    }, [])

    const closeMarket = useCallback(() => {
        setIsMarketOpen(false)
    }, [])

    const value = useMemo(() => ({
        isMarketOpen,
        marketActiveTab,
        openMarket,
        closeMarket,
        setMarketActiveTab,
    }), [isMarketOpen, marketActiveTab, openMarket, closeMarket])

    return (
        <RightPanelContext.Provider value={value}>
            {children}
        </RightPanelContext.Provider>
    )
}

export function useRightPanel() {
    const context = useContext(RightPanelContext)
    if (!context) {
        throw new Error('useRightPanel must be used within a RightPanelProvider')
    }
    return context
}
