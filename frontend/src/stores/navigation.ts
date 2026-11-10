import { reactive } from 'vue'
import { pageScrollPosition } from '@/utils/modalScrollLock'

const destinations = reactive<Record<string, string>>({})
const scroll = new Map<string, { left: number; top: number }>()

export function useNavigation() {
  return {
    destination: (path: string) => destinations[path] || path,
    remember: (path: string, fullPath: string) => { destinations[path] = fullPath },
    saveScroll: (fullPath: string) => scroll.set(fullPath, pageScrollPosition()),
    position: (fullPath: string) => scroll.get(fullPath),
    reset() { Object.keys(destinations).forEach(key => delete destinations[key]); scroll.clear() }
  }
}
