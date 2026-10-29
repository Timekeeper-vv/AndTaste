<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { markActive } from './utils/activity'
import { restoreSession } from './utils/session'
import { startUsageTracking, stopUsageTracking } from './utils/usage'

onLaunch(() => {
  // Do not intercept protected APIs in App.vue. On the first chooseImage call,
  // WeChat shows its native privacy dialog and resumes the original tap after
  // the user agrees. An App-level custom dialog is not reliably rendered over
  // every mini-program page and can leave the image picker blocked.
  // Visitors must be able to explore the home page before deciding whether to
  // log in. Privacy authorization is requested only by a protected action,
  // such as the user actively choosing phone-number quick login.
  restoreSession()
    .finally(() => setTimeout(() => uni.reLaunch({ url: '/pages/home/index' }), 0))
})

// 有效使用时长按前台停留 + 真实交互计算：进入前台开始计时，
// 每次交互刷新活跃时间，切到后台立即停止上报。
onShow(() => {
  markActive()
  startUsageTracking()
})

onHide(() => stopUsageTracking())
</script>

<style lang="scss">
page {
  background: #ffffff;
  color: #292622;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}
button::after { border: none; }
</style>
