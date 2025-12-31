import { ref, watch, onMounted } from 'vue'

const THEME_KEY = 'code-time-machine-theme'

const isDarkMode = ref(true)

export function useTheme() {
    function initTheme() {
        const savedTheme = localStorage.getItem(THEME_KEY)
        if (savedTheme) {
            isDarkMode.value = savedTheme === 'dark'
        } else {
            isDarkMode.value = window.matchMedia('(prefers-color-scheme: dark)').matches
        }
        applyTheme()
    }

    function applyTheme() {
        if (isDarkMode.value) {
            document.body.classList.remove('light-theme')
        } else {
            document.body.classList.add('light-theme')
        }
    }

    function toggleTheme() {
        isDarkMode.value = !isDarkMode.value
        localStorage.setItem(THEME_KEY, isDarkMode.value ? 'dark' : 'light')
        applyTheme()
    }

    function setTheme(dark: boolean) {
        isDarkMode.value = dark
        localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
        applyTheme()
    }

    watch(isDarkMode, applyTheme)

    onMounted(initTheme)

    return {
        isDarkMode,
        toggleTheme,
        setTheme,
        initTheme
    }
}
