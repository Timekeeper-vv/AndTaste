<script setup>
defineProps({
  msg: String,
  type: String,
  visible: Boolean,
})
</script>

<template>
  <div :class="['alert', `alert-${type}`, { show: visible }]">{{ msg }}</div>
</template>

<style scoped>
.alert {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  z-index: 9999;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.3s;
  white-space: nowrap;
}

.alert.show { opacity: 1; pointer-events: auto; }
.alert-success { background: #52c41a; color: #fff; }
.alert-error { background: #ff4d4f; color: #fff; }
</style>
