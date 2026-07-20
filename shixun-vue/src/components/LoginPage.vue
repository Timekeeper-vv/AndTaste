<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { User } from '../types'
import andTasteLogo from '../assets/and_taste.png'

const emit = defineEmits<{ login: [user: User] }>()

const lang = ref<'en' | 'zh'>('zh')
const toggleLang = () => { lang.value = lang.value === 'en' ? 'zh' : 'en' }

const translations = {
  en: {
    navBrand: '之间智造',
    navLogin: 'Log In',
    navSignup: 'Sign Up',
    heroEyebrow: 'AI CREATIVE MANUFACTURING',
    heroTitle1: '之间智造',
    heroTitle2: '之间味道-文创产品智能体平台',
    heroSub: 'From concept to product — an agentic platform for cultural creative design, prototyping, manufacturing and launch.',
    heroCta: 'Get Started',
    scrollHint: 'Explore Features',

    f1Badge: 'Artwork Library',
    f1Title1: 'Visual IP',
    f1Title2: 'Catalog Management',
    f1Desc: 'Build a high-resolution artwork library with categories, tags, stories, licensing status, and review workflow for monetizable creative assets.',
    f1p1: 'Upload, categorize, and tag image IP',
    f1p2: 'Manage story, license, and audit status',
    f1p3: 'Map one artwork to multiple creative SKUs',

    f2Badge: 'Creative Commerce',
    f2Title1: 'Artwork-to-SKU',
    f2Title2: 'Product Sales',
    f2Desc: 'Turn one illustration into postcards, prints, phone cases, canvas bags, stickers, and other cultural creative products with rich visual storytelling.',
    f2p1: 'Multi-SKU pricing, material, and stock',
    f2p2: 'Custom size, material, and signed editions',
    f2p3: 'Cart, checkout, and order tracking ready',

    f3Badge: 'Creator Ecosystem',
    f3Title1: 'Designer Onboarding &',
    f3Title2: 'Revenue Sharing',
    f3Desc: 'Support designer profiles, artwork submissions, licensing agreements, sales analytics, and revenue sharing for a scalable creative supply side.',
    f3p1: 'Designer profile, portfolio, and audit flow',
    f3p2: 'License type and revenue share rules',
    f3p3: 'Settlement and withdrawal extension points',

    f4Badge: 'Operations',
    f4Title1: 'Content Review',
    f4Title2: 'Order Operations',
    f4Desc: 'Operate artwork review, SKU listing, order handling, sales metrics, and financial settlement from a dedicated management workspace.',
    f4p1: 'Artwork, SKU, and order KPI overview',
    f4p2: 'Sales trends, hot IP, and stock alerts',
    f4p3: 'Payment, shipping, and review extensions',

    ctaTitle1: 'Ready to launch',
    ctaTitle2: 'your creative business?',
    ctaSub: 'Build a more efficient digital management experience.',
    ctaPrimary: 'Create Free Account',
    ctaGhost: 'Log In',

    footer1: '© 2026 之间智造',
    footer2: 'Creative Product Intelligence Platform',

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

    tlTitle: 'IP Sales Report',
    analyticsTitle: 'Monthly Overview',
    analyticsRange: 'Last 6 months',
  },
  zh: {
    navBrand: '之间智造',
    navLogin: '登 录',
    navSignup: '注 册',
    heroEyebrow: 'AI 文创智造平台',
    heroTitle1: '之间智造',
    heroTitle2: '之间味道-文创产品智能体平台',
    heroSub: '从一个创意概念出发，串联AI设计、智能评审、打样准备、量产管理与商品发售。',
    heroCta: '立即开始',
    scrollHint: '探索功能',

    f1Badge: '图片IP库',
    f1Title1: '高清图片IP',
    f1Title2: '分类与标签管理',
    f1Desc: '建立高清图片素材库，支持国风、治愈系、地域文化等分类，并通过标签、故事文案和授权状态沉淀可售数字资产。',
    f1p1: '图片上传、分类、标签化管理',
    f1p2: '作品故事、授权与审核状态管理',
    f1p3: '一张IP关联多个文创商品SKU',

    f2Badge: '商品售卖',
    f2Title1: '从图片IP到',
    f2Title2: '文创商品',
    f2Desc: '同一张插画可衍生明信片、装饰画、手机壳、帆布袋等多个SKU，突出视觉展示、设计理念和商品实物化能力。',
    f2p1: '支持多SKU、价格、库存和材质管理',
    f2p2: '支持尺寸、材质、签名版等定制选项',
    f2p3: '适配购物车、下单和订单状态追踪',

    f3Badge: '创作者生态',
    f3Title1: '设计师入驻与',
    f3Title2: '授权分成',
    f3Desc: '支持设计师/创作者入驻、作品投稿、授权协议、销售数据查看与收益分成，为平台持续供给优质图片IP。',
    f3p1: '设计师档案、作品集与审核流程',
    f3p2: '授权类型与收益分成规则配置',
    f3p3: '销售数据与结算提现能力预留',

    f4Badge: '运营后台',
    f4Title1: '内容审核',
    f4Title2: '订单运营',
    f4Desc: '面向运营人员提供作品审核、商品上下架、订单处理、销售统计与财务结算视图，保障文创交易闭环。',
    f4p1: '图片IP、SKU、订单关键指标总览',
    f4p2: '销售趋势、热门作品与库存预警',
    f4p3: '支付、发货、评价流程可扩展',

    ctaTitle1: '准备好开启',
    ctaTitle2: '您的文创生意了吗？',
    ctaSub: '用数字化能力提升业务管理效率。',
    ctaPrimary: '免费创建账号',
    ctaGhost: '立即登录',

    footer1: '© 2026 之间智造',
    footer2: '文创产品智能体平台',

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

    tlTitle: 'IP销售报告',
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
         HERO — premium animated pattern section
    ══════════════════════════════════════ -->
    <section class="hero">
      <div class="hero-stage" aria-hidden="true">
        <div class="hero-mesh"></div>
        <div class="hero-grid"></div>
        <div class="hero-lines"></div>
        <div class="hero-orb hero-orb--a"></div>
        <div class="hero-orb hero-orb--b"></div>
        <div class="hero-orb hero-orb--c"></div>
        <div class="hero-ring hero-ring--a"></div>
        <div class="hero-ring hero-ring--b"></div>
      </div>
      <div class="hero-overlay"></div>
      <div class="hero-vignette"></div>

      <!-- Glass nav bar (only in hero) -->
      <nav class="glass-nav">
        <div class="nav-brand">
          <div class="nav-logo-icon">
            <img :src="andTasteLogo" alt="之间味道 logo" />
          </div>
          <span class="nav-brand-name">之间智造</span>
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
        <div class="hero-flow" aria-label="文创产品智能体流程">
          <span>概念</span><i></i><span>设计</span><i></i><span>打样</span><i></i><span>量产</span><i></i><span>发售</span>
        </div>
        <p class="hero-flow-caption">系统支持从创意概念到设计生成、AI评审、打样准备、量产协同与上架发售的完整链路。</p>
        <div class="launch-proof" aria-label="平台发布亮点">
          <div><b>全流程</b><span>创意到交付一体化</span></div>
          <div><b>多角色</b><span>审批、生产、仓储协同</span></div>
          <div><b>可沉淀</b><span>资产、订单、数据长期留痕</span></div>
        </div>
        <div class="poster-preview" aria-hidden="true">
          <div class="poster-card poster-card-main">
            <small>LIVE OPERATIONS</small>
            <strong>92</strong>
            <span>经营健康指数</span>
            <i></i>
          </div>
          <div class="poster-card poster-card-sub">
            <small>CREATIVE ASSETS</small>
            <strong>AI · SKU · BOM</strong>
            <span>创意资产转生产方案</span>
          </div>
          <div class="poster-beam"></div>
        </div>
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

      <!-- Feature 1 — IP Asset Management -->
      <section class="feature-section feature-section--dark feature-section--pattern anim-section">
        <div class="feature-pattern feature-pattern--ip" aria-hidden="true">
          <span class="pattern-orb pattern-orb--a"></span>
          <span class="pattern-orb pattern-orb--b"></span>
        </div>
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
          <!-- Mock IP profile card -->
          <div class="mock-card mock-profile">
            <div class="mock-card-header">
              <div class="mock-tag-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
              </div>
              <span class="mock-ear-tag">IP20260710001</span>
              <span class="mock-status-badge active">Published</span>
            </div>
            <div class="mock-divider"></div>
            <div class="mock-rows">
              <div class="mock-row">
                <span class="mock-row-label">Style</span>
                <span class="mock-row-val">国风插画</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">Product Line</span>
                <span class="mock-row-val">明信片 / 帆布袋</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">License</span>
                <span class="mock-row-val">商业授权</span>
              </div>
              <div class="mock-row">
                <span class="mock-row-label">Batch</span>
                <span class="mock-row-val">LAUNCH202607</span>
              </div>
            </div>
            <div class="mock-actions">
              <div class="mock-action-btn">生成SKU</div>
              <div class="mock-action-btn">查看授权</div>
            </div>
          </div>
        </div>
      </section>

      <!-- Feature 2 — Creative Commerce (reversed) -->
      <section class="feature-section feature-section--alt feature-section--dark feature-section--pattern anim-section">
        <div class="feature-pattern feature-pattern--commerce" aria-hidden="true">
          <span class="pattern-orb pattern-orb--a"></span>
          <span class="pattern-orb pattern-orb--b"></span>
        </div>
        <div class="feat-overlay"></div>
        <div class="feature-visual">
          <!-- Mock timeline -->
          <div class="mock-card mock-timeline">
            <div class="mock-tl-title">{{ t.tlTitle }}</div>
            <div class="mock-tl-tag">IP20260710001</div>
            <div class="mock-timeline-list">
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-green"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">IP Approved</span>
                  <span class="mock-tl-meta">Jul 10, 2026 · 版权通过</span>
                </div>
              </div>
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-blue"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">SKU Mockup</span>
                  <span class="mock-tl-meta">Jul 12, 2026 · 手机壳 / 冰箱贴</span>
                </div>
              </div>
              <div class="mock-tl-item">
                <div class="mock-tl-dot tl-amber"></div>
                <div class="mock-tl-line"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Sample Ready</span>
                  <span class="mock-tl-meta">Jul 18, 2026 · 打样质检</span>
                </div>
              </div>
              <div class="mock-tl-item mock-tl-last">
                <div class="mock-tl-dot tl-gray"></div>
                <div class="mock-tl-content">
                  <span class="mock-tl-event">Online Sale</span>
                  <span class="mock-tl-meta">Jul 25, 2026 · 上架发售</span>
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

      <!-- Feature 3 — Creator Ecosystem -->
      <section class="feature-section feature-section--dark feature-section--dark-blue feature-section--pattern anim-section">
        <div class="feature-pattern feature-pattern--creator" aria-hidden="true">
          <span class="pattern-orb pattern-orb--a"></span>
          <span class="pattern-orb pattern-orb--b"></span>
        </div>
        <div class="feat-overlay"></div>
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
          <!-- Mock creator operations stack -->
          <div class="mock-health-stack">
            <div class="mock-health-card">
              <div class="mock-health-icon blue">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">Creator Audit</span>
                <span class="mock-health-meta">Jul 10, 2026 · admin</span>
                <span class="mock-health-drug">设计师资质与作品集审核</span>
              </div>
              <span class="mock-health-status done">Done</span>
            </div>
            <div class="mock-health-card mock-health-card--offset">
              <div class="mock-health-icon amber">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.5 20H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h3.93a2 2 0 0 1 1.66.9l.82 1.2a2 2 0 0 0 1.66.9H20a2 2 0 0 1 2 2v3"/><circle cx="18" cy="18" r="3"/><path d="m22 22-1.5-1.5"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">License Rule</span>
                <span class="mock-health-meta">Jul 12, 2026 · legal</span>
                <span class="mock-health-drug">商用授权 / 分成比例配置</span>
              </div>
              <span class="mock-health-status done">Done</span>
            </div>
            <div class="mock-health-card mock-health-card--offset2">
              <div class="mock-health-icon teal">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </div>
              <div class="mock-health-info">
                <span class="mock-health-type">Settlement Due</span>
                <span class="mock-health-meta">Jul 31, 2026 · Scheduled</span>
                <span class="mock-health-drug">月度销售收益结算</span>
              </div>
              <span class="mock-health-status pending">Due</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Feature 4 — Operations Analytics (reversed) -->
      <section class="feature-section feature-section--alt feature-section--dark feature-section--dark-purple feature-section--pattern anim-section">
        <div class="feature-pattern feature-pattern--ops" aria-hidden="true">
          <span class="pattern-orb pattern-orb--a"></span>
          <span class="pattern-orb pattern-orb--b"></span>
        </div>
        <div class="feat-overlay"></div>
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
                <img :src="andTasteLogo" alt="之间味道 logo" />
              </div>
              <span>之间智造</span>
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
  background: #020617;
  isolation: isolate;
}

.hero-stage {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 16% 18%, rgba(45, 212, 191, 0.30) 0, transparent 28%),
    radial-gradient(circle at 82% 28%, rgba(124, 58, 237, 0.26) 0, transparent 30%),
    radial-gradient(circle at 58% 86%, rgba(245, 158, 11, 0.16) 0, transparent 32%),
    linear-gradient(135deg, #020617 0%, #071426 48%, #111827 100%);
}

.hero-mesh {
  position: absolute;
  inset: -18%;
  background:
    conic-gradient(from 130deg at 52% 44%,
      rgba(94,234,212,.18),
      rgba(8,145,178,.04),
      rgba(139,92,246,.20),
      rgba(251,146,60,.08),
      rgba(94,234,212,.18));
  filter: blur(42px) saturate(150%);
  opacity: .88;
  animation: meshRotate 22s linear infinite;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.055) 1px, transparent 1px);
  background-size: 54px 54px;
  -webkit-mask-image: radial-gradient(circle at 50% 42%, #000 0%, rgba(0,0,0,.86) 40%, transparent 78%);
  mask-image: radial-gradient(circle at 50% 42%, #000 0%, rgba(0,0,0,.86) 40%, transparent 78%);
  opacity: .55;
  animation: gridDrift 18s linear infinite;
}

.hero-lines {
  position: absolute;
  inset: -20%;
  background:
    linear-gradient(112deg, transparent 0 38%, rgba(255,255,255,.10) 39%, transparent 40% 62%, rgba(94,234,212,.12) 63%, transparent 64%),
    linear-gradient(68deg, transparent 0 52%, rgba(167,139,250,.10) 53%, transparent 54%);
  opacity: .52;
  transform: rotate(-4deg);
  animation: lineSweep 12s ease-in-out infinite alternate;
}

.hero-orb,
.hero-ring {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
}

.hero-orb {
  filter: blur(14px);
  mix-blend-mode: screen;
  opacity: .68;
  animation: orbFloat 9s ease-in-out infinite;
}
.hero-orb--a {
  width: 220px; height: 220px;
  left: 8%; top: 18%;
  background: radial-gradient(circle, rgba(45,212,191,.55), transparent 68%);
}
.hero-orb--b {
  width: 280px; height: 280px;
  right: 8%; top: 16%;
  background: radial-gradient(circle, rgba(124,58,237,.45), transparent 70%);
  animation-delay: -2.4s;
}
.hero-orb--c {
  width: 180px; height: 180px;
  left: 56%; bottom: 10%;
  background: radial-gradient(circle, rgba(251,146,60,.32), transparent 70%);
  animation-delay: -4.8s;
}

.hero-ring {
  border: 1px solid rgba(255,255,255,.12);
  box-shadow: inset 0 0 38px rgba(94,234,212,.08), 0 0 50px rgba(139,92,246,.10);
  transform: rotate(18deg);
  animation: ringPulse 7s ease-in-out infinite;
}
.hero-ring--a {
  width: 520px; height: 220px;
  right: -120px; bottom: 16%;
}
.hero-ring--b {
  width: 360px; height: 150px;
  left: -90px; top: 34%;
  animation-delay: -2s;
}

.hero-overlay {
  position: absolute; inset: 0; z-index: 1;
  background:
    linear-gradient(180deg, rgba(2,6,23,.44), rgba(2,6,23,.68)),
    radial-gradient(ellipse at 50% 44%, rgba(15,23,42,.16), rgba(2,6,23,.66) 76%);
}

.hero-vignette {
  position: absolute; inset: 0; z-index: 1;
  background: radial-gradient(ellipse at center, transparent 42%, rgba(0,0,0,0.72) 100%);
  pointer-events: none;
}

@keyframes meshRotate {
  0% { transform: rotate(0deg) scale(1); }
  50% { transform: rotate(180deg) scale(1.08); }
  100% { transform: rotate(360deg) scale(1); }
}
@keyframes gridDrift {
  from { background-position: 0 0, 0 0; }
  to { background-position: 54px 54px, 54px 54px; }
}
@keyframes lineSweep {
  from { transform: translateX(-3%) rotate(-4deg); opacity: .34; }
  to { transform: translateX(3%) rotate(-4deg); opacity: .62; }
}
@keyframes orbFloat {
  0%, 100% { transform: translate3d(0,0,0) scale(1); }
  50% { transform: translate3d(22px,-28px,0) scale(1.08); }
}
@keyframes ringPulse {
  0%, 100% { opacity: .24; transform: rotate(18deg) scale(1); }
  50% { opacity: .52; transform: rotate(18deg) scale(1.04); }
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
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  box-shadow: 0 0 16px rgba(255,255,255,0.28);
}

.nav-logo-icon img {
  width: 100%; height: 100%;
  object-fit: contain;
  display: block;
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
  font-size: clamp(50px, 8.8vw, 104px);
  font-weight: 950; line-height: .98;
  color: #fff; margin: 0 0 22px;
  letter-spacing: -4px;
  text-shadow: 0 4px 32px rgba(0,0,0,.5);
}

.hero-title-accent {
  background: linear-gradient(135deg, #ffffff 0%, #99f6e4 36%, #a78bfa 78%, #38bdf8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-sub {
  font-size: 17px; color: rgba(255,255,255,.72);
  line-height: 1.8; max-width: 680px;
  margin: 0 auto 36px;
}

.hero-cta {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 14px 32px;
  background: linear-gradient(135deg, #14b8a6, #38bdf8);
  color: #fff; border: none; border-radius: 12px;
  font-size: 15px; font-weight: 700; font-family: var(--font);
  letter-spacing: .5px; cursor: pointer;
  box-shadow: 0 6px 28px rgba(13,148,136,0.5);
  transition: transform .15s, box-shadow .15s, opacity .15s;
}
.hero-cta:hover { transform: translateY(-2px); box-shadow: 0 10px 36px rgba(13,148,136,0.6); }
.hero-cta:active { transform: translateY(0); }

/* Scroll hint */

.hero-title-accent {
  font-size: clamp(28px, 4.2vw, 58px);
  line-height: 1.12;
}

.hero-flow {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255,255,255,0.10);
  border: 1px solid rgba(255,255,255,0.18);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: .06em;
}
.hero-flow i {
  width: 24px;
  height: 1px;
  background: linear-gradient(90deg, rgba(255,255,255,.2), rgba(255,255,255,.8));
}
.hero-flow-caption {
  max-width: 620px;
  margin: 12px 0 0;
  color: rgba(255,255,255,.72);
  font-size: 14px;
  line-height: 1.7;
}

.poster-preview {
  position: relative;
  display: grid;
  grid-template-columns: 170px 240px;
  gap: 12px;
  margin-top: 30px;
  perspective: 900px;
}
.poster-card {
  position: relative;
  min-height: 108px;
  padding: 18px;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,.18);
  border-radius: 22px;
  text-align: left;
  background: linear-gradient(145deg, rgba(255,255,255,.18), rgba(255,255,255,.07));
  box-shadow: 0 24px 70px rgba(0,0,0,.26), inset 0 1px 0 rgba(255,255,255,.12);
  backdrop-filter: blur(20px) saturate(150%);
  animation: posterFloat 6s ease-in-out infinite;
}
.poster-card-main { transform: rotateY(10deg) rotateX(4deg); }
.poster-card-sub {
  margin-top: 28px;
  transform: rotateY(-10deg) rotateX(3deg);
  animation-delay: -2.4s;
}
.poster-card small {
  display: block;
  color: rgba(165,243,252,.78);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: .18em;
}
.poster-card strong {
  display: block;
  margin-top: 10px;
  color: #fff;
  font-size: 34px;
  font-weight: 950;
  letter-spacing: -.06em;
}
.poster-card-sub strong {
  font-size: 18px;
  letter-spacing: .02em;
}
.poster-card span {
  display: block;
  margin-top: 4px;
  color: rgba(226,232,240,.72);
  font-size: 11px;
}
.poster-card i {
  position: absolute;
  right: -32px;
  bottom: -40px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(94,234,212,.30), transparent 68%);
}
.poster-beam {
  position: absolute;
  inset: 50% auto auto 50%;
  width: 390px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(94,234,212,.7), transparent);
  transform: translate(-50%,-50%) rotate(-12deg);
  filter: drop-shadow(0 0 18px rgba(94,234,212,.65));
}
@keyframes posterFloat {
  0%,100% { translate: 0 0; }
  50% { translate: 0 -8px; }
}

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

/* ── Premium CSS pattern background ── */
.feature-section--pattern {
  background:
    radial-gradient(circle at 18% 18%, rgba(45,212,191,.18), transparent 34%),
    radial-gradient(circle at 82% 75%, rgba(124,58,237,.15), transparent 38%),
    linear-gradient(135deg, #06111f 0%, #09182c 54%, #06101d 100%);
}

.feature-pattern {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}
.feature-pattern::before {
  content: '';
  position: absolute;
  inset: -30%;
  background:
    conic-gradient(from 210deg at 50% 50%,
      rgba(20,184,166,.18),
      rgba(59,130,246,.06),
      rgba(139,92,246,.18),
      rgba(245,158,11,.07),
      rgba(20,184,166,.18));
  filter: blur(34px);
  opacity: .7;
  animation: sectionMesh 24s linear infinite;
}
.feature-pattern::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.045) 1px, transparent 1px),
    radial-gradient(circle, rgba(255,255,255,.08) 1px, transparent 1.6px);
  background-size: 46px 46px, 46px 46px, 24px 24px;
  -webkit-mask-image: radial-gradient(circle at 52% 50%, #000 0 52%, transparent 82%);
  mask-image: radial-gradient(circle at 52% 50%, #000 0 52%, transparent 82%);
  opacity: .58;
  animation: patternDrift 20s linear infinite;
}

.pattern-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(12px);
  mix-blend-mode: screen;
  opacity: .5;
  animation: patternOrb 8s ease-in-out infinite;
}
.pattern-orb--a {
  width: 210px;
  height: 210px;
  left: 8%;
  bottom: 10%;
  background: radial-gradient(circle, rgba(45,212,191,.38), transparent 68%);
}
.pattern-orb--b {
  width: 240px;
  height: 240px;
  right: 10%;
  top: 8%;
  background: radial-gradient(circle, rgba(167,139,250,.35), transparent 70%);
  animation-delay: -3s;
}

.feature-pattern--commerce::before {
  background:
    conic-gradient(from 40deg at 52% 48%,
      rgba(14,165,233,.17),
      rgba(45,212,191,.16),
      rgba(251,146,60,.10),
      rgba(14,165,233,.17));
}
.feature-pattern--creator::before {
  opacity: .56;
  background:
    conic-gradient(from 90deg at 50% 50%,
      rgba(59,130,246,.22),
      rgba(20,184,166,.10),
      rgba(99,102,241,.18),
      rgba(59,130,246,.22));
}
.feature-pattern--ops::before {
  opacity: .58;
  background:
    conic-gradient(from 155deg at 50% 50%,
      rgba(168,85,247,.20),
      rgba(59,130,246,.08),
      rgba(236,72,153,.14),
      rgba(168,85,247,.20));
}

.feat-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(2,6,23,.74), rgba(2,6,23,.45) 48%, rgba(2,6,23,.74)),
    radial-gradient(ellipse at center, rgba(15,23,42,.05), rgba(2,6,23,.55));
  z-index: 1;
}

@keyframes sectionMesh {
  0% { transform: rotate(0deg) scale(1); }
  50% { transform: rotate(180deg) scale(1.06); }
  100% { transform: rotate(360deg) scale(1); }
}
@keyframes patternDrift {
  from { background-position: 0 0, 0 0, 0 0; }
  to { background-position: 46px 46px, 46px 46px, 24px 24px; }
}
@keyframes patternOrb {
  0%, 100% { transform: translate3d(0,0,0) scale(1); }
  50% { transform: translate3d(18px,-18px,0) scale(1.08); }
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
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.modal-brand-icon img {
  width: 100%; height: 100%;
  object-fit: contain;
  display: block;
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

/* ══════════════════════════════════════
   Bright premium theme overrides
══════════════════════════════════════ */
.landing {
  background: #f7fbff;
}

.hero {
  background: #f8fbff;
}

.hero-stage {
  background:
    radial-gradient(circle at 14% 18%, rgba(45, 212, 191, 0.26) 0, transparent 30%),
    radial-gradient(circle at 82% 24%, rgba(167, 139, 250, 0.24) 0, transparent 32%),
    radial-gradient(circle at 62% 88%, rgba(251, 191, 36, 0.20) 0, transparent 34%),
    linear-gradient(135deg, #fbfdff 0%, #eef8ff 44%, #fff9f0 100%);
}
.hero-mesh {
  background:
    conic-gradient(from 130deg at 52% 44%,
      rgba(20,184,166,.22),
      rgba(56,189,248,.10),
      rgba(167,139,250,.24),
      rgba(251,191,36,.12),
      rgba(20,184,166,.22));
  filter: blur(48px) saturate(135%);
  opacity: .72;
}
.hero-grid {
  background-image:
    linear-gradient(rgba(15,23,42,.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.055) 1px, transparent 1px);
  opacity: .56;
}
.hero-lines {
  background:
    linear-gradient(112deg, transparent 0 38%, rgba(13,148,136,.10) 39%, transparent 40% 62%, rgba(124,58,237,.10) 63%, transparent 64%),
    linear-gradient(68deg, transparent 0 52%, rgba(245,158,11,.10) 53%, transparent 54%);
  opacity: .52;
}
.hero-orb {
  mix-blend-mode: multiply;
  opacity: .48;
}
.hero-orb--a { background: radial-gradient(circle, rgba(45,212,191,.40), transparent 68%); }
.hero-orb--b { background: radial-gradient(circle, rgba(167,139,250,.34), transparent 70%); }
.hero-orb--c { background: radial-gradient(circle, rgba(251,191,36,.30), transparent 70%); }
.hero-ring {
  border-color: rgba(15,23,42,.10);
  box-shadow: inset 0 0 38px rgba(13,148,136,.08), 0 0 50px rgba(124,58,237,.12);
}
.hero-overlay {
  background:
    linear-gradient(180deg, rgba(255,255,255,.42), rgba(255,255,255,.66)),
    radial-gradient(ellipse at 50% 42%, rgba(255,255,255,.12), rgba(239,248,255,.70) 78%);
}
.hero-vignette {
  background: radial-gradient(ellipse at center, transparent 46%, rgba(203,213,225,.42) 100%);
}

.glass-nav {
  background: rgba(255,255,255,0.68);
  border-color: rgba(15,23,42,0.08);
  box-shadow: 0 18px 60px rgba(15,23,42,0.10), inset 0 1px 0 rgba(255,255,255,.78);
}
.nav-brand-name {
  color: #0f172a;
}
.nav-lang-toggle {
  color: #475569;
  border-color: rgba(15,23,42,0.12);
}
.nav-lang-toggle:hover {
  background: rgba(15,23,42,0.05);
  color: #0f172a;
  border-color: rgba(13,148,136,0.28);
}
.nav-btn-login {
  color: #0f172a;
  background: rgba(255,255,255,0.72);
  border-color: rgba(15,23,42,0.10);
}
.nav-btn-login:hover {
  background: #fff;
  border-color: rgba(13,148,136,0.26);
}
.nav-btn-signup {
  box-shadow: 0 10px 28px rgba(13,148,136,0.24);
}

.hero-eyebrow {
  color: #0d9488;
}
.hero-title {
  color: #0f172a;
  text-shadow: 0 16px 60px rgba(15,23,42,.10);
}
.hero-title-accent {
  background: linear-gradient(135deg, #0d9488, #0891b2 48%, #7c3aed);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.hero-sub {
  color: #475569;
}
.hero-flow {
  background: rgba(255,255,255,0.76);
  border-color: rgba(15,23,42,0.08);
  color: #0f172a;
  box-shadow: 0 16px 45px rgba(15,23,42,.08);
}
.hero-flow i {
  background: linear-gradient(90deg, rgba(13,148,136,.14), rgba(13,148,136,.70));
}
.hero-flow-caption {
  color: #475569;
}
.scroll-hint {
  color: #64748b;
}

.features-wrap {
  background:
    linear-gradient(180deg, #f7fbff 0%, #f3f8fc 48%, #f8fafc 100%);
}
.feature-section {
  border: 1px solid rgba(15,23,42,.07);
  box-shadow: 0 24px 80px rgba(15,23,42,.08);
}
.feature-section--pattern,
.feature-section--dark-blue,
.feature-section--dark-purple {
  background:
    radial-gradient(circle at 18% 18%, rgba(45,212,191,.20), transparent 34%),
    radial-gradient(circle at 84% 72%, rgba(167,139,250,.18), transparent 38%),
    linear-gradient(135deg, #ffffff 0%, #eef8ff 52%, #fff7ec 100%);
}
.feature-pattern::before,
.feature-section--dark-blue::before,
.feature-section--dark-purple::before {
  background:
    conic-gradient(from 210deg at 50% 50%,
      rgba(20,184,166,.18),
      rgba(59,130,246,.10),
      rgba(167,139,250,.18),
      rgba(251,191,36,.11),
      rgba(20,184,166,.18));
  opacity: .58;
}
.feature-pattern::after,
.feature-section--dark-blue::after,
.feature-section--dark-purple::after {
  background-image:
    linear-gradient(rgba(15,23,42,.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.045) 1px, transparent 1px),
    radial-gradient(circle, rgba(13,148,136,.12) 1px, transparent 1.6px);
  opacity: .48;
}
.pattern-orb {
  mix-blend-mode: multiply;
  opacity: .38;
}
.pattern-orb--a { background: radial-gradient(circle, rgba(45,212,191,.36), transparent 68%); }
.pattern-orb--b { background: radial-gradient(circle, rgba(167,139,250,.30), transparent 70%); }
.feat-overlay {
  background:
    linear-gradient(90deg, rgba(255,255,255,.62), rgba(255,255,255,.34) 48%, rgba(255,255,255,.62)),
    radial-gradient(ellipse at center, rgba(255,255,255,.06), rgba(248,250,252,.44));
}

.feature-section--dark .feature-title  { color: #0f172a; }
.feature-section--dark .feature-desc   { color: #475569; }
.feature-section--dark .feature-points li { color: #334155; }
.feature-section--dark .feature-points li svg { color: #0d9488; }
.feature-section--dark .feature-badge {
  background: rgba(13,148,136,.10);
  color: #0f766e;
  border-color: rgba(13,148,136,.20);
}
.feature-section--dark .feature-badge--teal {
  background: rgba(20,184,166,.11);
  color: #0f766e;
  border-color: rgba(20,184,166,.20);
}
.feature-section--dark .feature-badge--blue {
  background: rgba(59,130,246,.10);
  color: #1d4ed8;
  border-color: rgba(59,130,246,.18);
}
.feature-section--dark .feature-badge--purple {
  background: rgba(124,58,237,.10);
  color: #6d28d9;
  border-color: rgba(124,58,237,.18);
}

.feature-section--dark .mock-card,
.feature-section--dark .mock-health-card {
  background: rgba(255,255,255,.76);
  backdrop-filter: blur(22px) saturate(160%);
  -webkit-backdrop-filter: blur(22px) saturate(160%);
  border: 1px solid rgba(15,23,42,.08);
  box-shadow: 0 18px 60px rgba(15,23,42,.10), inset 0 1px 0 rgba(255,255,255,.72);
}
.feature-section--dark .mock-ear-tag,
.feature-section--dark .mock-row-val,
.feature-section--dark .mock-tl-event,
.feature-section--dark .mock-health-type,
.feature-section--dark .mock-analytics-title {
  color: #0f172a;
}
.feature-section--dark .mock-row-label,
.feature-section--dark .mock-tl-title,
.feature-section--dark .mock-tl-meta,
.feature-section--dark .mock-health-meta,
.feature-section--dark .mock-analytics-range,
.feature-section--dark .mock-stat-lbl {
  color: #64748b;
}
.feature-section--dark .mock-tl-tag {
  color: #0d9488;
}
.feature-section--dark .mock-health-drug,
.feature-section--dark .mock-chart-legend {
  color: #475569;
}
.feature-section--dark .mock-divider,
.feature-section--dark .mock-tl-line,
.feature-section--dark .mock-stat-row {
  background: rgba(15,23,42,.08);
  border-color: rgba(15,23,42,.08);
}
.feature-section--dark .mock-action-btn {
  background: rgba(255,255,255,.62);
  border-color: rgba(15,23,42,.09);
  color: #475569;
}
.feature-section--dark .mock-tag-icon {
  background: rgba(13,148,136,.12);
  color: #0d9488;
}

/* ══════════════════════════════════════
   Brand Launch Preview Skin — official, dynamic, presentation-ready
══════════════════════════════════════ */
.landing {
  background: #f7f3ea;
}
.hero {
  min-height: 720px;
  background:
    linear-gradient(110deg, rgba(255,255,255,.96) 0 46%, rgba(255,255,255,.58) 47% 64%, transparent 65%),
    radial-gradient(circle at 82% 18%, rgba(20,184,166,.20), transparent 26%),
    radial-gradient(circle at 92% 82%, rgba(246,173,85,.22), transparent 32%),
    linear-gradient(135deg, #fdfaf4 0%, #eef7f4 42%, #e8f0ff 100%);
}
.hero-stage {
  background:
    radial-gradient(circle at 78% 20%, rgba(20,184,166,.22), transparent 27%),
    radial-gradient(circle at 84% 72%, rgba(251,146,60,.18), transparent 30%),
    linear-gradient(135deg, #fdfaf4, #eef7f4 50%, #e8f0ff);
}
.hero-mesh,
.hero-lines {
  opacity: .18;
  filter: blur(34px) saturate(120%);
}
.hero-grid {
  opacity: .22;
  background-image:
    linear-gradient(rgba(15,23,42,.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.055) 1px, transparent 1px);
  -webkit-mask-image: linear-gradient(90deg, transparent 0 46%, #000 62%, transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0 46%, #000 62%, transparent 100%);
}
.hero-overlay {
  background:
    linear-gradient(90deg, rgba(255,255,255,.88), rgba(255,255,255,.42) 52%, rgba(255,255,255,.18)),
    radial-gradient(circle at 78% 38%, rgba(255,255,255,.10), rgba(255,255,255,.62) 72%);
}
.hero-vignette {
  background: linear-gradient(180deg, rgba(255,255,255,.04), rgba(247,243,234,.30));
}
.glass-nav {
  background: rgba(255,255,255,.78);
  border-color: rgba(15,23,42,.08);
  box-shadow: 0 18px 50px rgba(15,23,42,.08);
}
.nav-brand-name {
  color: #0f172a;
  font-weight: 900;
}
.nav-lang-toggle,
.nav-btn-login {
  color: #0f172a;
  background: rgba(255,255,255,.72);
  border-color: rgba(15,23,42,.10);
}
.nav-btn-signup {
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  box-shadow: 0 16px 32px rgba(20,184,166,.24);
}
.hero-body {
  align-items: flex-start;
  text-align: left;
  width: min(1180px, calc(100% - 56px));
  margin: 0 auto;
  padding-top: 34px;
}
.hero-eyebrow {
  color: #0f766e;
}
.hero-title {
  max-width: 760px;
  color: #0b1220;
  text-shadow: none;
  font-size: clamp(52px, 7vw, 94px);
  letter-spacing: -5px;
}
.hero-title-accent {
  background: linear-gradient(90deg, #0f766e 0%, #0b1220 46%, #b45309 100%);
  -webkit-background-clip: text;
  background-clip: text;
}
.hero-sub {
  margin-left: 0;
  max-width: 640px;
  color: #475569;
}
.hero-cta {
  color: #fff;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  box-shadow: 0 18px 38px rgba(20,184,166,.26);
}
.hero-flow {
  color: #0f172a;
  background: rgba(255,255,255,.76);
  border-color: rgba(15,23,42,.08);
  box-shadow: 0 14px 34px rgba(15,23,42,.08);
}
.hero-flow i {
  background: linear-gradient(90deg, rgba(15,23,42,.12), rgba(20,184,166,.72));
  animation: launchFlow 2.8s ease-in-out infinite;
}
.hero-flow-caption {
  color: #64748b;
}
.launch-proof {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: min(680px, 100%);
  margin-top: 24px;
}
.launch-proof div {
  padding: 16px 18px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 18px;
  background: rgba(255,255,255,.72);
  box-shadow: 0 16px 42px rgba(15,23,42,.07);
  backdrop-filter: blur(16px);
  animation: proofIn .55s ease both;
}
.launch-proof div:nth-child(2) { animation-delay: .08s; }
.launch-proof div:nth-child(3) { animation-delay: .16s; }
.launch-proof b {
  display: block;
  color: #0f766e;
  font-size: 18px;
  font-weight: 950;
}
.launch-proof span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
}
.poster-preview {
  position: absolute;
  right: max(42px, 7vw);
  top: 50%;
  transform: translateY(-39%);
  grid-template-columns: 210px 280px;
}
.poster-card {
  background: rgba(255,255,255,.82);
  border-color: rgba(15,23,42,.08);
  box-shadow: 0 30px 80px rgba(15,23,42,.13);
}
.poster-card small { color: #0f766e; }
.poster-card strong { color: #0f172a; }
.poster-card span { color: #64748b; }
.poster-beam {
  background: linear-gradient(90deg, transparent, rgba(20,184,166,.75), transparent);
}
.scroll-hint {
  color: #64748b;
}
@keyframes launchFlow {
  0%,100% { transform: scaleX(.55); opacity: .45; }
  50% { transform: scaleX(1); opacity: 1; }
}
@keyframes proofIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .hero-mesh,
  .hero-grid,
  .hero-lines,
  .hero-orb,
  .hero-ring,
  .feature-pattern::before,
  .feature-pattern::after,
  .pattern-orb,
  .scroll-chevron,
  .floatable .mock-card,
  .floatable .mock-health-stack {
    animation: none !important;
  }
}

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
@media (max-width: 1120px) {
  .poster-preview { display: none; }
  .hero-body { align-items: center; text-align: center; }
  .hero-sub { margin-left: auto; }
}
@media (max-width: 760px) {
  .poster-preview { display: none; }
  .hero-title { letter-spacing: -2px; }
  .launch-proof { grid-template-columns: 1fr; }
}
</style>
