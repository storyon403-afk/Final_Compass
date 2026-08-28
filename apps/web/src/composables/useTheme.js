import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export function useTheme() {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  const theme = ref(localStorage.getItem('finals-compass-theme') || 'system')
  const systemDark = ref(mediaQuery.matches)
  const themeLabel = computed(() => ({ system: '跟随系统', light: '浅色模式', dark: '深色模式' })[theme.value])
  const effectiveTheme = computed(() => theme.value === 'system' ? (systemDark.value ? 'dark' : 'light') : theme.value)
  const cycleTheme = () => { theme.value = theme.value === 'system' ? 'light' : theme.value === 'light' ? 'dark' : 'system' }
  const handleSystemTheme = (event) => { systemDark.value = event.matches }

  watch(theme, (value) => localStorage.setItem('finals-compass-theme', value))
  watch(effectiveTheme, (value) => { document.documentElement.dataset.theme = value }, { immediate: true })
  onMounted(() => mediaQuery.addEventListener('change', handleSystemTheme))
  onBeforeUnmount(() => mediaQuery.removeEventListener('change', handleSystemTheme))
  return { themeLabel, cycleTheme }
}
