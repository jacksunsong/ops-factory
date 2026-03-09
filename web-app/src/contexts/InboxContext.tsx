import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Session } from '@goosed/sdk'
import { useGoosed } from './GoosedContext'
import { useUser } from './UserContext'
import { isScheduledSession } from '../config/runtime'

interface InboxSession extends Session {
    agentId: string
}

interface InboxContextValue {
    unreadCount: number
    unreadSessions: InboxSession[]
    scheduledSessions: InboxSession[]
    isLoading: boolean
    refresh: () => Promise<void>
    isSessionRead: (agentId: string, sessionId: string) => boolean
    markSessionRead: (agentId: string, sessionId: string) => void
    markSessionUnread: (agentId: string, sessionId: string) => void
    markAllRead: () => void
}

function getReadStorageKey(userId: string): string {
    return `opsfactory:${userId}:inbox:read-sessions:v1`
}

const InboxContext = createContext<InboxContextValue | null>(null)

function makeSessionKey(agentId: string, sessionId: string): string {
    return `${agentId}:${sessionId}`
}

function loadReadSet(storageKey: string): Set<string> {
    if (typeof window === 'undefined') return new Set()
    try {
        const raw = window.localStorage.getItem(storageKey)
        if (!raw) return new Set()
        const parsed = JSON.parse(raw) as string[]
        if (!Array.isArray(parsed)) return new Set()
        return new Set(parsed)
    } catch {
        return new Set()
    }
}

function saveReadSet(storageKey: string, readSet: Set<string>): void {
    if (typeof window === 'undefined') return
    try {
        window.localStorage.setItem(storageKey, JSON.stringify(Array.from(readSet)))
    } catch {
        // Ignore write failures (private mode / quota)
    }
}

export function InboxProvider({ children }: { children: ReactNode }) {
    const { userId } = useUser()
    const { agents, getClient, isConnected } = useGoosed()
    const [scheduledSessions, setScheduledSessions] = useState<InboxSession[]>([])
    const storageKey = getReadStorageKey(userId || 'anonymous')
    const [readSessionKeys, setReadSessionKeys] = useState<Set<string>>(() => loadReadSet(storageKey))
    const [isLoading, setIsLoading] = useState(false)

    // Reload read set when userId changes
    useEffect(() => {
        setReadSessionKeys(loadReadSet(storageKey))
    }, [storageKey])

    const refresh = useCallback(async () => {
        if (!isConnected || agents.length === 0) {
            setScheduledSessions([])
            return
        }

        setIsLoading(true)
        try {
            const results = await Promise.allSettled(
                agents.map(async (agent) => {
                    const client = getClient(agent.id)
                    const sessions = await client.listSessions()
                    return sessions
                        .filter(session => isScheduledSession(session))
                        .map(session => ({ ...session, agentId: agent.id }))
                })
            )

            const allScheduled: InboxSession[] = []
            for (const result of results) {
                if (result.status === 'fulfilled') {
                    allScheduled.push(...result.value)
                }
            }

            allScheduled.sort((a, b) =>
                new Date(b.updated_at || b.created_at).getTime() - new Date(a.updated_at || a.created_at).getTime()
            )
            setScheduledSessions(allScheduled)
        } finally {
            setIsLoading(false)
        }
    }, [agents, getClient, isConnected])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useEffect(() => {
        const interval = window.setInterval(() => {
            void refresh()
        }, 30000)
        return () => window.clearInterval(interval)
    }, [refresh])

    useEffect(() => {
        saveReadSet(storageKey, readSessionKeys)
    }, [storageKey, readSessionKeys])

    const isSessionRead = useCallback((agentId: string, sessionId: string) => {
        return readSessionKeys.has(makeSessionKey(agentId, sessionId))
    }, [readSessionKeys])

    const markSessionRead = useCallback((agentId: string, sessionId: string) => {
        const key = makeSessionKey(agentId, sessionId)
        setReadSessionKeys(prev => {
            if (prev.has(key)) return prev
            const next = new Set(prev)
            next.add(key)
            return next
        })
    }, [])

    const markSessionUnread = useCallback((agentId: string, sessionId: string) => {
        const key = makeSessionKey(agentId, sessionId)
        setReadSessionKeys(prev => {
            if (!prev.has(key)) return prev
            const next = new Set(prev)
            next.delete(key)
            return next
        })
    }, [])

    const markAllRead = useCallback(() => {
        setReadSessionKeys(prev => {
            const next = new Set(prev)
            for (const session of scheduledSessions) {
                next.add(makeSessionKey(session.agentId, session.id))
            }
            return next
        })
    }, [scheduledSessions])

    const unreadSessions = useMemo(
        () => scheduledSessions.filter(session => !readSessionKeys.has(makeSessionKey(session.agentId, session.id))),
        [scheduledSessions, readSessionKeys]
    )

    const value = useMemo<InboxContextValue>(() => ({
        unreadCount: unreadSessions.length,
        unreadSessions,
        scheduledSessions,
        isLoading,
        refresh,
        isSessionRead,
        markSessionRead,
        markSessionUnread,
        markAllRead,
    }), [unreadSessions, scheduledSessions, isLoading, refresh, isSessionRead, markSessionRead, markSessionUnread, markAllRead])

    return (
        <InboxContext.Provider value={value}>
            {children}
        </InboxContext.Provider>
    )
}

export function useInbox(): InboxContextValue {
    const ctx = useContext(InboxContext)
    if (!ctx) {
        throw new Error('useInbox must be used within an InboxProvider')
    }
    return ctx
}
