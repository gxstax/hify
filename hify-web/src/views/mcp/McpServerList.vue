<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { PageQuery, TableColumn } from '@/types/components'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess, notifyWarning } from '@/utils/notify'
import {
  createMcpServer,
  deleteMcpServer,
  getMcpServerList,
  testMcpServer,
  updateMcpServer,
  type McpServerListItem,
  type McpServerPayload,
} from '@/api/mcp'

type TableApi = { refresh: () => Promise<void> }

const router = useRouter()
const tableRef = ref<TableApi>()
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const form = reactive<McpServerPayload>({
  name: '',
  endpoint: '',
  description: '',
  enabled: true,
})

const dialogTitle = computed(() => editingId.value ? '编辑 MCP Server' : '新增 MCP Server')
const rules: FormRules<McpServerPayload> = {
  name: [{ required: true, message: '请输入 Server 名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 Endpoint', trigger: 'blur' }],
}

const columns: TableColumn[] = [
  { label: '名称', prop: 'name', minWidth: 180, showOverflowTooltip: true },
  { label: 'Endpoint', prop: 'endpoint', minWidth: 300, showOverflowTooltip: true },
  { label: '描述', minWidth: 240, slot: 'description', showOverflowTooltip: true },
  { label: '状态', width: 100, slot: 'status' },
  { label: '操作', width: 280, fixed: 'right', slot: 'actions' },
]

function fetchPage(query: PageQuery) {
  return getMcpServerList(query.page, query.pageSize)
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    endpoint: '',
    description: '',
    enabled: true,
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: McpServerListItem) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    endpoint: row.endpoint,
    description: row.description,
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (submitting.value) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await updateMcpServer(editingId.value, { ...form })
      notifySuccess('更新成功')
    } else {
      await createMcpServer({ ...form })
      notifySuccess('新增成功')
    }
    dialogVisible.value = false
    await tableRef.value?.refresh()
  } catch {
    // Request interceptor shows the error.
  } finally {
    submitting.value = false
  }
}

async function testServer(row: McpServerListItem) {
  try {
    const result = await testMcpServer(row.id)
    if (result.success) {
      notifySuccess(`连接成功，同步 ${result.syncedToolCount} 个工具，耗时 ${result.latencyMs} ms`)
      await tableRef.value?.refresh()
    } else {
      notifyWarning(result.errorMessage || '连接失败')
    }
  } catch {
    // Request interceptor shows the error.
  }
}

const removeServer = useConfirm(
  { message: '确认删除该 MCP Server？删除后无法恢复。', successMessage: '删除成功' },
  async (id: number) => {
    await deleteMcpServer(id)
    await tableRef.value?.refresh()
  },
)
</script>

<template>
  <div class="page">
    <PageHeader
      title="MCP 工具服务"
      description="管理 MCP Server，让 Agent 能调用外部系统（订单、物流、工单等）"
    >
      <template #actions>
        <el-button type="primary" class="btn-brand-gradient" :icon="Plus" @click="openCreate">
          新增 Server
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card server-table-card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchPage">
        <template #description="{ row }">
          <span :class="{ 'empty-description': !row.description }">
            {{ row.description || '暂无描述' }}
          </span>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="warning" @click="testServer(row)">测试</el-button>
            <el-button text type="success" @click="router.push(`/mcp-servers/${row.id}`)">
              调试
            </el-button>
            <el-button text type="danger" @click="removeServer(row.id)">删除</el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="例如：财务-退款MCP" />
        </el-form-item>
        <el-form-item label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" maxlength="500" placeholder="例如：http://localhost:9001/mcp" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明该 Server 提供的工具能力"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.server-table-card {
  margin-top: 20px;
}

.empty-description {
  color: var(--hify-text-tertiary);
}

.action-buttons {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 0;
  white-space: nowrap;
}

.action-buttons :deep(.el-button) {
  padding-right: 5px;
  padding-left: 5px;
}

.action-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
