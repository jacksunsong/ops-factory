import { useRef, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Message, { ChatMessage, type DetectedFile } from './Message'
import type { ToolResponseMap } from './Message'
import { ChatState } from '../hooks/useChat'
import { extractSourceDocuments, type Citation } from '../utils/citationParser'
import { useUser } from '../contexts/UserContext'
import { GATEWAY_URL, GATEWAY_SECRET_KEY } from '../config/runtime'

interface MessageListProps {
    messages: ChatMessage[]
    isLoading?: boolean
    chatState?: ChatState
    agentId?: string
    onRetry?: () => void
}

interface ListedFile {
    name: string
    path: string
    size: number
    modifiedAt: string
    type: string
}

export default function MessageList({ messages, isLoading = false, chatState = ChatState.Idle, agentId, onRetry }: MessageListProps) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const containerRef = useRef<HTMLDivElement>(null)
    const bottomRef = useRef<HTMLDivElement>(null)
    const baselineFilesRef = useRef<Map<string, ListedFile>>(new Map())
    const processedAssistantMsgRef = useRef<string | null>(null)
    const [messageOutputFiles, setMessageOutputFiles] = useState<Map<string, DetectedFile[]>>(new Map())

    // Auto-scroll to bottom when new messages arrive
    useEffect(() => {
        if (bottomRef.current) {
            bottomRef.current.scrollIntoView({ behavior: 'smooth' })
        }
    }, [messages])

    const visibleMessages = useMemo(
        () => messages.filter(msg => !msg.metadata || msg.metadata.userVisible !== false),
        [messages]
    )

    const finalAssistantTextMessageId = useMemo(() => {
        for (let i = visibleMessages.length - 1; i >= 0; i--) {
            const msg = visibleMessages[i]
            if (msg.role === 'assistant' && msg.content.some(c => c.type === 'text' && typeof c.text === 'string' && c.text.trim().length > 0)) {
                return msg.id
            }
        }
        return undefined
    }, [visibleMessages])

    const toolResponses = useMemo<ToolResponseMap>(() => {
        const map: ToolResponseMap = new Map()
        for (const msg of visibleMessages) {
            for (const content of msg.content) {
                if (content.type === 'toolResponse' && content.id) {
                    const toolResult = content.toolResult
                    map.set(content.id, {
                        result: toolResult?.status === 'success' ? toolResult.value : toolResult,
                        isError: toolResult?.status === 'error'
                    })
                }
            }
        }
        return map
    }, [visibleMessages])

    // Extract source documents from tool call results for fallback references
    const sourceDocuments = useMemo<Citation[]>(() => {
        return extractSourceDocuments(visibleMessages)
    }, [visibleMessages])

    const fetchAgentFiles = async (targetAgentId: string): Promise<ListedFile[]> => {
        const headers: Record<string, string> = { 'x-secret-key': GATEWAY_SECRET_KEY }
        if (userId) headers['x-user-id'] = userId
        const res = await fetch(`${GATEWAY_URL}/agents/${targetAgentId}/files`, {
            headers,
        })
        if (!res.ok) return []
        const data = await res.json() as { files?: ListedFile[] }
        return Array.isArray(data.files) ? data.files : []
    }

    // Reset file tracking when agent changes.
    useEffect(() => {
        baselineFilesRef.current = new Map()
        processedAssistantMsgRef.current = null
        setMessageOutputFiles(new Map())

        if (!agentId) return
        let cancelled = false

        const initBaseline = async () => {
            const files = await fetchAgentFiles(agentId)
            if (cancelled) return
            baselineFilesRef.current = new Map(files.map(f => [f.path, f]))
        }

        initBaseline().catch(() => { /* best-effort baseline */ })
        return () => { cancelled = true }
    }, [agentId])

    // Stable file capsule source: detect newly created/updated files after each completed assistant turn.
    useEffect(() => {
        if (!agentId || isLoading) return
        const lastAssistant = [...visibleMessages].reverse().find(msg => msg.role === 'assistant' && msg.id)
        const assistantId = finalAssistantTextMessageId || lastAssistant?.id
        if (!assistantId) return
        if (processedAssistantMsgRef.current === assistantId) return

        let cancelled = false
        const updateTurnFiles = async () => {
            const currentFiles = await fetchAgentFiles(agentId)
            if (cancelled) return

            const currentMap = new Map(currentFiles.map(f => [f.path, f]))
            const baselineMap = baselineFilesRef.current
            const changed: DetectedFile[] = []

            for (const file of currentFiles) {
                const previous = baselineMap.get(file.path)
                const isNew = !previous
                const isUpdated = !!previous && (previous.modifiedAt !== file.modifiedAt || previous.size !== file.size)
                if (!isNew && !isUpdated) continue
                const ext = file.type?.toLowerCase() || (file.name.split('.').pop()?.toLowerCase() || '')
                changed.push({
                    path: file.path,
                    name: file.name,
                    ext,
                })
            }

            if (changed.length > 0) {
                setMessageOutputFiles(prev => {
                    const next = new Map(prev)
                    next.set(assistantId, changed)
                    return next
                })
            }

            baselineFilesRef.current = currentMap
            processedAssistantMsgRef.current = assistantId
        }

        updateTurnFiles().catch(() => { /* best-effort file detection */ })
        return () => { cancelled = true }
    }, [agentId, isLoading, visibleMessages, finalAssistantTextMessageId])

    if (visibleMessages.length === 0 && !isLoading) {
        return (
            <div className="empty-state">
                <svg
                    className="empty-state-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                >
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                <h3 className="empty-state-title">{t('chat.noMessages')}</h3>
                <p className="empty-state-description">
                    {t('chat.startConversation')}
                </p>
            </div>
        )
    }

    return (
        <div className="chat-messages" ref={containerRef}>
            {visibleMessages.map((message, index) => {
                const isLastAssistant =
                    isLoading &&
                    message.role === 'assistant' &&
                    index === visibleMessages.length - 1
                const isFinalAssistantResponse =
                    message.role === 'assistant' &&
                    !!message.id &&
                    message.id === finalAssistantTextMessageId
                return (
                    <Message
                        key={message.id || index}
                        message={message}
                        toolResponses={toolResponses}
                        agentId={agentId}
                        userId={userId}
                        isStreaming={isLastAssistant}
                        onRetry={message.role === 'assistant' && index === visibleMessages.length - 1 ? onRetry : undefined}
                        sourceDocuments={isFinalAssistantResponse ? sourceDocuments : undefined}
                        outputFiles={isFinalAssistantResponse && message.id ? messageOutputFiles.get(message.id) : undefined}
                        showFileCapsules={isFinalAssistantResponse}
                    />
                )
            })}

            {isLoading && visibleMessages[visibleMessages.length - 1]?.role !== 'assistant' && (
                <div className="message assistant animate-fade-in">
                    <div className="message-avatar">G</div>
                    <div className="message-content">
                        <div className="loading-dots">
                            <span></span>
                            <span></span>
                            <span></span>
                        </div>
                        {chatState === ChatState.Thinking && (
                            <div className="loading-status-text">{t('chat.thinking')}</div>
                        )}
                        {chatState === ChatState.Compacting && (
                            <div className="loading-status-text">{t('chat.compactingContext')}</div>
                        )}
                    </div>
                </div>
            )}

            <div ref={bottomRef} />
        </div>
    )
}
