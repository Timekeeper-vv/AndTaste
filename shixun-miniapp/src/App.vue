<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession } from './utils/session'

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
</script>

<style lang="scss">
page {
  background: #f7f3ed;
  color: #292622;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}
button::after { border: none; }
</style>
