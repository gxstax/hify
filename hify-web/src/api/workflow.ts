import { del, get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type WorkflowStatus = 'DRAFT' | 'PUBLISHED'
export type WorkflowNodeType = 'START' | 'END' | 'LLM' | 'API_CALL' | 'CONDITION' | 'KNOWLEDGE' | 'TEXT'

export interface WorkflowNodePayload {
  nodeKey: string
  name: string
  nodeType: WorkflowNodeType
  config: Record<string, unknown>
  configVersion: number
  sortOrder: number
}

export interface WorkflowEdgePayload {
  sourceNodeKey: string
  targetNodeKey: string
  sourcePort?: string | null
  priority: number
}

export interface WorkflowDefinition {
  version: number
  enabled: boolean
  nodes: WorkflowNodePayload[]
  edges: WorkflowEdgePayload[]
}

export interface WorkflowPayload extends WorkflowDefinition {
  name: string
  description?: string
}

export interface WorkflowItem {
  id: number
  name: string
  description: string
  version: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetail extends WorkflowItem {
  nodes: Array<WorkflowNodePayload & { id: number }>
  edges: Array<WorkflowEdgePayload & { id: number }>
}

export const workflowStatusOf = (enabled: boolean): WorkflowStatus =>
  enabled ? 'PUBLISHED' : 'DRAFT'

/** GET /v1/workflows */
export const getWorkflowList = (
  page: number,
  pageSize: number,
  keyword?: string,
  status?: WorkflowStatus,
) => get<PageResult<WorkflowItem>>('/v1/workflows', {
  params: {
    page,
    pageSize,
    keyword: keyword || undefined,
    enabled: status ? status === 'PUBLISHED' : undefined,
  },
})

/** GET /v1/workflows/{id} */
export const getWorkflow = (id: number) =>
  get<WorkflowDetail>(`/v1/workflows/${id}`)

/** POST /v1/workflows */
export const createWorkflow = (payload: WorkflowPayload) =>
  post<WorkflowDetail>('/v1/workflows', payload)

/** POST /v1/workflows/{id}/publish */
export const publishWorkflow = (id: number) =>
  post<void>(`/v1/workflows/${id}/publish`)

/** DELETE /v1/workflows/{id} */
export const deleteWorkflow = (id: number) =>
  del<void>(`/v1/workflows/${id}`)
