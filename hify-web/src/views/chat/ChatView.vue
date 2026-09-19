<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { marked, type Tokens } from 'marked'
import { getAgentList, type AgentItem } from '@/api/agent'
import {
  createSession,
  getMessages,
  getSessionList,
  sendMessage,
  type MessageItem,
  type SessionItem,
} from '@/api/chat'
import { notifyError } from '@/utils/notify'

/**
 * Markdown rendering for assistant replies.
 *
 * marked does not sanitize its output, and that output goes through v-html —
 * so any <script> / <img onerror=...> the model emitted would execute. The
 * renderer override escapes raw HTML instead of emitting it: the markup stays
 * visible as text rather than being dropped, so nothing the model wrote
 * disappears silently. Markdown syntax remains the only source of markup.
 */
marked.use({
  gfm: true,
  // Chat-style: a single newline is a real line break, not a soft wrap.
  breaks: true,
  renderer: {
    html: (token: Tokens.HTML | Tokens.Tag) => escapeHtml(token.raw),
  },
})

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMarkdown(content: string): string {
  return marked.parse(content, { async: false })
}

/* --- state ---------------------------------------------------------------- */

/** One rendered chat bubble. */
interface Bubble {
  role: 'user' | 'assistant'
  content: string
  /** True only while the assistant reply is still streaming. */
  streaming?: boolean
  /**
   * Failure notice. Kept apart from `content` so a partial answer stays
   * readable above the red line instead of being overwritten by the error.
   */
  error?: string
}

const agents = ref<AgentItem[]>([])
const sessions = ref<SessionItem[]>([])
const currentSessionId = ref<number | null>(null)
const bubbles = ref<Bubble[]>([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLElement | null>(null)

const canSend = computed(
  () => !sending.value && input.value.trim().length > 0 && currentSessionId.value !== null,
)

const currentLabel = computed(() => {
  const session = sessions.value.find((s) => s.id === currentSessionId.value)
  return session ? sessionLabel(session) : '对话'
})

onMounted(async () => {
  const page = await getAgentList(1, 100, undefined, true)
  agents.value = page.list
  await loadSessions()
})

onUnmounted(stopTypewriter)

/* --- session list --------------------------------------------------------- */

async function loadSessions() {
  const page = await getSessionList(1, 50)
  sessions.value = page.list
}

function agentName(agentId: number): string {
  return agents.value.find((agent) => agent.id === agentId)?.name ?? ''
}

/** Card heading: the derived title, falling back to the agent's name while
 *  a brand-new session has no message to name it after. */
function sessionLabel(session: SessionItem): string {
  return session.title || agentName(session.agentId) || '新对话'
}

/** "刚刚 / 12分钟前 / 3小时前 / 昨天 / 5天前 / 2026/9/1" */
function relativeTime(iso?: string | null): string {
  if (!iso) {
    return ''
  }
  const then = new Date(iso).getTime()
  const minutes = Math.floor((Date.now() - then) / 60_000)
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes}分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours}小时前`
  }
  const days = Math.floor(hours / 24)
  if (days === 1) {
    return '昨天'
  }
  if (days < 30) {
    return `${days}天前`
  }
  return new Date(iso).toLocaleDateString('zh-CN')
}

async function selectSession(session: SessionItem) {
  if (session.id === currentSessionId.value) {
    return
  }
  flushTypewriter()
  currentSessionId.value = session.id
  bubbles.value = []
  try {
    const history = await getMessages(session.id, undefined, 100)
    bubbles.value = history.map(toBubble)
    await scrollToBottom()
  } catch {
    // interceptor toasted
  }
}

/** History rows carry a status: FAILED turns were cut off mid-stream. */
function toBubble(message: MessageItem): Bubble {
  return {
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content,
    error: message.status === 'FAILED' ? '生成中断' : undefined,
  }
}

async function createNewSession(agentId: number) {
  if (sending.value) {
    return
  }
  flushTypewriter()
  try {
    const session = await createSession(agentId)
    await loadSessions()
    currentSessionId.value = session.id
    bubbles.value = []
  } catch {
    // interceptor toasted
  }
}

/* --- send ----------------------------------------------------------------- */

async function handleSend() {
  const content = input.value.trim()
  if (!content || sending.value || currentSessionId.value === null) {
    return
  }

  // ① Clear the input and show the user bubble right away — the UI must not
  //    wait for any network round trip.
  input.value = ''
  bubbles.value.push({ role: 'user', content })

  // ② An empty assistant bubble with the loading dots, directly below it.
  //    reactive() so the streaming updates below stay reactive after the push.
  const reply = reactive<Bubble>({ role: 'assistant', content: '', streaming: true })
  bubbles.value.push(reply)
  sending.value = true
  startTypewriter(reply)
  await scrollToBottom()

  try {
    // ③ Consume the SSE stream. Deltas enter the typewriter queue instead of
    //    the bubble directly, so they render at a steady pace.
    await sendMessage(currentSessionId.value, content, {
      onDelta: (event) => {
        typeQueue += event.content
      },
      onError: (event) => {
        // Mid-stream failure: keep whatever text arrived, add the reason below.
        reply.error = event.message
      },
    })
  } catch (error) {
    // Never became a stream (validation, backend down): same treatment.
    reply.error = (error as Error).message
    notifyError((error as Error).message)
  } finally {
    // ④ Stream over: hide the dots and re-enable the send button right away.
    //    The typewriter keeps draining whatever is still queued and stops on
    //    its own once the queue runs dry.
    reply.streaming = false
    streamFinished = true
    sending.value = false
    await scrollToBottom()
    // The card's excerpt, message count and relative time just changed.
    void loadSessions()
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/* --- typewriter ----------------------------------------------------------- */

/**
 * Typewriter pacing. Deltas arrive in bursts (the provider often sends several
 * characters per chunk), which reads as jumping blocks; queueing them and
 * rendering one character per tick smooths the output out.
 */
const TYPE_INTERVAL_MS = 30

/** Stream text received but not yet rendered. */
let typeQueue = ''
let typeTimer: number | null = null
/** Set when the stream ends so the timer knows to stop once it runs dry. */
let streamFinished = false
/** The bubble being typed into — only one stream runs at a time. */
let typingBubble: Bubble | null = null

function startTypewriter(bubble: Bubble) {
  flushTypewriter()
  typingBubble = bubble
  typeQueue = ''
  streamFinished = false
  typeTimer = window.setInterval(() => {
    const target = typingBubble
    if (!target) {
      stopTypewriter()
      return
    }
    if (typeQueue.length === 0) {
      // Idle until more arrives; once the stream is over, this is the end.
      if (streamFinished) {
        stopTypewriter()
      }
      return
    }
    target.content += typeQueue[0]
    typeQueue = typeQueue.slice(1)
    void scrollToBottom()
  }, TYPE_INTERVAL_MS)
}

function stopTypewriter() {
  if (typeTimer !== null) {
    window.clearInterval(typeTimer)
    typeTimer = null
  }
  typingBubble = null
  typeQueue = ''
  streamFinished = false
}

/** Render everything still queued immediately, then stop (switch, unmount). */
function flushTypewriter() {
  if (typingBubble && typeQueue.length > 0) {
    typingBubble.content += typeQueue
    void scrollToBottom()
  }
  stopTypewriter()
}
</script>

<template>
  <div class="page chat-page">
    <!-- left: conversation list -->
    <aside class="session-pane">
      <div class="session-head">
        <span class="session-head-title">对话列表</span>
        <el-dropdown trigger="click" @command="createNewSession">
          <el-button type="primary" size="small" :icon="Plus" :disabled="sending">
            新建
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="agent in agents" :key="agent.id" :command="agent.id">
                {{ agent.name }}
              </el-dropdown-item>
              <el-dropdown-item v-if="agents.length === 0" disabled>暂无可用 Agent</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="session-list">
        <el-empty v-if="sessions.length === 0" description="还没有对话" :image-size="60" />
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-card"
          :class="{ active: session.id === currentSessionId }"
          @click="selectSession(session)"
        >
          <div class="session-card-top">
            <span class="session-card-title">{{ sessionLabel(session) }}</span>
            <span class="session-card-time">{{ relativeTime(session.lastMessageAt) }}</span>
          </div>
          <div class="session-card-preview">{{ session.lastMessagePreview || '暂无消息' }}</div>
        </div>
      </div>
    </aside>

    <!-- right: conversation -->
    <section class="chat-pane">
      <header class="chat-head">{{ currentLabel }}</header>

      <div ref="scrollRef" class="chat-timeline">
        <el-empty
          v-if="bubbles.length === 0"
          :description="currentSessionId === null ? '点击左侧「新建」开始对话' : '发送第一条消息吧'"
        />
        <div v-for="(bubble, index) in bubbles" :key="index" class="row" :class="bubble.role">
          <div v-if="bubble.role === 'assistant'" class="avatar">AI</div>
          <div class="bubble" :class="{ failed: !!bubble.error }">
            <!-- User input stays literal text; only replies are Markdown. -->
            <span v-if="bubble.role === 'user' && bubble.content" class="bubble-text">
              {{ bubble.content }}
            </span>
            <div
              v-else-if="bubble.content"
              class="bubble-markdown"
              v-html="renderMarkdown(bubble.content)"
            ></div>
            <span v-if="bubble.streaming" class="typing"><i /><i /><i /></span>
            <div v-if="bubble.error" class="bubble-error">{{ bubble.error }}</div>
          </div>
          <div v-if="bubble.role === 'user'" class="avatar">我</div>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          :disabled="currentSessionId === null"
          :placeholder="
            currentSessionId === null ? '请先新建对话' : '输入消息，Enter 发送，Shift + Enter 换行'
          "
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button type="primary" :loading="sending" :disabled="!canSend" @click="handleSend">
          发送
        </el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* The chat page owns the whole canvas: no outer padding, two full-height
   panes side by side (overrides the .page defaults). */
.chat-page {
  flex-direction: row;
  gap: 0;
  padding: 0;
  height: 100%;
  box-sizing: border-box;
}

/* --- left: conversation list --------------------------------------------- */

.session-pane {
  flex: none;
  width: 270px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--hify-bg-container);
  border-right: 1px solid var(--hify-border);
}

.session-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
}

.session-head-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.session-card {
  padding: 10px 12px;
  border-radius: var(--hify-radius-sm);
  cursor: pointer;
  transition: background-color var(--hify-duration-fast) var(--hify-ease-out);
}

.session-card:hover {
  background: var(--hify-primary-50);
}

.session-card.active {
  background: var(--hify-primary-50);
  box-shadow: inset 3px 0 0 0 var(--hify-primary-500);
}

.session-card-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.session-card-title {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--hify-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-card-time {
  flex: none;
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.session-card-preview {
  margin-top: 4px;
  overflow: hidden;
  font-size: 12.5px;
  color: var(--hify-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* --- right: conversation -------------------------------------------------- */

.chat-pane {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--hify-bg-container);
}

.chat-head {
  flex: none;
  padding: 14px 20px;
  border-bottom: 1px solid var(--hify-border);
  font-size: 15px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.chat-timeline {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.row.user {
  justify-content: flex-end;
}

.avatar {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background-image: var(--hify-gradient-brand);
}

.bubble {
  max-width: 68%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.row.user .bubble {
  background: var(--hify-primary-500);
  color: var(--hify-text-on-primary);
}

.row.assistant .bubble {
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border);
  color: var(--hify-text-primary);
}

/* Failed replies keep their partial text but read as an error state. */
.bubble.failed {
  border: 1px solid var(--el-color-danger);
}

.bubble-error {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-color-danger-light-5);
  color: var(--el-color-danger);
  font-size: 13px;
  line-height: 1.5;
}

/* Rendered Markdown lives behind v-html, so scoped styles need :deep(). */
.bubble-markdown {
  /* .bubble sets white-space: pre-wrap for literal user text; rendered HTML
     must not inherit it, or every newline in the Markdown source shows up as
     extra blank space. */
  white-space: normal;
}

.bubble-markdown :deep(> :first-child) {
  margin-top: 0;
}

.bubble-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.bubble-markdown :deep(p) {
  margin: 0 0 8px;
}

.bubble-markdown :deep(h1),
.bubble-markdown :deep(h2),
.bubble-markdown :deep(h3),
.bubble-markdown :deep(h4) {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.bubble-markdown :deep(ul),
.bubble-markdown :deep(ol) {
  margin: 0 0 8px;
  padding-left: 20px;
}

.bubble-markdown :deep(li) {
  margin: 2px 0;
}

.bubble-markdown :deep(a) {
  color: var(--hify-primary-500);
}

/* Code is monospace everywhere; blocks get their own recessed surface. */
.bubble-markdown :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 12.5px;
}

.bubble-markdown :deep(pre) {
  margin: 0 0 8px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--hify-primary-50);
  overflow-x: auto;
}

.bubble-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

/* Inline code only — the block form is styled above. */
.bubble-markdown :deep(:not(pre) > code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--hify-primary-50);
}

.bubble-markdown :deep(blockquote) {
  margin: 0 0 8px;
  padding: 2px 0 2px 10px;
  border-left: 3px solid var(--hify-border);
  color: var(--hify-text-secondary);
}

.bubble-markdown :deep(table) {
  margin: 0 0 8px;
  border-collapse: collapse;
}

.bubble-markdown :deep(th),
.bubble-markdown :deep(td) {
  padding: 4px 10px;
  border: 1px solid var(--hify-border);
}

.bubble-markdown :deep(hr) {
  margin: 12px 0;
  border: none;
  border-top: 1px solid var(--hify-border);
}

/* Loading dots: shown from bubble creation until the stream ends. */
.typing {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  margin-left: 2px;
}

.typing i {
  width: 5px;
  height: 5px;
  margin: 0 1.5px;
  border-radius: 50%;
  background: var(--hify-text-tertiary);
  animation: typing-blink 1.4s infinite both;
}

.typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.typing i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

.composer {
  flex: none;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid var(--hify-border);
}
</style>
