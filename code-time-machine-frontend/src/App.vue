<script setup lang="ts">
// AI Code Time Machine - Main App
// Modern Premium Design
</script>

<template>
  <div class="app-container">
    <div class="background-effects">
      <div class="glow-spot top-right"></div>
      <div class="glow-spot bottom-left"></div>
    </div>
    
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  color: var(--text-primary);
}

.background-effects {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: -1;
  pointer-events: none;
  background: var(--bg-main);
}

.glow-spot {
  position: absolute;
  width: 60vw;
  height: 60vw;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.15;
  animation: breathe 10s ease-in-out infinite alternate;
}

.top-right {
  top: -20%;
  right: -10%;
  background: radial-gradient(circle, var(--color-primary), transparent 70%);
}

.bottom-left {
  bottom: -20%;
  left: -10%;
  background: radial-gradient(circle, var(--color-accent), transparent 70%);
  animation-delay: -5s;
}

@keyframes breathe {
  0% { transform: scale(1); opacity: 0.1; }
  100% { transform: scale(1.1); opacity: 0.2; }
}

/* Page Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
