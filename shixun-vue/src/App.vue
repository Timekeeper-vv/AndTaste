<script setup>
import { ref, onMounted } from 'vue'
import LoginPage from './components/LoginPage.vue'
import Sidebar from './components/Sidebar.vue'
import UserManagement from './components/UserManagement.vue'
import ProductManagement from './components/ProductManagement.vue'
import GlobalAlert from './components/GlobalAlert.vue'

const currentUser = ref(null)
const currentPage = ref('users')
const alertMsg = ref('')
const alertType = ref('success')
const alertVisible = ref(false)
let alertTimer = null

onMounted(() => {
  const saved = sessionStorage.getItem('currentUser')
  if (saved) currentUser.value = JSON.parse(saved)
})

function showAlert(msg, type = 'success') {
  alertMsg.value = msg
  alertType.value = type
  alertVisible.value = true
  if (alertTimer) clearTimeout(alertTimer)
  alertTimer = setTimeout(() => { alertVisible.value = false }, 2500)
}

function onLogin(user) {
  currentUser.value = user
  sessionStorage.setItem('currentUser', JSON.stringify(user))
  currentPage.value = 'users'
}

function onLogout() {
  currentUser.value = null
  sessionStorage.removeItem('currentUser')
}
</script>

<template>
  <LoginPage v-if="!currentUser" @login="onLogin" />

  <template v-else>
    <Sidebar
      :current-user="currentUser"
      :current-page="currentPage"
      @switch-page="p => currentPage = p"
      @logout="onLogout"
    />
    <div class="main">
      <UserManagement
        v-if="currentPage === 'users'"
        @alert="showAlert"
      />
      <ProductManagement
        v-if="currentPage === 'products'"
        @alert="showAlert"
      />
    </div>
  </template>

  <GlobalAlert :msg="alertMsg" :type="alertType" :visible="alertVisible" />
</template>

<style>
.main {
  margin-left: 220px;
  padding: 24px;
  min-height: 100vh;
}
</style>
