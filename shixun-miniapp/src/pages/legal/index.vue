<template>
  <view class="page">
    <view class="topbar"><text class="back" @tap="goBack">‹</text><text class="topbar-title">{{ document.title }}</text></view>
    <scroll-view class="article-scroll" scroll-y>
      <view class="article">
        <text class="title">{{ document.title }}</text>
        <text class="updated">更新日期：2026年10月14日</text>
        <text class="lead">{{ document.intro }}</text>
        <view v-for="section in document.sections" :key="section.title" class="section">
          <text class="section-title">{{ section.title }}</text>
          <text class="section-body">{{ section.body }}</text>
        </view>
        <view class="contact">
          <text class="contact-title">联系与咨询</text>
          <text>如需咨询账号、隐私或个人信息处理问题，可通过小程序「我的 - 在线客服」联系平台服务团队。我们会在核实身份后依法处理相关请求。</text>
          <text v-if="document.type === 'privacy'" class="contact-link" @tap="openSupport">前往在线客服 ›</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const type = ref<'terms' | 'privacy'>('privacy')

const privacySections = [
  { title: '一、我们处理哪些信息', body: '为创建和维护账号，我们会处理你主动提交的用户名、邮箱、手机号（如填写）、年龄及签署名；使用手机号快捷登录时，会在你主动授权后接收微信提供的手机号验证凭证及手机号。使用创作服务时，我们会处理你输入的创作描述、上传的图片或文件、生成结果和作品管理信息。使用订单、积分、打样或合作服务时，会处理完成该项服务所需的订单、支付状态、收件人及配送信息。' },
  { title: '二、处理目的与方式', body: '上述信息仅用于账号注册和登录、提供创作与作品管理服务、处理你的服务请求、完成订单及交付、保障账号和交易安全、处理售后与依法履行义务。我们按照最小必要原则，以信息系统自动处理和必要的人工服务方式处理信息；不会因为你浏览公开页面而要求提供非必要信息。' },
  { title: '三、设备权限与第三方服务', body: '仅在你主动使用相应功能时，小程序才会申请所需权限，例如选择并上传作品文件、保存图片或使用手机号快捷验证。部分创作、云存储、登录、支付或配送功能需要由相应服务提供方处理完成服务所必需的信息；我们会要求其按照适用法律及约定提供保护，不会出售你的个人信息。具体权限以微信系统弹窗和小程序隐私保护指引为准。' },
  { title: '四、保存与保护', body: '我们采取访问控制、传输保护和必要的安全管理措施，防止信息遭到未经授权的访问、泄露、篡改或丢失。信息仅在提供服务、处理争议及满足法定保存期限所需的期间内保存；超过必要期限后，将删除或进行匿名化处理，法律法规另有要求的除外。' },
  { title: '五、你的权利', body: '你可以通过小程序内的账号功能或在线客服查询、更正个人信息，申请删除信息、撤回授权或注销账号。为保护账号安全，我们可能需要核验你的身份。撤回授权或删除信息可能导致相应功能无法继续使用；依法必须保存的信息会在保存期限届满后删除或匿名化。' },
  { title: '六、未成年人保护', body: '请在监护人指导下使用本服务。我们不会以提供创作服务为由要求提交身份证件；如发现未经监护人同意处理了未成年人个人信息，请通过在线客服联系我们处理。' },
  { title: '七、政策更新', body: '当服务功能或信息处理方式发生变化时，我们会更新本说明并在小程序内提示。若变更需要重新取得你的同意，我们会依法再次征求同意。' },
]

const termsSections = [
  { title: '一、服务内容', body: '「之间智造」提供创意辅助、作品管理、渠道征集、产品智造、打样及相关咨询服务。实际可用功能以小程序页面展示为准；生成结果属于创作辅助内容，不保证必然符合特定机构、生产或商业使用要求。' },
  { title: '二、账号与信息', body: '请提供真实、有效且由你控制的账号信息，并妥善保管登录凭证。账号仅限本人使用。你应及时更新失效信息，并对通过账号进行的操作负责；发现异常时请尽快通过在线客服联系平台。' },
  { title: '三、作品与知识产权', body: '你应确保提交的文字、图片、商标、人物肖像及其他素材由你创作、依法可以使用或已取得充分授权。你保留依法享有的作品权利，并授权平台在提供本服务所必需的范围内存储、处理和展示相关资料。未经你另行确认，平台服务本身不代表获得作品的独家商业授权。' },
  { title: '四、合规使用', body: '不得利用服务制作、传播违法或侵害他人合法权益的内容，不得绕过安全措施、干扰平台运行或提交恶意数据。平台可对疑似违规内容进行必要核查，并依法采取限制、下架或停止相关服务等措施。' },
  { title: '五、订单与合作', body: '积分、费用、工期、生产、物流、退款及渠道合作以相应功能页面展示及双方另行确认的订单或协议为准。平台审核或推荐不构成目标机构的采购、授权、合作承诺，也不保证作品必然通过审核或实现销售。' },
  { title: '六、服务变更与争议处理', body: '平台会持续维护服务，并可在法律允许范围内对功能进行调整。发生争议时，双方应先友好协商；未尽事项按照适用法律法规处理。' },
]

const document = computed(() => type.value === 'privacy'
  ? { type: 'privacy', title: '隐私政策', intro: '之间智造重视个人信息和隐私保护。本政策说明我们在你使用小程序及相关服务时，如何处理必要信息，以及你可以如何管理这些信息。', sections: privacySections }
  : { type: 'terms', title: '用户协议', intro: '欢迎使用之间智造小程序及相关服务。请在使用前阅读本协议；当你注册、登录或使用服务，即表示你已阅读并理解适用条款。', sections: termsSections })

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/login/index' })
}

function openSupport() { uni.navigateTo({ url: '/pages/support/index?tab=chat' }) }

onLoad(query => { type.value = query?.type === 'terms' ? 'terms' : 'privacy' })
</script>

<style scoped lang="scss">
.page { min-height: 100vh; box-sizing: border-box; background: #f7f8f6; color: #292e2b; }
.topbar { position: sticky; top: 0; z-index: 2; display: flex; align-items: center; justify-content: center; height: calc(94rpx + env(safe-area-inset-top)); box-sizing: border-box; padding: env(safe-area-inset-top) 28rpx 0; background: rgba(255,255,255,.98); }
.back { position: absolute; left: 28rpx; bottom: 8rpx; width: 56rpx; color: #333a36; font-size: 58rpx; line-height: 1; }
.topbar-title { padding-top: env(safe-area-inset-top); font-size: 30rpx; font-weight: 700; }
.article-scroll { height: calc(100vh - 94rpx - env(safe-area-inset-top)); }
.article { padding: 36rpx 34rpx calc(60rpx + env(safe-area-inset-bottom)); }
.title, .updated, .lead, .section-title, .section-body, .contact-title, .contact text { display: block; }
.title { font-size: 42rpx; font-weight: 800; }
.updated { margin-top: 13rpx; color: #89918d; font-size: 20rpx; }
.lead { margin-top: 28rpx; color: #4f5954; font-size: 24rpx; line-height: 1.8; }
.section { margin-top: 29rpx; }
.section-title { color: #2e3934; font-size: 25rpx; font-weight: 750; }
.section-body { margin-top: 9rpx; color: #59645f; font-size: 22rpx; line-height: 1.8; text-align: justify; }
.contact { margin-top: 32rpx; padding: 22rpx; border-radius: 15rpx; background: #eaf5f0; color: #56645e; font-size: 21rpx; line-height: 1.7; }
.contact-title { margin-bottom: 8rpx; color: #355b4d; font-size: 23rpx; font-weight: 750; }
.contact-link { margin-top: 12rpx; color: #078b70; font-weight: 700; }
</style>
