<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Expand, Fold, Setting, User } from '@element-plus/icons-vue'
import { useMediaQuery } from '@/composables/useMediaQuery'

const route = useRoute()

/** Manual collapse via the footer button (wide screens only). */
const collapsed = ref(false)
/** Auto-collapse below 1200px: narrow viewports always show the icon rail. */
const isNarrow = useMediaQuery('(max-width: 1199.98px)')
/** Effective rail state: narrow viewport forces it regardless of manual state. */
const asideCollapsed = computed(() => collapsed.value || isNarrow.value)

/** Breadcrumb trail: fixed "首页" + every matched route that carries a title */
const crumbs = computed(() =>
  route.matched.filter((r) => r.meta?.title).map((r) => r.meta.title as string),
)
</script>

<template>
  <!-- App shell: dark sidebar + (top bar / light canvas) column -->
  <el-container class="app-layout">
    <el-aside :width="asideCollapsed ? '64px' : '224px'" class="app-aside">
      <!-- Brand -->
      <div class="app-brand" :class="{ collapsed: asideCollapsed }">
        <div class="brand-mark" aria-hidden="true"></div>
        <div v-if="!asideCollapsed" class="brand-text">
          <span class="brand-name">Hify</span>
          <span class="brand-sub">AI Agent Platform</span>
        </div>
      </div>

      <!-- Nav -->
      <el-menu
        router
        :default-active="route.path"
        :collapse="asideCollapsed"
        :collapse-transition="false"
        class="app-menu"
      >
        <el-menu-item index="/providers">
          <el-icon><Setting /></el-icon>
          <template #title>模型提供商管理</template>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><User /></el-icon>
          <template #title>Agent 管理</template>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>对话</template>
        </el-menu-item>
      </el-menu>

      <!-- Footer: collapse toggle + version -->
      <div class="app-sidebar-footer" :class="{ collapsed: asideCollapsed }">
        <button
          type="button"
          class="fold-btn"
          :disabled="isNarrow"
          :title="isNarrow ? '窄屏下侧边栏保持收起' : asideCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="collapsed = !collapsed"
        >
          <el-icon :size="16">
            <Expand v-if="asideCollapsed" /><Fold v-else />
          </el-icon>
        </button>
        <span v-if="!asideCollapsed" class="version">v0.1.0</span>
      </div>
    </el-aside>

    <!-- Right column: top bar + content -->
    <el-container class="app-right">
      <el-header class="app-header" height="56px">
        <el-breadcrumb separator="/" class="app-breadcrumb">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-for="c in crumbs" :key="c">{{ c }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="app-user">
          <el-avatar :size="30" class="user-avatar">A</el-avatar>
          <span class="user-name">Admin</span>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100%;
}

/* --- left: dark sidebar ------------------------------------------------ */
.app-aside {
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg-dark);
  border-right: 1px solid var(--hify-sidebar-border);
  transition: width var(--hify-duration-base) var(--hify-ease-in-out);
  overflow: hidden;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid var(--hify-sidebar-border);
  white-space: nowrap;
}

.brand-mark {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: linear-gradient(135deg, var(--hify-primary-300), var(--hify-primary-600) 60%, var(--hify-accent-500));
  box-shadow: 0 0 12px color-mix(in srgb, var(--hify-primary-400) 45%, transparent);
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.4px;
  line-height: 1.2;
  background: linear-gradient(90deg, var(--hify-primary-200), var(--hify-primary-400) 60%, var(--hify-primary-500));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-sub {
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--hify-sidebar-text);
  opacity: 0.75;
}

.app-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;
  border-right: none;
}

.app-menu :deep(.el-menu-item) {
  margin-bottom: 2px;
  border-radius: var(--hify-radius-sm);
  white-space: nowrap;
}

.app-menu :deep(.el-menu-item:hover) {
  background-color: var(--hify-sidebar-bg-hover);
  color: var(--hify-sidebar-text-hover);
}

.app-menu :deep(.el-menu-item.is-active) {
  background-color: var(--hify-sidebar-bg-active);
  color: var(--hify-sidebar-text-active);
  box-shadow: inset 3px 0 0 0 var(--hify-sidebar-accent);
}

.app-sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-top: 1px solid var(--hify-sidebar-border);
}

.app-sidebar-footer.collapsed {
  justify-content: center;
}

.fold-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--hify-radius-sm);
  background: transparent;
  color: var(--hify-sidebar-text);
  cursor: pointer;
  transition: background-color var(--hify-duration-fast) var(--hify-ease-out),
    color var(--hify-duration-fast) var(--hify-ease-out);
}

.fold-btn:hover {
  background-color: var(--hify-sidebar-bg-hover);
  color: var(--hify-sidebar-text-hover);
}

.fold-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.fold-btn:disabled:hover {
  background-color: transparent;
  color: var(--hify-sidebar-text);
}

.version {
  font-size: 12px;
  color: var(--hify-sidebar-text);
  opacity: 0.6;
  user-select: none;
}

/* --- right: top bar + content ------------------------------------------ */
.app-right {
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--hify-space-page);
  background-color: var(--hify-bg-container);
  border-bottom: 1px solid var(--hify-border);
}

.app-breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--hify-text-tertiary);
  font-size: 13px;
}

.app-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--hify-text-primary);
  font-weight: 500;
}

.app-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  background-image: var(--hify-gradient-brand);
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--hify-text-secondary);
}

/* Content scrolls here; pages provide their own 24px .page padding */
.app-main {
  padding: 0;
  overflow: auto;
  background-color: var(--color-bg-secondary);
}
</style>
