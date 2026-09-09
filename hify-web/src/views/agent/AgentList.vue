<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div class="page">
    <PageHeader title="Agent 管理" description="创建 Agent，绑定模型与 MCP 工具，配置 System Prompt">
      <template #actions>
        <el-button type="primary" class="btn-brand-gradient">新建 Agent</el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <!-- Agent table lands here -->
      <el-empty description="暂无 Agent，点击右上角新建" />
    </div>
  </div>
</template>
