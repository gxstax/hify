import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types/api'
import { notifyError } from './notify'

/**
 * Shared axios instance for all API calls.
 *
 * - baseURL /api: Vite proxies /api to the backend (localhost:8080), so calls
 *   use the full backend path, e.g. get('/api/v1/providers')... callers usually
 *   pass '/v1/providers' since this base already carries the /api prefix.
 * - timeout 60s: backend chat endpoints may stream for a long time.
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60_000,
})

request.interceptors.response.use(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- unwraps the envelope; axios types only allow AxiosResponse here
  (response): any => {
    const result = response.data as Result
    if (result.code === 200) {
      // Auto-unwrap: resolve with the payload only
      return result.data
    }
    notifyError(result.message || '请求失败')
    return Promise.reject(new Error(result.message))
  },
  (error) => {
    notifyError(error?.response?.data?.message || error?.message || '网络错误')
    return Promise.reject(error)
  },
)

/** GET; resolves with the unwrapped `data` field of the Result envelope. */
export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.get(url, config) as Promise<T>

/** POST; resolves with the unwrapped `data` field of the Result envelope. */
export const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.post(url, data, config) as Promise<T>

/** PUT; resolves with the unwrapped `data` field of the Result envelope. */
export const put = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  request.put(url, data, config) as Promise<T>

/** DELETE (named `del`, not `delete`, which is a reserved word). */
export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.delete(url, config) as Promise<T>

export default request
