<script setup lang="ts">
/**
 * Provider list page (mock data — swap fetch/add/update/remove with the real
 * api/provider.ts functions once the backend endpoints land).
 */
import { computed, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { PageResult } from '@/types/api'
import type { PageQuery, TableColumn } from '@/types/components'
import { useMediaQuery } from '@/composables/useMediaQuery'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'

type ProviderType = 'OPENAI' | 'ANTHROPIC' | 'OLLAMA' | 'OPENAI_COMPATIBLE'

/** Auth payload stored in provider.auth_config; shape depends on type. */
type AuthConfig = {
  apiKey?: string
  [key: string]: unknown
}

/** Row shape shown in the table (mirrors the backend provider table). */
type ProviderRecord = {
  id: number
  name: string
  type: ProviderType
  baseUrl: string
  authConfig?: AuthConfig | null
  enabled: boolean
  createdAt: string
}

/** Form payload of the dialog (create + edit). */
type ProviderForm = {
  name: string
  type: ProviderType | ''
  apiKey?: string
  baseUrl?: string
}

const PROVIDER_TYPES: ProviderType[] = ['OPENAI', 'ANTHROPIC', 'OLLAMA', 'OPENAI_COMPATIBLE']

/* --- mock store ----------------------------------------------------------- */
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

let mockProviders: ProviderRecord[] = [
  { id: 1, name: 'OpenAI 主接入', type: 'OPENAI', baseUrl: 'https://api.openai.com/v1', authConfig: { apiKey: 'sk-xxxx' }, enabled: true, createdAt: '2026-08-12 10:24:00' },
  { id: 2, name: 'Anthropic Claude', type: 'ANTHROPIC', baseUrl: 'https://api.anthropic.com', authConfig: { apiKey: 'sk-ant-xxxx' }, enabled: true, createdAt: '2026-08-15 16:02:00' },
  { id: 3, name: '公司兼容网关', type: 'OPENAI_COMPATIBLE', baseUrl: 'https://gateway.internal.local/v1', authConfig: { apiKey: 'gw-xxxx' }, enabled: true, createdAt: '2026-08-20 09:41:00' },
  { id: 4, name: '本地 Ollama', type: 'OLLAMA', baseUrl: 'http://localhost:11434', authConfig: null, enabled: true, createdAt: '2026-08-22 20:10:00' },
  { id: 5, name: 'OpenAI 备用 Key', type: 'OPENAI', baseUrl: 'https://api.openai.com/v1', authConfig: { apiKey: 'sk-yyyy' }, enabled: false, createdAt: '2026-09-01 11:30:00' },
]

async function fetchProviders(query: PageQuery): Promise<PageResult<ProviderRecord>> {
  await delay(300) // simulate network latency so loading state is visible
  const start = (query.page - 1) * query.pageSize
  const list = mockProviders.slice(start, start + query.pageSize)
  return { list, total: mockProviders.length, page: query.page, pageSize: query.pageSize }
}

/* --- table ---------------------------------------------------------------- */
// Generic SFCs cannot be referenced via InstanceType<typeof X<T>>; declare the
// exposed surface structurally instead.
type TableApi = { refresh: () => Promise<void> }
const tableRef = ref<TableApi>()

/** Full column set; narrow screens drop Base URL and createdAt (see below). */
const fullColumns: TableColumn[] = [
  { label: '名称', prop: 'name', showOverflowTooltip: true },
  { label: '类型', prop: 'type', width: 180, showOverflowTooltip: true },
  { label: 'Base URL', prop: 'baseUrl', showOverflowTooltip: true },
  { label: '状态', prop: 'enabled', width: 90, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 170 },
  { label: '操作', width: 140, slot: 'actions' },
]

// Same breakpoint as the auto-collapsed sidebar: below 1200px keep only the
// essential columns (name / type / status / actions).
const isNarrow = useMediaQuery('(max-width: 1199.98px)')
const columns = computed(() =>
  isNarrow.value
    ? fullColumns.filter((c) => c.prop !== 'baseUrl' && c.prop !== 'createdAt')
    : fullColumns,
)

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
}

function openCreate() {
  editingId.value = null
  dialogRef.value?.open()
}

function openEdit(row: ProviderRecord) {
  editingId.value = row.id
  dialogRef.value?.open({
    name: row.name,
    type: row.type,
    apiKey: row.authConfig?.apiKey,
    baseUrl: row.baseUrl,
  })
}

/** Build the auth_config JSON persisted on the provider row. */
function buildAuthConfig(type: ProviderType | '', apiKey?: string): AuthConfig | null {
  if (type === 'OLLAMA') {
    return null // local model server, no auth
  }
  const auth: AuthConfig = {}
  if (apiKey) {
    auth.apiKey = apiKey
  }
  return auth
}

async function onSubmit(raw: Record<string, any>) {
  // Generic SFC events surface the constraint type; narrow to the form shape
  const form = raw as unknown as ProviderForm
  const authConfig = buildAuthConfig(form.type, form.apiKey)
  if (editingId.value == null) {
    mockProviders = [
      ...mockProviders,
      {
        id: Math.max(...mockProviders.map((p) => p.id)) + 1,
        name: form.name,
        type: form.type as ProviderType,
        baseUrl: form.baseUrl ?? '',
        authConfig,
        enabled: true,
        createdAt: '2026-09-09 10:00:00',
      },
    ]
    notifySuccess('新增成功')
  } else {
    mockProviders = mockProviders.map((p) =>
      p.id === editingId.value
        ? { ...p, name: form.name, type: form.type as ProviderType, baseUrl: form.baseUrl ?? '', authConfig }
        : p,
    )
    notifySuccess('保存成功')
  }
  dialogVisible.value = false // success path closes the dialog
  tableRef.value?.refresh()
}

/* --- delete --------------------------------------------------------------- */
const removeItem = useConfirm(
  { message: '确认删除该提供商？删除后相关模型配置将不可用。', successMessage: '删除成功' },
  async (id: number) => {
    await delay(200)
    mockProviders = mockProviders.filter((p) => p.id !== id)
    tableRef.value?.refresh()
  },
)
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
      <HifyTable ref="tableRef" :columns="columns" :api="fetchProviders">
        <!-- status -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- actions: text buttons, blue edit / red delete -->
        <template #actions="{ row }">
          <div class="cell-actions">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
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
          <!-- show-password renders Element Plus' built-in visibility toggle -->
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            placeholder="留空则不配置密钥（如本地 Ollama）"
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
/* Row actions: flex container so the two text buttons share a tight,
   whitespace-free gap (inline buttons would also count template whitespace
   between them). nowrap keeps the pair from wrapping inside the cell. */
.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}
</style>
