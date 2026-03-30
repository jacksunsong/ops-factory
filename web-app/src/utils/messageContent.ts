import type { ChatMessage, MessageContent } from '../types/message'

const THINK_BLOCK_REGEX = /<think>([\s\S]*?)<\/think>/gi
const UNCLOSED_THINK_REGEX = /<think>([\s\S]*)$/i

function getTextItems(message: ChatMessage): string[] {
    return message.content.flatMap(content => {
        if (content.type === 'text' && typeof content.text === 'string') {
            return [content.text]
        }
        return []
    })
}

export function getFullTextContent(message: ChatMessage): string {
    return getTextItems(message).join('\n')
}

export function hasTextContent(message: ChatMessage): boolean {
    return getTextItems(message).some(text => text.trim().length > 0)
}

export function hasToolContent(message: ChatMessage): boolean {
    return message.content.some(content => content.type === 'toolRequest' || content.type === 'toolResponse')
}

export function getDisplayTextContent(message: ChatMessage): string {
    const fullText = getFullTextContent(message)
    return fullText.replace(THINK_BLOCK_REGEX, '').trim()
}

export function hasDisplayTextContent(message: ChatMessage): boolean {
    return getDisplayTextContent(message).trim().length > 0
}

export function getReasoningContent(message: ChatMessage): string | null {
    const reasoning = message.content.flatMap(content => {
        if (content.type === 'reasoning' && typeof content.text === 'string' && content.text.length > 0) {
            return [content.text]
        }
        return []
    })

    return reasoning.length > 0 ? reasoning.join('') : null
}

export function getThinkingContent(message: ChatMessage): string | null {
    const structuredThinking = message.content.flatMap(content => {
        if (content.type === 'thinking' && typeof content.thinking === 'string' && content.thinking.length > 0) {
            return [content.thinking]
        }
        return []
    })

    if (structuredThinking.length > 0) {
        return structuredThinking.join('')
    }

    const fullText = getFullTextContent(message)
    const thinkingParts: string[] = []
    fullText.replace(THINK_BLOCK_REGEX, (_match, content) => {
        const trimmed = typeof content === 'string' ? content.trim() : ''
        if (trimmed.length > 0) {
            thinkingParts.push(trimmed)
        }
        return ''
    })

    if (thinkingParts.length > 0) {
        return thinkingParts.join('\n\n')
    }

    const unclosed = fullText.match(UNCLOSED_THINK_REGEX)
    return unclosed?.[1]?.trim() || null
}

export function getThinkingMessage(message: ChatMessage | undefined): string | undefined {
    if (!message || message.role !== 'assistant') {
        return undefined
    }

    for (const content of message.content) {
        if (
            content.type === 'systemNotification' &&
            content.notificationType === 'thinkingMessage' &&
            typeof content.msg === 'string'
        ) {
            return content.msg
        }
    }

    return undefined
}

export function getCompactingMessage(message: ChatMessage | undefined): string | undefined {
    if (!message || message.role !== 'assistant') {
        return undefined
    }

    for (const content of message.content) {
        if (
            content.type === 'systemNotification' &&
            (content.notificationType === 'compactingMessage' ||
                content.msg === 'goose is compacting the conversation...')
        ) {
            return typeof content.msg === 'string' ? content.msg : 'goose is compacting the conversation...'
        }
    }

    return undefined
}

export function hasRenderableAssistantContent(message: ChatMessage): boolean {
    if (message.role !== 'assistant') {
        return true
    }

    return hasTextContent(message) ||
        hasToolContent(message) ||
        message.content.some((content: MessageContent) => content.type === 'reasoning' || content.type === 'thinking')
}

export function hasProcessContent(message: ChatMessage): boolean {
    if (message.role !== 'assistant') {
        return false
    }

    return hasToolContent(message) || !!getReasoningContent(message) || !!getThinkingContent(message)
}
