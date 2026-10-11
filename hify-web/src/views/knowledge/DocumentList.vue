<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, Loading, Plus, UploadFilled } from '@element-plus/icons-vue'
import type {
  UploadInstance,
  UploadProps,
  UploadRequestOptions,
  UploadUserFile,
} from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'
import {
  deleteDocument,
  getDocument,
  getDocumentChunks,
  getDocumentList,
  getKnowledgeBase,
  uploadDocument,
  type DocumentChunkItem,
  type DocumentItem,
  type DocumentStatus,
} from '@/api/knowledge'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const POLL_INTERVAL_MS = 3_000
const CONTENT_PREVIEW_LENGTH = 200
const ACTIVE_STATUSES = new Set<DocumentStatus>(['PENDING', 'PROCESSING'])

const route = useRoute()
const router = useRouter()
const knowledgeBaseId = computed(() => Number(route.params.kbId))
const knowledgeBaseName = ref('')

const rows = ref<DocumentItem[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadKnowledgeBase() {
  if (!Number.isInteger(knowledgeBaseId.value) || knowledgeBaseId.value <= 0) {
    notifyError('知识库 ID 无效')
    return
  }
  try {
    const detail = await getKnowledgeBase(knowledgeBaseId.value)
    knowledgeBaseName.value = detail.name
  } catch {
    knowledgeBaseName.value = `知识库 #${knowledgeBaseId.value}`
  }
}

async function loadDocuments() {
  if (!Number.isInteger(knowledgeBaseId.value) || knowledgeBaseId.value <= 0) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await getDocumentList(
      knowledgeBaseId.value,
      page.value,
      pageSize.value,
    )
    rows.value = result.list
    total.value = result.total
    for (const document of result.list) {
      if (ACTIVE_STATUSES.has(document.status)) {
        pollingIds.add(document.id)
      }
    }
    ensurePolling()
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadDocuments()
}

function handleCurrentChange() {
  loadDocuments()
}

/* --- status polling ------------------------------------------------------- */
const pollingIds = new Set<number>()
let pollingTimer: ReturnType<typeof setInterval> | null = null
let polling = false

function ensurePolling() {
  if (pollingIds.size === 0 || pollingTimer != null) {
    return
  }
  pollingTimer = setInterval(pollDocumentStatuses, POLL_INTERVAL_MS)
}

function stopPollingIfIdle() {
  if (pollingIds.size === 0 && pollingTimer != null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function stopPollingDocument(id: number) {
  pollingIds.delete(id)
  stopPollingIfIdle()
}

async function pollDocumentStatuses() {
  if (polling || pollingIds.size === 0) {
    return
  }
  polling = true
  let terminalReached = false
  try {
    const ids = Array.from(pollingIds)
    const results = await Promise.allSettled(ids.map((id) => getDocument(id)))
    results.forEach((result, index) => {
      const id = ids[index]
      if (result.status === 'rejected') {
        pollingIds.delete(id)
        return
      }
      const document = result.value
      const rowIndex = rows.value.findIndex((item) => item.id === document.id)
      if (rowIndex >= 0) {
        rows.value[rowIndex] = document
      }
      if (!ACTIVE_STATUSES.has(document.status)) {
        pollingIds.delete(document.id)
        terminalReached = true
      }
    })
    if (terminalReached) {
      await loadDocuments()
    }
  } finally {
    polling = false
    stopPollingIfIdle()
  }
}

function clearPolling() {
  if (pollingTimer != null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  pollingIds.clear()
}

/* --- upload --------------------------------------------------------------- */
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref<UploadInstance>()
const uploadFiles = ref<UploadUserFile[]>([])

function openUpload() {
  uploadFiles.value = []
  uploadVisible.value = true
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const extension = file.name.includes('.')
    ? file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
    : ''
  if (!['txt', 'md', 'pdf'].includes(extension)) {
    notifyError('仅支持 txt、md、pdf 文件')
    return false
  }
  if (file.size > MAX_FILE_SIZE) {
    notifyError('文件大小不能超过 10MB')
    return false
  }
  return true
}

const handleExceed: UploadProps['onExceed'] = () => {
  notifyWarning('每次只能上传一个文件，请先移除已选文件')
}

async function handleUploadRequest(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const documentId = await uploadDocument(knowledgeBaseId.value, options.file)
    options.onSuccess(documentId)
    notifySuccess('上传成功，文档正在处理中')
    pollingIds.add(documentId)
    uploadVisible.value = false
    uploadFiles.value = []
    await loadDocuments()
    ensurePolling()
  } catch (error) {
    const uploadError = Object.assign(
      error instanceof Error ? error : new Error('上传失败'),
      { status: 0, method: 'POST', url: '' },
    ) as Parameters<typeof options.onError>[0]
    options.onError(uploadError)
  } finally {
    uploading.value = false
  }
}

function submitUpload() {
  if (uploadFiles.value.length === 0) {
    notifyWarning('请先选择要上传的文档')
    return
  }
  uploadRef.value?.submit()
}

function closeUpload() {
  if (uploading.value) {
    return
  }
  uploadVisible.value = false
  uploadRef.value?.clearFiles()
  uploadFiles.value = []
}

function handleUploadBeforeClose(done: () => void) {
  if (uploading.value) {
    return
  }
  uploadRef.value?.clearFiles()
  uploadFiles.value = []
  done()
}

/* --- chunks --------------------------------------------------------------- */
const chunksVisible = ref(false)
const chunksLoading = ref(false)
const chunkDocumentName = ref('')
const chunks = ref<DocumentChunkItem[]>([])
const expandedChunkIds = ref<Set<number>>(new Set())

async function showChunks(document: DocumentItem) {
  chunkDocumentName.value = document.name
  chunks.value = []
  expandedChunkIds.value = new Set()
  chunksVisible.value = true
  chunksLoading.value = true
  try {
    chunks.value = await getDocumentChunks(document.id)
  } catch {
    chunksVisible.value = false
  } finally {
    chunksLoading.value = false
  }
}

function chunkContent(chunk: DocumentChunkItem): string {
  if (expandedChunkIds.value.has(chunk.id) || chunk.content.length <= CONTENT_PREVIEW_LENGTH) {
    return chunk.content
  }
  return `${chunk.content.slice(0, CONTENT_PREVIEW_LENGTH)}…`
}

function toggleChunk(chunkId: number) {
  const next = new Set(expandedChunkIds.value)
  if (next.has(chunkId)) {
    next.delete(chunkId)
  } else {
    next.add(chunkId)
  }
  expandedChunkIds.value = next
}

function closeChunks() {
  chunksVisible.value = false
  chunks.value = []
  expandedChunkIds.value = new Set()
}

/* --- delete and display helpers ------------------------------------------ */
const removeDocument = useConfirm(
  { message: '确认删除该文档？相关分块也会一并删除。', successMessage: '删除成功' },
  async (document: DocumentItem) => {
    await deleteDocument(document.id)
    stopPollingDocument(document.id)
    await loadDocuments()
  },
)

const statusMeta: Record<DocumentStatus, {
  label: string
  type: 'primary' | 'success' | 'info' | 'danger'
}> = {
  PENDING: { label: '等待处理', type: 'info' },
  PROCESSING: { label: '处理中', type: 'primary' },
  DONE: { label: '处理完成', type: 'success' },
  FAILED: { label: '处理失败', type: 'danger' },
}

function statusOf(value: unknown) {
  const status = typeof value === 'string' && value in statusMeta
    ? value as DocumentStatus
    : 'PENDING'
  return statusMeta[status]
}

function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '-'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatDateTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

onMounted(() => {
  loadKnowledgeBase()
  loadDocuments()
})

onBeforeUnmount(clearPolling)
</script>

<template>
  <div class="page">
    <PageHeader
      :title="knowledgeBaseName || '文档管理'"
      description="管理知识库文档，并查看异步解析与向量化状态"
    >
      <template #actions>
        <el-button :icon="ArrowLeft" @click="router.push('/knowledge-bases')">
          返回知识库
        </el-button>
        <el-button type="primary" class="btn-brand-gradient" :icon="Plus" @click="openUpload">
          上传文档
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <el-table v-loading="loading" :data="rows" :row-style="{ height: '52px' }">
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileType" label="文件类型" width="100">
          <template #default="{ row }">
            <span class="file-type">{{ row.fileType?.toUpperCase() || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数量" width="100" />
        <el-table-column label="处理状态" width="130">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 'FAILED'"
              :content="row.errorMessage || '文档处理失败，暂无详细信息'"
              placement="top"
            >
              <el-tag type="danger" disable-transitions>
                {{ statusOf('FAILED').label }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusOf(row.status).type" disable-transitions>
              <el-icon v-if="row.status === 'PROCESSING'" class="is-loading status-loading">
                <Loading />
              </el-icon>
              {{ statusOf(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="168">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="cell-actions">
              <el-button text type="primary" @click="showChunks(row)">查看分块</el-button>
              <el-button
                text
                type="danger"
                :disabled="row.status === 'PROCESSING'"
                @click="removeDocument(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无文档，请点击右上角上传" :image-size="90" />
        </template>
      </el-table>

      <div class="document-pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="uploadVisible"
      title="上传文档"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      :before-close="handleUploadBeforeClose"
    >
      <el-upload
        ref="uploadRef"
        v-model:file-list="uploadFiles"
        drag
        action="#"
        accept=".txt,.md,.pdf"
        :auto-upload="false"
        :limit="1"
        :before-upload="beforeUpload"
        :on-exceed="handleExceed"
        :http-request="handleUploadRequest"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 TXT、MD、PDF，单个文件不超过 10MB</div>
        </template>
      </el-upload>

      <template #footer>
        <el-button :disabled="uploading" @click="closeUpload">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chunksVisible"
      :title="`文档分块：${chunkDocumentName}`"
      width="760px"
      @closed="closeChunks"
    >
      <div v-loading="chunksLoading" class="chunks-container">
        <el-empty
          v-if="!chunksLoading && chunks.length === 0"
          description="暂无分块内容"
          :image-size="80"
        />
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
          <div class="chunk-header">
            <span>分块 #{{ chunk.chunkIndex + 1 }}</span>
            <span class="chunk-tokens">{{ chunk.tokenCount }} tokens</span>
          </div>
          <div class="chunk-content">{{ chunkContent(chunk) }}</div>
          <el-button
            v-if="chunk.content.length > CONTENT_PREVIEW_LENGTH"
            text
            type="primary"
            class="chunk-toggle"
            @click="toggleChunk(chunk.id)"
          >
            {{ expandedChunkIds.has(chunk.id) ? '收起' : '展开全文' }}
          </el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="chunksVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.file-type {
  font-size: 12px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.status-loading {
  margin-right: 4px;
  vertical-align: -2px;
}

.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.document-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--hify-space-element);
  padding-top: 12px;
  border-top: 1px solid var(--hify-border);
}

.chunks-container {
  min-height: 120px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}

.chunk-card {
  padding: 14px 16px;
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-sm);
  background: var(--hify-bg-subtle);
}

.chunk-card + .chunk-card {
  margin-top: 12px;
}

.chunk-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.chunk-tokens {
  font-weight: 400;
  color: var(--hify-text-tertiary);
}

.chunk-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.65;
  color: var(--hify-text-secondary);
}

.chunk-toggle {
  margin-top: 6px;
  padding-left: 0;
}

@media (max-width: 767.98px) {
  .document-pager {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
