<script setup>
defineProps({
  show: Boolean,
  title: String,
})

const emit = defineEmits(['close'])
</script>

<template>
  <div class="modal-overlay" :class="{ show }" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <h3>{{ title }}</h3>
        <button class="modal-close" @click="emit('close')">&times;</button>
      </div>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 999;
  align-items: center;
  justify-content: center;
}

.modal-overlay.show { display: flex; }

.modal {
  background: #fff;
  border-radius: 14px;
  padding: 28px 28px 20px;
  width: 480px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-header h3 { font-size: 17px; }

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #999;
  line-height: 1;
}
</style>
