import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { rmSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * `public/generated` and `public/uploads` used to be convenient local
 * development locations for creator files.  They must never be copied into
 * the deployable static bundle: private files are now read exclusively via
 * the authenticated creative-asset endpoints.
 */
const removePrivateLegacyAssets = {
  name: 'remove-private-legacy-assets',
  closeBundle() {
    const outputDirectory = resolve(process.cwd(), 'dist')
    for (const directory of ['generated', 'uploads']) {
      rmSync(resolve(outputDirectory, directory), { recursive: true, force: true })
    }
  },
}

/**
 * Vite serves files under `public/` directly in development, before the
 * backend's authenticated media controller is involved.  Keep old local
 * folders from becoming an unauthenticated development-only backdoor while
 * their historical files are being retained for migration/forensics.
 */
const blockPrivateLegacyAssetPaths: Plugin = {
  name: 'block-private-legacy-asset-paths',
  configureServer(server) {
    server.middlewares.use((request, response, next) => {
      const pathname = (request.url || '').split('?')[0]
      if (pathname === '/generated' || pathname.startsWith('/generated/') || pathname === '/uploads' || pathname.startsWith('/uploads/')) {
        response.statusCode = 404
        response.end()
        return
      }
      next()
    })
  },
  configurePreviewServer(server) {
    server.middlewares.use((request, response, next) => {
      const pathname = (request.url || '').split('?')[0]
      if (pathname === '/generated' || pathname.startsWith('/generated/') || pathname === '/uploads' || pathname.startsWith('/uploads/')) {
        response.statusCode = 404
        response.end()
        return
      }
      next()
    })
  },
}

export default defineConfig({
  plugins: [vue({
    template: {
      compilerOptions: {
        isCustomElement: tag => tag === 'model-viewer',
      },
    },
  }), removePrivateLegacyAssets, blockPrivateLegacyAssetPaths],
  build: {
    rollupOptions: {
      input: {
        index: new URL('./index.html', import.meta.url).pathname,
        'model-preview': new URL('./model-preview.html', import.meta.url).pathname,
        'material-lab': new URL('./material-lab.html', import.meta.url).pathname,
      },
    },
  },
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
