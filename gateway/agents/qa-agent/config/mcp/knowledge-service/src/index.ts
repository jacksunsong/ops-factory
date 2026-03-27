import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js'
import { dispatch, tools } from './handlers.js'

const server = new Server(
  { name: 'knowledge-service', version: '1.0.0' },
  { capabilities: { tools: {} } },
)

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools }))

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params
  const result = await dispatch(name, args ?? {})
  return { content: [{ type: 'text', text: result }] }
})

const transport = new StdioServerTransport()
await server.connect(transport)
console.error('[knowledge-service] MCP server running on stdio')
