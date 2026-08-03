<template>
  <view class="page">
    <view class="ink ink-one" />
    <view class="ink ink-two" />
    <view class="hero">
      <text class="eyebrow">WELCOME TO THE ATELIER</text>
      <text class="title">创建创作账号</text>
      <text class="sub">留下必要的信息后，就可以把灵感、作品与生产进度放在同一个工作台里。</text>
    </view>

    <view class="form-card">
      <view class="form-head"><text>基础信息</text><text>用于账号识别和必要服务联系</text></view>
      <input v-model.trim="form.username" class="field" maxlength="40" placeholder="用户名" placeholder-class="placeholder" />
      <view class="field-row"><input v-model.trim="form.phone" class="field" maxlength="30" type="number" placeholder="手机号" placeholder-class="placeholder" /><input v-model.trim="form.age" class="field age" maxlength="3" type="number" placeholder="年龄" placeholder-class="placeholder" /></view>
      <input v-model.trim="form.email" class="field" maxlength="100" type="text" placeholder="邮箱" placeholder-class="placeholder" />
      <input v-model="form.password" class="field" maxlength="100" password placeholder="设置密码（至少 12 位）" placeholder-class="placeholder" />
      <input v-model="passwordAgain" class="field" maxlength="100" password placeholder="再次确认密码" placeholder-class="placeholder" />

      <view class="sign-block">
        <view><text>合规电子签署</text><text>请输入你的真实姓名或常用签署名</text></view>
        <input v-model.trim="form.signature" class="sign-input" maxlength="100" placeholder="输入签署名" placeholder-class="placeholder" />
      </view>

      <view class="consent-title"><text>使用前请确认</text><text>注册即建立可追溯的合规记录</text></view>
      <view v-for="item in consents" :key="item.key" class="consent-row" :class="{ checked: Boolean(form[item.key]) }" @tap="toggleConsent(item.key)">
        <text class="check">{{ form[item.key] ? '✓' : '' }}</text>
        <view><text>我已阅读并同意《{{ item.title }}》</text><text>{{ item.summary }}</text></view>
        <text class="read" @tap.stop="showAgreement(item.title, item.content)">查看</text>
      </view>
      <view class="consent-row real-name" :class="{ checked: form.realNameAcknowledged }" @tap="form.realNameAcknowledged = !form.realNameAcknowledged">
        <text class="check">{{ form.realNameAcknowledged ? '✓' : '' }}</text>
        <view><text>我确认：如涉及作品合作、生产或上架，将按平台要求完成实名认证</text><text>当前注册不会收集身份证件</text></view>
      </view>

      <button class="primary" :loading="submitting" @tap="register">同意并创建账号</button>
      <text class="privacy-note">注册信息仅用于账户、创作服务与必要的订单/生产联络。请不要在作品描述中提交身份证号、银行卡号等敏感信息。</text>
    </view>

    <view class="login-link"><text>已有账号？</text><text @tap="goLogin">去登录 ›</text></view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { request } from '../../api/client'

type ConsentKey = 'agreeDisclaimer' | 'agreeConfidentiality' | 'agreeContentPolicy'

const form = reactive({
  username: '',
  phone: '',
  age: '',
  email: '',
  password: '',
  signature: '',
  agreeDisclaimer: false,
  agreeConfidentiality: false,
  agreeContentPolicy: false,
  realNameAcknowledged: false,
})
const passwordAgain = ref('')
const submitting = ref(false)
const consents: Array<{ key: ConsentKey; title: string; summary: string; content: string }> = [
  {
    key: 'agreeDisclaimer', title: '用户服务与隐私说明', summary: '了解账户、作品和必要服务信息的使用边界。',
    content: '平台仅在提供账号、AI 创作、作品管理、订单或生产服务所必需的范围内处理你的注册信息与作品资料。请勿上传无权使用的图片、他人隐私信息或违法内容。涉及生产、报价、交付与退款的具体规则，以实际服务页面和双方确认内容为准。',
  },
  {
    key: 'agreeConfidentiality', title: '保密与知识产权约定', summary: '确认只提交自己拥有或已获授权的创作素材。',
    content: '你应确保上传、输入或委托处理的素材拥有合法权利或授权，并尊重馆藏、商标、肖像与第三方知识产权。平台会按服务需要处理作品资料；涉及公开展示、商业上架、授权合作或生产前，应另行确认权利与范围。',
  },
  {
    key: 'agreeContentPolicy', title: '内容创作规范', summary: '共同维护合规、尊重文化来源的创作环境。',
    content: '不得提交违法、侵权、色情、暴力、仇恨、欺诈或其他不当内容；涉及文化遗产、博物馆、景区标识与授权 IP 时，应遵守相应的使用规范。平台可对不符合规则的内容采取限制展示、拒绝生产或终止服务等合理措施。',
  },
]

function toggleConsent(key: ConsentKey) { form[key] = !form[key] }

function showAgreement(title: string, content: string) {
  uni.showModal({ title, content, showCancel: false, confirmText: '我已了解' })
}

function validEmail(value: string) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) }
function validPhone(value: string) { return /^[0-9+()\-\s]{6,30}$/.test(value) }

async function register() {
  if (!form.username || !form.phone || !form.age || !form.email || !form.password || !form.signature) {
    uni.showToast({ title: '请完整填写注册信息与签署名', icon: 'none' }); return
  }
  const age = Number(form.age)
  if (!Number.isInteger(age) || age <= 0 || age > 120) { uni.showToast({ title: '请填写有效年龄', icon: 'none' }); return }
  if (!validPhone(form.phone)) { uni.showToast({ title: '手机号格式不正确', icon: 'none' }); return }
  if (!validEmail(form.email)) { uni.showToast({ title: '邮箱格式不正确', icon: 'none' }); return }
  if (form.password.length < 12) { uni.showToast({ title: '密码至少需要 12 位', icon: 'none' }); return }
  if (form.password !== passwordAgain.value) { uni.showToast({ title: '两次输入的密码不一致', icon: 'none' }); return }
  if (!form.agreeDisclaimer || !form.agreeConfidentiality || !form.agreeContentPolicy || !form.realNameAcknowledged) {
    uni.showToast({ title: '请先完成全部使用确认', icon: 'none' }); return
  }
  submitting.value = true
  try {
    await request('/api/users', {
      method: 'POST',
      data: {
        username: form.username, phone: form.phone, age, email: form.email, password: form.password,
        agreeDisclaimer: form.agreeDisclaimer, agreeConfidentiality: form.agreeConfidentiality,
        agreeContentPolicy: form.agreeContentPolicy, realNameAcknowledged: form.realNameAcknowledged,
        complianceSignature: form.signature,
      },
      // 注册只允许匿名 C 端用户调用；不能意外携带设备上的旧 JWT。
      header: { 'content-type': 'application/json', Authorization: '' },
    })
    uni.showModal({
      title: '账号创建成功', content: '你的合规确认已记录。请使用刚设置的用户名和密码登录。', showCancel: false, confirmText: '去登录',
      success: () => uni.redirectTo({ url: '/pages/login/index' }),
    })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '注册失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function goLogin() { uni.redirectTo({ url: '/pages/login/index' }) }
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;box-sizing:border-box;overflow:hidden;padding:73rpx 32rpx calc(65rpx + env(safe-area-inset-bottom));background:linear-gradient(150deg,#fbfaf6,#f0ece4)}.ink{position:absolute;pointer-events:none;border-radius:50%;filter:blur(2rpx)}.ink-one{top:-145rpx;right:-160rpx;width:470rpx;height:440rpx;background:radial-gradient(ellipse,rgba(105,144,123,.19),transparent 67%)}.ink-two{bottom:120rpx;left:-220rpx;width:450rpx;height:340rpx;background:radial-gradient(ellipse,rgba(188,108,81,.1),transparent 70%)}.hero,.form-card,.login-link{position:relative;z-index:1}.hero{display:flex;flex-direction:column;padding:0 8rpx 35rpx}.eyebrow{color:#6a897b;font-size:18rpx;font-weight:900;letter-spacing:2.6rpx}.title{margin-top:13rpx;color:#31352f;font-family:"Songti SC","STSong",serif;font-size:49rpx;font-weight:800}.sub{margin-top:13rpx;color:#7e8179;font-size:22rpx;line-height:1.65}.form-card{padding:26rpx;border:1rpx solid rgba(119,108,91,.14);border-radius:27rpx;background:rgba(255,254,250,.9);box-shadow:0 17rpx 36rpx rgba(64,54,42,.065)}.form-head{display:flex;flex-direction:column;gap:5rpx;margin-bottom:16rpx}.form-head text:first-child{color:#474a44;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.form-head text:last-child{color:#988f84;font-size:18rpx}.field{box-sizing:border-box;width:100%;height:83rpx;margin-top:12rpx;padding:0 19rpx;border:1rpx solid #e5ddd3;border-radius:14rpx;color:#3f403a;background:#fcfbf7;font-size:23rpx}.field-row{display:grid;grid-template-columns:minmax(0,1fr) 134rpx;gap:11rpx}.field-row .field{min-width:0}.placeholder{color:#b4aaa0}.sign-block{display:flex;flex-direction:column;gap:10rpx;margin-top:24rpx;padding:17rpx;border-radius:17rpx;background:#f1f5ef}.sign-block view{display:flex;flex-direction:column;gap:4rpx}.sign-block view text:first-child{color:#527264;font-size:23rpx;font-weight:850}.sign-block view text:last-child{color:#859087;font-size:18rpx}.sign-input{box-sizing:border-box;width:100%;height:72rpx;padding:0 15rpx;border:1rpx solid #d7e2d7;border-radius:12rpx;color:#42443e;background:#fffefa;font-size:22rpx}.consent-title{display:flex;align-items:baseline;justify-content:space-between;gap:8rpx;margin:26rpx 3rpx 11rpx}.consent-title text:first-child{color:#494b44;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.consent-title text:last-child{color:#a0968b;font-size:16rpx;text-align:right}.consent-row{display:grid;grid-template-columns:31rpx minmax(0,1fr) auto;gap:10rpx;align-items:start;margin-top:11rpx;padding:13rpx;border:1rpx solid #ebe4da;border-radius:15rpx;background:#fcfbf8}.consent-row.checked{border-color:#aac2ae;background:#f1f7ef}.check{display:grid;place-items:center;width:27rpx;height:27rpx;border:1rpx solid #cfc8bd;border-radius:8rpx;color:#fff;background:#fff;font-size:19rpx;font-weight:900}.checked .check{border-color:#6f927d;background:#6f927d}.consent-row>view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.consent-row>view text:first-child{color:#5a584f;font-size:19rpx;font-weight:750;line-height:1.45}.consent-row>view text:last-child{color:#9c9186;font-size:16rpx;line-height:1.45}.read{padding:5rpx 1rpx;color:#678474;font-size:18rpx;font-weight:850}.real-name{grid-template-columns:31rpx minmax(0,1fr)}.primary{height:91rpx;line-height:91rpx;margin-top:24rpx;border-radius:18rpx;color:#fff;background:linear-gradient(135deg,#3f3934,#617f72);font-size:26rpx;font-weight:850}.privacy-note{display:block;margin:15rpx 8rpx 0;color:#999188;font-size:16rpx;line-height:1.65;text-align:center}.login-link{display:flex;justify-content:center;gap:9rpx;margin-top:28rpx;color:#968c81;font-size:21rpx}.login-link text:last-child{color:#567768;font-weight:850}
</style>
