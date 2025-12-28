<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="not-found-page">
    <div class="content">
      <div class="error-code">
        <span class="digit">4</span>
        <span class="digit zero">0</span>
        <span class="digit">4</span>
      </div>
      <h1>页面不存在</h1>
      <p>看起来你穿越到了一个不存在的时空...</p>
      <el-button type="primary" size="large" @click="goHome">
        <el-icon><House /></el-icon>
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.content {
  max-width: 400px;
}

.error-code {
  display: flex;
  justify-content: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.digit {
  font-size: 6rem;
  font-weight: 700;
  font-family: var(--font-mono);
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: float 3s ease-in-out infinite;
}

.digit:nth-child(2) {
  animation-delay: 0.2s;
}

.digit:nth-child(3) {
  animation-delay: 0.4s;
}

.digit.zero {
  background: linear-gradient(135deg, var(--color-secondary), var(--color-warning));
  -webkit-background-clip: text;
  background-clip: text;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

h1 {
  font-size: 1.5rem;
  margin-bottom: var(--spacing-sm);
}

p {
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xl);
}
</style>
