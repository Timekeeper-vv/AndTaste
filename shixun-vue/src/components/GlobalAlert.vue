<script setup>
defineProps({
  msg: String,
  type: String,
  visible: Boolean,
})
</script>

<template>
  <transition name="toast">
    <div v-if="visible" :class="['toast', `toast-${type}`]">
      <svg v-if="type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
      <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      {{ msg }}
    </div>
  </transition>
</template>

<style scoped>
.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: var(--r-md);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font);
  z-index: 9999;
  white-space: nowrap;
  box-shadow: var(--shadow-lg);
}

.toast-success {
  background: #fff;
  color: var(--c-success);
  border: 1px solid #A7F3D0;
}

.toast-error {
  background: #fff;
  color: var(--c-error);
  border: 1px solid #FECACA;
}

.toast-enter-active { transition: all .25s ease; }
.toast-leave-active { transition: all .2s ease; }
.toast-enter-from  { opacity: 0; transform: translateX(-50%) translateY(-8px); }
.toast-leave-to    { opacity: 0; transform: translateX(-50%) translateY(-4px); }
</style>
