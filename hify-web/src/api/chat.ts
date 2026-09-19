import { del, get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** Session row (GET /v1/chat/sessions). */
export interface SessionItem {
  id: number
  agentId: number
  title?: string | null
  status: string
  messageCount: number
  /** Excerpt of the newest message, for the session-list card. */
  lastMessagePreview?: string | null
  lastMessageAt?: string | null
  createdAt: string
  updatedAt: string
}

/** Message row (GET /v1/chat/sessions/{id}/messages). */
export interface MessageItem {
  id: number
  sessionId: number
  parentId?: number | null
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  /** FAILED keeps whatever text was produced before the stream died. */
  status: 'GENERATING' | 'COMPLETED' | 'FAILED'
  finishReason?: string | null
  promptTokens: number
  completionTokens: number
  createdAt: string
}

// ---------------------------------------------------------------------------
// Session management (plain JSON endpoints)
// ---------------------------------------------------------------------------

/** POST /v1/chat/sessions — creates an empty session; the title is derived
 *  from the first message when omitted. */
export const createSession = (agentId: number, title?: string) =>
  post<SessionItem>('/v1/chat/sessions', { agentId, title })

/** GET /v1/chat/sessions — most recently active first. */
export const getSessionList = (page: number, pageSize: number, agentId?: number) =>
  get<PageResult<SessionItem>>('/v1/chat/sessions', { params: { page, pageSize, agentId } })

/** GET /v1/chat/sessions/{id} */
export const getSession = (id: number) => get<SessionItem>(`/v1/chat/sessions/${id}`)

/** DELETE /v1/chat/sessions/{id} — messages are removed physically. */
export const deleteSession = (id: number) => del<void>(`/v1/chat/sessions/${id}`)

/**
 * GET /v1/chat/sessions/{id}/messages — oldest first.
 *
 * Cursor-paginated: pass the id of the oldest message already held to load the
 * page before it; omit for the newest page. A page shorter than `limit` means
 * the history is exhausted.
 */
export const getMessages = (id: number, cursor?: number, limit = 50) =>
  get<MessageItem[]>(`/v1/chat/sessions/${id}/messages`, { params: { cursor, limit } })

// ---------------------------------------------------------------------------
// Streaming (SSE over POST)
// ---------------------------------------------------------------------------

/** First event of every stream: which session/message the deltas belong to. */
export interface StartEvent {
  sessionId: number
  messageId: number
}

export interface DeltaEvent {
  content: string
}

export interface DoneEvent {
  messageId: number
  finishReason: string
  promptTokens: number
  completionTokens: number
}

export interface ErrorEvent {
  code: number
  message: string
}

export interface StreamHandlers {
  onStart?: (event: StartEvent) => void
  onDelta: (event: DeltaEvent) => void
  onDone?: (event: DoneEvent) => void
  onError?: (event: ErrorEvent) => void
}

/**
 * POST /v1/chat/sessions/{id}/messages and consume the SSE reply.
 *
 * Deliberately fetch + ReadableStream instead of EventSource: the latter can
 * only issue GET requests and cannot carry a JSON body, while sending a message
 * is a POST.
 *
 * Resolves when the stream ends — whether it ended with `done` or `error`
 * (the latter also fires `onError` first). Rejects only when the request never
 * became a stream at all (validation errors, backend down), so callers can
 * tell "the reply failed mid-way" from "the message was never sent".
 */
export async function sendMessage(
  sessionId: number,
  content: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
    signal,
  })

  // Validation errors (blank content, unknown session) come back as a normal
  // JSON envelope — the stream only opens once the request passed validation.
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const message = await readErrorMessage(response)
    throw new Error(message)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  await consumeSse(response.body, handlers)
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const result = (await response.json()) as { message?: string }
    return result.message || `请求失败 (HTTP ${response.status})`
  } catch {
    return `请求失败 (HTTP ${response.status})`
  }
}

/** Decode an SSE byte stream, one handler call per event. */
async function consumeSse(
  body: ReadableStream<Uint8Array>,
  handlers: StreamHandlers,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break

      // stream: true keeps multi-byte characters intact across chunk borders.
      buffer += decoder.decode(value, { stream: true })

      // Frames end with a blank line; anything after the last blank line is an
      // incomplete frame and stays buffered until the rest arrives.
      let end = buffer.indexOf('\n\n')
      while (end >= 0) {
        dispatchFrame(buffer.slice(0, end), handlers)
        buffer = buffer.slice(end + 2)
        end = buffer.indexOf('\n\n')
      }
    }
    // A well-formed stream ends with a blank line, so nothing should be left.
    if (buffer.trim()) {
      dispatchFrame(buffer, handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

function dispatchFrame(frame: string, handlers: StreamHandlers): void {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }
  if (dataLines.length === 0) return

  const payload = JSON.parse(dataLines.join('\n'))
  switch (event) {
    case 'start':
      handlers.onStart?.(payload as StartEvent)
      break
    case 'delta':
      handlers.onDelta(payload as DeltaEvent)
      break
    case 'done':
      handlers.onDone?.(payload as DoneEvent)
      break
    case 'error':
      handlers.onError?.(payload as ErrorEvent)
      break
  }
}
