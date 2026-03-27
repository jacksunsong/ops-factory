# Knowledge Service MCP for qa-agent

This MCP server exposes `search` and `fetch` tools backed by `knowledge-service`.

## Runtime registration

The extension is registered in:

- `gateway/agents/qa-agent/config/config.yaml`

Extension name: `knowledge-service`

## Tools

| Tool | Usage |
|------|-------|
| `search` | Search chunk candidates from the configured knowledge sources. Uses the configured default `sourceId` when `sourceIds` is omitted. |
| `fetch` | Fetch full chunk content and optional neighbor chunks for a known `chunkId`. |

## Environment

Required secrets in `gateway/agents/qa-agent/config/secrets.yaml`:

- `KNOWLEDGE_SERVICE_URL`
- `KNOWLEDGE_DEFAULT_SOURCE_ID`

Optional:

- `KNOWLEDGE_REQUEST_TIMEOUT_MS`

## Usage policy

- Intended for RAG only.
- Prefer `search` first, then `fetch` promising chunks.
- Keep final answers concise and cite chunk-level evidence.
