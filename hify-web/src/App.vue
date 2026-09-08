<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <!-- App shell: sidebar menu (module entries) + content area -->
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <el-menu router :default-active="route.path" class="app-menu">
        <el-menu-item index="/providers">模型提供商管理</el-menu-item>
        <el-menu-item index="/agents">Agent 管理</el-menu-item>
        <el-menu-item index="/chat">对话</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100%;
}

.app-aside {
  border-right: 1px solid var(--el-border-color);
}

.app-menu {
  height: 100%;
  border-right: none;
}

.app-main {
  background-color: var(--el-bg-color-page);
}
</style>
