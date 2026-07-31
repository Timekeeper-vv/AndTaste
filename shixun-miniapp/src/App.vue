<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession } from './utils/session'

onLaunch(() => {
  restoreSession().catch(() => undefined)
})
</script>

<style lang="scss">
page { background: #fffaf5; color: #251b17; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif; }
button::after { border: none; }
</style>
