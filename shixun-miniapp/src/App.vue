<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession, sessionStartRoute } from './utils/session'

onLaunch(() => {
  restoreSession()
    .then(() => {
      const target = sessionStartRoute()
      // 用户端统一由网页端承载；本地令牌只用于 web-view 启动时的一次性引导。
      setTimeout(() => uni.reLaunch({ url: target }), 0)
    })
    .catch(() => undefined)
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
