import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'

import { getUrlParam } from '../../../utils/urlParams'
import { updateLoggingContext } from '../logging/context'

const STORAGE_KEY = 'opsfactory:userId'

interface UserContextType {
    userId: string | null
    login: (username: string) => void
    logout: () => void
}

const UserContext = createContext<UserContextType | null>(null)

export function getCookie(name: string) {
    const cookies = document.cookie
    const cookieArray = cookies ? cookies.split('; ') : []

    for (const cookie of cookieArray) {
        const [cookieName,cookieValue] = cookie.split('=')
        if (cookieName === name && cookieValue) {
            return decodeURIComponent(cookieValue)
        }
    }
    return null
}

export function UserProvider({ children }: { children: ReactNode }) {
    const [userId, setUserId] = useState<string | null>(() => {
        const urlUserId = getUrlParam('uid') || getUrlParam('userId')
        const cookieUserId = getCookie('username')

        const resolvedUserId = urlUserId || cookieUserId

        if (resolvedUserId) {
            localStorage.setItem(STORAGE_KEY, resolvedUserId)
            return resolvedUserId
        }

        const storedUserId = localStorage.getItem(STORAGE_KEY)
        if (storedUserId) {
            return storedUserId
        }

        const fallbackUserId = 'admin'
        localStorage.setItem(STORAGE_KEY, fallbackUserId)
        return fallbackUserId
    })

    useEffect(() => {
        const urlUserId = getUrlParam('uid') || getUrlParam('userId')
        if (!urlUserId || urlUserId === userId) return

        if (urlUserId) {
            localStorage.setItem(STORAGE_KEY, urlUserId)
            setUserId(urlUserId)
        }
    }, [userId])

    useEffect(() => {
        if (userId) {
            updateLoggingContext({ userId })
        } else {
            updateLoggingContext({ userId: undefined })
        }
    }, [userId])

    const login = useCallback((username: string) => {
        const trimmed = username.trim()
        if (!trimmed) return
        localStorage.setItem(STORAGE_KEY, trimmed)
        setUserId(trimmed)
    }, [])

    const logout = useCallback(() => {
        localStorage.removeItem(STORAGE_KEY)
        setUserId(null)
    }, [])

    return (
        <UserContext.Provider value={{ userId, login, logout }}>
            {children}
        </UserContext.Provider>
    )
}

export function useUser(): UserContextType {
    const context = useContext(UserContext)
    if (!context) {
        throw new Error('useUser must be used within a UserProvider')
    }
    return context
}

/** User identity is resolved from URL, cookie, localStorage, or the default runtime user. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
    return <>{children}</>
}
