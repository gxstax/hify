<script setup lang="ts">
/**
 * Agent list page, wired to the real backend API.
 *
 * Form specifics: the model picker groups options by provider; temperature is
 * a 0-1 slider (always a concrete value — the "provider default" null case is
 * not reachable through the slider); tool binding lives in its own section
 * below a divider.
 */
import { computed, onMounted, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { PageQuery, TableColumn } from '@/types/components'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  createAgent,
  deleteAgent,
  getAgentDetail,
  getAgentList,
  updateAgent,
  updateAgentTools,
  type AgentPayload,
} from '@/api/agent'
import { listModelConfigs, type ModelConfigBrief } from '@/api/provider'

/** Selectable tool (MCP server). Empty until the MCP module ships a list API. */
interface ToolOption {
  id: number
  name: string
}

/** Dialog form shape (modelConfigId null until picked). */
type AgentForm = {
  name: string
  description?: string
  systemPrompt?: string
  modelConfigId?: number | null
  temperature?: number | null
  maxTokens?: number | null
  maxContextTurns?: number | null
  enabled: boolean
  toolIds: number[]
}

/** Slider default when creating / when the stored value is null. */
const DEFAULT_TEMPERATURE = 0.7

/* --- table ---------------------------------------------------------------- */
type TableApi = { refresh: () => Promise<void> }
const tableRef = ref<TableApi>()

const fetchPage = (query: PageQuery) => getAgentList(query.page, query.pageSize)

const columns: TableColumn[] = [
  { label: '名称', prop: 'name', minWidth: 150, showOverflowTooltip: true },
  { label: '关联模型', prop: 'modelName', minWidth: 160, showOverflowTooltip: true },
  { label: '工具数量', width: 90, slot: 'tools' },
  { label: 'Temperature', prop: 'temperature', width: 110 },
  { label: '状态', prop: 'enabled', width: 90, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 160 },
  { label: '操作', width: 130, slot: 'actions' },
]

/* --- model picker (grouped by provider) + tool options -------------------- */
const modelOptions = ref<ModelConfigBrief[]>([])
// Tool options stay empty until the MCP module exposes its server list API;
// bind the real source here afterwards (single change point).
const toolOptions = ref<ToolOption[]>([])

onMounted(async () => {
  try {
    modelOptions.value = await listModelConfigs()
  } catch {
    // interceptor toasted; the picker just stays empty
  }
})

const groupedModels = computed(() => {
  const groups = new Map<string, ModelConfigBrief[]>()
  for (const m of modelOptions.value) {
    const key = m.providerName || '未知供应商'
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(m)
  }
  return Array.from(groups, ([label, options]) => ({ label, options }))
})

/* --- dialog --------------------------------------------------------------- */
type DialogApi = {
  open: (data?: Partial<AgentForm>) => Promise<void>
  setSubmitting: (value: boolean) => void
}
const dialogVisible = ref(false)
const dialogRef = ref<DialogApi>()
const editingId = ref<number | null>(null)

/**
 * Bound model that is NOT in the available list (disabled model / disabled
 * provider / deleted). Without this stub the select would render a raw id;
 * it is shown disabled so the binding stays visible but cannot be re-picked
 * unless the model becomes available again.
 */
const missingModel = ref<{ id: number; label: string } | null>(null)

const dialogTitle = () => (editingId.value == null ? '新增 Agent' : '编辑 Agent')

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  missingModel.value = null
  dialogRef.value?.open({
    enabled: true,
    maxContextTurns: 10,
    temperature: DEFAULT_TEMPERATURE,
    toolIds: [],
  })
}

async function openEdit(row: { id: number }) {
  // system prompt and the rest are not in the list payload — load the detail
  const detail = await getAgentDetail(row.id)

  // Keep a disabled/unavailable bound model visible instead of a raw id
  missingModel.value = modelOptions.value.some((m) => m.id === detail.modelConfigId)
      ? null
      : {
          id: detail.modelConfigId,
          label: `${detail.modelName ?? `模型 #${detail.modelConfigId}`}（已禁用或不可用）`,
        }

  editingId.value = detail.id
  dialogRef.value?.open({
    name: detail.name,
    description: detail.description ?? '',
    systemPrompt: detail.systemPrompt ?? '',
    modelConfigId: detail.modelConfigId,
    temperature: detail.temperature ?? DEFAULT_TEMPERATURE,
    maxTokens: detail.maxTokens,
    maxContextTurns: detail.maxContextTurns,
    enabled: detail.enabled,
    toolIds: detail.toolIds ?? [],
  })
}

async function onSubmit(raw: Record<string, any>) {
  // Generic SFC events surface the constraint type; narrow to the form shape
  const form = raw as unknown as AgentForm
  const payload: AgentPayload = {
    name: form.name,
    description: form.description || undefined,
    systemPrompt: form.systemPrompt || undefined,
    modelConfigId: form.modelConfigId as number,
    temperature: form.temperature ?? DEFAULT_TEMPERATURE,
    maxTokens: form.maxTokens ?? null,
    maxContextTurns: form.maxContextTurns ?? null,
    enabled: form.enabled,
  }
  try {
    if (editingId.value == null) {
      // tools ride along in the create call (one transaction server-side)
      await createAgent({ ...payload, toolIds: form.toolIds })
      notifySuccess('新增成功')
    } else {
      await updateAgent(editingId.value, payload)
      // bindings are a separate endpoint; idempotent full replacement
      await updateAgentTools(editingId.value, form.toolIds)
      notifySuccess('保存成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // keep the dialog open for correction; toast comes from the interceptor
    dialogRef.value?.setSubmitting(false)
  }
}

/* --- delete --------------------------------------------------------------- */
const removeItem = useConfirm(
  { message: '确认删除该 Agent？其工具绑定将一并清除。', successMessage: '删除成功' },
  async (id: number) => {
    await deleteAgent(id)
    tableRef.value?.refresh()
  },
)
</script>

<template>
  <div class="page">
    <PageHeader title="Agent 管理" description="创建 Agent，绑定模型与 MCP 工具，配置 System Prompt">
      <template #actions>
        <el-button type="primary" class="btn-brand-gradient" :icon="Plus" @click="openCreate">
          新增 Agent
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchPage">
        <!-- tool count -->
        <template #tools="{ row }">
          {{ row.toolCount ?? 0 }}
        </template>

        <!-- enabled -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- actions -->
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
          <el-input v-model="form.name" placeholder="例如：客服助手" clearable />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="用途说明" />
        </el-form-item>
        <el-form-item label="模型" prop="modelConfigId">
          <el-select
            v-model="form.modelConfigId"
            placeholder="请选择模型"
            filterable
            style="width: 100%"
          >
            <el-option-group v-for="g in groupedModels" :key="g.label" :label="g.label">
              <el-option v-for="m in g.options" :key="m.id" :label="m.name" :value="m.id">
                <span class="opt-name">{{ m.name }}</span>
                <span class="opt-id">{{ m.modelId }}</span>
              </el-option>
            </el-option-group>
            <!-- Bound model that is no longer available: visible but not pickable -->
            <el-option
              v-if="missingModel"
              :key="`missing-${missingModel.id}`"
              :label="missingModel.label"
              :value="missingModel.id"
              disabled
            />
            <template #empty>
              <p class="select-empty">
                暂无可用模型，请先在「模型提供商管理」中添加并启用模型
              </p>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="定义 Agent 的角色、规则与输出格式"
          />
        </el-form-item>
        <el-form-item label="Temperature" prop="temperature">
          <el-slider
            v-model="form.temperature"
            :min="0"
            :max="1"
            :step="0.1"
            show-input
            :show-input-controls="false"
          />
        </el-form-item>
        <el-form-item label="Max Tokens" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :step="256" placeholder="默认" />
        </el-form-item>
        <el-form-item label="上下文轮数" prop="maxContextTurns">
          <el-input-number v-model="form.maxContextTurns" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-divider content-position="left">工具绑定</el-divider>
        <el-form-item label="MCP 工具" prop="toolIds">
          <div class="tool-bind">
            <el-checkbox-group v-if="toolOptions.length" v-model="form.toolIds">
              <el-checkbox v-for="t in toolOptions" :key="t.id" :value="t.id">
                {{ t.name }}
              </el-checkbox>
            </el-checkbox-group>
            <el-empty
              v-else
              description="暂无可绑定工具（MCP 模块接入后可用）"
              :image-size="60"
            />
            <!-- Existing bindings stay visible even without options loaded -->
            <div v-if="form.toolIds?.length" class="bound-hint">
              已绑定：#{{ (form.toolIds as number[]).join('、#') }}
            </div>
          </div>
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
/* Row actions: flex container so the text buttons share a tight,
   whitespace-free gap. */
.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.tool-bind {
  width: 100%;
}

/* Model option: display name left, raw model id right in muted text */
.opt-name {
  float: left;
}

.opt-id {
  float: right;
  margin-left: 16px;
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.select-empty {
  margin: 8px 0;
  padding: 0 12px;
  font-size: 12px;
  color: var(--hify-text-tertiary);
  text-align: center;
}

.bound-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hify-text-tertiary);
}
</style>
