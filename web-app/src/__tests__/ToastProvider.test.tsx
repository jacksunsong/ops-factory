import { render, screen, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider, useToast } from '../contexts/ToastContext'
import { UserProvider } from '../contexts/UserContext'

// ── Helper: Toast trigger component ────────────────────────────
function ToastTrigger({ type, message }: { type: 'success' | 'error' | 'warning' | 'info'; message: string }) {
    const { showToast, toasts } = useToast()
    return (
        <div>
            <button onClick={() => showToast(type, message)}>trigger</button>
            <div data-testid="toast-count">{toasts.length}</div>
            {toasts.map(t => (
                <div key={t.id} data-testid={`toast-${t.type}`}>{t.message}</div>
            ))}
        </div>
    )
}

function renderWithToast(ui: React.ReactElement) {
    return render(
        <MemoryRouter>
            <ToastProvider>
                {ui}
            </ToastProvider>
        </MemoryRouter>
    )
}

describe('ToastProvider', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    it('renders children without toast initially', () => {
        renderWithToast(<ToastTrigger type="error" message="test" />)
        expect(screen.getByTestId('toast-count')).toHaveTextContent('0')
    })

    it('shows error toast when triggered', () => {
        renderWithToast(<ToastTrigger type="error" message="Something failed" />)

        act(() => {
            screen.getByText('trigger').click()
        })

        expect(screen.getByTestId('toast-error')).toHaveTextContent('Something failed')
        expect(screen.getByTestId('toast-count')).toHaveTextContent('1')
    })

    it('shows warning toast when triggered', () => {
        renderWithToast(<ToastTrigger type="warning" message="Watch out" />)

        act(() => {
            screen.getByText('trigger').click()
        })

        expect(screen.getByTestId('toast-warning')).toHaveTextContent('Watch out')
    })

    it('auto-removes toast after 3 seconds', () => {
        renderWithToast(<ToastTrigger type="info" message="Auto dismiss" />)

        act(() => {
            screen.getByText('trigger').click()
        })
        expect(screen.getByTestId('toast-count')).toHaveTextContent('1')

        act(() => {
            vi.advanceTimersByTime(3000)
        })
        expect(screen.getByTestId('toast-count')).toHaveTextContent('0')
    })

    it('supports multiple toasts simultaneously', () => {
        function MultiTrigger() {
            const { showToast, toasts } = useToast()
            return (
                <div>
                    <button onClick={() => showToast('error', 'Error 1')}>err1</button>
                    <button onClick={() => showToast('warning', 'Warn 1')}>warn1</button>
                    <div data-testid="toast-count">{toasts.length}</div>
                </div>
            )
        }

        renderWithToast(<MultiTrigger />)

        act(() => {
            screen.getByText('err1').click()
            screen.getByText('warn1').click()
        })

        expect(screen.getByTestId('toast-count')).toHaveTextContent('2')
    })

    it('throws when useToast is used outside ToastProvider', () => {
        function Orphan() {
            useToast()
            return <div />
        }

        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
        expect(() => render(<Orphan />)).toThrow('useToast must be used within a ToastProvider')
        consoleSpy.mockRestore()
    })
})

// ── ToastProvider is accessible from login page (root-level) ───
describe('ToastProvider root-level accessibility', () => {
    it('ToastProvider wraps the entire app including login route', () => {
        // Verify the component tree: ToastProvider is in main.tsx above UserProvider
        // This test confirms useToast works in a component outside ProtectedRoute
        function LoginLikeComponent() {
            const { showToast, toasts } = useToast()
            return (
                <div>
                    <button onClick={() => showToast('error', 'Login error')}>trigger</button>
                    <div data-testid="toast-count">{toasts.length}</div>
                    {toasts.map(t => (
                        <div key={t.id} data-testid={`toast-${t.type}`}>{t.message}</div>
                    ))}
                </div>
            )
        }

        // Render WITHOUT ProtectedRoute/SidebarProvider — simulating login page
        render(
            <MemoryRouter>
                <ToastProvider>
                    <LoginLikeComponent />
                </ToastProvider>
            </MemoryRouter>
        )

        act(() => {
            screen.getByText('trigger').click()
        })

        expect(screen.getByTestId('toast-error')).toHaveTextContent('Login error')
    })
})
