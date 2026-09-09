import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Reactive matchMedia. Tracks a CSS media query and stays in sync with
 * viewport changes.
 *
 * ```
 * const isNarrow = useMediaQuery('(max-width: 1199.98px)')
 * ```
 */
export function useMediaQuery(query: string) {
  const matches = ref(false)
  let mq: MediaQueryList | undefined

  const onChange = (event: MediaQueryListEvent) => {
    matches.value = event.matches
  }

  onMounted(() => {
    mq = window.matchMedia(query)
    matches.value = mq.matches
    mq.addEventListener('change', onChange)
  })

  onBeforeUnmount(() => {
    mq?.removeEventListener('change', onChange)
  })

  return matches
}
