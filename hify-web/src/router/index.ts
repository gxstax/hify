import { createRouter, createWebHistory } from 'vue-router'

/**
 * Module routes, one per management page. Pages are lazy-loaded from the
 * matching directory under src/views; the App layout menu navigates these.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/providers' },
    {
      path: '/providers',
      name: 'providers',
      component: () => import('@/views/provider/ProviderList.vue'),
      meta: { title: '模型提供商管理' },
    },
    {
      path: '/agents',
      name: 'agents',
      component: () => import('@/views/agent/AgentList.vue'),
      meta: { title: 'Agent 管理' },
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/chat/ChatView.vue'),
      meta: { title: '对话' },
    },
  ],
})

export default router
