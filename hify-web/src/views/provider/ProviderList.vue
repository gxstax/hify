<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getHealth } from '@/api/health'

const backendConnected = ref(false)

onMounted(async () => {
  try {
    await getHealth()
    backendConnected.value = true
  } catch {
    backendConnected.value = false
  }
})
</script>

<template>
  <div>
    <h2>模型提供商管理</h2>
    <p :class="backendConnected ? 'status-ok' : 'status-fail'">
      {{ backendConnected ? '后端已连接：Hify is running' : '后端未连接' }}
    </p>
  </div>
</template>

<style scoped>
.status-ok {
  color: var(--el-color-success);
}

.status-fail {
  color: var(--el-color-danger);
}
</style>
