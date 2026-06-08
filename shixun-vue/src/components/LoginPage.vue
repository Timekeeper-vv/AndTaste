<script setup>
import { ref } from 'vue'

const emit = defineEmits(['login'])

const mode = ref('login')

const username = ref('')
const password = ref('')
const loginMsg = ref('')
const loginLoading = ref(false)

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
    regMsg.value = '注册成功！正在跳转登录...'
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
    <!-- Left brand panel -->
    <div class="brand-panel">
      <div class="brand-content">
        <div class="brand-logo">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9 22 9 12 15 12 15 22"/>
          </svg>
        </div>

        <div class="brand-badge">Enterprise Edition</div>

        <h1 class="brand-title">智慧养殖<br>管理平台</h1>
        <p class="brand-subtitle">Smart Livestock Farm Management System</p>

        <div class="brand-divider"></div>

        <ul class="feature-list">
          <li>
            <div class="feature-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
            </div>
            <span>动物个体档案全程追溯管理</span>
          </li>
          <li>
            <div class="feature-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <span>免疫接种与用药记录一体化</span>
          </li>
          <li>
            <div class="feature-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>
            </div>
            <span>批次管理与数据可视化分析</span>
          </li>
          <li>
            <div class="feature-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            </div>
            <span>屠宰出栏食品安全溯源链</span>
          </li>
        </ul>

        <p class="brand-footer">© 2026 智慧养殖平台 · 数字农业解决方案</p>
      </div>
    </div>

    <!-- Right form panel -->
    <div class="form-panel">
      <div class="form-box">
        <div class="form-header">
          <h2>{{ mode === 'login' ? '欢迎登录' : '创建账号' }}</h2>
          <p>{{ mode === 'login' ? '使用您的账号访问管理平台' : '填写信息以加入平台' }}</p>
        </div>

        <div class="tab-bar">
          <button :class="['tab-btn', { active: mode === 'login' }]" @click="switchMode('login')">登录</button>
          <button :class="['tab-btn', { active: mode === 'register' }]" @click="switchMode('register')">注册</button>
        </div>

        <!-- Login form -->
        <form v-if="mode === 'login'" @submit.prevent="login" class="auth-form">
          <div class="field">
            <label>用户名</label>
            <div class="input-wrap">
              <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <input v-model="username" placeholder="请输入用户名" required autocomplete="username" />
            </div>
          </div>
          <div class="field">
            <label>密码</label>
            <div class="input-wrap">
              <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <input v-model="password" type="password" placeholder="请输入密码" required autocomplete="current-password" />
            </div>
          </div>
          <div v-if="loginMsg" class="form-msg error">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            {{ loginMsg }}
          </div>
          <button type="submit" class="btn-submit" :disabled="loginLoading">
            <span v-if="loginLoading" class="spinner"></span>
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
          <p class="switch-hint">没有账号？<a @click="switchMode('register')">免费注册</a></p>
        </form>

        <!-- Register form -->
        <form v-else @submit.prevent="register" class="auth-form">
          <div class="field-row">
            <div class="field">
              <label>用户名 <span class="required">*</span></label>
              <div class="input-wrap">
                <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                <input v-model="regUsername" placeholder="用户名" required autocomplete="username" />
              </div>
            </div>
            <div class="field">
              <label>年龄 <span class="required">*</span></label>
              <div class="input-wrap">
                <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                <input v-model="regAge" type="number" placeholder="年龄" min="1" max="150" required />
              </div>
            </div>
          </div>
          <div class="field">
            <label>邮箱 <span class="required">*</span></label>
            <div class="input-wrap">
              <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
              <input v-model="regEmail" type="email" placeholder="电子邮箱" required autocomplete="email" />
            </div>
          </div>
          <div class="field">
            <label>手机号 <span class="optional">选填</span></label>
            <div class="input-wrap">
              <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 14a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.77 3h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 10.91a16 16 0 0 0 6 6l.91-.91a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 18.92z"/></svg>
              <input v-model="regPhone" placeholder="手机号码" autocomplete="tel" />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label>密码 <span class="required">*</span></label>
              <div class="input-wrap">
                <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <input v-model="regPassword" type="password" placeholder="设置密码" required autocomplete="new-password" />
              </div>
            </div>
            <div class="field">
              <label>确认密码 <span class="required">*</span></label>
              <div class="input-wrap">
                <svg class="input-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <input v-model="regConfirm" type="password" placeholder="再次输入密码" required autocomplete="new-password" />
              </div>
            </div>
          </div>
          <div v-if="regMsg" :class="['form-msg', regSuccess ? 'success' : 'error']">
            <svg v-if="!regSuccess" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            {{ regMsg }}
          </div>
          <button type="submit" class="btn-submit" :disabled="regLoading">
            <span v-if="regLoading" class="spinner"></span>
            {{ regLoading ? '注册中...' : '创建账号' }}
          </button>
          <p class="switch-hint">已有账号？<a @click="switchMode('login')">立即登录</a></p>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  font-family: var(--font);
}

/* Left brand panel */
.brand-panel {
  flex: 0 0 420px;
  background: #0F172A;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  position: relative;
}

.brand-content {
  width: 100%;
  max-width: 320px;
  color: #fff;
}

.brand-logo {
  width: 48px;
  height: 48px;
  background: var(--c-primary);
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 20px;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  background: rgba(13,148,136,.2);
  border: 1px solid rgba(13,148,136,.3);
  border-radius: 11px;
  font-size: 11px;
  font-weight: 600;
  color: var(--c-primary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.brand-title {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 8px;
  color: #fff;
}

.brand-subtitle {
  font-size: 12px;
  color: rgba(255,255,255,.35);
  margin-bottom: 28px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.brand-divider {
  width: 40px;
  height: 2px;
  background: var(--c-primary);
  border-radius: 1px;
  margin-bottom: 28px;
}

.feature-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 40px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: rgba(255,255,255,.65);
}

.feature-icon {
  width: 28px;
  height: 28px;
  background: rgba(13,148,136,.15);
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-primary);
  flex-shrink: 0;
}

.brand-footer {
  font-size: 12px;
  color: rgba(255,255,255,.2);
}

/* Right form panel */
.form-panel {
  flex: 1;
  background: var(--c-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
}

.form-box {
  width: 100%;
  max-width: 400px;
}

.form-header {
  margin-bottom: 28px;
}

.form-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--c-text);
  margin-bottom: 4px;
}

.form-header p {
  font-size: 13px;
  color: var(--c-text-2);
}

/* Tab bar */
.tab-bar {
  display: flex;
  background: var(--c-border);
  border-radius: var(--r);
  padding: 3px;
  margin-bottom: 24px;
  gap: 2px;
}

.tab-btn {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--r-sm);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font);
  color: var(--c-text-2);
  cursor: pointer;
  transition: all .15s;
}

.tab-btn.active {
  background: var(--c-surface);
  color: var(--c-text);
  box-shadow: var(--shadow-sm);
}

/* Form fields */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field label {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-2);
  margin: 0;
}

.required { color: var(--c-error); }
.optional { font-weight: 400; color: var(--c-text-3); font-size: 12px; }

.input-wrap {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--c-text-3);
  pointer-events: none;
}

.input-wrap input {
  width: 100%;
  height: 38px;
  padding: 0 12px 0 34px;
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  font-size: 14px;
  font-family: var(--font);
  color: var(--c-text);
  background: var(--c-surface);
  transition: border-color .15s, box-shadow .15s;
}

.input-wrap input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(13,148,136,.12);
}

.input-wrap input::placeholder { color: var(--c-text-3); }

/* Messages */
.form-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 9px 12px;
  border-radius: var(--r);
}

.form-msg.error {
  background: var(--c-error-bg);
  color: var(--c-error);
  border: 1px solid #FECACA;
}

.form-msg.success {
  background: var(--c-success-bg);
  color: var(--c-success);
  border: 1px solid #A7F3D0;
}

/* Submit button */
.btn-submit {
  width: 100%;
  height: 40px;
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--r);
  font-size: 14px;
  font-weight: 600;
  font-family: var(--font);
  cursor: pointer;
  transition: background .15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
}

.btn-submit:hover:not(:disabled) { background: var(--c-primary-dark); }
.btn-submit:disabled { opacity: .6; cursor: not-allowed; }

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin .7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.switch-hint {
  text-align: center;
  font-size: 13px;
  color: var(--c-text-2);
}

.switch-hint a {
  color: var(--c-primary);
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
}

.switch-hint a:hover { text-decoration: underline; }

@media (max-width: 768px) {
  .brand-panel { display: none; }
  .form-panel { background: var(--c-surface); }
}
</style>
