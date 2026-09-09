<script setup lang="ts">
/**
 * Page title area: title + description on the left, action buttons on the
 * right. Usage:
 *   <PageHeader title="模型提供商管理" description="...">
 *     <template #actions>
 *       <el-button type="primary" class="btn-brand-gradient">新建</el-button>
 *     </template>
 *   </PageHeader>
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header-text">
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--hify-space-element);
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--hify-text-primary);
}

.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--hify-text-tertiary);
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
