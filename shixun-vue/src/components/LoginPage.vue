<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ login: [user: User] }>()

const lang = ref<'en' | 'zh'>('zh')
const toggleLang = () => { lang.value = lang.value === 'en' ? 'zh' : 'en' }

const translations = {
  en: {
    navBrand: 'SmartFarm',
    navLogin: 'Log In',
    navSignup: 'Sign Up',
    heroEyebrow: 'INTELLIGENT LIVESTOCK MANAGEMENT',
    heroTitle1: 'Smart Farm',
    heroTitle2: 'Management',
    heroSub: 'Complete digital solution for modern livestock farming —\nfrom individual tracking to full-chain traceability.',
    heroCta: 'Get Started',
    scrollHint: 'Explore Features',

    f1Badge: 'Animal Records',
    f1Title1: 'Complete Animal',
    f1Title2: 'Lifecycle Tracking',
    f1Desc: 'Every animal receives a unique ear tag from day one. Track breed, pen location, entry date, health status, and the full history of transfers, vaccinations, and treatments — all in one place.',
    f1p1: 'Auto-generated unique ear tag numbers',
    f1p2: 'Real-time stock status (Active / Sold)',
    f1p3: 'Full pen transfer audit trail',

    f2Badge: 'Traceability',
    f2Title1: 'End-to-End',
    f2Title2: 'Chain Traceability',
    f2Desc: 'Query the complete life history of any individual or batch through ear tag or batch code. Every event — entry, vaccination, transfer, slaughter — is timestamped and permanently recorded.',
    f2p1: 'Search by ear tag or batch code',
    f2p2: 'Full chronological event timeline',
    f2p3: 'Food safety compliance ready',

    f3Badge: 'Health & Safety',
    f3Title1: 'Vaccination &',
    f3Title2: 'Medication Records',
    f3Desc: 'Schedule and log every vaccination and treatment. Operators are auto-bound to their account — no manual entry, no errors. Automated alerts surface animals overdue for immunization.',
    f3p1: 'Operator identity locked to login account',
    f3p2: 'Standardized drug & vaccine library',
    f3p3: '30-day immunization due alerts',

    f4Badge: 'Analytics',
    f4Title1: 'Real-Time Data',
    f4Title2: 'Visualization',
    f4Desc: 'Monitor entry vs exit trends, pen utilization, and immunization coverage through interactive charts. The dashboard instantly shows your entry, stock, and exit balance at a glance.',
    f4p1: 'Entry = Stock + Exit balance equation',
    f4p2: '6-month trend charts with ECharts',
    f4p3: 'Pen capacity utilization overview',

    ctaTitle1: 'Ready to modernize',
    ctaTitle2: 'your livestock operation?',
    ctaSub: 'Join hundreds of farms already running on SmartFarm.',
    ctaPrimary: 'Create Free Account',
    ctaGhost: 'Log In',

    footer1: '© 2026 SmartFarm Management Platform',
    footer2: 'Digital Agriculture Solutions',

    modalWelcomeLogin: 'Welcome back',
    modalWelcomeReg: 'Create your account',
    modalTabLogin: 'Log In',
    modalTabReg: 'Sign Up',
    fieldUsername: 'Username',
    fieldPwd: 'Password',
    fieldAge: 'Age',
    fieldEmail: 'Email',
    fieldPhone: 'Phone',
    fieldPhoneOpt: 'Optional',
    fieldConfirm: 'Confirm',
    fieldUserPh: 'Enter username',
    fieldPwdPh: 'Enter password',
    fieldAgePh: 'Age',
    fieldEmailPh: 'Email address',
    fieldPhonePh: 'Phone number',
    fieldConfirmPh: 'Repeat password',
    fieldNewPwdPh: 'Password',
    submitLogin: 'Sign In',
    submittingLogin: 'Signing in…',
    submitReg: 'Create Account',
    submittingReg: 'Creating account…',
    switchToReg: "No account?",
    switchToRegLink: "Sign up free",
    switchToLogin: "Already have an account?",
    switchToLoginLink: "Log in",

    tlTitle: 'Traceability Report',
    analyticsTitle: 'Monthly Overview',
    analyticsRange: 'Last 6 months',
  },
  zh: {
    navBrand: 'SmartFarm',
    navLogin: '登 录',
    navSignup: '注 册',
    heroEyebrow: '智慧畜牧养殖管理系统',
    heroTitle1: '智慧养殖',
    heroTitle2: '管理平台',
    heroSub: '面向现代农业的全链路数字化解决方案 —\n从个体档案管理到全链路溯源追踪。',
    heroCta: '立即开始',
    scrollHint: '探索功能',

    f1Badge: '个体档案',
    f1Title1: '完整个体',
    f1Title2: '全生命周期追踪',
    f1Desc: '每头牲畜从入栏起即分配唯一耳标编号，追踪品种、圈舍位置、入栏日期、健康状态，以及调转、免疫和治疗的完整历史记录。',
    f1p1: '系统自动分配唯一耳标编号',
    f1p2: '实时存栏状态（在栏 / 已出栏）',
    f1p3: '完整圈舍调转审计记录',

    f2Badge: '全链路溯源',
    f2Title1: '端到端',
    f2Title2: '全链路溯源',
    f2Desc: '通过耳标号或批次号查询任意个体或批次的完整生命历程。每一次入栏、免疫、调转、出栏事件均带时间戳永久记录。',
    f2p1: '支持耳标号 / 批次号双向检索',
    f2p2: '完整时间轴事件记录',
    f2p3: '符合食品安全合规要求',

    f3Badge: '健康管理',
    f3Title1: '免疫接种与',
    f3Title2: '用药记录',
    f3Desc: '制定并记录每次免疫接种和治疗方案。操作员自动绑定当前登录账户，无需手动填写，杜绝人为错误，并自动提示逾期未接种个体。',
    f3p1: '操作员自动绑定登录账户，不可篡改',
    f3p2: '标准化药品 / 疫苗条目库',
    f3p3: '30天免疫到期智能提醒',

    f4Badge: '数据统计',
    f4Title1: '实时数据',
    f4Title2: '可视化分析',
    f4Desc: '通过交互式图表监控入栏与出栏趋势、圈舍利用率和免疫覆盖情况。仪表盘一眼呈现入栏、存栏、出栏的平衡关系。',
    f4p1: '入栏 = 存栏 + 出栏 平衡等式',
    f4p2: '近6个月趋势折线 / 柱状图',
    f4p3: '圈舍容量利用率总览',

    ctaTitle1: '准备好升级',
    ctaTitle2: '您的养殖管理了吗？',
    ctaSub: '已有众多养殖场在使用 SmartFarm 高效运营。',
    ctaPrimary: '免费创建账号',
    ctaGhost: '立即登录',

    footer1: '© 2026 智慧养殖管理平台',
    footer2: '数字农业解决方案',

    modalWelcomeLogin: '欢迎回来',
    modalWelcomeReg: '创建您的账号',
    modalTabLogin: '登 录',
    modalTabReg: '注 册',
    fieldUsername: '用户名',
    fieldPwd: '密码',
    fieldAge: '年龄',
    fieldEmail: '邮箱',
    fieldPhone: '手机号',
    fieldPhoneOpt: '选填',
    fieldConfirm: '确认密码',
    fieldUserPh: '请输入用户名',
    fieldPwdPh: '请输入密码',
    fieldAgePh: '年龄',
    fieldEmailPh: '电子邮箱',
    fieldPhonePh: '手机号码',
    fieldConfirmPh: '再次输入密码',
    fieldNewPwdPh: '设置密码',
    submitLogin: '登 录',
    submittingLogin: '登录中…',
    submitReg: '创建账号',
    submittingReg: '注册中…',
    switchToReg: '没有账号？',
    switchToRegLink: '免费注册',
    switchToLogin: '已有账号？',
    switchToLoginLink: '立即登录',

    tlTitle: '溯源报告',
    analyticsTitle: '月度概览',
    analyticsRange: '近6个月',
  },
}

const t = computed(() => translations[lang.value])

const modal = ref<'none' | 'login' | 'register'>('none')

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

function openModal(m: 'login' | 'register') {
  modal.value = m
  loginMsg.value = ''
  regMsg.value = ''
  regSuccess.value = false
  document.body.style.overflow = 'hidden'
}

function closeModal() {
  modal.value = 'none'
  document.body.style.overflow = ''
}

function switchModal(m: 'login' | 'register') {
  modal.value = m
  loginMsg.value = ''
  regMsg.value = ''
  regSuccess.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') closeModal()
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
      loginMsg.value = res.status === 401 ? 'Incorrect username or password' : `Login failed: ${text}`
      return
    }
    document.body.style.overflow = ''
    emit('login', await res.json())
  } catch {
    loginMsg.value = 'Network error, please try again'
  } finally {
    loginLoading.value = false
  }
}

async function register() {
  if (regLoading.value) return
  regMsg.value = ''
  regSuccess.value = false
  if (regPassword.value !== regConfirm.value) { regMsg.value = 'Passwords do not match'; return }
  if (regAge.value && (isNaN(Number(regAge.value)) || Number(regAge.value) <= 0)) { regMsg.value = 'Please enter a valid age'; return }
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
      regMsg.value = res.status === 409 ? 'Username already taken' : `Registration failed: ${text}`
      return
    }
    regSuccess.value = true
    regMsg.value = 'Account created! Redirecting to login…'
    username.value = regUsername.value
    password.value = ''
    setTimeout(() => switchModal('login'), 1500)
  } catch {
    regMsg.value = 'Network error, please try again'
  } finally {
    regLoading.value = false
  }
}

let scrollObserver: IntersectionObserver | null = null

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  scrollObserver = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        e.target.classList.add('visible')
        setTimeout(() => e.target.classList.add('floatable'), 1000)
        scrollObserver?.unobserve(e.target)
      }
    })
  }, { threshold: 0.1 })
  document.querySelectorAll('.anim-section').forEach(el => scrollObserver!.observe(el))
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  scrollObserver?.disconnect()
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="landing">

    <!-- ══════════════════════════════════════
         HERO — full-screen video section
    ══════════════════════════════════════ -->
    <section class="hero">
      <video class="hero-video" src="/login-bg.mp4" autoplay muted loop playsinline></video>
      <div class="hero-overlay"></div>
      <div class="hero-vignette"></div>

      <!-- Glass nav bar (only in hero) -->
      <nav class="glass-nav">
        <div class="nav-brand">
          <div class="nav-logo-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
          </div>
          <span class="nav-brand-name">SmartFarm</span>
        </div>
        <div class="nav-actions">
          <button class="nav-lang-toggle" @click="toggleLang">
            {{ lang === 'zh' ? 'EN' : '中文' }}
          </button>
          <button class="nav-btn-login" @click="openModal('login')">{{ t.navLogin }}</button>
          <button class="nav-btn-signup" @click="openModal('register')">{{ t.navSignup }}</button>
        </div>
      </nav>

      <!-- Hero content -->
      <div class="hero-body">
        <p class="hero-eyebrow">{{ t.heroEyebrow }}</p>
        <h1 class="hero-title">
          {{ t.heroTitle1 }}<br/>
          <span class="hero-title-accent">{{ t.heroTitle2 }}</span>
        </h1>
        <p class="hero-sub" style="white-space:pre-line">{{ t.heroSub }}</p>
        <button class="hero-cta" @click="openModal('login')">
          {{ t.heroCta }}
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </button>
      </div>

      <!-- Scroll indicator -->
      <div class="scroll-hint">
        <span>{{ t.scrollHint }}</span>
        <div class="scroll-chevron">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
      </div>
    </section>

    <!-- ══════════════════════════════════════
         FEATURES — light scrollable sections
    ══════════════════════════════════════ -->
    <div class="features-wrap">

      <!-- Feature 1 — Individual Animal Records -->
      <section class="feature-section feature-section--dark feature-section--video anim-section">
        <video class="feat-video" src="/feature-1.mp4" autoplay muted loop playsinline></video>
        <div class="feat-overlay"></div>
        <div class="feature-content">
          <div class="feature-badge anim-child" style="--d:0s">{{ t.f1Badge }}</div>
          <h2 class="feature-title anim-title" style="--d:0.12s">{{ t.f1Title1 }}<br/>{{ t.f1Title2 }}</h2>
          <p class="feature-desc anim-child" style="--d:0.24s">{{ t.f1Desc }}</p>
          <ul class="feature-points">
            <li class="anim-child" style="--d:0.34s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f1p1 }}</li>
            <li class="anim-child" style="--d:0.44s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f1p2 }}</li>
            <li class="anim-child" style="--d:0.54s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f1p3 }}</li>
          </ul>
        </div>
        <div class="feature-visual">
          <!-- Mock animal profile card -->
          <div class="mock-card mock-profile">
            <div class="mock-card-header">
              <div class="mock-tag-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
              </div>
              <span class="mock-ear-tag">ET20260610001</span>
              <span class="mock-status-badge active">Active</span>
            </div>
            <div class="mock-divider"></div>
            <div class="mock-rows">
              <div class="mock-row">
                <span class="mock-row-label">Breed</span>
                <span class="mock-row-val">Duroc Pig</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">Current Pen</span>
                <span class="mock-row-val">Pen A-01</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">Entry Date</span>
                <span class="mock-row-val">2026-01-15</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">Batch</span>
                <span class="mock-row-val">BATCH20260115</span>
              </div>
            </div>
            <div class="mock-actions">
              <div class="mock-action-btn">Health Records</div>
              <div class="mock-action-btn">Transfer</div>
            </div>
          </div>
        </div>
      </section>

      <!-- Feature 2 — Full Traceability (reversed) -->
      <section class="feature-section feature-section--alt feature-section--dark feature-section--video anim-section">
        <video class="feat-video" src="/feature-2.mp4" autoplay muted loop playsinline></video>
        <div class="feat-overlay"></div>
        <div class="feature-visual">
          <!-- Mock timeline -->
          <div class="mock-card mock-timeline">
            <div class="mock-tl-title">{{ t.tlTitle }}</div>
            <div class="mock-tl-tag">ET20260610001</div>
            <div class="mock-timeline-list">
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-green"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Entry Recorded</span>
                  <span class="mock-tl-meta">Jan 15, 2026 · Pen A-01</span>
                </div>
              </div>
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-blue"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Vaccination</span>
                  <span class="mock-tl-meta">Feb 01, 2026 · Swine Flu Vaccine</span>
                </div>
              </div>
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-amber"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Pen Transfer</span>
                  <span class="mock-tl-meta">Mar 10, 2026 · Pen A-01 → B-02</span>
                </div>
              </div>
              <div class="mock-tl-item mock-tl-last">
                <div class="mock-tl-dot tl-gray"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Slaughter</span>
                  <span class="mock-tl-meta">Apr 20, 2026</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="feature-content">
          <div class="feature-badge feature-badge--teal anim-child" style="--d:0s">{{ t.f2Badge }}</div>
          <h2 class="feature-title anim-title" style="--d:0.12s">{{ t.f2Title1 }}<br/>{{ t.f2Title2 }}</h2>
          <p class="feature-desc anim-child" style="--d:0.24s">{{ t.f2Desc }}</p>
          <ul class="feature-points">
            <li class="anim-child" style="--d:0.34s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f2p1 }}</li>
            <li class="anim-child" style="--d:0.44s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f2p2 }}</li>
            <li class="anim-child" style="--d:0.54s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f2p3 }}</li>
          </ul>
        </div>
      </section>

      <!-- Feature 3 — Health Management -->
      <section class="feature-section feature-section--dark feature-section--dark-blue anim-section">
        <div class="feature-content">
          <div class="feature-badge feature-badge--blue anim-child" style="--d:0s">{{ t.f3Badge }}</div>
          <h2 class="feature-title anim-title" style="--d:0.12s">{{ t.f3Title1 }}<br/>{{ t.f3Title2 }}</h2>
          <p class="feature-desc anim-child" style="--d:0.24s">{{ t.f3Desc }}</p>
          <ul class="feature-points">
            <li class="anim-child" style="--d:0.34s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f3p1 }}</li>
            <li class="anim-child" style="--d:0.44s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f3p2 }}</li>
            <li class="anim-child" style="--d:0.54s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f3p3 }}</li>
          </ul>
        </div>
        <div class="feature-visual">
          <!-- Mock health records stack -->
          <div class="mock-health-stack">
            <div class="mock-health-card">
              <div class="mock-health-icon blue">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">Vaccination</span>
                <span class="mock-health-meta">Mar 15, 2026 · admin</span>
                <span class="mock-health-drug">Swine Influenza Vaccine</span>
              </div>
              <span class="mock-health-status done">Done</span>
            </div>
            <div class="mock-health-card mock-health-card--offset">
              <div class="mock-health-icon amber">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.5 20H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h3.93a2 2 0 0 1 1.66.9l.82 1.2a2 2 0 0 0 1.66.9H20a2 2 0 0 1 2 2v3"/><circle cx="18" cy="18" r="3"/><path d="m22 22-1.5-1.5"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">Medication</span>
                <span class="mock-health-meta">Mar 22, 2026 · admin</span>
                <span class="mock-health-drug">Amoxicillin · 500mg</span>
              </div>
              <span class="mock-health-status done">Done</span>
            </div>
            <div class="mock-health-card mock-health-card--offset2">
              <div class="mock-health-icon teal">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">Vaccination Due</span>
                <span class="mock-health-meta">Apr 01, 2026 · Scheduled</span>
                <span class="mock-health-drug">Foot-and-Mouth Vaccine</span>
              </div>
              <span class="mock-health-status pending">Due</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Feature 4 — Analytics (reversed) -->
      <section class="feature-section feature-section--alt feature-section--dark feature-section--dark-purple anim-section">
        <div class="feature-visual">
          <!-- Mock analytics dashboard -->
          <div class="mock-card mock-analytics">
            <div class="mock-analytics-header">
              <span class="mock-analytics-title">{{ t.analyticsTitle }}</span>
              <span class="mock-analytics-range">{{ t.analyticsRange }}</span>
            </div>
            <div class="mock-chart-area">
              <div class="mock-chart-bars">
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:38%"></div>
                  <div class="mock-bar exit"  style="--h:20%"></div>
                  <span class="mock-bar-label">Jan</span>
                </div>
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:55%"></div>
                  <div class="mock-bar exit"  style="--h:30%"></div>
                  <span class="mock-bar-label">Feb</span>
                </div>
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:70%"></div>
                  <div class="mock-bar exit"  style="--h:48%"></div>
                  <span class="mock-bar-label">Mar</span>
                </div>
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:60%"></div>
                  <div class="mock-bar exit"  style="--h:55%"></div>
                  <span class="mock-bar-label">Apr</span>
                </div>
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:82%"></div>
                  <div class="mock-bar exit"  style="--h:40%"></div>
                  <span class="mock-bar-label">May</span>
                </div>
                <div class="mock-bar-group">
                  <div class="mock-bar entry" style="--h:90%"></div>
                  <div class="mock-bar exit"  style="--h:62%"></div>
                  <span class="mock-bar-label">Jun</span>
                </div>
              </div>
            </div>
            <div class="mock-chart-legend">
              <span class="mock-legend-dot entry"></span> Entry
              <span class="mock-legend-dot exit" style="margin-left:12px"></span> Exit
            </div>
            <div class="mock-stat-row">
              <div class="mock-stat">
                <span class="mock-stat-num green">+124</span>
                <span class="mock-stat-lbl">Entered</span>
              </div>
              <div class="mock-stat">
                <span class="mock-stat-num orange">−68</span>
                <span class="mock-stat-lbl">Exited</span>
              </div>
              <div class="mock-stat">
                <span class="mock-stat-num blue">56</span>
                <span class="mock-stat-lbl">In Stock</span>
              </div>
            </div>
          </div>
        </div>
        <div class="feature-content">
          <div class="feature-badge feature-badge--purple anim-child" style="--d:0s">{{ t.f4Badge }}</div>
          <h2 class="feature-title anim-title" style="--d:0.12s">{{ t.f4Title1 }}<br/>{{ t.f4Title2 }}</h2>
          <p class="feature-desc anim-child" style="--d:0.24s">{{ t.f4Desc }}</p>
          <ul class="feature-points">
            <li class="anim-child" style="--d:0.34s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f4p1 }}</li>
            <li class="anim-child" style="--d:0.44s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f4p2 }}</li>
            <li class="anim-child" style="--d:0.54s"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>{{ t.f4p3 }}</li>
          </ul>
        </div>
      </section>

    </div>

    <!-- CTA section -->
    <section class="cta-section anim-section">
      <h2 class="cta-title">{{ t.ctaTitle1 }}<br/>{{ t.ctaTitle2 }}</h2>
      <p class="cta-sub">{{ t.ctaSub }}</p>
      <div class="cta-actions">
        <button class="cta-btn-primary" @click="openModal('register')">{{ t.ctaPrimary }}</button>
        <button class="cta-btn-ghost" @click="openModal('login')">{{ t.ctaGhost }}</button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="site-footer">
      <span>{{ t.footer1 }}</span>
      <span class="footer-dot">·</span>
      <span>{{ t.footer2 }}</span>
    </footer>

    <!-- ══════════════════════════════════════
         MODAL OVERLAY
    ══════════════════════════════════════ -->
    <Transition name="modal-fade">
      <div v-if="modal !== 'none'" class="modal-backdrop" @click.self="closeModal">
        <Transition name="modal-slide" appear>
          <div class="modal-box">
            <button class="modal-close-btn" @click="closeModal" aria-label="Close">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <div class="modal-brand">
              <div class="modal-brand-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
              </div>
              <span>SmartFarm</span>
            </div>

            <div class="modal-tabs">
              <button :class="['modal-tab', { active: modal === 'login' }]" @click="switchModal('login')">{{ t.modalTabLogin }}</button>
              <button :class="['modal-tab', { active: modal === 'register' }]" @click="switchModal('register')">{{ t.modalTabReg }}</button>
            </div>

            <p class="modal-welcome">
              {{ modal === 'login' ? t.modalWelcomeLogin : t.modalWelcomeReg }}
            </p>

            <!-- Login form -->
            <form v-if="modal === 'login'" @submit.prevent="login" class="modal-form">
              <div class="mfield">
                <label>{{ t.fieldUsername }}</label>
                <input v-model="username" :placeholder="t.fieldUserPh" required autocomplete="username" />
              </div>
              <div class="mfield">
                <label>{{ t.fieldPwd }}</label>
                <input v-model="password" type="password" :placeholder="t.fieldPwdPh" required autocomplete="current-password" />
              </div>
              <div v-if="loginMsg" class="modal-msg error">{{ loginMsg }}</div>
              <button type="submit" class="modal-submit" :disabled="loginLoading">
                <span v-if="loginLoading" class="spinner"></span>
                {{ loginLoading ? t.submittingLogin : t.submitLogin }}
              </button>
              <p class="modal-switch">{{ t.switchToReg }} <a @click="switchModal('register')">{{ t.switchToRegLink }}</a></p>
            </form>

            <!-- Register form -->
            <form v-else @submit.prevent="register" class="modal-form">
              <div class="mfield-row">
                <div class="mfield">
                  <label>{{ t.fieldUsername }} <span class="req">*</span></label>
                  <input v-model="regUsername" :placeholder="t.fieldUserPh" required autocomplete="username" />
                </div>
                <div class="mfield">
                  <label>{{ t.fieldAge }} <span class="req">*</span></label>
                  <input v-model="regAge" type="number" :placeholder="t.fieldAgePh" min="1" max="150" required />
                </div>
              </div>
              <div class="mfield">
                <label>{{ t.fieldEmail }} <span class="req">*</span></label>
                <input v-model="regEmail" type="email" :placeholder="t.fieldEmailPh" required autocomplete="email" />
              </div>
              <div class="mfield">
                <label>{{ t.fieldPhone }} <span class="opt">{{ t.fieldPhoneOpt }}</span></label>
                <input v-model="regPhone" :placeholder="t.fieldPhonePh" autocomplete="tel" />
              </div>
              <div class="mfield-row">
                <div class="mfield">
                  <label>{{ t.fieldPwd }} <span class="req">*</span></label>
                  <input v-model="regPassword" type="password" :placeholder="t.fieldNewPwdPh" required autocomplete="new-password" />
                </div>
                <div class="mfield">
                  <label>{{ t.fieldConfirm }} <span class="req">*</span></label>
                  <input v-model="regConfirm" type="password" :placeholder="t.fieldConfirmPh" required autocomplete="new-password" />
                </div>
              </div>
              <div v-if="regMsg" :class="['modal-msg', regSuccess ? 'success' : 'error']">{{ regMsg }}</div>
              <button type="submit" class="modal-submit" :disabled="regLoading">
                <span v-if="regLoading" class="spinner"></span>
                {{ regLoading ? t.submittingReg : t.submitReg }}
              </button>
              <p class="modal-switch">{{ t.switchToLogin }} <a @click="switchModal('login')">{{ t.switchToLoginLink }}</a></p>
            </form>
          </div>
        </Transition>
      </div>
    </Transition>

  </div>
</template>

<style scoped>
/* ── Reset ── */
.landing {
  font-family: var(--font);
  color: #0f172a;
}

/* ══════════════════════════════════════
   HERO
══════════════════════════════════════ */
.hero {
  position: relative;
  height: 100vh;
  min-height: 600px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.hero-video {
  position: absolute;
  inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  object-position: center;
  transform: scale(1.04);
  z-index: 0;
}

.hero-overlay {
  position: absolute; inset: 0; z-index: 1;
  background: rgba(2, 8, 20, 0.60);
}

.hero-vignette {
  position: absolute; inset: 0; z-index: 1;
  background: radial-gradient(ellipse at center, transparent 45%, rgba(0,0,0,0.65) 100%);
  pointer-events: none;
}

/* Glass nav */
.glass-nav {
  position: relative; z-index: 10;
  display: flex; align-items: center; justify-content: space-between;
  margin: 20px 28px 0;
  padding: 12px 20px;
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(20px) saturate(150%);
  -webkit-backdrop-filter: blur(20px) saturate(150%);
  border: 1px solid rgba(255,255,255,0.14);
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.2);
}

.nav-brand {
  display: flex; align-items: center; gap: 10px;
}

.nav-logo-icon {
  width: 34px; height: 34px; border-radius: 9px;
  background: var(--c-primary);
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  box-shadow: 0 0 16px rgba(13,148,136,0.55);
}

.nav-brand-name {
  font-size: 16px; font-weight: 700; color: #fff; letter-spacing: .3px;
}

.nav-actions { display: flex; gap: 8px; }

.nav-lang-toggle {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.28);
  border-radius: 8px;
  color: rgba(255,255,255,0.75);
  font-size: 12px; font-weight: 700; font-family: var(--font);
  letter-spacing: .5px; cursor: pointer;
  transition: background .15s, color .15s, border-color .15s;
}
.nav-lang-toggle:hover {
  background: rgba(255,255,255,0.12);
  color: #fff;
  border-color: rgba(255,255,255,0.45);
}

.nav-btn-login {
  padding: 7px 18px;
  background: rgba(255,255,255,0.10);
  border: 1px solid rgba(255,255,255,0.20);
  border-radius: 8px;
  color: #fff;
  font-size: 13px; font-weight: 600; font-family: var(--font);
  cursor: pointer; letter-spacing: .3px;
  transition: background .15s, border-color .15s;
}
.nav-btn-login:hover { background: rgba(255,255,255,0.18); border-color: rgba(255,255,255,0.35); }

.nav-btn-signup {
  padding: 7px 18px;
  background: var(--c-primary);
  border: 1px solid transparent;
  border-radius: 8px;
  color: #fff;
  font-size: 13px; font-weight: 600; font-family: var(--font);
  cursor: pointer; letter-spacing: .3px;
  box-shadow: 0 0 16px rgba(13,148,136,0.4);
  transition: opacity .15s, box-shadow .15s;
}
.nav-btn-signup:hover { opacity: .88; box-shadow: 0 0 24px rgba(13,148,136,0.6); }

/* Hero body */
.hero-body {
  position: relative; z-index: 10;
  flex: 1;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  text-align: center;
  padding: 0 24px;
}

.hero-eyebrow {
  font-size: 11px; font-weight: 700; letter-spacing: 3px;
  color: #5eead4; margin: 0 0 20px;
}

.hero-title {
  font-size: clamp(44px, 8vw, 86px);
  font-weight: 800; line-height: 1.06;
  color: #fff; margin: 0 0 22px;
  letter-spacing: -1px;
  text-shadow: 0 4px 32px rgba(0,0,0,.5);
}

.hero-title-accent {
  background: linear-gradient(135deg, #5eead4, #0891b2);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-sub {
  font-size: 16px; color: rgba(255,255,255,.62);
  line-height: 1.7; max-width: 520px;
  margin: 0 auto 36px;
}

.hero-cta {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 14px 32px;
  background: linear-gradient(135deg, #0d9488, #0891b2);
  color: #fff; border: none; border-radius: 12px;
  font-size: 15px; font-weight: 700; font-family: var(--font);
  letter-spacing: .5px; cursor: pointer;
  box-shadow: 0 6px 28px rgba(13,148,136,0.5);
  transition: transform .15s, box-shadow .15s, opacity .15s;
}
.hero-cta:hover { transform: translateY(-2px); box-shadow: 0 10px 36px rgba(13,148,136,0.6); }
.hero-cta:active { transform: translateY(0); }

/* Scroll hint */
.scroll-hint {
  position: relative; z-index: 10;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 0 0 28px;
  color: rgba(255,255,255,.35); font-size: 11px; letter-spacing: 1px;
}

.scroll-chevron {
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50%       { transform: translateY(6px); }
}

/* ══════════════════════════════════════
   FEATURES
══════════════════════════════════════ */
.features-wrap {
  background: #07101f;
  padding: 40px 20px 0;
}

.feature-section {
  display: flex;
  align-items: center;
  gap: 72px;
  max-width: 1080px;
  margin: 0 auto 20px;
  padding: 88px 40px;
  position: relative;
  overflow: hidden;
  border-radius: 24px;
}

.feature-section--alt {
  flex-direction: row-reverse;
}

/* ── Video background ── */
.feat-video {
  position: absolute;
  inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  object-position: center;
  z-index: 0;
  transform: scale(1.04);
}
.feat-overlay {
  position: absolute;
  inset: 0;
  background: rgba(4, 10, 22, 0.72);
  z-index: 1;
}

/* ── Dark CSS gradient sections (3 & 4) ── */
.feature-section--dark-blue {
  background: linear-gradient(135deg, #060e25 0%, #0a1a40 55%, #061828 100%);
}
.feature-section--dark-blue::before {
  content: '';
  position: absolute; inset: 0; z-index: 0;
  background:
    radial-gradient(ellipse 60% 50% at 85% 20%, rgba(59,130,246,.18) 0%, transparent 70%),
    radial-gradient(ellipse 40% 60% at 10% 80%, rgba(13,148,136,.12) 0%, transparent 70%);
  pointer-events: none;
}
.feature-section--dark-blue::after {
  content: '';
  position: absolute; inset: 0; z-index: 0;
  background-image:
    linear-gradient(rgba(59,130,246,.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59,130,246,.04) 1px, transparent 1px);
  background-size: 44px 44px;
  pointer-events: none;
}

.feature-section--dark-purple {
  background: linear-gradient(135deg, #0a0618 0%, #160826 55%, #0d0a20 100%);
}
.feature-section--dark-purple::before {
  content: '';
  position: absolute; inset: 0; z-index: 0;
  background:
    radial-gradient(ellipse 55% 60% at 15% 25%, rgba(139,92,246,.2) 0%, transparent 65%),
    radial-gradient(ellipse 45% 45% at 88% 75%, rgba(99,102,241,.15) 0%, transparent 65%);
  pointer-events: none;
}
.feature-section--dark-purple::after {
  content: '';
  position: absolute; inset: 0; z-index: 0;
  background-image: radial-gradient(circle, rgba(139,92,246,.06) 1px, transparent 1px);
  background-size: 28px 28px;
  pointer-events: none;
}

/* All content inside dark sections sits above bg decorations */
.feature-section--dark .feature-content,
.feature-section--dark .feature-visual {
  position: relative;
  z-index: 2;
}

/* ── Dark section: text overrides ── */
.feature-section--dark .feature-title  { color: #f1f5f9; }
.feature-section--dark .feature-desc   { color: rgba(255,255,255,.62); }
.feature-section--dark .feature-points li { color: rgba(255,255,255,.8); }
.feature-section--dark .feature-points li svg { color: #5eead4; }

.feature-section--dark .feature-badge {
  background: rgba(34,197,94,.14); color: #4ade80;
  border-color: rgba(34,197,94,.28);
}
.feature-section--dark .feature-badge--teal {
  background: rgba(20,184,166,.14); color: #2dd4bf;
  border-color: rgba(20,184,166,.28);
}
.feature-section--dark .feature-badge--blue {
  background: rgba(96,165,250,.14); color: #93c5fd;
  border-color: rgba(96,165,250,.28);
}
.feature-section--dark .feature-badge--purple {
  background: rgba(167,139,250,.14); color: #c4b5fd;
  border-color: rgba(167,139,250,.28);
}

/* ── Dark section: mock card glass ── */
.feature-section--dark .mock-card {
  background: rgba(255,255,255,.07);
  backdrop-filter: blur(22px) saturate(160%);
  -webkit-backdrop-filter: blur(22px) saturate(160%);
  border: 1px solid rgba(255,255,255,.13);
  box-shadow: 0 8px 48px rgba(0,0,0,.4), inset 0 1px 0 rgba(255,255,255,.07);
}
.feature-section--dark .mock-ear-tag        { color: #e2e8f0; }
.feature-section--dark .mock-row-label      { color: rgba(255,255,255,.38); }
.feature-section--dark .mock-row-val        { color: rgba(255,255,255,.85); }
.feature-section--dark .mock-divider        { background: rgba(255,255,255,.1); }
.feature-section--dark .mock-action-btn {
  background: rgba(255,255,255,.08);
  border-color: rgba(255,255,255,.15);
  color: rgba(255,255,255,.6);
}
.feature-section--dark .mock-tag-icon { background: rgba(13,148,136,.25); color: #5eead4; }

/* Timeline on dark */
.feature-section--dark .mock-tl-title { color: rgba(255,255,255,.38); }
.feature-section--dark .mock-tl-tag   { color: #5eead4; }
.feature-section--dark .mock-tl-event { color: rgba(255,255,255,.88); }
.feature-section--dark .mock-tl-meta  { color: rgba(255,255,255,.38); }
.feature-section--dark .mock-tl-line  { background: rgba(255,255,255,.12); }

/* Health cards on dark */
.feature-section--dark .mock-health-card {
  background: rgba(255,255,255,.08);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border-color: rgba(255,255,255,.12);
  box-shadow: 0 4px 20px rgba(0,0,0,.3);
}
.feature-section--dark .mock-health-type { color: rgba(255,255,255,.9); }
.feature-section--dark .mock-health-meta { color: rgba(255,255,255,.35); }
.feature-section--dark .mock-health-drug { color: rgba(255,255,255,.55); }

/* Analytics on dark */
.feature-section--dark .mock-analytics-title { color: #f1f5f9; }
.feature-section--dark .mock-analytics-range { color: rgba(255,255,255,.38); }
.feature-section--dark .mock-chart-legend    { color: rgba(255,255,255,.55); }
.feature-section--dark .mock-stat-lbl        { color: rgba(255,255,255,.38); }
.feature-section--dark .mock-stat-row { border-color: rgba(255,255,255,.08); }

/* ── Entrance: feature-visual (scale + lift) ── */
.anim-section .feature-visual {
  opacity: 0;
  transform: scale(0.92) translateY(30px);
  transition: opacity 0.85s cubic-bezier(0.16,1,0.3,1) 0.2s,
              transform 0.85s cubic-bezier(0.16,1,0.3,1) 0.2s;
}
.anim-section.visible .feature-visual {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* ── Staggered children (badge, desc, bullets) ── */
.anim-child {
  opacity: 0;
  transform: translateY(22px);
  transition: opacity 0.65s cubic-bezier(0.16,1,0.3,1) var(--d, 0s),
              transform 0.65s cubic-bezier(0.16,1,0.3,1) var(--d, 0s);
}
.anim-section.visible .anim-child {
  opacity: 1;
  transform: translateY(0);
}

/* ── Clip-path title reveal ── */
.anim-title {
  clip-path: inset(0 100% 0 0);
  transition: clip-path 0.9s cubic-bezier(0.16,1,0.3,1) var(--d, 0.12s);
}
.anim-section.visible .anim-title {
  clip-path: inset(0 0% 0 0);
}

/* ── Floating card (starts after entrance) ── */
@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50%       { transform: translateY(-9px); }
}
.floatable .mock-card        { animation: float 4s ease-in-out infinite; }
.floatable .mock-health-stack { animation: float 4.6s ease-in-out infinite; }

/* ── Timeline: sequential slide-in per item ── */
.anim-section .mock-tl-item {
  opacity: 0;
  transform: translateX(-14px);
  transition: opacity 0.45s ease, transform 0.45s cubic-bezier(0.16,1,0.3,1);
}
.anim-section .mock-tl-item:nth-child(1) { transition-delay: 0.32s; }
.anim-section .mock-tl-item:nth-child(2) { transition-delay: 0.52s; }
.anim-section .mock-tl-item:nth-child(3) { transition-delay: 0.72s; }
.anim-section .mock-tl-item:nth-child(4) { transition-delay: 0.92s; }
.anim-section.visible .mock-tl-item { opacity: 1; transform: translateX(0); }

/* Timeline dots: bouncy pop-in */
.anim-section .mock-tl-dot {
  transform: scale(0);
  transition: transform 0.4s cubic-bezier(0.34,1.56,0.64,1);
}
.anim-section .mock-tl-item:nth-child(1) .mock-tl-dot { transition-delay: 0.38s; }
.anim-section .mock-tl-item:nth-child(2) .mock-tl-dot { transition-delay: 0.58s; }
.anim-section .mock-tl-item:nth-child(3) .mock-tl-dot { transition-delay: 0.78s; }
.anim-section .mock-tl-item:nth-child(4) .mock-tl-dot { transition-delay: 0.98s; }
.anim-section.visible .mock-tl-dot { transform: scale(1); }

/* ── Health cards: fan in with slight rotate ── */
.anim-section .mock-health-card {
  opacity: 0;
  transform: translateY(18px) rotate(2deg);
  transition: opacity 0.5s ease, transform 0.5s cubic-bezier(0.16,1,0.3,1);
}
.anim-section .mock-health-stack .mock-health-card:nth-child(1) { transition-delay: 0.30s; }
.anim-section .mock-health-stack .mock-health-card:nth-child(2) { transition-delay: 0.48s; }
.anim-section .mock-health-stack .mock-health-card:nth-child(3) { transition-delay: 0.66s; }
.anim-section.visible .mock-health-card {
  opacity: 1;
  transform: translateY(0) rotate(0deg);
}

.feature-content { flex: 1; min-width: 0; }
.feature-visual  { flex: 1; min-width: 0; display: flex; justify-content: center; }

.feature-badge {
  display: inline-block;
  padding: 4px 12px; border-radius: 20px;
  background: #f0fdf4; color: #16a34a;
  border: 1px solid #bbf7d0;
  font-size: 11px; font-weight: 700; letter-spacing: .8px;
  text-transform: uppercase; margin-bottom: 18px;
}
.feature-badge--teal  { background: #f0fdfa; color: #0f766e; border-color: #99f6e4; }
.feature-badge--blue  { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }
.feature-badge--purple{ background: #faf5ff; color: #7c3aed; border-color: #ddd6fe; }

.feature-title {
  font-size: clamp(28px, 3.5vw, 40px);
  font-weight: 800; line-height: 1.15;
  color: #0f172a; margin: 0 0 18px;
  letter-spacing: -.5px;
}

.feature-desc {
  font-size: 15px; color: #475569;
  line-height: 1.75; margin: 0 0 24px;
}

.feature-points {
  list-style: none; padding: 0; margin: 0;
  display: flex; flex-direction: column; gap: 10px;
}

.feature-points li {
  display: flex; align-items: center; gap: 10px;
  font-size: 14px; color: #334155; font-weight: 500;
}
.feature-points li svg { color: #0d9488; flex-shrink: 0; }

/* ── Mock Card Base ── */
.mock-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 22px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.07), 0 1px 4px rgba(0,0,0,0.04);
  width: 100%;
  max-width: 340px;
}

/* Mock: Animal Profile */
.mock-profile {}

.mock-card-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 14px;
}
.mock-tag-icon {
  width: 28px; height: 28px; border-radius: 7px;
  background: #f0fdfa; color: #0d9488;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.mock-ear-tag { font-size: 13px; font-weight: 700; color: #0f172a; flex: 1; font-family: 'Courier New', monospace; }
.mock-status-badge { font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 20px; }
.mock-status-badge.active { background: #dcfce7; color: #16a34a; }

.mock-divider { height: 1px; background: #f1f5f9; margin-bottom: 14px; }

.mock-rows { display: flex; flex-direction: column; gap: 9px; margin-bottom: 18px; }
.mock-row  { display: flex; justify-content: space-between; align-items: center; }
.mock-row-label { font-size: 11px; color: #94a3b8; font-weight: 500; }
.mock-row-val   { font-size: 12px; color: #334155; font-weight: 600; }

.mock-actions { display: flex; gap: 8px; }
.mock-action-btn {
  flex: 1; text-align: center; padding: 7px 0;
  border: 1px solid #e2e8f0; border-radius: 8px;
  font-size: 11px; font-weight: 600; color: #475569;
  cursor: default;
}

/* Mock: Timeline */
.mock-timeline { padding: 22px 22px 18px; }
.mock-tl-title { font-size: 12px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: .6px; margin-bottom: 4px; }
.mock-tl-tag { font-size: 13px; font-weight: 700; color: #0f172a; font-family: 'Courier New', monospace; margin-bottom: 18px; }

.mock-timeline-list { display: flex; flex-direction: column; }
.mock-tl-item {
  display: flex; align-items: flex-start; gap: 12px;
  position: relative; padding-bottom: 16px;
}
.mock-tl-last { padding-bottom: 0; }

.mock-tl-dot {
  width: 12px; height: 12px; border-radius: 50%;
  flex-shrink: 0; margin-top: 3px; z-index: 1;
}
.tl-green { background: #22c55e; }
.tl-blue  { background: #3b82f6; }
.tl-amber { background: #f59e0b; }
.tl-gray  { background: #cbd5e1; }

.mock-tl-line {
  position: absolute;
  left: 5px; top: 16px;
  width: 2px; height: calc(100% - 8px);
  background: #e2e8f0;
}

.mock-tl-content { display: flex; flex-direction: column; gap: 2px; }
.mock-tl-event { font-size: 13px; font-weight: 600; color: #1e293b; }
.mock-tl-meta  { font-size: 11px; color: #94a3b8; }

/* Mock: Health Stack */
.mock-health-stack { display: flex; flex-direction: column; gap: 0; width: 100%; max-width: 340px; }

.mock-health-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}
.mock-health-card--offset  { margin-top: -8px; margin-left: 16px; }
.mock-health-card--offset2 { margin-top: -8px; margin-left: 32px; }

.mock-health-icon {
  width: 36px; height: 36px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.mock-health-icon.blue   { background: #eff6ff; color: #2563eb; }
.mock-health-icon.amber  { background: #fffbeb; color: #d97706; }
.mock-health-icon.teal   { background: #f0fdfa; color: #0d9488; }

.mock-health-info { flex: 1; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.mock-health-type { font-size: 12px; font-weight: 700; color: #0f172a; }
.mock-health-meta { font-size: 10px; color: #94a3b8; }
.mock-health-drug { font-size: 11px; color: #475569; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.mock-health-status { font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 20px; flex-shrink: 0; }
.mock-health-status.done    { background: #dcfce7; color: #16a34a; }
.mock-health-status.pending { background: #fef3c7; color: #d97706; }

/* Mock: Analytics */
.mock-analytics { padding: 18px 20px; }
.mock-analytics-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.mock-analytics-title { font-size: 13px; font-weight: 700; color: #0f172a; }
.mock-analytics-range { font-size: 11px; color: #94a3b8; }

.mock-chart-area {
  height: 100px;
  display: flex; align-items: flex-end;
  margin-bottom: 8px;
}
.mock-chart-bars {
  display: flex; align-items: flex-end;
  gap: 8px; width: 100%; height: 100%;
}
.mock-bar-group {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: flex-end; gap: 3px; height: 100%;
}
.mock-bar {
  width: 100%; border-radius: 4px 4px 0 0;
  min-width: 12px;
  height: 0;
  transition: height 0.75s cubic-bezier(0.16,1,0.3,1);
}
.mock-bar.entry { background: #22c55e; opacity: .85; }
.mock-bar.exit  { background: #fb923c; opacity: .85; }

/* Bar grow on section visible — staggered by column */
.anim-section.visible .mock-bar-group:nth-child(1) .mock-bar { height: var(--h); transition-delay: 0.30s; }
.anim-section.visible .mock-bar-group:nth-child(2) .mock-bar { height: var(--h); transition-delay: 0.42s; }
.anim-section.visible .mock-bar-group:nth-child(3) .mock-bar { height: var(--h); transition-delay: 0.54s; }
.anim-section.visible .mock-bar-group:nth-child(4) .mock-bar { height: var(--h); transition-delay: 0.66s; }
.anim-section.visible .mock-bar-group:nth-child(5) .mock-bar { height: var(--h); transition-delay: 0.78s; }
.anim-section.visible .mock-bar-group:nth-child(6) .mock-bar { height: var(--h); transition-delay: 0.90s; }
.mock-bar-label { font-size: 9px; color: #94a3b8; }

.mock-chart-legend {
  display: flex; align-items: center;
  font-size: 11px; color: #64748b; margin-bottom: 14px;
}
.mock-legend-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px;
}
.mock-legend-dot.entry { background: #22c55e; }
.mock-legend-dot.exit  { background: #fb923c; }

.mock-stat-row { display: flex; gap: 0; border-top: 1px solid #f1f5f9; padding-top: 12px; }
.mock-stat { flex: 1; text-align: center; display: flex; flex-direction: column; gap: 2px; }
.mock-stat-num { font-size: 18px; font-weight: 800; }
.mock-stat-num.green  { color: #22c55e; }
.mock-stat-num.orange { color: #fb923c; }
.mock-stat-num.blue   { color: #3b82f6; }
.mock-stat-lbl { font-size: 10px; color: #94a3b8; font-weight: 500; }

/* ── CTA Section ── */
.cta-section {
  text-align: center;
  padding: 96px 32px;
  background: #fff;
  border-top: none;
  border-bottom: 1px solid #e2e8f0;
  position: relative;
  z-index: 2;
}

.cta-title {
  font-size: clamp(28px, 4vw, 44px);
  font-weight: 800; color: #0f172a;
  line-height: 1.2; margin: 0 0 16px;
  letter-spacing: -.5px;
}

.cta-sub {
  font-size: 16px; color: #64748b; margin: 0 0 36px;
}

.cta-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

.cta-btn-primary {
  padding: 14px 32px;
  background: linear-gradient(135deg, #0d9488, #0891b2);
  color: #fff; border: none; border-radius: 12px;
  font-size: 15px; font-weight: 700; font-family: var(--font);
  cursor: pointer; box-shadow: 0 4px 20px rgba(13,148,136,.4);
  transition: opacity .15s, transform .15s;
}
.cta-btn-primary:hover { opacity: .88; transform: translateY(-1px); }

.cta-btn-ghost {
  padding: 14px 32px;
  background: transparent;
  color: #0d9488; border: 1.5px solid #0d9488;
  border-radius: 12px;
  font-size: 15px; font-weight: 700; font-family: var(--font);
  cursor: pointer; transition: background .15s, color .15s;
}
.cta-btn-ghost:hover { background: #0d94880d; }

/* Footer */
.site-footer {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 24px;
  background: #fff;
  font-size: 12px; color: #94a3b8;
}
.footer-dot { opacity: .4; }

/* ══════════════════════════════════════
   MODAL
══════════════════════════════════════ */
.modal-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(2, 8, 20, 0.72);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  padding: 20px;
}

.modal-box {
  background: #fff;
  border-radius: 20px;
  padding: 32px;
  width: 100%; max-width: 420px;
  position: relative;
  box-shadow: 0 24px 80px rgba(0,0,0,0.35);
  max-height: 90vh;
  overflow-y: auto;
}

.modal-close-btn {
  position: absolute; top: 16px; right: 16px;
  width: 30px; height: 30px; border-radius: 8px;
  background: #f1f5f9; border: none; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #64748b; transition: background .15s;
}
.modal-close-btn:hover { background: #e2e8f0; }

.modal-brand {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 700; color: #0f172a;
  margin-bottom: 20px;
}
.modal-brand-icon {
  width: 28px; height: 28px; border-radius: 7px;
  background: #0d9488; color: #fff;
  display: flex; align-items: center; justify-content: center;
}

.modal-tabs {
  display: flex;
  background: #f1f5f9;
  border-radius: 10px;
  padding: 3px; gap: 3px;
  margin-bottom: 20px;
}
.modal-tab {
  flex: 1; height: 34px; border: none;
  background: transparent; border-radius: 8px;
  font-size: 13px; font-weight: 600; font-family: var(--font);
  color: #64748b; cursor: pointer; transition: all .15s;
}
.modal-tab.active {
  background: #fff; color: #0f172a;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}

.modal-welcome {
  font-size: 20px; font-weight: 700; color: #0f172a;
  margin: 0 0 20px;
}

.modal-form { display: flex; flex-direction: column; gap: 14px; }

.mfield { display: flex; flex-direction: column; gap: 5px; }
.mfield label { font-size: 12px; font-weight: 600; color: #475569; }
.mfield input {
  height: 40px;
  padding: 0 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px; font-family: var(--font); color: #0f172a;
  background: #fff;
  transition: border-color .15s, box-shadow .15s;
  box-sizing: border-box; width: 100%;
}
.mfield input::placeholder { color: #cbd5e1; }
.mfield input:focus {
  outline: none;
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13,148,136,.12);
}

.mfield-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

.req { color: #ef4444; }
.opt { font-weight: 400; color: #94a3b8; font-size: 11px; }

.modal-msg {
  font-size: 13px; padding: 9px 13px; border-radius: 9px;
}
.modal-msg.error   { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
.modal-msg.success { background: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; }

.modal-submit {
  width: 100%; height: 44px;
  background: linear-gradient(135deg, #0d9488, #0891b2);
  color: #fff; border: none; border-radius: 11px;
  font-size: 15px; font-weight: 700; font-family: var(--font);
  cursor: pointer; margin-top: 2px;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  box-shadow: 0 4px 16px rgba(13,148,136,.35);
  transition: opacity .15s, transform .15s;
}
.modal-submit:hover:not(:disabled) { opacity: .9; transform: translateY(-1px); }
.modal-submit:disabled { opacity: .5; cursor: not-allowed; }

.modal-switch {
  text-align: center; font-size: 13px; color: #64748b; margin: 0;
}
.modal-switch a { color: #0d9488; cursor: pointer; font-weight: 600; }
.modal-switch a:hover { text-decoration: underline; }

.spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,255,255,.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin .7s linear infinite; flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Modal transitions */
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity .2s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }

.modal-slide-enter-active { transition: opacity .25s ease, transform .25s cubic-bezier(0.16,1,0.3,1); }
.modal-slide-enter-from   { opacity: 0; transform: translateY(20px) scale(0.97); }

/* ── Responsive ── */
@media (max-width: 768px) {
  .feature-section, .feature-section--alt {
    flex-direction: column;
    padding: 64px 24px;
    gap: 40px;
  }
  .glass-nav { margin: 12px 14px 0; }
  .hero-title { font-size: 42px; }
  .mfield-row { grid-template-columns: 1fr; }
}
</style>
