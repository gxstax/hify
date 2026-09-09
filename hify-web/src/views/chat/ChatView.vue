<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div class="page chat-page">
    <PageHeader title="对话" description="选择 Agent，开始多轮对话（支持流式输出）" />

    <div class="page-card chat-card">
      <!-- Conversation workspace lands here -->
      <el-empty description="对话工作台开发中" />
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  box-sizing: border-box;
}

.chat-card {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
