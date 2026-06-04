<script setup>
import { ref } from 'vue'

const emit = defineEmits(['login'])

const mode = ref('login') // 'login' | 'register'

// 登录
const username = ref('')
const password = ref('')
const loginMsg = ref('')
const loginLoading = ref(false)

// 注册
const regUsername = ref('')
const regAge = ref('')
const regEmail = ref('')
const regPhone = ref('')
const regPassword = ref('')
const regConfirm = ref('')
const regMsg = ref('')
const regSuccess = ref(false)
const regLoading = ref(false)

function switchMode(m) {
  mode.value = m
  loginMsg.value = ''
  regMsg.value = ''
  regSuccess.value = false
}

async function login() {
  if (loginLoading.value) return
  loginMsg.value = ''
  loginLoading.value = true
  try {
    const res = await fetch('/api/users/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value })
    })
    if (!res.ok) {
      const text = await res.text()
      loginMsg.value = res.status === 401 ? '用户名或密码错误' : `登录失败：${text}`
      return
    }
    const user = await res.json()
    emit('login', user)
  } catch {
    loginMsg.value = '网络错误，请重试'
  } finally {
    loginLoading.value = false
  }
}

async function register() {
  if (regLoading.value) return
  regMsg.value = ''
  regSuccess.value = false

  if (regPassword.value !== regConfirm.value) {
    regMsg.value = '两次输入的密码不一致'
    return
  }
  if (regAge.value && (isNaN(regAge.value) || Number(regAge.value) <= 0)) {
    regMsg.value = '请输入有效的年龄'
    return
  }

  regLoading.value = true
  try {
    const res = await fetch('/api/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: regUsername.value,
        age: Number(regAge.value),
        email: regEmail.value,
        phone: regPhone.value || undefined,
        password: regPassword.value
      })
    })
    if (!res.ok) {
      const text = await res.text()
      regMsg.value = res.status === 409 ? '用户名已存在' : `注册失败：${text}`
      return
    }
    regSuccess.value = true
    regMsg.value = '注册成功！请登录'
    // 自动填充用户名并跳转到登录
    username.value = regUsername.value
    password.value = ''
    setTimeout(() => switchMode('login'), 1500)
  } catch {
    regMsg.value = '网络错误，请重试'
  } finally {
    regLoading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <h1>管理系统</h1>

      <!-- 模式切换 -->
      <div class="tab-bar">
        <button :class="['tab-btn', mode === 'login' && 'active']" @click="switchMode('login')">登 录</button>
        <button :class="['tab-btn', mode === 'register' && 'active']" @click="switchMode('register')">注 册</button>
      </div>

      <!-- 登录表单 -->
      <form v-if="mode === 'login'" @submit.prevent="login">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" placeholder="请输入用户名" required autocomplete="username">
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" required autocomplete="current-password">
        </div>
        <div class="msg">{{ loginMsg }}</div>
        <button type="submit" class="btn btn-primary btn-block" :disabled="loginLoading">
          {{ loginLoading ? '登录中...' : '登 录' }}
        </button>
        <p class="switch-tip">还没有账号？<a @click="switchMode('register')">立即注册</a></p>
      </form>

      <!-- 注册表单 -->
      <form v-else @submit.prevent="register">
        <div class="form-group">
          <label>用户名 <span class="required">*</span></label>
          <input v-model="regUsername" placeholder="请输入用户名" required autocomplete="username">
        </div>
        <div class="form-group">
          <label>年龄 <span class="required">*</span></label>
          <input v-model="regAge" type="number" placeholder="请输入年龄" min="1" max="150" required>
        </div>
        <div class="form-group">
          <label>邮箱 <span class="required">*</span></label>
          <input v-model="regEmail" type="email" placeholder="请输入邮箱" required autocomplete="email">
        </div>
        <div class="form-group">
          <label>手机号</label>
          <input v-model="regPhone" placeholder="请输入手机号（选填）" autocomplete="tel">
        </div>
        <div class="form-group">
          <label>密码 <span class="required">*</span></label>
          <input v-model="regPassword" type="password" placeholder="请设置密码" required autocomplete="new-password">
        </div>
        <div class="form-group">
          <label>确认密码 <span class="required">*</span></label>
          <input v-model="regConfirm" type="password" placeholder="请再次输入密码" required autocomplete="new-password">
        </div>
        <div :class="['msg', regSuccess && 'msg-success']">{{ regMsg }}</div>
        <button type="submit" class="btn btn-primary btn-block" :disabled="regLoading">
          {{ regLoading ? '注册中...' : '注 册' }}
        </button>
        <p class="switch-tip">已有账号？<a @click="switchMode('login')">去登录</a></p>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1677ff 0%, #0950c5 100%);
}

.login-box {
  background: #fff;
  border-radius: 16px;
  padding: 36px 36px 28px;
  width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}

.login-box h1 {
  text-align: center;
  font-size: 22px;
  margin-bottom: 20px;
  color: #1677ff;
}

.tab-bar {
  display: flex;
  border-bottom: 2px solid #f0f0f0;
  margin-bottom: 24px;
}

.tab-btn {
  flex: 1;
  background: none;
  border: none;
  padding: 10px 0;
  font-size: 15px;
  color: #999;
  cursor: pointer;
  position: relative;
  transition: color 0.2s;
}

.tab-btn.active {
  color: #1677ff;
  font-weight: 600;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 20%;
  width: 60%;
  height: 2px;
  background: #1677ff;
  border-radius: 2px;
}

.msg {
  color: #ff4d4f;
  font-size: 13px;
  min-height: 20px;
  margin-bottom: 12px;
}

.msg-success {
  color: #52c41a;
}

.required {
  color: #ff4d4f;
}

.switch-tip {
  text-align: center;
  font-size: 13px;
  color: #999;
  margin-top: 16px;
}

.switch-tip a {
  color: #1677ff;
  cursor: pointer;
  text-decoration: none;
}

.switch-tip a:hover {
  text-decoration: underline;
}
</style>
