<script setup lang="ts">
import { ref } from 'vue'
import { Back, Check, DocumentChecked } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import {
  createWorkflow,
  type WorkflowDefinition,
  type WorkflowEdgePayload,
  type WorkflowNodePayload,
  type WorkflowNodeType,
} from '@/api/workflow'
import { notifyError, notifySuccess } from '@/utils/notify'

const NODE_TYPES: WorkflowNodeType[] = [
  'START', 'END', 'LLM', 'API_CALL', 'CONDITION', 'KNOWLEDGE', 'TEXT',
]
const DEFINITION_KEYS = ['version', 'enabled', 'nodes', 'edges']
const NODE_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/
const OUTPUT_VARIABLE_PATTERN = /^[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*$/

const DEFAULT_DEFINITION: WorkflowDefinition = {
  version: 1,
  enabled: false,
  nodes: [
    {
      nodeKey: 'start',
      name: '接收客户问题',
      nodeType: 'START',
      config: {
        inputs: [{ name: 'userMessage', type: 'STRING', required: true }],
      },
      configVersion: 1,
      sortOrder: 0,
    },
    {
      nodeKey: 'classify',
      name: '识别退款咨询',
      nodeType: 'CONDITION',
      config: {
        expression: '{{start.userMessage}} == "退款"',
        outputVariable: 'isRefund',
      },
      configVersion: 1,
      sortOrder: 10,
    },
    {
      nodeKey: 'refundReply',
      name: '退款咨询回复',
      nodeType: 'TEXT',
      config: {
        template: '您好，已识别为退款咨询，请提供订单号，我们将协助核实退款进度。',
      },
      configVersion: 1,
      sortOrder: 20,
    },
    {
      nodeKey: 'generalReply',
      name: '一般咨询回复',
      nodeType: 'TEXT',
      config: {
        template: '您好，已识别为一般咨询，请补充具体问题，我们会尽快为您处理。',
      },
      configVersion: 1,
      sortOrder: 30,
    },
    {
      nodeKey: 'refundEnd',
      name: '结束退款分支',
      nodeType: 'END',
      config: { outputVariable: 'refundReply.output' },
      configVersion: 1,
      sortOrder: 40,
    },
    {
      nodeKey: 'generalEnd',
      name: '结束一般咨询分支',
      nodeType: 'END',
      config: { outputVariable: 'generalReply.output' },
      configVersion: 1,
      sortOrder: 50,
    },
  ],
  edges: [
    { sourceNodeKey: 'start', targetNodeKey: 'classify', sourcePort: 'default', priority: 0 },
    { sourceNodeKey: 'classify', targetNodeKey: 'refundReply', sourcePort: 'true', priority: 0 },
    { sourceNodeKey: 'classify', targetNodeKey: 'generalReply', sourcePort: 'false', priority: 1 },
    { sourceNodeKey: 'refundReply', targetNodeKey: 'refundEnd', sourcePort: 'default', priority: 0 },
    { sourceNodeKey: 'generalReply', targetNodeKey: 'generalEnd', sourcePort: 'default', priority: 0 },
  ],
}

const router = useRouter()
const submitting = ref(false)
const name = ref('')
const description = ref('')
const definitionText = ref(JSON.stringify(DEFAULT_DEFINITION, null, 2))

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requireString(value: unknown, field: string): asserts value is string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${field} 必须是非空字符串`)
  }
}

function requireNonNegativeInteger(value: unknown, field: string, minimum = 0): asserts value is number {
  if (!Number.isInteger(value) || (value as number) < minimum) {
    throw new Error(`${field} 必须是大于等于 ${minimum} 的整数`)
  }
}

function validateNode(value: unknown, index: number): WorkflowNodePayload {
  if (!isRecord(value)) {
    throw new Error(`nodes[${index}] 必须是对象`)
  }
  requireString(value.nodeKey, `nodes[${index}].nodeKey`)
  if (!NODE_KEY_PATTERN.test(value.nodeKey)) {
    throw new Error(`nodes[${index}].nodeKey 格式不正确`)
  }
  requireString(value.name, `nodes[${index}].name`)
  if (!NODE_TYPES.includes(value.nodeType as WorkflowNodeType)) {
    throw new Error(`nodes[${index}].nodeType 不受支持`)
  }
  if (!isRecord(value.config)) {
    throw new Error(`nodes[${index}].config 必须是对象`)
  }
  if (value.nodeType === 'END') {
    const outputVariable = value.config.outputVariable
    if (typeof outputVariable !== 'string' || !OUTPUT_VARIABLE_PATTERN.test(outputVariable)) {
      throw new Error(`nodes[${index}].config.outputVariable 必须是 nodeKey.variableName`)
    }
  }
  requireNonNegativeInteger(value.configVersion, `nodes[${index}].configVersion`, 1)
  requireNonNegativeInteger(value.sortOrder, `nodes[${index}].sortOrder`)
  return value as unknown as WorkflowNodePayload
}

function validateEdge(value: unknown, index: number): WorkflowEdgePayload {
  if (!isRecord(value)) {
    throw new Error(`edges[${index}] 必须是对象`)
  }
  requireString(value.sourceNodeKey, `edges[${index}].sourceNodeKey`)
  requireString(value.targetNodeKey, `edges[${index}].targetNodeKey`)
  if (value.sourcePort != null && typeof value.sourcePort !== 'string') {
    throw new Error(`edges[${index}].sourcePort 必须是字符串或 null`)
  }
  requireNonNegativeInteger(value.priority, `edges[${index}].priority`)
  return value as unknown as WorkflowEdgePayload
}

function parseDefinition(): WorkflowDefinition {
  let raw: unknown
  try {
    raw = JSON.parse(definitionText.value)
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知语法错误'
    throw new Error(`JSON 格式错误：${message}`)
  }
  if (!isRecord(raw)) {
    throw new Error('工作流定义必须是 JSON 对象')
  }
  const keys = Object.keys(raw)
  const unexpectedKey = keys.find((key) => !DEFINITION_KEYS.includes(key))
  if (unexpectedKey) {
    throw new Error(`JSON 编辑区不支持字段：${unexpectedKey}`)
  }
  const missingKey = DEFINITION_KEYS.find((key) => !(key in raw))
  if (missingKey) {
    throw new Error(`JSON 编辑区缺少字段：${missingKey}`)
  }
  requireNonNegativeInteger(raw.version, 'version', 1)
  if (typeof raw.enabled !== 'boolean') {
    throw new Error('enabled 必须是 boolean')
  }
  if (!Array.isArray(raw.nodes) || raw.nodes.length === 0) {
    throw new Error('nodes 必须是非空数组')
  }
  if (!Array.isArray(raw.edges)) {
    throw new Error('edges 必须是数组')
  }

  const nodes = raw.nodes.map(validateNode)
  const edges = raw.edges.map(validateEdge)
  const nodeKeys = new Set<string>()
  for (const node of nodes) {
    if (nodeKeys.has(node.nodeKey)) {
      throw new Error(`nodeKey 重复：${node.nodeKey}`)
    }
    nodeKeys.add(node.nodeKey)
  }
  if (nodes.filter((node) => node.nodeType === 'START').length !== 1) {
    throw new Error('工作流必须且只能包含一个 START 节点')
  }
  if (!nodes.some((node) => node.nodeType === 'END')) {
    throw new Error('工作流至少包含一个 END 节点')
  }
  const nodeTypeByKey = new Map(nodes.map((node) => [node.nodeKey, node.nodeType]))
  for (const edge of edges) {
    if (!nodeKeys.has(edge.sourceNodeKey) || !nodeKeys.has(edge.targetNodeKey)) {
      throw new Error(`连线引用了不存在的节点：${edge.sourceNodeKey} -> ${edge.targetNodeKey}`)
    }
    if (nodeTypeByKey.get(edge.targetNodeKey) === 'START') {
      throw new Error('START 节点不能有入边')
    }
    if (nodeTypeByKey.get(edge.sourceNodeKey) === 'END') {
      throw new Error('END 节点不能有出边')
    }
  }
  for (const node of nodes.filter((item) => item.nodeType === 'CONDITION')) {
    const ports = edges
      .filter((edge) => edge.sourceNodeKey === node.nodeKey)
      .map((edge) => edge.sourcePort)
    if (ports.filter((port) => port === 'true').length !== 1
      || ports.filter((port) => port === 'false').length !== 1) {
      throw new Error(`CONDITION 节点 ${node.nodeKey} 必须各有一条 true/false 出边`)
    }
  }

  return { version: raw.version, enabled: raw.enabled, nodes, edges }
}

function formatDefinition() {
  try {
    const parsed: unknown = JSON.parse(definitionText.value)
    definitionText.value = JSON.stringify(parsed, null, 2)
    notifySuccess('格式化成功')
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知语法错误'
    notifyError(`JSON 格式错误：${message}`)
  }
}

async function submit() {
  const workflowName = name.value.trim()
  if (!workflowName) {
    notifyError('请输入工作流名称')
    return
  }
  if (workflowName.length > 100) {
    notifyError('工作流名称长度不能超过 100')
    return
  }
  if (description.value.trim().length > 500) {
    notifyError('描述长度不能超过 500')
    return
  }

  let definition: WorkflowDefinition
  try {
    definition = parseDefinition()
  } catch (error) {
    notifyError(error instanceof Error ? error.message : '工作流定义校验失败')
    return
  }

  submitting.value = true
  try {
    const payload = {
      name: workflowName,
      description: description.value.trim(),
      ...definition,
    }
    await createWorkflow(payload)
    notifySuccess('创建成功')
    await router.push('/workflows')
  } catch {
    // Request interceptor shows the error and the editor stays open.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="新建工作流""
      description="JSON 编辑区仅维护 version、enabled、nodes、edges"
    >
      <template #actions>
        <el-button :icon="Back" @click="router.push('/workflows')">返回列表</el-button>
        <el-button :icon="DocumentChecked" @click="formatDefinition">格式化</el-button>
        <el-button
          type="primary"
          class="btn-brand-gradient"
          :icon="Check"
          :loading="submitting"
          @click="submit"
        >
          创建
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card workflow-form">
      <el-form label-position="top">
        <div class="basic-fields">
          <el-form-item label="名称" required>
            <el-input v-model="name" maxlength="100" show-word-limit placeholder="请输入工作流名称" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="description"
              maxlength="500"
              show-word-limit
              placeholder="请输入工作流用途说明（可选）"
            />
          </el-form-item>
        </div>
        <el-form-item label="工作流定义 JSON" required>
          <el-input
            v-model="definitionText"
            type="textarea"
            :rows="20"
            resize="vertical"
            spellcheck="false"
            class="json-editor"
          />
        </el-form-item>
      </el-form>
      <p class="editor-hint">
        保存前会校验 JSON 语法、必填字段、节点 key、START/END 数量、连线引用和条件分支。
      </p>
    </div>
  </div>
</template>

<style scoped>
.workflow-form {
  min-width: 0;
}

.basic-fields {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(320px, 2fr);
  gap: var(--hify-space-element);
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.55;
  tab-size: 2;
}

.editor-hint {
  margin: -8px 0 0;
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

@media (max-width: 767.98px) {
  .basic-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
