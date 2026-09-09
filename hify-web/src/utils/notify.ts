import { ElMessage } from 'element-plus'

/**
 * Unified toast notifications. Every success/error/warning toast in the app
 * goes through here so duration & grouping stay consistent.
 */

/** Success toasts are short-lived. */
export function notifySuccess(message: string): void {
  ElMessage.success({ message, duration: 3000, grouping: true })
}

/** Warning toasts carry a bit more reading time. */
export function notifyWarning(message: string): void {
  ElMessage.warning({ message, duration: 4000, grouping: true })
}

/** Errors stay longer so the user can read them. */
export function notifyError(message: string): void {
  ElMessage.error({ message, duration: 5000, grouping: true })
}
