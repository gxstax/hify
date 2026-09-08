import axios from 'axios'

/**
 * Backend health payload. Actuator-style bare JSON (NOT the Result envelope):
 * `{"status":"UP","components":{"mysql":"UP","redis":"UP","pgvector":"skipped"}}`.
 * The backend maps failing components to HTTP 503.
 */
export interface HealthInfo {
  status: 'UP' | 'DOWN'
  /** Per-component state: "UP" | "DOWN" | "skipped", or {status, error} when failing */
  components: Record<string, unknown>
}

/**
 * Probe backend liveness: GET /api/v1/health.
 *
 * Uses a raw axios call instead of the shared request instance on purpose:
 * the shared instance's interceptor unwraps the Result envelope ({code/message/data})
 * and would treat this envelope-less body (code === undefined) as a business
 * failure. Health responses succeed/fail via the HTTP status alone:
 * 2xx resolves, anything else rejects. Do NOT route this through `@/utils/request`.
 */
export const getHealth = (): Promise<HealthInfo> =>
  axios
    .get<HealthInfo>('/api/v1/health', { timeout: 5_000 })
    .then((res) => res.data)
