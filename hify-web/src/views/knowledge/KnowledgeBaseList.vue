<script setup lang="ts">
import { computed, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import type { PageQuery, TableColumn } from '@/types/components'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import HifyTable from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseList,
  updateKnowledgeBase,
  type KnowledgeBaseItem,
} from '@/api/knowledge'

type KnowledgeBaseForm = {
  name: string
  description?: string
}

type TableApi = { refresh: () => Promise<void> }
type DialogApi = {
  open: (data?: Partial<KnowledgeBaseForm>) => Promise<void>
  setSubmitting: (value: boolean) => void
}

const tableRef = ref<TableApi>()
const searchName = ref('')
const appliedName = ref('')

const fetchPage = computed(() => {
  const name = appliedName.value
  return (query: PageQuery) => getKnowledgeBaseList(query.page, query.pageSize, name)
})

const columns: TableColumn[] = [
  { label: '名称', minWidth: 180, slot: 'name', showOverflowTooltip: true },
  { label: '描述', minWidth: 240, slot: 'description', showOverflowTooltip: true },
  { label: '状态', width: 90, slot: 'status' },
  { label: '文档数量', width: 100, slot: 'documents' },
  { label: '创建时间', width: 168, slot: 'createdAt' },
  { label: '操作', width: 130, slot: 'actions' },
]

function applySearch() {
  const nextName = searchName.value.trim()
  if (nextName === appliedName.value) {
    tableRef.value?.refresh()
    return
  }
  appliedName.value = nextName
}

const dialogVisible = ref(false)
const dialogRef = ref<DialogApi>()
const editingItem = ref<KnowledgeBaseItem | null>(null)
const dialogTitle = computed(() => editingItem.value ? '编辑知识库' : '新建知识库')

const formRules = {
  name: [
    { required: true, whitespace: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 100, message: '名称长度不能超过 100 个字符', trigger: 'blur' },
  ],
  description: [
    { max: 500, message: '描述长度不能超过 500 个字符', trigger: 'blur' },
  ],
}

function openCreate() {
  editingItem.value = null
  dialogRef.value?.open({ name: '', description: '' })
}

function openEdit(row: KnowledgeBaseItem) {
  editingItem.value = row
  dialogRef.value?.open({
    name: row.name,
    description: row.description ?? '',
  })
}

async function onSubmit(raw: Record<string, any>) {
  const form = raw as unknown as KnowledgeBaseForm
  const payload = {
    name: form.name.trim(),
    description: form.description?.trim() || '',
  }
  try {
    if (editingItem.value) {
      await updateKnowledgeBase(editingItem.value.id, {
        ...payload,
        enabled: editingItem.value.enabled,
      })
      notifySuccess('保存成功')
    } else {
      await createKnowledgeBase(payload)
      notifySuccess('新建成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    dialogRef.value?.setSubmitting(false)
  }
}

const removeItem = useConfirm(
  {
    message: '确认删除该知识库？关联文档及其向量分块也会被删除。',
    successMessage: '删除成功',
  },
  async (id: number) => {
    await deleteKnowledgeBase(id)
    tableRef.value?.refresh()
  },
)

function formatDateTime(value: string): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="page">
    <PageHeader title="知识库管理" description="管理用于 Agent 检索增强的知识库与文档">
      <template #actions>
        <el-button type="primary" class="btn-brand-gradient" :icon="Plus" @click="openCreate">
          新建知识库
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="knowledge-toolbar">
        <el-input
          v-model="searchName"
          placeholder="按名称搜索知识库"
          clearable
          class="knowledge-search"
          @keyup.enter="applySearch"
          @clear="applySearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="applySearch">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchPage">
        <template #name="{ row }">
          <router-link
            class="knowledge-name"
            :to="`/knowledge-bases/${row.id}/documents`"
          >
            {{ row.name }}
          </router-link>
        </template>

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

        <template #documents="{ row }">
          {{ row.documentCount ?? 0 }}
        </template>

        <template #createdAt="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>

        <template #actions="{ row }">
          <div class="cell-actions">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" @click="removeItem(row.id)">删除</el-button>
          </div>
        </template>

        <template #empty>
          <el-empty
            :description="appliedName ? '未找到匹配的知识库' : '暂无知识库，请点击右上角新建'"
            :image-size="90"
          />
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      :title="dialogTitle"
      :rules="formRules"
      @submit="onSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="100"
            show-word-limit
            clearable
            placeholder="请输入知识库名称"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入知识库用途说明（可选）"
          />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.knowledge-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--hify-space-element);
}

.knowledge-search {
  width: min(360px, 100%);
}

.knowledge-name {
  color: var(--hify-primary-500);
  font-weight: 500;
  text-decoration: none;
}

.knowledge-name:hover {
  text-decoration: underline;
}

.empty-description {
  color: var(--hify-text-tertiary);
}

.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

@media (max-width: 575.98px) {
  .knowledge-toolbar {
    align-items: stretch;
  }

  .knowledge-search {
    flex: 1;
    width: auto;
  }
}
</style>
