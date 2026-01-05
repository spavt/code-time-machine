<script setup lang="ts">
import type { RecommendedRepo } from '@/config/recommendedRepos'

interface Props {
  repo: RecommendedRepo
}

defineProps<Props>()
const emit = defineEmits<{
  select: [url: string]
}>()
</script>

<template>
  <div class="repo-card" @click="emit('select', repo.url)">
    <div class="repo-header">
      <h4 class="repo-name">{{ repo.name }}</h4>
      <span v-if="repo.stars" class="repo-stars">
        <svg viewBox="0 0 16 16" fill="currentColor" width="14" height="14">
          <path d="M8 .25a.75.75 0 01.673.418l1.882 3.815 4.21.612a.75.75 0 01.416 1.279l-3.046 2.97.719 4.192a.75.75 0 01-1.088.791L8 12.347l-3.766 1.98a.75.75 0 01-1.088-.79l.72-4.194L.818 6.374a.75.75 0 01.416-1.28l4.21-.611L7.327.668A.75.75 0 018 .25z"/>
        </svg>
        {{ repo.stars }}
      </span>
    </div>
    
    <p class="repo-desc">{{ repo.description }}</p>
    
    <div class="repo-footer">
      <span v-if="repo.language" class="repo-lang">
        <span class="lang-dot" :class="repo.language.toLowerCase()"></span>
        {{ repo.language }}
      </span>
      <div class="repo-tags">
        <span v-for="tag in repo.tags.slice(0, 2)" :key="tag" class="repo-tag">
          {{ tag }}
        </span>
      </div>
    </div>
    
    <div class="repo-action">
      <span>开始学习</span>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <path d="M5 12h14M12 5l7 7-7 7"/>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.repo-card {
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  position: relative;
  overflow: hidden;
}

.repo-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease;
}

.repo-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}

.repo-card:hover::before {
  transform: scaleX(1);
}

.repo-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
}

.repo-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.repo-stars {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.8rem;
  color: var(--color-warning);
  white-space: nowrap;
}

.repo-desc {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.repo-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-sm);
}

.repo-lang {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.lang-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--text-muted);
}

.lang-dot.javascript { background: #f1e05a; }
.lang-dot.typescript { background: #3178c6; }
.lang-dot.c\+\+ { background: #f34b7d; }
.lang-dot.多语言 { background: linear-gradient(135deg, #f1e05a, #3178c6, #f34b7d); }

.repo-tags {
  display: flex;
  gap: 6px;
}

.repo-tag {
  padding: 2px 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  font-size: 0.7rem;
  color: var(--text-secondary);
}

.repo-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-primary);
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.3s ease;
}

.repo-card:hover .repo-action {
  opacity: 1;
  transform: translateY(0);
}
</style>
