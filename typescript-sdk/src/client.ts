/**
 * goosed-sdk client
 */

import type {
    Session,
    CleanupEmptySessionResult,
    ToolInfo,
    CallToolResponse,
    SSEEvent,
    SSEEventType,
    Message,
    SessionReplyRequest,
    SessionReplyResponse,
    SessionCancelRequest,
    SessionEventsOptions,
    SessionSSEEvent,
    SessionErrorContext,
    SessionErrorEnvelope,
    SessionErrorLayer,
    SessionErrorSeverity,
    SessionSuggestedAction,
    SystemInfo,
    ExtensionResult,
    GoosedClientOptions,
    ImageData,
    UploadResult,
    Recipe,
    RecipeManifest,
    ScheduledJob,
    ListSchedulesResponse,
    RunNowResponse,
    ScheduleSessionInfo,
    PromptTemplate,
    PromptListResponse,
    PromptContentResponse,
    OutputFile,
} from './types.js';

export class GoosedException extends Error {
    statusCode?: number;
    sessionError?: SessionErrorEnvelope;

    constructor(message: string, statusCode?: number, sessionError?: SessionErrorEnvelope) {
        super(message);
        this.name = 'GoosedException';
        this.statusCode = statusCode;
        this.sessionError = sessionError;
    }
}

export class GoosedAuthError extends GoosedException {
    constructor(message = 'Authentication failed', sessionError?: SessionErrorEnvelope) {
        super(message, 401, sessionError);
        this.name = 'GoosedAuthError';
    }
}

export class GoosedNotFoundError extends GoosedException {
    constructor(message = 'Resource not found', sessionError?: SessionErrorEnvelope) {
        super(message, 404, sessionError);
        this.name = 'GoosedNotFoundError';
    }
}

export class GoosedAgentNotInitializedError extends GoosedException {
    constructor(message = 'Agent not initialized', sessionError?: SessionErrorEnvelope) {
        super(message, 424, sessionError);
        this.name = 'GoosedAgentNotInitializedError';
    }
}

export class GoosedServerError extends GoosedException {
    constructor(message = 'Server error', statusCode = 500, sessionError?: SessionErrorEnvelope) {
        super(message, statusCode, sessionError);
        this.name = 'GoosedServerError';
    }
}

export class GoosedConnectionError extends GoosedException {
    constructor(message = 'Connection error', sessionError?: SessionErrorEnvelope) {
        super(message, undefined, sessionError);
        this.name = 'GoosedConnectionError';
    }
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseJsonMaybe(value: string): unknown {
    try {
        return JSON.parse(value);
    } catch {
        return value;
    }
}

function isSessionErrorEnvelope(value: unknown): value is SessionErrorEnvelope {
    return isRecord(value) &&
        value.type === 'Error' &&
        typeof value.layer === 'string' &&
        typeof value.code === 'string' &&
        typeof value.message === 'string';
}

function statusToLayer(status?: number): SessionErrorLayer {
    if (status === 401 || status === 403 || status === 404 || status === 424) return 'gateway';
    if (status === 429) return 'policy';
    if (status !== undefined && status >= 500) return 'gateway';
    return 'goosed';
}

function statusToCode(status?: number): string {
    switch (status) {
        case 400:
            return 'goosed_request_rejected';
        case 401:
        case 403:
            return 'gateway_unauthorized';
        case 404:
            return 'gateway_agent_not_found';
        case 409:
            return 'goosed_active_request_conflict';
        case 424:
            return 'gateway_agent_unavailable';
        case 429:
            return 'gateway_rate_limited';
        case 502:
        case 503:
            return 'gateway_goosed_unavailable';
        case 504:
            return 'gateway_submit_timeout';
        default:
            return status !== undefined && status >= 500 ? 'gateway_internal_error' : 'goosed_error';
    }
}

function codeToActions(code: string): SessionSuggestedAction[] {
    switch (code) {
        case 'frontend_events_disconnected':
            return ['reconnect', 'wait'];
        case 'user_cancelled':
        case 'goosed_request_cancelled':
            return ['new_request'];
        case 'gateway_unauthorized':
            return ['login', 'contact_support'];
        case 'gateway_agent_not_found':
        case 'provider_auth_or_quota_failed':
        case 'gateway_internal_error':
            return ['contact_support'];
        case 'goosed_active_request_conflict':
            return ['wait', 'cancel', 'retry'];
        case 'gateway_max_duration_reached':
        case 'context_too_large':
            return ['reduce_context', 'new_request'];
        case 'provider_timeout':
            return ['retry', 'reduce_context'];
        case 'provider_rate_limited':
            return ['wait', 'retry'];
        case 'tool_execution_failed':
            return ['retry', 'check_tool'];
        case 'mcp_unavailable':
            return ['retry', 'check_tool', 'contact_support'];
        default:
            return ['retry'];
    }
}

function codeIsRetryable(code: string): boolean {
    return codeToActions(code).includes('retry') || codeToActions(code).includes('reconnect');
}

export function normalizeSessionError(error: unknown, context: SessionErrorContext = {}): SessionErrorEnvelope {
    if (error instanceof GoosedException && error.sessionError) {
        return {
            ...error.sessionError,
            session_id: context.sessionId ?? error.sessionError.session_id,
            request_id: context.requestId ?? error.sessionError.request_id,
            agent_id: context.agentId ?? error.sessionError.agent_id,
            trace_id: context.traceId ?? error.sessionError.trace_id,
        };
    }

    const raw = typeof error === 'string' ? parseJsonMaybe(error) : error;
    if (isSessionErrorEnvelope(raw)) {
        return {
            ...raw,
            severity: raw.severity ?? 'error',
            retryable: raw.retryable ?? codeIsRetryable(raw.code),
            suggested_actions: raw.suggested_actions ?? codeToActions(raw.code),
            session_id: context.sessionId ?? raw.session_id,
            request_id: context.requestId ?? raw.request_id,
            agent_id: context.agentId ?? raw.agent_id,
            trace_id: context.traceId ?? raw.trace_id,
            raw,
        };
    }

    const record = isRecord(raw) ? raw : undefined;
    const status = context.httpStatus ??
        (error instanceof GoosedException ? error.statusCode : undefined) ??
        (typeof record?.http_status === 'number' ? record.http_status : undefined);
    const inferredCode = context.code ?? (
        typeof record?.code === 'string' ? record.code : statusToCode(status)
    );
    const message = context.message ??
        (typeof record?.message === 'string' ? record.message : undefined) ??
        (typeof record?.error === 'string' ? record.error : undefined) ??
        (error instanceof Error ? error.message : undefined) ??
        (typeof raw === 'string' ? raw : undefined) ??
        'Agent request failed';
    const detail = context.detail ??
        (typeof record?.detail === 'string' ? record.detail : undefined) ??
        (typeof record?.error === 'string' ? record.error : undefined);
    const layer = context.layer ??
        (typeof record?.layer === 'string' ? record.layer as SessionErrorLayer : statusToLayer(status));
    const severity = context.severity ??
        (typeof record?.severity === 'string' ? record.severity as SessionErrorSeverity : 'error');
    const suggestedActions = context.suggestedActions ??
        (Array.isArray(record?.suggested_actions)
            ? record.suggested_actions.filter((action): action is SessionSuggestedAction => typeof action === 'string')
            : codeToActions(inferredCode));

    return {
        type: 'Error',
        layer,
        code: inferredCode,
        severity,
        message,
        detail,
        retryable: context.retryable ?? (typeof record?.retryable === 'boolean' ? record.retryable : codeIsRetryable(inferredCode)),
        suggested_actions: suggestedActions,
        session_id: context.sessionId ?? (typeof record?.session_id === 'string' ? record.session_id : undefined),
        request_id: context.requestId ?? (typeof record?.request_id === 'string' ? record.request_id : undefined),
        agent_id: context.agentId ?? (typeof record?.agent_id === 'string' ? record.agent_id : undefined),
        elapsed_ms: typeof record?.elapsed_ms === 'number' ? record.elapsed_ms : undefined,
        http_status: status,
        upstream_status: context.upstreamStatus ?? (typeof record?.upstream_status === 'number' ? record.upstream_status : undefined),
        trace_id: context.traceId ?? (typeof record?.trace_id === 'string' ? record.trace_id : undefined),
        raw,
    };
}

export class GoosedClient {
    private baseUrl: string;
    private secretKey: string;
    private timeout: number;
    private userId?: string;

    constructor(options: GoosedClientOptions = {}) {
        const env = typeof process !== 'undefined' ? process.env : {} as Record<string, string | undefined>;
        const defaultBaseUrl = env.GOOSED_BASE_URL || 'https://127.0.0.1:3000/ops-gateway';
        const defaultSecretKey = env.GOOSED_SECRET_KEY || 'test';
        
        this.baseUrl = (options.baseUrl ?? defaultBaseUrl).replace(/\/$/, '');
        this.secretKey = options.secretKey ?? defaultSecretKey;
        this.timeout = options.timeout ?? 30000;
        this.userId = options.userId;
    }

    private headers(): Record<string, string> {
        const h: Record<string, string> = {
            'Content-Type': 'application/json',
            'x-secret-key': this.secretKey,
        };
        if (this.userId) {
            h['x-user-id'] = this.userId;
        }
        return h;
    }

    private async handleResponse<T>(response: Response): Promise<T> {
        if (response.ok) {
            const contentType = response.headers.get('content-type') ?? '';
            if (contentType.includes('application/json')) {
                const text = await response.text();
                if (text === '') {
                    return undefined as T;
                }
                return JSON.parse(text) as T;
            }
            const text = await response.text();
            if (text === '') {
                return undefined as T;
            }
            return text as unknown as T;
        }

        const text = await response.text();
        const parsed = text ? parseJsonMaybe(text) : undefined;
        const sessionError = normalizeSessionError(parsed ?? text, { httpStatus: response.status });
        switch (response.status) {
            case 401:
                throw new GoosedAuthError(sessionError.message, sessionError);
            case 404:
                throw new GoosedNotFoundError(sessionError.message, sessionError);
            case 424:
                throw new GoosedAgentNotInitializedError(sessionError.message, sessionError);
            default:
                if (response.status >= 500) {
                    throw new GoosedServerError(sessionError.message, response.status, sessionError);
                }
                throw new GoosedException(sessionError.message, response.status, sessionError);
        }
    }

    private async get<T>(path: string, params?: Record<string, string>): Promise<T> {
        let fullUrl = this.baseUrl+ path;
        if (params) {
           const paramStrings= Object.entries(params).map(([key, value]) => {
               return encodeURIComponent(key) + '=' + encodeURIComponent(value);
            });
            const paramString = paramStrings.join('&');
            if (paramString) {
                fullUrl += (fullUrl.includes('?') ? '&' : '?') + paramString;
            }
        }

        try {
            const response = await fetch(fullUrl, {
                method: 'GET',
                headers: this.headers(),
                signal: AbortSignal.timeout(this.timeout),
            });
            return this.handleResponse<T>(response);
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message, normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_goosed_unavailable',
                    retryable: true,
                    suggestedActions: ['retry', 'contact_support'],
                }));
            }
            if (error instanceof DOMException && error.name === 'AbortError') {
                throw new GoosedConnectionError('Request timed out', normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_submit_timeout',
                    message: 'Request timed out',
                    retryable: true,
                    suggestedActions: ['retry'],
                }));
            }
            throw error;
        }
    }

    private async post<T>(path: string, body?: Record<string, unknown>): Promise<T> {
        try {
            const response = await fetch(`${this.baseUrl}${path}`, {
                method: 'POST',
                headers: this.headers(),
                body: body ? JSON.stringify(body) : undefined,
                signal: AbortSignal.timeout(this.timeout),
            });
            return this.handleResponse<T>(response);
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message, normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_goosed_unavailable',
                    retryable: true,
                    suggestedActions: ['retry', 'contact_support'],
                }));
            }
            if (error instanceof DOMException && error.name === 'AbortError') {
                throw new GoosedConnectionError('Request timed out', normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_submit_timeout',
                    message: 'Request timed out',
                    retryable: true,
                    suggestedActions: ['retry'],
                }));
            }
            throw error;
        }
    }

    private async put<T>(path: string, body?: Record<string, unknown>): Promise<T> {
        try {
            const response = await fetch(`${this.baseUrl}${path}`, {
                method: 'PUT',
                headers: this.headers(),
                body: body ? JSON.stringify(body) : undefined,
                signal: AbortSignal.timeout(this.timeout),
            });
            return this.handleResponse<T>(response);
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message, normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_goosed_unavailable',
                    retryable: true,
                    suggestedActions: ['retry', 'contact_support'],
                }));
            }
            if (error instanceof DOMException && error.name === 'AbortError') {
                throw new GoosedConnectionError('Request timed out', normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_submit_timeout',
                    message: 'Request timed out',
                    retryable: true,
                    suggestedActions: ['retry'],
                }));
            }
            throw error;
        }
    }

    private async delete<T>(path: string): Promise<T> {
        try {
            const response = await fetch(`${this.baseUrl}${path}`, {
                method: 'DELETE',
                headers: this.headers(),
                signal: AbortSignal.timeout(this.timeout),
            });
            return this.handleResponse<T>(response);
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message, normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_goosed_unavailable',
                    retryable: true,
                    suggestedActions: ['retry', 'contact_support'],
                }));
            }
            if (error instanceof DOMException && error.name === 'AbortError') {
                throw new GoosedConnectionError('Request timed out', normalizeSessionError(error, {
                    layer: 'gateway',
                    code: 'gateway_submit_timeout',
                    message: 'Request timed out',
                    retryable: true,
                    suggestedActions: ['retry'],
                }));
            }
            throw error;
        }
    }

    // === Status APIs ===

    async status(): Promise<string> {
        return this.get<string>('/status');
    }

    async systemInfo(): Promise<SystemInfo> {
        return this.get<SystemInfo>('/system_info');
    }

    // === Agent APIs ===

    async startSession(workingDir?: string): Promise<Session> {
        const body: Record<string, unknown> = {};
        if (workingDir) body.working_dir = workingDir;
        return this.post<Session>('/agent/start', body);
    }

    async resumeSession(
        sessionId: string,
        loadModelAndExtensions = true
    ): Promise<{ session: Session; extensionResults: ExtensionResult[] }> {
        const data = await this.post<{ session: Session; extension_results: ExtensionResult[] }>(
            '/agent/resume',
            { session_id: sessionId, load_model_and_extensions: loadModelAndExtensions }
        );
        return {
            session: data.session,
            extensionResults: data.extension_results ?? [],
        };
    }

    async restartSession(sessionId: string): Promise<ExtensionResult[]> {
        const data = await this.post<{ extension_results: ExtensionResult[] }>(
            '/agent/restart',
            { session_id: sessionId }
        );
        return data.extension_results ?? [];
    }

    async getTools(sessionId: string, extensionName?: string): Promise<ToolInfo[]> {
        const params: Record<string, string> = { session_id: sessionId };
        if (extensionName) {
            params.extension_name = extensionName;
        }
        return this.get<ToolInfo[]>('/agent/tools', params);
    }

    async callTool(
        sessionId: string,
        name: string,
        args: Record<string, unknown>
    ): Promise<CallToolResponse> {
        return this.post<CallToolResponse>('/agent/call_tool', {
            session_id: sessionId,
            name,
            arguments: args,
        });
    }

    // === Chat APIs ===

    private async *parseSseResponse(response: Response): AsyncGenerator<SessionSSEEvent> {
        const reader = response.body?.getReader();
        if (!reader) {
            throw new GoosedException('No response body');
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let dataLines: string[] = [];
        let eventId: string | undefined;

        const flushEvent = function *(): Generator<SessionSSEEvent> {
            if (dataLines.length === 0) return;
            const event = JSON.parse(dataLines.join('\n')) as SSEEvent;
            yield { event, eventId };
            dataLines = [];
            eventId = undefined;
        };

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';

            for (const line of lines) {
                const trimmed = line.replace(/\r$/, '');
                if (trimmed === '') {
                    yield* flushEvent();
                    continue;
                }
                if (trimmed.startsWith(':')) {
                    continue;
                }
                if (trimmed.startsWith('id:')) {
                    eventId = trimmed.slice(3).trimStart();
                    continue;
                }
                if (trimmed.startsWith('data:')) {
                    dataLines.push(trimmed.slice(5).trimStart());
                }
            }
        }

        if (buffer.trim() !== '') {
            const trimmed = buffer.replace(/\r$/, '');
            if (trimmed.startsWith('id:')) {
                eventId = trimmed.slice(3).trimStart();
            } else if (trimmed.startsWith('data:')) {
                dataLines.push(trimmed.slice(5).trimStart());
            }
        }

        yield* flushEvent();
    }

    async submitSessionReply(sessionId: string, request: SessionReplyRequest): Promise<SessionReplyResponse> {
        return this.post<SessionReplyResponse>(`/sessions/${encodeURIComponent(sessionId)}/reply`, request as unknown as Record<string, unknown>);
    }

    async cancelSessionReply(sessionId: string, requestId: string): Promise<void> {
        const request: SessionCancelRequest = { request_id: requestId };
        await this.post(`/sessions/${encodeURIComponent(sessionId)}/cancel`, request as unknown as Record<string, unknown>);
    }

    async *subscribeSessionEvents(
        sessionId: string,
        options: SessionEventsOptions = {}
    ): AsyncGenerator<SessionSSEEvent> {
        let response: Response;
        try {
            const headers = this.headers();
            if (options.lastEventId) {
                headers['Last-Event-ID'] = options.lastEventId;
            }
            response = await fetch(`${this.baseUrl}/sessions/${encodeURIComponent(sessionId)}/events`, {
                method: 'GET',
                headers,
                signal: options.signal,
            });
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message, normalizeSessionError(error, {
                    layer: 'frontend',
                    code: 'frontend_events_disconnected',
                    retryable: true,
                    suggestedActions: ['reconnect', 'wait'],
                    sessionId,
                }));
            }
            throw error;
        }

        if (!response.ok) {
            await this.handleResponse(response);
        }

        for await (const item of this.parseSseResponse(response)) {
            yield item;
        }
    }

    // === File Upload APIs ===

    async uploadFile(file: File, sessionId: string): Promise<UploadResult> {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('sessionId', sessionId);

        // Don't set Content-Type header — browser sets it automatically with boundary
        const headers: Record<string, string> = {
            'x-secret-key': this.secretKey,
        };
        if (this.userId) {
            headers['x-user-id'] = this.userId;
        }

        try {
            const response = await fetch(`${this.baseUrl}/files/upload`, {
                method: 'POST',
                headers,
                body: formData,
                signal: AbortSignal.timeout(this.timeout),
            });
            return this.handleResponse<UploadResult>(response);
        } catch (error) {
            if (error instanceof TypeError) {
                throw new GoosedConnectionError(error.message);
            }
            if (error instanceof DOMException && error.name === 'AbortError') {
                throw new GoosedConnectionError('Request timed out');
            }
            throw error;
        }
    }

    // === Session APIs ===

    async listSessions(): Promise<Session[]> {
        const data = await this.get<{ sessions: Session[] }>('/sessions');
        return data.sessions ?? [];
    }

    async getSession(sessionId: string): Promise<Session> {
        return this.get<Session>(`/sessions/${sessionId}`);
    }

    async updateSessionName(sessionId: string, name: string): Promise<void> {
        await this.put(`/sessions/${sessionId}/name`, { name });
    }

    async deleteSession(sessionId: string): Promise<void> {
        await this.delete(`/sessions/${sessionId}`);
    }

    async cleanupEmptySession(sessionId: string): Promise<CleanupEmptySessionResult> {
        return this.post<CleanupEmptySessionResult>(`/sessions/${sessionId}/cleanup-empty`);
    }

    async exportSession(sessionId: string): Promise<string> {
        return this.get<string>(`/sessions/${sessionId}/export`);
    }

    // === Recipe APIs ===

    async saveRecipe(recipe: Recipe, id?: string): Promise<{ id: string }> {
        const body: Record<string, unknown> = { recipe };
        if (id) {
            body.id = id;
        }
        return this.post<{ id: string }>('/recipes/save', body);
    }

    async listRecipes(): Promise<RecipeManifest[]> {
        const data = await this.get<{ manifests: RecipeManifest[] }>('/recipes/list');
        return data.manifests ?? [];
    }

    // === Schedule APIs ===

    async createSchedule(request: { id: string; recipe: Recipe; cron: string }): Promise<ScheduledJob> {
        return this.post<ScheduledJob>('/schedule/create', request);
    }

    async listSchedules(): Promise<ScheduledJob[]> {
        const data = await this.get<ListSchedulesResponse>('/schedule/list');
        return data.jobs ?? [];
    }

    async updateSchedule(id: string, cron: string): Promise<ScheduledJob> {
        return this.put<ScheduledJob>(`/schedule/${id}`, { cron });
    }

    async deleteSchedule(id: string): Promise<void> {
        await this.delete(`/schedule/delete/${id}`);
    }

    async runScheduleNow(id: string): Promise<string> {
        const data = await this.post<RunNowResponse>(`/schedule/${id}/run_now`);
        return data.session_id;
    }

    async pauseSchedule(id: string): Promise<void> {
        await this.post(`/schedule/${id}/pause`);
    }

    async unpauseSchedule(id: string): Promise<void> {
        await this.post(`/schedule/${id}/unpause`);
    }

    async listScheduleSessions(id: string, limit = 20): Promise<ScheduleSessionInfo[]> {
        return this.get<ScheduleSessionInfo[]>(`/schedule/${id}/sessions`, { limit: String(limit) });
    }

    async killSchedule(id: string): Promise<{ message: string }> {
        return this.post<{ message: string }>(`/schedule/${id}/kill`);
    }

    async inspectSchedule(id: string): Promise<{
        sessionId?: string | null;
        processStartTime?: string | null;
        runningDurationSeconds?: number | null;
    }> {
        return this.get<{
            sessionId?: string | null;
            processStartTime?: string | null;
            runningDurationSeconds?: number | null;
        }>(`/schedule/${id}/inspect`);
    }

    // === Prompt APIs ===

    async listPrompts(): Promise<PromptTemplate[]> {
        const data = await this.get<PromptListResponse>('/config/prompts');
        return data.prompts ?? [];
    }

    async getPrompt(name: string): Promise<PromptContentResponse> {
        return this.get<PromptContentResponse>(`/config/prompts/${encodeURIComponent(name)}`);
    }

    async savePrompt(name: string, content: string): Promise<void> {
        await this.put(`/config/prompts/${encodeURIComponent(name)}`, { content });
    }

    async resetPrompt(name: string): Promise<void> {
        await this.delete(`/config/prompts/${encodeURIComponent(name)}`);
    }

}

// Export types that are used by the webapp
export type { SSEEvent, SSEEventType, OutputFile, SessionSSEEvent };
export type {
    Session,
    Message,
    ToolInfo,
    CallToolResponse,
    SystemInfo,
    ExtensionResult,
    GoosedClientOptions,
    ImageData,
    SessionReplyRequest,
    SessionReplyResponse,
    SessionCancelRequest,
    SessionEventsOptions,
    UploadResult,
    Recipe,
    RecipeManifest,
    ScheduledJob,
    ListSchedulesResponse,
    RunNowResponse,
    ScheduleSessionInfo,
    PromptTemplate,
    PromptListResponse,
    PromptContentResponse,
};
