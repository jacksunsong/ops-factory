import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import App from '../App'
import { BrowserRouter } from 'react-router-dom'
import { UserProvider } from '../app/platform/providers/UserContext'
import { GoosedProvider } from '../app/platform/providers/GoosedContext'
import { ToastProvider } from '../app/platform/providers/ToastContext'

const STORAGE_KEY = 'opsfactory:userId'

describe('App', () => {
    beforeEach(() => {
        // Set a userId so ProtectedRoute allows access
        localStorage.setItem(STORAGE_KEY, 'test-user')
    })

    afterEach(() => {
        localStorage.clear()
    })

    it('renders without crashing', () => {
        render(
            <BrowserRouter>
                <ToastProvider>
                    <UserProvider>
                        <GoosedProvider>
                            <App />
                        </GoosedProvider>
                    </UserProvider>
                </ToastProvider>
            </BrowserRouter>
        )
        // Sidebar should be present for authenticated user. The test should
        // not depend on the default runtime language.
        expect(screen.getByText(/Home|首页/)).toBeInTheDocument()
        expect(screen.getByText(/History|历史记录/)).toBeInTheDocument()
        expect(screen.getByText(/Inbox|收件箱/)).toBeInTheDocument()
    })
})
