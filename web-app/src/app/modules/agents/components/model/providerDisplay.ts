export function formatProviderEngine(engine?: string): string {
    return engine === 'openai' || !engine ? 'openai-compatible' : engine
}
