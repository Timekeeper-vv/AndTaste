<script setup>
defineProps({
  currentUser: Object,
  currentPage: String,
})

const emit = defineEmits(['switch-page', 'logout'])
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-title">🚀 管理系统</div>
    <div
      class="nav-item"
      :class="{ active: currentPage === 'users' }"
      @click="emit('switch-page', 'users')"
    >
      👤 用户管理
    </div>
    <div
      class="nav-item"
      :class="{ active: currentPage === 'products' }"
      @click="emit('switch-page', 'products')"
    >
      🛒 商品管理
    </div>
    <div class="sidebar-footer">
      <div class="user-info">当前用户：{{ currentUser.username }}</div>
      <button class="btn btn-danger btn-block" @click="emit('logout')">退出登录</button>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0; left: 0;
  width: 220px;
  height: 100vh;
  background: #001529;
  color: #fff;
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.sidebar-title {
  padding: 24px 20px 20px;
  font-size: 16px;
  font-weight: 700;
  border-bottom: 1px solid #0d2137;
  color: #fff;
}

.nav-item {
  padding: 14px 20px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background 0.2s;
  color: #ffffffb3;
}

.nav-item:hover { background: #0d2137; color: #fff; }
.nav-item.active { background: #1677ff; color: #fff; }

.sidebar-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid #0d2137;
}

.user-info {
  font-size: 13px;
  color: #ffffffb3;
  margin-bottom: 10px;
}
</style>
