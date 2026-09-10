<script setup lang="ts">
/**
 * Provider list page, wired to the real backend API.
 * List rows carry live health probe data (from provider_health via the list
 * endpoint) and the enabled-model count; model detail is loaded lazily into
 * the popover on demand.
 */
import { ref } from 'vue'
import { Loading, Plus } from '@element-plus/icons-vue'
import type { PageQuery, TableColumn } from '@/types/components'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifyError, notifySuccess } from '@/utils/notify'
import {
  PROVIDER_TYPES,
  createProvider,
  deleteProvider,
  getProviderDetail,
  getProviderList,
  testConnection,
  updateProvider,
  type HealthStatus,
  type ModelConfigItem,
  type ProviderItem,
  type ProviderType,
} from '@/api/provider'

/** Form payload of the dialog (create + edit). apiKey is write-only. */
type ProviderForm = {
  name: string
  type: ProviderType | ''
  apiKey?: string
  baseUrl?: string
}

/** Configured keep-the-stored-key semantics: blank = unchanged on update. */
function buildPayload(form: ProviderForm) {
  return {
    name: form.name,
    type: form.type as ProviderType,
    baseUrl: form.baseUrl ?? '',
    apiKey: form.apiKey?.trim() || undefined,
  }
}

/* --- table ---------------------------------------------------------------- */
type TableApi = { refresh: () => Promise<void> }
const tableRef = ref<TableApi>()

/** HifyTable api prop: paging comes from the component, health stays fresh. */
const fetchPage = (query: PageQuery) => getProviderList(query.page, query.pageSize)

const healthMeta: Record<HealthStatus, { tag: 'success' | 'warning' | 'danger' | 'info'; label: string }> = {
  UP: { tag: 'success', label: 'UP' },
  DOWN: { tag: 'danger', label: 'DOWN' },
  DEGRADED: { tag: 'warning', label: 'DEGRADED' },
  UNKNOWN: { tag: 'info', label: 'UNKNOWN' },
}

/** Slot rows are untyped; resolve the health badge with a safe fallback. */
function healthOf(status: unknown) {
  return healthMeta[(status as HealthStatus) ?? 'UNKNOWN'] ?? healthMeta.UNKNOWN
}

/**
 * Column layout: every column is always shown. Fixed columns keep compact
 * widths; flexible columns use min-width so they absorb leftover space and
 * never collapse below readable width. When the viewport is too narrow for
 * the sum of all columns, el-table scrolls horizontally instead of squeezing
 * content out of the cell.
 */
const columns: TableColumn[] = [
  { label: '名称', prop: 'name', minWidth: 150, showOverflowTooltip: true },
  { label: '类型', prop: 'type', width: 140, showOverflowTooltip: true },
  { label: 'Base URL', prop: 'baseUrl', minWidth: 200, showOverflowTooltip: true },
  { label: '启用状态', prop: 'enabled', width: 90, slot: 'status' },
  { label: '健康状态', width: 128, slot: 'health' },
  { label: '模型数', width: 84, slot: 'models' },
  { label: '创建时间', prop: 'createdAt', width: 160 },
  { label: '操作', width: 168, slot: 'actions' },
]

/* --- model popover --------------------------------------------------------- */
const modelsLoadingId = ref<number | null>(null)
const modelsByProvider = ref<Record<number, ModelConfigItem[]>>({})

async function showModels(row: ProviderItem) {
  if (modelsByProvider.value[row.id]) {
    return // already loaded; re-open is instant
  }
  modelsLoadingId.value = row.id
  try {
    const detail = await getProviderDetail(row.id)
    modelsByProvider.value = { ...modelsByProvider.value, [row.id]: detail.models }
  } catch {
    // interceptor toasted
  } finally {
    modelsLoadingId.value = null
  }
}

/* --- dialog --------------------------------------------------------------- */
type DialogApi = {
  open: (data?: Partial<ProviderForm>) => Promise<void>
  setSubmitting: (value: boolean) => void
}
const dialogVisible = ref(false)
const dialogRef = ref<DialogApi>()
const editingId = ref<number | null>(null)

const dialogTitle = () => (editingId.value == null ? '新增提供商' : '编辑提供商')

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 API 基础地址', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  dialogRef.value?.open()
}

function openEdit(row: ProviderItem) {
  editingId.value = row.id
  dialogRef.value?.open({
    name: row.name,
    type: row.type,
    apiKey: undefined, // never echoed back; blank keeps the stored key
    baseUrl: row.baseUrl,
  })
}

async function onSubmit(raw: Record<string, any>) {
  // Generic SFC events surface the constraint type; narrow to the form shape
  const form = raw as unknown as ProviderForm
  try {
    if (editingId.value == null) {
      await createProvider(buildPayload(form))
      notifySuccess('新增成功')
    } else {
      await updateProvider(editingId.value, buildPayload(form))
      notifySuccess('保存成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // keep the dialog open for correction; toast comes from the interceptor
    dialogRef.value?.setSubmitting(false)
  }
}

/* --- delete & test --------------------------------------------------------- */
const removeItem = useConfirm(
  { message: '确认删除该提供商？其模型配置将一并删除。', successMessage: '删除成功' },
  async (id: number) => {
    await deleteProvider(id)
    tableRef.value?.refresh()
  },
)

const testingId = ref<number | null>(null)

async function runTest(row: ProviderItem) {
  testingId.value = row.id
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      notifySuccess(`连接成功：${result.modelCount} 个模型，${result.latencyMs}ms`)
    } else {
      notifyError(`连接失败：${result.errorMessage ?? '未知错误'}`)
    }
  } catch {
    // interceptor toasted
  } finally {
    testingId.value = null
  }
}
</script>

<template>
  <div class="page">
    <PageHeader title="模型提供商管理" description="配置 OpenAI、Anthropic、Ollama 及 OpenAI 兼容网关等模型接入">
      <template #actions>
        <el-button type="primary" class="btn-brand-gradient" :icon="Plus" @click="openCreate">
          新增提供商
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchPage">
        <!-- enabled -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- health: probe status + latest latency -->
        <template #health="{ row }">
          <el-tag :type="healthOf(row.healthStatus).tag" disable-transitions>
            {{ healthOf(row.healthStatus).label }}
          </el-tag>
          <span v-if="row.healthLatencyMs != null" class="health-latency">
            {{ row.healthLatencyMs }}ms
          </span>
        </template>

        <!-- models: count, click to expand the model list -->
        <template #models="{ row }">
          <el-popover trigger="click" :width="320" placement="bottom-start">
            <template #reference>
              <span class="model-count" @click="showModels(row)">
                <el-icon v-if="modelsLoadingId === row.id" class="is-loading"><Loading /></el-icon>
                <template v-else>{{ row.enabledModelCount ?? 0 }}</template>
              </span>
            </template>
            <div class="models-pop">
              <el-empty
                v-if="!modelsByProvider[row.id]?.length"
                description="暂无模型"
                :image-size="50"
              />
              <div v-for="m in modelsByProvider[row.id]" :key="m.id" class="models-pop-row">
                <span class="models-pop-name">{{ m.name }}</span>
                <span class="models-pop-id">{{ m.modelId }}</span>
                <el-tag size="small" :type="m.enabled ? 'success' : 'info'" disable-transitions>
                  {{ m.enabled ? '启用' : '禁用' }}
                </el-tag>
              </div>
            </div>
          </el-popover>
        </template>

        <!-- actions: edit / test / delete -->
        <template #actions="{ row }">
          <div class="cell-actions">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text :loading="testingId === row.id" @click="runTest(row)">测试</el-button>
            <el-button text type="danger" @click="removeItem(row.id)">删除</el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog
      v-model="dialogVisible"
      :title="dialogTitle()"
      :rules="formRules"
      ref="dialogRef"
      @submit="onSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：OpenAI 主接入" clearable />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择提供商类型" style="width: 100%">
            <el-option v-for="t in PROVIDER_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <!-- Write-only: never prefilled; blank keeps the stored key on edit -->
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId == null ? 'OLLAMA 等本地服务可留空' : '留空则保持原密钥不变'"
          />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
/* Row actions: flex container so the text buttons share a tight,
   whitespace-free gap. nowrap keeps the group from wrapping inside the cell. */
.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.health-latency {
  margin-left: 6px;
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.model-count {
  color: var(--hify-primary-500);
  cursor: pointer;
  user-select: none;
}

.model-count:hover {
  text-decoration: underline;
}

.models-pop-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.models-pop-name {
  color: var(--hify-text-primary);
}

.models-pop-id {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--hify-text-tertiary);
  font-size: 12px;
}
</style>
