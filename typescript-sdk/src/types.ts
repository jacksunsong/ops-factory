/**
 * goosed-sdk types
 */

export interface MessageMetadata {
    userVisible: boolean;
    agentVisible: boolean;
}

export interface TextContent {
    type: 'text';
    text: string;
}

export interface ImageContent {
    type: 'image';
    data: string;
    mimeType: string;
}

export interface ToolRequestContent {
    type: 'toolRequest';
    id?: string;
    toolCall?: Record<string, unknown>;
}

export interface ToolResponseContent {
    type: 'toolResponse';
    id?: string;
    toolResult?: Record<string, unknown>;
}

export interface ReasoningContent {
    type: 'reasoning';
    text: string;
}

export interface ThinkingContent {
    type: 'thinking';
    thinking: string;
    signature?: string;
}

export interface RedactedThinkingContent {
    type: 'redactedThinking';
    data: string;
}

export interface SystemNotificationContent {
    type: 'systemNotification';
    notificationType?: string;
    msg?: string;
}

export interface GenericMessageContent {
    type: string;
    text?: string;
    data?: string;
    mimeType?: string;
    id?: string;
    toolCall?: Record<string, unknown>;
    toolResult?: Record<string, unknown>;
    notificationType?: string;
    msg?: string;
    thinking?: string;
    signature?: string;
}

export type MessageContent =
    | TextContent
    | ImageContent
    | ToolRequestContent
    | ToolResponseContent
    | ReasoningContent
    | ThinkingContent
    | RedactedThinkingContent
    | SystemNotificationContent
    | GenericMessageContent;

export interface Message {
    id?: string;
    role: 'user' | 'assistant';
    created: number;
    content: MessageContent[];
    metadata: MessageMetadata;
}

export interface TokenState {
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
    accumulatedInputTokens: number;
    accumulatedOutputTokens: number;
    accumulatedTotalTokens: number;
}

export interface ExtensionConfig {
    type: string;
    name: string;
    description?: string;
    bundled?: boolean;
}

export interface Session {
    id: string;
    name: string;
    working_dir: string;
    session_type: string;
    schedule_id?: string | null;
    created_at: string;
    updated_at: string;
    user_set_name?: boolean;
    message_count?: number;
    total_tokens?: number | null;
    input_tokens?: number | null;
    output_tokens?: number | null;
    provider_name?: string | null;
    conversation?: Record<string, unknown>[] | null;
}

export interface CleanupEmptySessionResult {
    deleted: boolean;
    reason: string;
}

export interface ToolInfo {
    name: string;
    description: string;
    parameters: string[];
    permission?: string | null;
}

export interface CallToolResponse {
    content: Record<string, unknown>[];
    is_error: boolean;
}

export interface ExtensionResult {
    name: string;
    success: boolean;
}

export interface SystemInfo {
    app_version: string;
    os: string;
    os_version: string;
    architecture: string;
    provider: string;
    model: string;
    enabled_extensions: string[];
}

export type SSEEventType = 'Ping' | 'Message' | 'Finish' | 'Error' | 'ModelChange' | 'Notification' | 'UpdateConversation' | 'OutputFiles' | 'ActiveRequests';

export interface OutputFile {
    path: string;
    name: string;
    ext: string;
}

export interface SSEEvent {
    type: SSEEventType;
    message?: Record<string, unknown>;
    token_state?: TokenState;
    reason?: string;
    error?: string;
    layer?: SessionErrorLayer;
    code?: string;
    severity?: SessionErrorSeverity;
    message_key?: string;
    detail?: string;
    retryable?: boolean;
    suggested_actions?: SessionSuggestedAction[];
    model?: string;
    mode?: string;
    request_id?: string;
    chat_request_id?: string;
    request_ids?: string[];
    conversation?: Record<string, unknown>[];
    // OutputFiles event fields
    sessionId?: string;
    files?: OutputFile[];
}

export interface SessionReplyRequest {
    request_id: string;
    user_message: Message;
    override_conversation?: Message[];
}

export interface SessionReplyResponse {
    request_id: string;
}

export interface SessionCancelRequest {
    request_id: string;
}

export interface SessionEventsOptions {
    lastEventId?: string;
    signal?: AbortSignal;
}

export interface SessionSSEEvent {
    event: SSEEvent;
    eventId?: string;
}

export type SessionErrorLayer = 'frontend' | 'gateway' | 'goosed' | 'provider' | 'tool' | 'mcp' | 'policy';

export type SessionErrorSeverity = 'info' | 'warning' | 'error' | 'fatal';

export type SessionSuggestedAction =
    | 'reconnect'
    | 'wait'
    | 'retry'
    | 'cancel'
    | 'continue'
    | 'new_request'
    | 'reduce_context'
    | 'check_tool'
    | 'login'
    | 'contact_support';

export interface SessionErrorEnvelope {
    type: 'Error';
    layer: SessionErrorLayer;
    code: string;
    severity: SessionErrorSeverity;
    message_key?: string;
    message: string;
    detail?: string;
    retryable: boolean;
    suggested_actions: SessionSuggestedAction[];
    session_id?: string;
    request_id?: string;
    agent_id?: string;
    elapsed_ms?: number;
    http_status?: number;
    upstream_status?: number;
    trace_id?: string;
    raw?: unknown;
}

export interface SessionErrorContext {
    layer?: SessionErrorLayer;
    code?: string;
    severity?: SessionErrorSeverity;
    message?: string;
    detail?: string;
    retryable?: boolean;
    suggestedActions?: SessionSuggestedAction[];
    sessionId?: string;
    requestId?: string;
    agentId?: string;
    httpStatus?: number;
    upstreamStatus?: number;
    traceId?: string;
}

export interface ImageData {
    data: string;       // base64 encoded image data (no data URL prefix)
    mimeType: string;   // e.g. 'image/jpeg', 'image/png'
}

export interface UploadResult {
    path: string;
    name: string;
    size: number;
    type: string;
}

export interface GoosedClientOptions {
    baseUrl?: string;
    secretKey?: string;
    timeout?: number;
    userId?: string;
}

export interface SetProviderRequest {
    provider: string;
    model: string;
}

export interface Recipe {
    title: string;
    description: string;
    instructions?: string;
    prompt?: string;
    [key: string]: unknown;
}

export interface RecipeManifest {
    id: string;
    recipe: Recipe;
    file_path: string;
    last_modified: string;
    schedule_cron?: string | null;
    slash_command?: string | null;
}

export interface ScheduledJob {
    id: string;
    source: string;
    cron: string;
    last_run?: string | null;
    currently_running?: boolean;
    paused?: boolean;
    current_session_id?: string | null;
    process_start_time?: string | null;
}

export interface ListSchedulesResponse {
    jobs: ScheduledJob[];
}

export interface RunNowResponse {
    session_id: string;
}

// === Prompt Types ===

export interface PromptTemplate {
    name: string;
    description: string;
    default_content: string;
    user_content: string | null;
    is_customized: boolean;
}

export interface PromptListResponse {
    prompts: PromptTemplate[];
}

export interface PromptContentResponse {
    name: string;
    content: string;
    default_content: string;
    is_customized: boolean;
}

export interface ScheduleSessionInfo {
    id: string;
    name: string;
    createdAt: string;
    workingDir: string;
    scheduleId?: string | null;
    messageCount: number;
    totalTokens?: number | null;
    inputTokens?: number | null;
    outputTokens?: number | null;
    accumulatedTotalTokens?: number | null;
    accumulatedInputTokens?: number | null;
    accumulatedOutputTokens?: number | null;
}
