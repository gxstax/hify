<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ArrowLeft, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  debugMcpServer,
  getMcpServer,
  testMcpServer,
  type McpServerDetail,
  type McpTool,
} from '@/api/mcp'
import { notifySuccess, notifyWarning } from '@/utils/notify'

interface SchemaField {
  name: string
  label: string
  description: string
  type: 'string' | 'number' | 'integer' | 'unsupported'
  required: boolean
  defaultValue?: string | number
  rawType: string
}

interface DebugResult {
  success: boolean
  toolName: string
  result: string
  elapsedMs: number
}

interface DebugHistoryItem extends DebugResult {
  id: string
  arguments: Record<string, unknown>
  calledAt: string
}

const route = useRoute()
const router = useRouter()
const detailLoading = ref(false)
const refreshing = ref(false)
const server = ref<McpServerDetail>()
const selectedToolName = ref('')
const submitting = ref(false)
const currentResult = ref<DebugResult>()
const history = ref<DebugHistoryItem[]>([])
const connectionState = ref<'idle' | 'success' | 'failure'>('idle')
const formValuesByTool = reactive<Record<string, Record<string, unknown>>>({})
const fieldErrors = reactive<Record<string, string>>({})

const selectedTool = computed(() =>
  server.value?.tools.find((tool) => tool.name === selectedToolName.value),
)
const schemaFields = computed<SchemaField[]>(() => parseFields(selectedTool.value))
const unsupportedFields = computed(() => schemaFields.value.filter((field) => field.type === 'unsupported'))
const currentValues = computed(() => formValuesByTool[selectedToolName.value] ?? {})
const canCall = computed(() => Boolean(
  server.value?.enabled
    && selectedTool.value
    && unsupportedFields.value.length === 0
    && !submitting.value,
))

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseFields(tool?: McpTool): SchemaField[] {
  const schema = tool?.inputSchema
  if (!isRecord(schema) || !isRecord(schema.properties)) {
    return []
  }
  const required = Array.isArray(schema.required)
    ? new Set(schema.required.filter((item): item is string => typeof item === 'string'))
    : new Set<string>()

  return Object.entries(schema.properties).flatMap(([name, value]) => {
    if (!isRecord(value)) {
      return []
    }
    const rawType = typeof value.type === 'string' ? value.type : 'unknown'
    const type = rawType === 'string' || rawType === 'number' || rawType === 'integer'
      ? rawType
      : 'unsupported'
    const defaultValue = typeof value.default === 'string' || typeof value.default === 'number'
      ? value.default
      : undefined
    return [{
      name,
      label: typeof value.title === 'string' ? value.title : name,
      description: typeof value.description === 'string' ? value.description : '',
      type,
      required: required.has(name),
      defaultValue,
      rawType,
    }]
  })
}

function initializeValues(tool: McpTool) {
  if (formValuesByTool[tool.name]) {
    return
  }
  const values: Record<string, unknown> = {}
  for (const field of parseFields(tool)) {
    if (field.defaultValue !== undefined) {
      values[field.name] = field.defaultValue
    }
  }
  formValuesByTool[tool.name] = values
}

function handleToolChange(name: string) {
  const tool = server.value?.tools.find((item) => item.name === name)
  if (tool) {
    initializeValues(tool)
  }
  currentResult.value = undefined
  clearErrors()
}

function clearErrors() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function buildArguments(): Record<string, unknown> | null {
  clearErrors()
  const values = currentValues.value
  const argumentsValue: Record<string, unknown> = {}

  for (const field of schemaFields.value) {
    const value = values[field.name]
    if (field.required) {
      if (field.type === 'string' && (typeof value !== 'string' || !value.trim())) {
        fieldErrors[field.name] = `${field.label}不能为空`
      }
      if ((field.type === 'number' || field.type === 'integer')
          && (typeof value !== 'number' || Number.isNaN(value))) {
        fieldErrors[field.name] = `${field.label}不能为空`
      }
    }
    if (value !== undefined && value !== null && value !== '') {
      argumentsValue[field.name] = value
    }
  }

  return Object.keys(fieldErrors).length > 0 ? null : argumentsValue
}

function cloneArguments(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>
}

function displayResult(value: string): string {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function addHistory(item: Omit<DebugHistoryItem, 'id'>) {
  history.value = [{
    ...item,
    id: `${Date.now()}-${Math.random()}`,
    arguments: cloneArguments(item.arguments),
  }, ...history.value].slice(0, 5)
}

async function callTool() {
  if (submitting.value || !selectedTool.value) {
    return
  }
  if (unsupportedFields.value.length > 0) {
    notifyWarning('当前工具包含暂不支持的参数类型')
    return
  }
  const argumentsValue = buildArguments()
  if (!argumentsValue) {
    return
  }

  const toolName = selectedTool.value.name
  const started = performance.now()
  submitting.value = true
  try {
    const response = await debugMcpServer(server.value!.id, {
      toolName,
      arguments: argumentsValue,
    })
    const result: DebugResult = {
      success: true,
      toolName,
      result: response.result,
      elapsedMs: response.elapsedMs,
    }
    currentResult.value = result
    addHistory({ ...result, arguments: argumentsValue, calledAt: new Date().toLocaleString() })
  } catch (error) {
    const result: DebugResult = {
      success: false,
      toolName,
      result: error instanceof Error ? error.message : '调用失败',
      elapsedMs: Math.round(performance.now() - started),
    }
    currentResult.value = result
    addHistory({ ...result, arguments: argumentsValue, calledAt: new Date().toLocaleString() })
  } finally {
    submitting.value = false
  }
}

async function refreshTools() {
  if (!server.value || refreshing.value) {
    return
  }
  refreshing.value = true
  try {
    const result = await testMcpServer(server.value.id)
    connectionState.value = result.success ? 'success' : 'failure'
    if (!result.success) {
      notifyWarning(result.errorMessage || '连接失败')
      return
    }
    notifySuccess(`已同步 ${result.syncedToolCount} 个工具`)
    await loadDetail(server.value.id, false)
  } finally {
    refreshing.value = false
  }
}

async function loadDetail(id: number, resetHistory = true) {
  detailLoading.value = true
  if (resetHistory) {
    server.value = undefined
    currentResult.value = undefined
    history.value = []
  }
  try {
    const detail = await getMcpServer(id)
    server.value = detail
    if (!detail.tools.some((tool) => tool.name === selectedToolName.value)) {
      selectedToolName.value = detail.tools[0]?.name ?? ''
    }
    if (selectedToolName.value) {
      handleToolChange(selectedToolName.value)
    }
  } catch {
    router.replace('/mcp-servers')
  } finally {
    detailLoading.value = false
  }
}

watch(
  () => route.params.id,
  (value) => {
    const id = Number(value)
    if (!Number.isInteger(id) || id <= 0) {
      router.replace('/mcp-servers')
      return
    }
    loadDetail(id)
  },
  { immediate: true },
)
</script>

<template>
  <div v-loading="detailLoading" class="page debug-page">
    <template v-if="server">
      <div class="debug-header">
        <div>
          <el-button text :icon="ArrowLeft" class="back-button" @click="router.push('/mcp-servers')">
            返回
          </el-button>
          <h1>{{ server.name }}</h1>
          <p>{{ server.endpoint }}</p>
        </div>
        <div class="header-actions">
          <el-tag
            :type="connectionState === 'failure' ? 'danger' : connectionState === 'success' ? 'success' : server.enabled ? 'success' : 'info'"
          >
            {{ connectionState === 'failure' ? '无法连接' : connectionState === 'success' ? '连接正常' : server.enabled ? '已启用' : '已禁用' }}
          </el-tag>
          <el-button :icon="Refresh" :loading="refreshing" @click="refreshTools">刷新工具</el-button>
        </div>
      </div>

      <div class="debug-workspace">
        <aside class="control-column">
          <section class="control-card server-card">
            <div class="card-title muted-title">SERVER 信息</div>
            <dl>
              <div><dt>名称</dt><dd>{{ server.name }}</dd></div>
              <div><dt>Endpoint</dt><dd class="mono">{{ server.endpoint }}</dd></div>
              <div><dt>描述</dt><dd>{{ server.description || '暂无描述' }}</dd></div>
            </dl>
          </section>

          <section class="control-card">
            <div class="card-heading">
              <span>选择工具</span>
              <small>从列表选择</small>
            </div>
            <el-select
              v-model="selectedToolName"
              placeholder="选择工具，如 refund_order"
              filterable
              :disabled="submitting"
              class="tool-select"
              @change="handleToolChange"
            >
              <el-option
                v-for="tool in server.tools"
                :key="tool.id"
                :label="tool.name"
                :value="tool.name"
              />
            </el-select>
            <p v-if="selectedTool" class="tool-description">
              {{ selectedTool.description || '暂无工具描述' }}
            </p>
          </section>

          <section class="control-card parameter-card">
            <div class="card-heading">
              <span>输入参数</span>
              <small>动态表单</small>
            </div>
            <el-alert
              v-if="unsupportedFields.length > 0"
              type="warning"
              :closable="false"
              :title="`暂不支持：${unsupportedFields.map((field) => `${field.name}(${field.rawType})`).join('、')}`"
            />
            <el-form v-else-if="selectedTool" label-position="top" class="argument-form">
              <el-form-item
                v-for="field in schemaFields"
                :key="field.name"
                :label="field.label"
                :required="field.required"
                :error="fieldErrors[field.name]"
              >
                <el-input
                  v-if="field.type === 'string'"
                  v-model="currentValues[field.name] as string"
                  :placeholder="field.description || `请输入${field.label}`"
                  :disabled="submitting"
                  @input="delete fieldErrors[field.name]"
                />
                <el-input-number
                  v-else-if="field.type === 'number' || field.type === 'integer'"
                  v-model="currentValues[field.name] as number"
                  :precision="field.type === 'integer' ? 0 : undefined"
                  :placeholder="field.description || `请输入${field.label}`"
                  :disabled="submitting"
                  controls-position="right"
                  class="number-input"
                  @change="delete fieldErrors[field.name]"
                />
                <div v-if="field.description" class="field-description">{{ field.description }}</div>
              </el-form-item>
              <p v-if="schemaFields.length === 0" class="no-parameter">该工具无需参数</p>
            </el-form>
            <p v-else class="no-parameter">请先选择工具</p>
          </section>

          <el-button
            type="primary"
            class="execute-button btn-brand-gradient"
            :icon="VideoPlay"
            :loading="submitting"
            :disabled="!canCall"
            @click="callTool"
          >
            执行调用
          </el-button>
        </aside>

        <main class="result-panel">
          <div class="result-title">调用结果</div>
          <div v-if="currentResult" class="result-content">
            <div class="result-meta">
              <el-tag :type="currentResult.success ? 'success' : 'danger'">
                {{ currentResult.success ? '成功' : '失败' }}
              </el-tag>
              <span>{{ currentResult.toolName }}</span>
              <span>耗时 {{ currentResult.elapsedMs }} ms</span>
            </div>
            <pre>{{ displayResult(currentResult.result) }}</pre>
          </div>
          <div v-else class="empty-result">
            <el-icon :size="36"><VideoPlay /></el-icon>
            <p>填写参数后点击「执行调用」</p>
          </div>

          <section v-if="history.length > 0" class="history-section">
            <div class="history-title">最近调用</div>
            <el-table :data="history" size="small">
              <el-table-column prop="calledAt" label="时间" width="170" />
              <el-table-column prop="toolName" label="工具" min-width="150" />
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                    {{ row.success ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
              <el-table-column label="参数" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ JSON.stringify(row.arguments) }}</template>
              </el-table-column>
              <el-table-column prop="result" label="结果" min-width="200" show-overflow-tooltip />
            </el-table>
          </section>
        </main>
      </div>
    </template>
  </div>
</template>

<style scoped>
.debug-page {
  min-height: 100%;
}

.debug-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.back-button {
  margin: 0 0 8px -12px;
  color: var(--hify-text-secondary);
}

.debug-header h1 {
  margin: 0;
  font-size: 21px;
  color: var(--hify-text-primary);
}

.debug-header p {
  margin: 5px 0 0;
  color: var(--hify-text-tertiary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 32px;
}

.debug-workspace {
  display: grid;
  grid-template-columns: 376px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.control-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-card,
.result-panel {
  border: 1px solid var(--hify-border);
  border-radius: 10px;
  background: var(--hify-bg-container);
}

.control-card {
  padding: 16px;
}

.card-title,
.card-heading,
.result-title,
.history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.muted-title {
  color: var(--hify-text-tertiary);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.server-card dl {
  margin: 12px 0 0;
}

.server-card dl > div {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 0;
  margin-top: 8px;
  font-size: 13px;
}

.server-card dt {
  color: var(--hify-text-tertiary);
}

.server-card dd {
  margin: 0;
  color: var(--hify-text-primary);
  overflow-wrap: anywhere;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.card-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-heading small {
  font-weight: 400;
  color: var(--hify-text-tertiary);
}

.tool-select,
.number-input {
  width: 100%;
}

.tool-description,
.field-description,
.no-parameter {
  margin: 9px 0 0;
  color: var(--hify-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.argument-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.execute-button {
  width: 100%;
  height: 42px;
}

.result-panel {
  min-height: 416px;
  padding: 18px;
}

.result-title {
  margin-bottom: 16px;
}

.empty-result {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--hify-text-tertiary);
}

.empty-result p {
  margin-top: 12px;
  font-size: 13px;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--hify-text-tertiary);
  font-size: 13px;
}

.result-content pre {
  min-height: 250px;
  max-height: 430px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border-radius: 8px;
  background: var(--color-bg-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.6;
}

.history-section {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--hify-border);
}

.history-title {
  margin-bottom: 12px;
}

@media (max-width: 960px) {
  .debug-workspace {
    grid-template-columns: 1fr;
  }

  .debug-header {
    flex-direction: column;
  }

  .header-actions {
    padding-top: 0;
  }
}
</style>
