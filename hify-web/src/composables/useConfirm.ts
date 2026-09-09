import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/utils/notify'

export interface ConfirmOptions {
  /** Text shown in the confirm box. */
  message: string
  /** Toast after a successful API call (default '操作成功'). */
  successMessage?: string
  /** Box title (default '提示'). */
  title?: string
  /** Confirm button text (default '删除'). */
  confirmText?: string
}

/**
 * One-liner delete flow: confirm box -> call API -> success toast.
 *
 * ```
 * const removeItem = useConfirm({ message: '确认删除该记录？' }, deleteItem)
 * // then, in a template handler:
 * await removeItem(row.id)          // boolean: true = deleted
 * ```
 *
 * Returns a function with the same signature as `api` that resolves
 * `true` when the API succeeded. Cancel/close and API failures resolve
 * `false` (failure toasts come from the axios interceptor).
 */
export function useConfirm<T extends unknown[]>(
  options: ConfirmOptions,
  api: (...args: T) => Promise<unknown>,
) {
  return async (...args: T): Promise<boolean> => {
    try {
      await ElMessageBox.confirm(options.message, options.title ?? '提示', {
        type: 'warning',
        confirmButtonText: options.confirmText ?? '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      })
    } catch {
      return false // user cancelled or closed the box
    }

    try {
      await api(...args)
      notifySuccess(options.successMessage ?? '操作成功')
      return true
    } catch {
      return false // error already toasted by the request layer
    }
  }
}
