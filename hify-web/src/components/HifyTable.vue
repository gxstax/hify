<script setup lang="ts" generic="T">
/**
 * Generic paged table for list pages.
 *
 * Manages loading / paging params / data fetching internally and loads on
 * mount. Call `refresh()` when the query conditions change (close over them
 * in the api function) or after create/update/delete.
 *
 * ```
 * <HifyTable ref="tableRef" :columns="columns" :api="api">
 *   <template #status="{ row }"><el-tag>...</el-tag></template>
 *   <template #actions="{ row }"><el-button>编辑</el-button></template>
 * </HifyTable>
 * ```
 *
 * columns: [{ label: '名称', prop: 'name' }, { label: '操作', slot: 'actions' }]
 */
import { ref, watch } from 'vue'
import type { PageApi, TableColumn } from '@/types/components'

interface Props {
  columns: TableColumn[]
  /** Paged list API; receives { page, pageSize } (page starts at 1). */
  api: PageApi<T>
  /** Whether to show the pager at the bottom (default true). */
  pagination?: boolean
}

const props = withDefaults(defineProps<Props>(), { pagination: true })

const rows = ref<T[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await props.api({ page: page.value, pageSize: pageSize.value })
    rows.value = res.list
    total.value = res.total
  } catch {
    // error toast already shown by the axios interceptor
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// Reload when the page size changed or when a new api instance is provided
function handleSizeChange() {
  page.value = 1
  load()
}

function handleCurrentChange() {
  load()
}

watch(() => props.api, () => {
  page.value = 1
  load()
}, { immediate: true })

/** Reload current page (or reset to page 1 with the new conditions). */
defineExpose({ refresh: load })
</script>

<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="rows"
      :row-style="{ height: '52px' }"
      class="hify-table-body"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop ?? col.slot"
        :label="col.label"
        :prop="col.prop"
        :width="col.width"
        :show-overflow-tooltip="col.showOverflowTooltip"
      >
        <!-- Custom render column via named slot; otherwise Element renders prop -->
        <template v-if="col.slot" #default="scope">
          <slot :name="col.slot" :row="scope.row" />
        </template>
      </el-table-column>

      <template #empty>
        <slot name="empty">
          <el-empty description="暂无数据" :image-size="90" />
        </slot>
      </template>
    </el-table>

    <div v-if="pagination" class="hify-table-pager">
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
</template>

<style scoped>
.hify-table-body {
  width: 100%;
}

.hify-table-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--hify-space-element);
  padding-top: 12px;
  border-top: 1px solid var(--hify-border);
}
</style>
