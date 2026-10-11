import { del, get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

export interface KnowledgeBaseItem {
  id: number
  name: string
  description: string
  enabled: boolean
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseCreatePayload {
  name: string
  description?: string
}

export interface KnowledgeBaseUpdatePayload extends KnowledgeBaseCreatePayload {
  enabled: boolean
}

/** GET /v1/knowledge-bases — paged list with optional name search. */
export const getKnowledgeBaseList = (page: number, pageSize: number, name?: string) =>
  get<PageResult<KnowledgeBaseItem>>('/v1/knowledge-bases', {
    params: { page, size: pageSize, name: name || undefined },
  })

/** POST /v1/knowledge-bases */
export const createKnowledgeBase = (payload: KnowledgeBaseCreatePayload) =>
  post<KnowledgeBaseItem>('/v1/knowledge-bases', payload)

/** PUT /v1/knowledge-bases/{id} */
export const updateKnowledgeBase = (id: number, payload: KnowledgeBaseUpdatePayload) =>
  put<KnowledgeBaseItem>(`/v1/knowledge-bases/${id}`, payload)

/** DELETE /v1/knowledge-bases/{id} */
export const deleteKnowledgeBase = (id: number) =>
  del<void>(`/v1/knowledge-bases/${id}`)

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

export interface DocumentItem {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  errorMessage: string
  chunkCount: number
  createdAt: string
  updatedAt: string
}

export interface DocumentChunkItem {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  tokenCount: number
  createdAt: string
}

/** GET /v1/knowledge-bases/{id} */
export const getKnowledgeBase = (id: number) =>
  get<KnowledgeBaseItem>(`/v1/knowledge-bases/${id}`)

/** GET /v1/knowledge-bases/{kbId}/documents */
export const getDocumentList = (kbId: number, page: number, pageSize: number) =>
  get<PageResult<DocumentItem>>(`/v1/knowledge-bases/${kbId}/documents`, {
    params: { page, pageSize },
  })

/** POST /v1/knowledge-bases/{kbId}/documents */
export const uploadDocument = (kbId: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return post<number>(`/v1/knowledge-bases/${kbId}/documents`, formData)
}

/** GET /v1/documents/{id} */
export const getDocument = (id: number) =>
  get<DocumentItem>(`/v1/documents/${id}`)

/** GET /v1/documents/{id}/chunks */
export const getDocumentChunks = (id: number) =>
  get<DocumentChunkItem[]>(`/v1/documents/${id}/chunks`)

/** DELETE /v1/documents/{id} */
export const deleteDocument = (id: number) =>
  del<void>(`/v1/documents/${id}`)
