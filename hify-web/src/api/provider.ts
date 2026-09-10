import { del, get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** Provider protocol template. */
export type ProviderType = 'OPENAI' | 'ANTHROPIC' | 'DEEPSEEK' | 'OLLAMA' | 'OPENAI_COMPATIBLE'

export const PROVIDER_TYPES: ProviderType[] = [
  'OPENAI',
  'ANTHROPIC',
  'DEEPSEEK',
  'OLLAMA',
  'OPENAI_COMPATIBLE',
]

/** Health probe status shown in the list (UNKNOWN until the first probe). */
export type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

/** Provider row / detail payload (never contains credentials). */
export interface ProviderItem {
  id: number
  name: string
  type: ProviderType
  baseUrl: string
  description?: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
  /** Filled by the list endpoint only. */
  healthStatus?: HealthStatus
  healthLatencyMs?: number | null
  enabledModelCount?: number
}

/** Create / update payload; apiKey is write-only (blank on update = keep). */
export interface ProviderPayload {
  name: string
  type: ProviderType
  baseUrl: string
  apiKey?: string
  description?: string
  enabled?: boolean
}

/** Model row nested in the provider detail. */
export interface ModelConfigItem {
  id: number
  providerId: number
  name: string
  modelId: string
  contextSize?: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** Health probe row nested in the provider detail. */
export interface ProviderHealthItem {
  providerId: number
  status: HealthStatus
  lastCheckAt?: string | null
  lastSuccessAt?: string | null
  failCount: number
  latencyMs?: number | null
  errorMessage?: string | null
}

export interface ProviderDetail {
  provider: ProviderItem
  models: ModelConfigItem[]
  health: ProviderHealthItem | null
}

/** Connectivity probe outcome. */
export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage?: string | null
}

/** GET /v1/providers — paged list with health + enabled model count. */
export const getProviderList = (
  page: number,
  pageSize: number,
  type?: ProviderType,
  enabled?: boolean,
) =>
  get<PageResult<ProviderItem>>('/v1/providers', {
    params: { page, pageSize, type, enabled },
  })

/** POST /v1/providers */
export const createProvider = (payload: ProviderPayload) =>
  post<ProviderItem>('/v1/providers', payload)

/** PUT /v1/providers/{id} — blank apiKey keeps the stored key. */
export const updateProvider = (id: number, payload: ProviderPayload) =>
  put<void>(`/v1/providers/${id}`, payload)

/** DELETE /v1/providers/{id} — cascades to models and health. */
export const deleteProvider = (id: number) => del<void>(`/v1/providers/${id}`)

/** GET /v1/providers/{id} — provider + models + health. */
export const getProviderDetail = (id: number) => get<ProviderDetail>(`/v1/providers/${id}`)

/** POST /v1/providers/{id}/test-connection */
export const testConnection = (id: number) =>
  post<ConnectionTestResult>(`/v1/providers/${id}/test-connection`)
