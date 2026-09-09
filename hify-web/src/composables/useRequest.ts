import { ref } from 'vue'

/**
 * Request state management: wraps an API call and exposes data / loading /
 * error, so pages avoid hand-rolling try-catch-finally boilerplate.
 *
 * ```
 * const { data, loading, execute } = useRequest(listProviders)
 * onMounted(() => execute(1, 20))
 * ```
 *
 * Error toasts are handled by the shared axios interceptor (utils/request.ts);
 * this composable only records the error state and resolves undefined on
 * failure — callers that need custom error handling read `error`.
 */
export function useRequest<T, A extends unknown[]>(
  api: (...args: A) => Promise<T>,
) {
  const data = ref<T>()
  const loading = ref(false)
  const error = ref<unknown>(null)

  /** Run the wrapped API; resolves the payload, or undefined on failure. */
  async function execute(...args: A): Promise<T | undefined> {
    loading.value = true
    error.value = null
    try {
      data.value = await api(...args)
      return data.value
    } catch (e) {
      error.value = e
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
