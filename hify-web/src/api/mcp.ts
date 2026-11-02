import { del, get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

export interface JsonSchema {
  type?: string | string[]
  title?: string
  description?: string
  properties?: Record<string, JsonSchema>
  required?: string[]
  default?: unknown
  [key: string]: unknown
}

export interface McpTool {
  id: number
  serverId: number
  name: string
  description: string
  inputSchema: JsonSchema | null
}

export interface McpServerListItem {
  id: number
  name: string
  endpoint: string
  description: string
  enabled: boolean
  toolCount: number
  createdAt: string
  updatedAt: string
}

export interface McpServerDetail {
  id: number
  name: string
  endpoint: string
  description: string
  enabled: boolean
  tools: McpTool[]
  createdAt: string
  updatedAt: string
}

export interface McpDebugRequest {
  toolName: string
  arguments: Record<string, unknown>
}

export interface McpDebugResponse {
  result: string
  elapsedMs: number
}

export interface McpServerPayload {
  name: string
  endpoint: string
  description?: string
  enabled: boolean
}

export interface McpConnectionTestResponse {
  success: boolean
  latencyMs: number
  toolCount: number
  syncedToolCount: number
  errorMessage: string
}

export const getMcpServerList = (page: number, pageSize: number) =>
  get<PageResult<McpServerListItem>>('/v1/mcp-servers', {
    params: { page, pageSize },
  })

export const getMcpServer = (id: number) =>
  get<McpServerDetail>(`/v1/mcp-servers/${id}`)

export const createMcpServer = (payload: McpServerPayload) =>
  post<McpServerDetail>('/v1/mcp-servers', payload)

export const updateMcpServer = (id: number, payload: McpServerPayload) =>
  put<McpServerDetail>(`/v1/mcp-servers/${id}`, payload)

export const deleteMcpServer = (id: number) =>
  del<void>(`/v1/mcp-servers/${id}`)

export const testMcpServer = (id: number) =>
  post<McpConnectionTestResponse>(`/v1/mcp-servers/${id}/test`)

export const debugMcpServer = (id: number, payload: McpDebugRequest) =>
  post<McpDebugResponse>(`/v1/mcp-servers/${id}/debug`, payload)
