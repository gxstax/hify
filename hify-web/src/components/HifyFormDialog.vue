<script setup lang="ts" generic="T extends Record<string, any>">
/**
 * Generic form dialog.
 *
 * ```
 * <HifyFormDialog v-model="dialogVisible" title="新建提供商" :rules="rules" ref="dialogRef"
 *                 @submit="onSubmit">
 *   <template #default="{ form }">
 *     <el-form-item prop="name" label="名称"><el-input v-model="form.name" /></el-form-item>
 *   </template>
 * </HifyFormDialog>
 * ```
 *
 * Contract:
 *  - `open()` = create mode (empty form); `open(data)` = edit mode (prefilled)
 *  - Submit validates the form, then emits `submit` with a copy of the form;
 *    the PARENT calls the API. On success close the dialog (v-model=false) —
 *    closing auto-resets the form and the submit loading. On failure call
 *    `setSubmitting(false)` on the ref to keep editing.
 */
import { nextTick, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  title: string
  /** Dialog width (default '560px'). */
  width?: string
  /** Element Plus form rules, keyed by form field. */
  rules?: FormRules
}

const props = withDefaults(defineProps<Props>(), { width: '520px', rules: undefined })

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  /** Fired after validation passes; payload is a copy of the form data. */
  submit: [form: T]
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const editing = ref(false)

/** Form data lives here; fields are rendered by the parent via the slot. */
const form = reactive({} as T)

/** Open in create mode (no data) or edit mode (data prefills the form). */
async function open(data?: Partial<T>) {
  editing.value = data != null
  resetForm()
  if (data) {
    Object.assign(form, data)
  }
  visible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

function resetForm() {
  for (const key of Object.keys(form)) {
    delete (form as Record<string, unknown>)[key]
  }
}

// Closing (success path or user cancel) resets loading; the form itself is
// reset at the next open() — a fresh open must not show stale values.
watch(visible, (v) => {
  if (!v) {
    submitting.value = false
    resetForm()
  }
})

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return // field errors are shown inline by the form
  }
  submitting.value = true
  emit('submit', snapshot())
}

/** Called by the parent when the submit API failed, to release the loading. */
function setSubmitting(value: boolean) {
  submitting.value = value
}

/** Plain copy of the reactive form, detached from the dialog state. */
function snapshot(): T {
  return Object.assign({}, form) as unknown as T
}

defineExpose({ open, setSubmitting })
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => (visible = v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="props.rules"
      label-width="100px"
      label-position="right"
    >
      <slot :form="form" :editing="editing" />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        class="btn-brand-gradient"
        :loading="submitting"
        @click="submit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
