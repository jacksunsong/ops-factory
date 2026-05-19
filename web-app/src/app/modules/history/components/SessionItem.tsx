import type { MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { Session } from '@goosed/sdk'
import { FileArchive, Inbox, Loader2, Pencil, Trash2 } from 'lucide-react'
import { isScheduledSession } from '../../../../config/runtime'
import { ItemActionButton, ItemActionGroup } from '../../../platform/ui/primitives/ItemAction'
import ListCard from '../../../platform/ui/list/ListCard'

export type SessionWithAgent = Session & { agentId?: string }

interface SessionItemProps {
    session: SessionWithAgent
    agentName?: string
    onResume: (session: SessionWithAgent) => void
    onRename: (session: SessionWithAgent) => void
    onDelete: (session: SessionWithAgent) => void
    isDeleting?: boolean
    onTrace?: (session: SessionWithAgent) => void
    isTracing?: boolean
    onMarkUnread?: (session: SessionWithAgent) => void
}

function truncateSessionId(sessionId: string, edgeLength = 6): string {
    if (sessionId.length <= edgeLength * 2 + 3) return sessionId
    return `${sessionId.slice(0, edgeLength)}...${sessionId.slice(-edgeLength)}`
}

export default function SessionItem({
    session,
    agentName,
    onResume,
    onRename,
    onDelete,
    isDeleting = false,
    onTrace,
    isTracing = false,
    onMarkUnread,
}: SessionItemProps) {
    const { t, i18n } = useTranslation()
    const locale = i18n.language === 'en' ? 'en-US' : 'zh-CN'
    const formattedDate = new Date(session.created_at).toLocaleDateString(locale, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    })
    const sessionType = isScheduledSession(session) ? 'scheduled' : 'user'

    const handleDeleteClick = (e: MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        onDelete(session)
    }

    const handleRenameClick = (e: MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        onRename(session)
    }

    const handleTraceClick = (e: MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        onTrace?.(session)
    }

    const handleMarkUnreadClick = (e: MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        onMarkUnread?.(session)
    }

    return (
        <ListCard className="session-item animate-slide-in">
            <div
                className="session-info"
                onClick={() => onResume(session)}
                style={{ cursor: 'pointer', flex: 1 }}
            >
                <div className="session-name">{session.name || t('history.untitledSession')}</div>
                <div className="session-meta">
                    <div className="session-meta-tags">
                        <span className={`session-type-badge ${sessionType}`}>{sessionType.toUpperCase()}</span>
                        {session.agentId && (
                            <span
                                className="session-agent-tag"
                                title={agentName && agentName !== session.agentId ? `${agentName} (${session.agentId})` : session.agentId}
                            >
                                {agentName || session.agentId}
                            </span>
                        )}
                    </div>
                    <div className="session-meta-details">
                        {sessionType === 'scheduled' && session.schedule_id && (
                            <span className="session-schedule-id">{t('history.schedule', { id: session.schedule_id })}</span>
                        )}
                        <span>{formattedDate}</span>
                        {session.message_count !== undefined && (
                            <span>{session.message_count} {t('common.messages')}</span>
                        )}
                        {session.total_tokens !== undefined && session.total_tokens !== null && (
                            <span>{session.total_tokens.toLocaleString()} {t('common.tokens')}</span>
                        )}
                        <span className="session-meta-id" title={`${t('history.sessionIdLabel')}: ${session.id}`}>
                            · {truncateSessionId(session.id)}
                        </span>
                    </div>
                </div>
            </div>

            <ItemActionGroup className="session-actions">
                <ItemActionButton
                    icon={Pencil}
                    onClick={handleRenameClick}
                    label={t('history.renameSession')}
                />
                {onMarkUnread && sessionType === 'scheduled' && (
                    <ItemActionButton
                        icon={Inbox}
                        onClick={handleMarkUnreadClick}
                        label={t('history.moveToInbox')}
                        aria-label={t('history.markAsUnread')}
                        tone="primary"
                    />
                )}
                {onTrace && (
                    <ItemActionButton
                        icon={isTracing ? Loader2 : FileArchive}
                        onClick={handleTraceClick}
                        disabled={isTracing}
                        aria-busy={isTracing}
                        label={isTracing ? t('history.traceSessionRunning') : t('history.traceSession')}
                        iconClassName={isTracing ? 'item-action-spinner' : undefined}
                    />
                )}
                <ItemActionButton
                    icon={Trash2}
                    onClick={handleDeleteClick}
                    disabled={isDeleting}
                    aria-busy={isDeleting}
                    label={t('history.deleteSession')}
                    tone="danger"
                />
            </ItemActionGroup>
        </ListCard>
    )
}
