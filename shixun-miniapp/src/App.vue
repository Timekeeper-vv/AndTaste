<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession, sessionStartRoute } from './utils/session'

onLaunch(() => {
  restoreSession()
    .then(() => {
      const target = sessionStartRoute()
      // login 是 pages.json 的启动页；启动后按本地会话恢复到用户应在的页面。
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
