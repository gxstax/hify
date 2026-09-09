import type { PageResult } from './api'

/** Column descriptor consumed by HifyTable. */
export interface TableColumn {
  /** Header text. */
  label: string
  /** Row field rendered by default when no slot is given. */
  prop?: string
  /** Column width: number (px) or string (e.g. '120px'). */
  width?: number | string
  /** Named scoped slot to render this column: <template #slotName="{ row }">. */
  slot?: string
  /** Truncate overflow with a hover tooltip (long text columns). */
  showOverflowTooltip?: boolean
}

/** Paging params passed to list APIs (page starts at 1). */
export interface PageQuery {
  page: number
  pageSize: number
}

/** Signature of a paged list API consumed by HifyTable. */
export type PageApi<T> = (query: PageQuery) => Promise<PageResult<T>>
