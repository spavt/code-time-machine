import { ref, watch, onMounted } from 'vue'

const THEME_KEY = 'code-time-machine-theme'

// 全局状态
const isDarkMode = ref(true)

export function useTheme() {
    // 初始化主题
    function initTheme() {
        const savedTheme = localStorage.getItem(THEME_KEY)
        if (savedTheme) {
            isDarkMode.value = savedTheme === 'dark'
        } else {
            // 检查系统偏好
            isDarkMode.value = window.matchMedia('(prefers-color-scheme: dark)').matches
        }
        applyTheme()
    }

    // 应用主题到 DOM
    function applyTheme() {
        if (isDarkMode.value) {
            document.body.classList.remove('light-theme')
        } else {
            document.body.classList.add('light-theme')
        }
    }

    // 切换主题
    function toggleTheme() {
        isDarkMode.value = !isDarkMode.value
        localStorage.setItem(THEME_KEY, isDarkMode.value ? 'dark' : 'light')
        applyTheme()
    }

    // 设置主题
    function setTheme(dark: boolean) {
        isDarkMode.value = dark
        localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
        applyTheme()
    }

    // 监听变化
    watch(isDarkMode, applyTheme)

    // 组件挂载时初始化
    onMounted(initTheme)

    return {
        isDarkMode,
        toggleTheme,
        setTheme,
        initTheme
    }
}
