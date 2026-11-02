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
      path: '/workflows',
      name: 'workflows',
      component: () => import('@/views/workflow/WorkflowList.vue'),
      meta: { title: '工作流管理' },
    },
    {
      path: '/workflows/create',
      name: 'workflow-create',
      component: () => import('@/views/workflow/WorkflowCreate.vue'),
      meta: { title: '新建工作流' },
    },
    {
      path: '/knowledge-bases',
      name: 'knowledge-bases',
      component: () => import('@/views/knowledge/KnowledgeBaseList.vue'),
      meta: { title: '知识库管理' },
    },
    {
      path: '/knowledge-bases/:kbId/documents',
      name: 'knowledge-base-documents',
      component: () => import('@/views/knowledge/DocumentList.vue'),
      meta: { title: '文档管理' },
    },
    {
      path: '/mcp-servers',
      name: 'mcp-servers',
      component: () => import('@/views/mcp/McpServerList.vue'),
      meta: { title: 'MCP 工具' },
    },
    {
      path: '/mcp-servers/:id',
      name: 'mcp-server-detail',
      component: () => import('@/views/mcp/McpServerDetail.vue'),
      meta: { title: 'MCP Server 详情' },
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
