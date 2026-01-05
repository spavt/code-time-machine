<script setup lang="ts">
import { type SkillLevel, skillLevelConfig } from '@/config/recommendedRepos'

interface Props {
  modelValue: SkillLevel | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [level: SkillLevel]
}>()

const levels: SkillLevel[] = ['beginner', 'intermediate', 'advanced']

function selectLevel(level: SkillLevel) {
  emit('update:modelValue', level)
}
</script>

<template>
  <div class="skill-selector">
    <h3 class="selector-title">你的编程水平是？</h3>
    <p class="selector-subtitle">我们会根据你的水平推荐合适的学习项目</p>
    
    <div class="level-cards">
      <div
        v-for="level in levels"
        :key="level"
        class="level-card"
        :class="{ active: props.modelValue === level }"
        :style="{ '--level-color': skillLevelConfig[level].color }"
        @click="selectLevel(level)"
      >
        <span class="level-icon">{{ skillLevelConfig[level].icon }}</span>
        <div class="level-content">
          <h4 class="level-title">{{ skillLevelConfig[level].title }}</h4>
          <p class="level-subtitle">{{ skillLevelConfig[level].subtitle }}</p>
        </div>
        <div class="level-check" v-if="props.modelValue === level">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skill-selector {
  margin-bottom: var(--spacing-2xl);
}

.selector-title {
  font-size: 1.25rem;
  text-align: center;
  margin-bottom: var(--spacing-xs);
}

.selector-subtitle {
  color: var(--text-secondary);
  text-align: center;
  font-size: 0.9rem;
  margin-bottom: var(--spacing-lg);
}

.level-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
}

.level-card {
  position: relative;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--spacing-sm);
}

.level-card:hover {
  border-color: var(--level-color);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.level-card.active {
  border-color: var(--level-color);
  background: color-mix(in srgb, var(--level-color) 8%, var(--bg-card));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--level-color) 20%, transparent);
}

.level-icon {
  font-size: 2.5rem;
  line-height: 1;
}

.level-content {
  flex: 1;
}

.level-title {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--text-primary);
}

.level-subtitle {
  font-size: 0.8rem;
  color: var(--text-secondary);
  margin: 0;
}

.level-check {
  position: absolute;
  top: var(--spacing-sm);
  right: var(--spacing-sm);
  width: 24px;
  height: 24px;
  background: var(--level-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  animation: checkPop 0.3s ease;
}

.level-check svg {
  width: 14px;
  height: 14px;
}

@keyframes checkPop {
  0% {
    transform: scale(0);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .level-cards {
    grid-template-columns: 1fr;
  }
  
  .level-card {
    flex-direction: row;
    text-align: left;
  }
  
  .level-icon {
    font-size: 2rem;
  }
}
</style>
