<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { PageQuery, TableColumn } from '@/types/components'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  deleteWorkflow,
  getWorkflow,
  getWorkflowList,
  publishWorkflow,
  workflowStatusOf,
  type WorkflowDetail,
  type WorkflowStatus,
} from '@/api/workflow'

type TableApi = { refresh: () => Promise<void> }

const router = useRouter()
const tableRef = ref<TableApi>()
const keyword = ref('')
const appliedKeyword = ref('')
const status = ref<WorkflowStatus | ''>('')
const appliedStatus = ref<WorkflowStatus | ''>('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const workflowDetail = ref<WorkflowDetail>()

const fetchPage = computed(() => {
  const currentKeyword = appliedKeyword.value
  const currentStatus = appliedStatus.value || undefined
  return (query: PageQuery) => getWorkflowList(
    query.page,
    query.pageSize,
    currentKeyword,
    currentStatus,
  )
})

const columns: TableColumn[] = [
  { label: '名称', prop: 'name', minWidth: 180, showOverflowTooltip: true },
  { label: '描述', minWidth: 240, slot: 'description', showOverflowTooltip: true },
  { label: '状态', width: 120, slot: 'status' },
  { label: '创建时间', width: 180, slot: 'createdAt' },
  { label: '操作', width: 190, slot: 'actions' },
]

function applySearch() {
  const nextKeyword = keyword.value.trim()
  const nextStatus = status.value
  if (nextKeyword === appliedKeyword.value && nextStatus === appliedStatus.value) {
    tableRef.value?.refresh()
    return
  }
  appliedKeyword.value = nextKeyword
  appliedStatus.value = nextStatus
}

function formatDateTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function viewWorkflow(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  workflowDetail.value = undefined
  try {
    workflowDetail.value = await getWorkflow(id)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function formatDefinition(detail: WorkflowDetail): string {
  return JSON.stringify({
    version: detail.version,
    enabled: detail.enabled,
    nodes: detail.nodes,
    edges: detail.edges,
  }, null, 2)
}

async function publishItem(id: number) {
  try {
    await ElMessageBox.confirm(
      '确认发布该工作流？发布后将立即启用。',
      '发布工作流',
      {
        type: 'warning',
        confirmButtonText: '发布',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    await publishWorkflow(id)
    notifySuccess('发布成功')
    await tableRef.value?.refresh()
  } catch {
    // Request interceptor shows the error.
  }
}

const removeItem = useConfirm(
  { message: '确认删除该工作流？删除后无法恢复。', successMessage: '删除成功' },
  async (id: number) => {
    await deleteWorkflow(id)
    tableRef.value?.refresh()
  },
)
</script>

<template>
  <div class="page">
    <PageHeader title="工作流管理" description="通过 JSON 定义工作流节点、分支与执行顺序">
      <template #actions>
        <el-button
          type="primary"
          class="btn-brand-gradient"
          :icon="Plus"
          @click="router.push('/workflows/create')"
        >
          新建工作流
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="workflow-toolbar">
        <el-input
          v-model="keyword"
          placeholder="按名称或描述搜索"
          clearable
          class="workflow-search"
          @keyup.enter="applySearch"
          @clear="applySearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="status" placeholder="全部状态" clearable class="status-select">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
        <el-button type="primary" @click="applySearch">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchPage">
        <template #description="{ row }">
          <span :class="{ 'empty-description': !row.description }">
            {{ row.description || '暂无描述' }}
          </span>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>
            {{ workflowStatusOf(row.enabled) }}
          </el-tag>
        </template>
        <template #createdAt="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        <template #actions="{ row }">
          <el-button text type="primary" @click="viewWorkflow(row.id)">查看</el-button>
          <el-button
            v-if="!row.enabled"
            text
            type="primary"
            @click="publishItem(row.id)"
          >
            发布
          </el-button>
          <el-button text type="danger" @click="removeItem(row.id)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog v-model="detailVisible" title="工作流详情" width="min(760px, 92vw)">
      <div v-loading="detailLoading" class="workflow-detail">
        <template v-if="workflowDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="名称">{{ workflowDetail.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="workflowDetail.enabled ? 'success' : 'info'">
                {{ workflowStatusOf(workflowDetail.enabled) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              {{ workflowDetail.description || '暂无描述' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDateTime(workflowDetail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDateTime(workflowDetail.updatedAt) }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="definition-title">工作流配置</div>
          <pre class="definition-preview">{{ formatDefinition(workflowDetail) }}</pre>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--hify-space-element);
}

.workflow-search {
  width: min(360px, 100%);
}

.status-select {
  width: 140px;
}

.empty-description {
  color: var(--hify-text-tertiary);
}

.workflow-detail {
  min-height: 120px;
}

.definition-title {
  margin: 20px 0 8px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.definition-preview {
  max-height: 440px;
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-sm);
  background: var(--hify-bg-subtle);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 575.98px) {
  .workflow-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .workflow-search {
    flex: 1 1 100%;
    width: auto;
  }
}
</style>
