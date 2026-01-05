import { ref, computed } from 'vue'
import { type SkillLevel, skillLevelConfig, getReposByLevel } from '@/config/recommendedRepos'

const STORAGE_KEY = 'code-time-machine-skill-level'

const selectedLevel = ref<SkillLevel | null>(null)

// 初始化时从 localStorage 读取
function initFromStorage() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY)
        if (stored && (stored === 'beginner' || stored === 'intermediate' || stored === 'advanced')) {
            selectedLevel.value = stored as SkillLevel
        }
    } catch {
        // localStorage 不可用时忽略
    }
}

initFromStorage()

export function useSkillLevel() {
    const currentLevelConfig = computed(() => {
        if (!selectedLevel.value) return null
        return skillLevelConfig[selectedLevel.value]
    })

    const recommendedRepos = computed(() => {
        if (!selectedLevel.value) return []
        return getReposByLevel(selectedLevel.value)
    })

    function setLevel(level: SkillLevel) {
        selectedLevel.value = level
        try {
            localStorage.setItem(STORAGE_KEY, level)
        } catch {
            // localStorage 不可用时忽略
        }
    }

    function clearLevel() {
        selectedLevel.value = null
        try {
            localStorage.removeItem(STORAGE_KEY)
        } catch {
            // localStorage 不可用时忽略
        }
    }

    return {
        selectedLevel,
        currentLevelConfig,
        recommendedRepos,
        setLevel,
        clearLevel
    }
}
