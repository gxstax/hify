/**
 * API types mirroring the backend unified response contract (hify-common):
 * every endpoint returns Result<T>; paginated ones nest a PageResult<T> in data.
 */

/** Unified backend response wrapper. */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** Paginated payload nested inside Result.data. */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
