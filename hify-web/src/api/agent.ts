import { del, get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** Agent list row (model info resolved by the backend, no system prompt). */
export interface AgentItem {
  id: number
  name: string
  description?: string | null
  modelConfigId: number
  knowledgeBaseId?: number | null
  workflowId?: number | null
  modelName?: string | null
  providerName?: string | null
  temperature?: number | null
  maxTokens?: number | null
  maxContextTurns?: number | null
  enabled: boolean
  toolCount?: number
  createdAt: string
  updatedAt: string
}

/** Agent detail: full fields + bound tool ids. */
export interface AgentDetail extends AgentItem {
  systemPrompt?: string | null
  toolIds: number[]
}

/** Update payload of the basic info. */
export interface AgentPayload {
  name: string
  description?: string
  systemPrompt?: string
  modelConfigId: number
  knowledgeBaseId?: number | null
  temperature?: number | null
  maxTokens?: number | null
  maxContextTurns?: number | null
  enabled?: boolean
}

/** Create payload including relations written in the same backend transaction. */
export interface AgentCreatePayload extends AgentPayload {
  workflowId?: number | null
  toolIds?: number[]
}

/** GET /v1/agents — paged list with model info + tool count. */
export const getAgentList = (page: number, pageSize: number, keyword?: string, enabled?: boolean) =>
  get<PageResult<AgentItem>>('/v1/agents', { params: { page, pageSize, keyword, enabled } })

/** GET /v1/agents/{id} */
export const getAgentDetail = (id: number) => get<AgentDetail>(`/v1/agents/${id}`)

/** POST /v1/agents */
export const createAgent = (payload: AgentCreatePayload) => post<AgentDetail>('/v1/agents', payload)

/** PUT /v1/agents/{id} — basic info only. */
export const updateAgent = (id: number, payload: AgentPayload) =>
  put<AgentDetail>(`/v1/agents/${id}`, payload)

/** DELETE /v1/agents/{id} */
export const deleteAgent = (id: number) => del<void>(`/v1/agents/${id}`)

/** PUT /v1/agents/{id}/tools — full replacement of the tool bindings. */
export const updateAgentTools = (id: number, toolIds: number[]) =>
  put<number[]>(`/v1/agents/${id}/tools`, { toolIds })
