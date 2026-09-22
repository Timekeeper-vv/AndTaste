<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import type { User } from '../types'
import { requestAssetPreviewAccess } from '../utils/assetAccess'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

interface ProjectTask {
  id: number
  projectId: number
  projectNo: string
  title: string
  category: string
  ownerUsername: string
  stageIndex: number
  stageKey?: string
  stageName: string
  assigneeRole: string
  status: string
  createdAt: string
  assets?: ProjectAsset[]
  bundleId?: number
  bundleNo?: string
  productNo?: string
  productName?: string
  material?: string
  productSize?: string
  viewCount?: number
  purpose?: string
  museumName?: string
  note?: string
  reviewComment?: string
  bundleStatus?: string
}

interface ProjectAsset {
  id: number
  projectId: number
  assetId: number
  assetType: string
  fileName: string
  fileUrl: string
  fileSize: number
  mimeType: string
  createdBy: string
  createdAt: string
  previewUrl?: string
  downloadUrl?: string
}

interface ProjectSummary {
  id: number
  projectNo: string
  title: string
  category: string
  status: string
  currentStageName: string
  currentAssigneeRole: string
  createdAt: string
  updatedAt: string
  assets?: ProjectAsset[]
}

const pendingTasks = ref<ProjectTask[]>([])
const myProjects = ref<ProjectSummary[]>([])
const loading = ref(false)
const processingId = ref<number | null>(null)
const activePreview = ref<{ url: string; label: string } | null>(null)
const rejectionTask = ref<ProjectTask | null>(null)
const rejectionReason = ref('')
const productionFileUploadingId = ref<number | null>(null)

const roleLabel = computed(() => {
  const labels: Record<string, string> = {
    designer: '设计师',
    project_manager: '项目经理',
    production: '生产部门',
    finance: '财务',
    logistics: '物流',
    admin: '超级管理员'
  }
  return labels[props.currentUser.role || 'admin'] || '员工'
})

async function loadTasks() {
  loading.value = true
  try {
    const [pendingRes, projectsRes] = await Promise.all([
      fetch('/api/projects/my-tasks'),
      fetch('/api/projects/my-projects')
    ])
    if (pendingRes.ok) {
      const tasks = await pendingRes.json()
      await Promise.all(tasks.map(async (task: ProjectTask) => {
        const assetsRes = await fetch(`/api/projects/${task.projectId}/assets`)
        if (assetsRes.ok) task.assets = await hydrateAssets(await assetsRes.json())
      }))
      pendingTasks.value = tasks
    }
    if (projectsRes.ok) {
      const projects = await projectsRes.json()
      await Promise.all(projects.map(async (project: ProjectSummary) => {
        const assetsRes = await fetch(`/api/projects/${project.id}/assets`)
        if (assetsRes.ok) project.assets = await hydrateAssets(await assetsRes.json())
      }))
      myProjects.value = projects
    }
  } catch (error) {
    console.error('加载任务失败:', error)
    emit('alert', '加载任务失败', 'error')
  } finally {
    loading.value = false
  }
}

async function hydrateAssets(assets: ProjectAsset[]): Promise<ProjectAsset[]> {
  return Promise.all((assets || []).map(async asset => {
    if (!asset.assetId) return asset
    try {
      const access = await requestAssetPreviewAccess(asset.assetId)
      return { ...asset, previewUrl: access.previewUrl, downloadUrl: access.url }
    } catch {
      return asset
    }
  }))
}

function isMultiViewTask(task: ProjectTask) {
  return task.category === 'multiview' && !!task.bundleId
}

function isBundleReviewTask(task: ProjectTask) {
  return isMultiViewTask(task) && ['designer_review', 'project_manager_review'].includes(String(task.stageKey))
}

function isProductionFileTask(task: ProjectTask) {
  return isMultiViewTask(task) && task.stageKey === 'designer_production_files'
}

function isProductionReviewTask(task: ProjectTask) {
  return isMultiViewTask(task) && task.stageKey === 'production_review'
}

function productionFile(task: ProjectTask) {
  return (task.assets || []).find(asset => asset.assetType === 'production_file')
}

async function uploadProductionFile(event: Event, task: ProjectTask) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.zip')) {
    emit('alert', '生产文件必须是 ZIP 压缩包', 'error')
    return
  }
  productionFileUploadingId.value = task.id
  try {
    const form = new FormData()
    form.append('file', file)
    const res = await fetch(`/api/projects/${task.projectId}/production-file`, { method: 'POST', body: form })
    const data = await res.json().catch(() => null)
    if (!res.ok) throw new Error(data?.message || `HTTP ${res.status}`)
    emit('alert', '生产文件已上传，已提交生产人员审核', 'success')
    await loadTasks()
  } catch (error: any) {
    emit('alert', `生产文件上传失败：${error?.message || error}`, 'error')
  } finally {
    productionFileUploadingId.value = null
  }
}

async function handleComplete(task: ProjectTask) {
  const comment = ''
  processingId.value = task.id
  try {
    const url = isBundleReviewTask(task)
      ? `/api/creative/ai/consumer-multiview-bundles/${task.bundleId}/review`
      : isProductionReviewTask(task)
        ? `/api/projects/tasks/${task.id}/production-review`
      : `/api/projects/tasks/${task.id}/complete`
    const res = await fetch(url, {
      method: isBundleReviewTask(task) ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(isBundleReviewTask(task)
        ? { status: 'approved', comment, taskId: task.id }
        : isProductionReviewTask(task) ? { status: 'approved', comment } : { comment })
    })
    if (res.ok) {
      const data = await res.json().catch(() => null)
      emit('alert', data?.message || '已完成，任务已流转到下一环节', 'success')
      loadTasks()
    } else {
      const err = await res.json()
      emit('alert', '操作失败: ' + (err.message || '未知错误'), 'error')
    }
  } catch (error) {
    console.error('操作失败:', error)
    emit('alert', '操作失败,请重试', 'error')
  } finally {
    processingId.value = null
  }
}

async function handleReject(task: ProjectTask) {
  rejectionTask.value = task
  rejectionReason.value = ''
  document.body.style.overflow = 'hidden'
}

function closeReject() {
  rejectionTask.value = null
  rejectionReason.value = ''
  if (!activePreview.value) document.body.style.overflow = ''
}

async function submitReject() {
  const task = rejectionTask.value
  const comment = rejectionReason.value.trim()
  if (!task || !comment) return
  processingId.value = task.id
  try {
    const url = isBundleReviewTask(task)
      ? `/api/creative/ai/consumer-multiview-bundles/${task.bundleId}/review`
      : isProductionReviewTask(task)
        ? `/api/projects/tasks/${task.id}/production-review`
      : `/api/projects/tasks/${task.id}/reject`
    const res = await fetch(url, {
      method: isBundleReviewTask(task) ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(isBundleReviewTask(task)
        ? { status: 'rejected', comment, taskId: task.id }
        : isProductionReviewTask(task) ? { status: 'rejected', comment } : { comment })
    })
    if (res.ok) {
      const data = await res.json().catch(() => null)
      emit('alert', data?.message || '已驳回该项目', 'success')
      closeReject()
      loadTasks()
    } else {
      const err = await res.json()
      emit('alert', '驳回失败: ' + (err.message || '未知错误'), 'error')
    }
  } catch (error) {
    console.error('驳回失败:', error)
    emit('alert', '驳回失败,请重试', 'error')
  } finally {
    processingId.value = null
  }
}

async function previewAsset(asset: ProjectAsset) {
  try {
    let url = asset.previewUrl || ''
    if (asset.assetId) {
      const access = await requestAssetPreviewAccess(asset.assetId)
      url = access.previewUrl
    }
    if (!url) throw new Error('预览地址不可用')
    activePreview.value = { url, label: assetTypeLabel(asset.assetType) }
    document.body.style.overflow = 'hidden'
  } catch (e: any) {
    emit('alert', `预览失败：${e?.message || e}`, 'error')
  }
}

function closePreview() {
  activePreview.value = null
  if (!rejectionTask.value) document.body.style.overflow = ''
}

async function downloadAsset(asset: ProjectAsset) {
  try {
    let url = asset.downloadUrl || asset.previewUrl || ''
    if (asset.assetId) {
      const access = await requestAssetPreviewAccess(asset.assetId)
      url = access.url
    }
    if (!url) throw new Error('下载地址不可用')
    const response = await fetch(url, { cache: 'no-store' })
    if (!response.ok) {
      const error = await response.json().catch(() => null)
      throw new Error(error?.message || `HTTP ${response.status}`)
    }
    const blobUrl = URL.createObjectURL(await response.blob())
    const anchor = document.createElement('a')
    anchor.href = blobUrl
    anchor.download = asset.fileName || (asset.assetType === 'production_file' ? 'production-files.zip' : 'asset')
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  } catch (error: any) {
    emit('alert', `文件下载失败：${error?.message || error}`, 'error')
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function assetTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    'front': '前视图',
    'left': '侧视图',
    'back': '后视图',
    'right': '右视图',
    'side': '侧视图',
    'top': '俯视图',
    'model': '3D模型',
    'sketch': '草图',
    'rendering': '效果图',
    'technical': '技术文档',
    'production': '生产文件',
    'production_file': '生产文件 ZIP',
    'other': '其他'
  }
  return labels[type] || type
}

function coverImage(task: ProjectTask): string {
  const assets = task.assets || []
  const imageAsset = assets.find(a => a.mimeType?.startsWith('image/'))
  return (imageAsset as any)?.previewUrl || imageAsset?.fileUrl || ''
}

function projectCoverImage(project: ProjectSummary): string {
  const assets = project.assets || []
  const imageAsset = assets.find(a => a.mimeType?.startsWith('image/'))
  return (imageAsset as any)?.previewUrl || imageAsset?.fileUrl || ''
}

function getStatusBadge(status: string) {
  const badges: Record<string, { label: string; class: string }> = {
    active: { label: '进行中', class: 'status-active' },
    completed: { label: '已完成', class: 'status-completed' },
    rejected: { label: '已驳回', class: 'status-rejected' }
  }
  return badges[status] || { label: status, class: 'status-pending' }
}

function fmtTime(v?: string) {
  return v ? String(v).replace('T',' ').slice(0,19) : '-'
}

onMounted(loadTasks)
</script>

<template>
  <div class="role-workspace">
    <div class="workspace-header">
      <h2>{{ roleLabel }}工作台</h2>
      <button @click="loadTasks" class="refresh-btn" :disabled="loading">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </div>

    <div class="workspace-content">
      <!-- 待处理任务 -->
      <section class="task-section">
        <h3>🔔 待我处理 ({{ pendingTasks.length }})</h3>
        <div v-if="pendingTasks.length === 0" class="empty-state">
          <p>暂无待处理任务</p>
        </div>
        <div v-else class="task-list">
          <div v-for="task in pendingTasks" :key="task.id" class="task-card" :class="{ 'multiview-task-card': isMultiViewTask(task) }">
            <!-- 封面 -->
            <div v-if="coverImage(task) && !isMultiViewTask(task)" class="card-cover">
              <img :src="coverImage(task)" :alt="task.title" />
              <div class="cover-overlay">
                <span class="stage-badge">{{ task.stageName }}</span>
                <span class="status-badge badge-pending">待处理</span>
              </div>
            </div>

            <!-- 卡片内容 -->
            <div class="card-body">
              <div class="card-header">
                <h4>{{ task.title }}</h4>
                <small>{{ task.projectNo }}<template v-if="isMultiViewTask(task)"> · 作品包 {{ task.bundleNo || `#${task.bundleId}` }}</template></small>
              </div>

              <div class="card-meta">
                <span>发起人: {{ task.ownerUsername }}</span>
                <span>时间: {{ fmtTime(task.createdAt) }}</span>
              </div>

              <div v-if="isMultiViewTask(task)" class="multiview-summary">
                <span>产品号：{{ task.productNo || '未关联产品号' }}</span>
                <span>{{ task.material || '材质待定' }}</span>
                <span>{{ task.productSize || '尺寸待定' }}</span>
                <span>{{ task.purpose === 'museum_sale' ? `博物馆售卖${task.museumName ? ` · ${task.museumName}` : ''}` : '个人创作' }}</span>
              </div>

              <!-- 三视图/资产网格 -->
              <div v-if="task.assets && task.assets.length > 0" class="assets-grid-section">
                <div class="assets-grid" :class="{ 'multiview-assets-grid': isMultiViewTask(task) }">
                  <div
                    v-for="asset in task.assets.filter(asset => !isProductionFileTask(task) || asset.assetType !== 'production_file')"
                    :key="asset.id"
                    class="asset-card"
                  >
                    <div class="asset-preview" @click="previewAsset(asset)">
                      <img
                        v-if="asset.mimeType?.startsWith('image/')"
                        :src="(asset as any).previewUrl"
                        :alt="asset.fileName"
                      />
                      <div v-else class="asset-placeholder">
                        <span>📄</span>
                      </div>
                    </div>
                    <div class="asset-label">{{ assetTypeLabel(asset.assetType) }}</div>
                    <small v-if="isMultiViewTask(task)" class="preview-hint">点击查看大图</small>
                    <button v-else class="download-btn" @click.stop="downloadAsset(asset)">
                      下载
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="isProductionFileTask(task)" class="production-file-upload">
                <div class="production-file-heading"><strong>生产文件制作</strong><span>请根据上方三视图制作完整生产文件，打包为 ZIP 后上传。</span></div>
                <label class="production-upload-control" :class="{ uploading: productionFileUploadingId === task.id }">
                  <input type="file" accept=".zip,application/zip" :disabled="productionFileUploadingId === task.id" @change="uploadProductionFile($event, task)" />
                  <span>{{ productionFileUploadingId === task.id ? '正在上传并提交审核…' : '选择 ZIP 生产文件' }}</span>
                </label>
              </div>

              <div v-if="isProductionReviewTask(task) && productionFile(task)" class="production-file-review">
                <div><strong>设计师已上传生产文件</strong><span>{{ productionFile(task)?.fileName }} · {{ formatFileSize(productionFile(task)?.fileSize || 0) }}</span></div>
                <button type="button" class="download-btn" @click="downloadAsset(productionFile(task)!)">下载 ZIP</button>
              </div>

              <!-- 操作按钮 -->
              <div v-if="!isProductionFileTask(task)" class="card-actions">
                <button
                  @click="handleComplete(task)"
                  :disabled="processingId === task.id"
                  class="btn btn-primary"
                >
                  {{ processingId === task.id ? '处理中...' : isBundleReviewTask(task) ? (task.stageKey === 'designer_review' ? '初审通过并流转' : '通过整包') : isProductionReviewTask(task) ? '通过生产文件' : '完成并流转' }}
                </button>
                <button
                  @click="handleReject(task)"
                  :disabled="processingId === task.id"
                  class="btn btn-danger"
                >
                  驳回
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 我参与的项目 -->
      <section class="task-section">
        <h3>📁 我参与的项目 ({{ myProjects.length }})</h3>
        <div v-if="myProjects.length === 0" class="empty-state">
          <p>暂无项目记录</p>
        </div>
        <div v-else class="task-list">
          <div v-for="project in myProjects" :key="project.id" class="task-card">
            <!-- 封面 -->
            <div v-if="projectCoverImage(project)" class="card-cover">
              <img :src="projectCoverImage(project)" :alt="project.title" />
              <div class="cover-overlay">
                <span class="stage-badge">{{ project.currentStageName || '已完成' }}</span>
                <span :class="['status-badge', getStatusBadge(project.status).class]">
                  {{ getStatusBadge(project.status).label }}
                </span>
              </div>
            </div>

            <!-- 卡片内容 -->
            <div class="card-body">
              <div class="card-header">
                <h4>{{ project.title }}</h4>
                <small>{{ project.projectNo }}</small>
              </div>

              <div class="card-meta">
                <span>当前负责: {{ project.currentAssigneeRole || '无' }}</span>
                <span>时间: {{ fmtTime(project.updatedAt) }}</span>
              </div>

              <!-- 项目资产 -->
              <div v-if="project.assets && project.assets.length > 0" class="assets-grid-section">
                <div class="assets-grid">
                  <div
                    v-for="asset in project.assets"
                    :key="asset.id"
                    class="asset-card"
                  >
                    <div class="asset-preview">
                      <img
                        v-if="asset.mimeType?.startsWith('image/')"
                        :src="(asset as any).previewUrl"
                        :alt="asset.fileName"
                        @click="previewAsset(asset)"
                      />
                      <div v-else class="asset-placeholder" @click="previewAsset(asset)">
                        <span>📄</span>
                      </div>
                    </div>
                    <div class="asset-label">{{ assetTypeLabel(asset.assetType) }}</div>
                    <button class="download-btn" @click.stop="downloadAsset(asset)">
                      {{ asset.assetType === 'production_file' ? '下载 ZIP' : '下载' }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-if="activePreview" class="workspace-modal" role="dialog" aria-modal="true" @click.self="closePreview">
      <div class="preview-dialog">
        <header><strong>{{ activePreview.label }}</strong><button type="button" @click="closePreview">×</button></header>
        <img :src="activePreview.url" :alt="activePreview.label" />
        <p>在线高清预览 · 点击右上角关闭</p>
      </div>
    </div>

    <div v-if="rejectionTask" class="workspace-modal" role="dialog" aria-modal="true" @click.self="closeReject">
      <div class="reject-dialog">
        <header><div><small>REVIEW FEEDBACK</small><strong>填写不通过原因</strong><span>{{ rejectionTask.title }}</span></div><button type="button" @click="closeReject">×</button></header>
        <textarea v-model="rejectionReason" maxlength="500" autofocus placeholder="请说明需要修改的视图、结构、材质或其他问题"></textarea>
        <footer><span>{{ rejectionReason.length }}/500</span><div><button type="button" class="btn" @click="closeReject">取消</button><button type="button" class="btn btn-danger" :disabled="!rejectionReason.trim() || processingId === rejectionTask.id" @click="submitReject">确认不通过</button></div></footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.role-workspace {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  background: #f5f5f5;
  min-height: 100vh;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 15px;
  border-bottom: 2px solid #e5e7eb;
}

.workspace-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.refresh-btn {
  padding: 8px 16px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background: #2563eb;
}

.refresh-btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.workspace-content {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.task-section {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.task-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: #374151;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.task-card {
  display: flex;
  gap: 20px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.task-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.card-cover {
  position: relative;
  flex-shrink: 0;
  width: 200px;
  height: 200px;
  background: #f3f4f6;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(0,0,0,0) 50%, rgba(0,0,0,0.3) 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px;
}

.stage-badge {
  background: rgba(255,255,255,0.9);
  color: #374151;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  align-self: flex-start;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  align-self: flex-end;
  width: fit-content;
}

.badge-pending {
  background: #fef3c7;
  color: #92400e;
}

.status-active {
  background: #bfdbfe;
  color: #1e40af;
}

.status-completed {
  background: #d1fae5;
  color: #065f46;
}

.status-rejected {
  background: #fee2e2;
  color: #991b1b;
}

.card-body {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.card-header small {
  color: #6b7280;
  font-size: 13px;
}

.card-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #6b7280;
}

.assets-grid-section {
  margin: 10px 0;
}

.assets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 12px;
}

.asset-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #f9fafb;
  border-radius: 6px;
  transition: all 0.2s;
}

.asset-card:hover {
  background: #f3f4f6;
}

.asset-preview {
  width: 80px;
  height: 80px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;
}

.asset-preview:hover {
  transform: scale(1.05);
}

.asset-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.asset-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  cursor: pointer;
}

.asset-label {
  font-size: 12px;
  color: #374151;
  font-weight: 500;
  text-align: center;
  word-break: break-word;
}

.download-btn {
  padding: 4px 8px;
  font-size: 12px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}

.download-btn:hover {
  background: #2563eb;
}

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.btn-primary {
  background: #10b981;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #059669;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #dc2626;
}

.btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.multiview-task-card{border-color:#dce8dd;border-radius:15px;background:#fbfdfb}.multiview-task-card .card-body{padding:18px}.multiview-summary{display:flex;flex-wrap:wrap;gap:7px}.multiview-summary span{padding:6px 9px;border-radius:8px;background:#eef5ef;color:#607768;font-size:12px}.multiview-assets-grid{grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.multiview-assets-grid .asset-card{align-items:stretch;padding:0;gap:0;overflow:hidden;border:1px solid #e1eae2;border-radius:10px;background:#f5f8f5}.multiview-assets-grid .asset-preview{width:100%;height:180px;border:0;border-radius:0;background:#edf2ed}.multiview-assets-grid .asset-preview:hover{transform:none}.multiview-assets-grid .asset-preview img{object-fit:contain}.multiview-assets-grid .asset-label{padding:8px 8px 2px;color:#607768;text-align:center}.preview-hint{padding:0 8px 8px;color:#8a978d;font-size:11px;text-align:center}.workspace-modal{position:fixed;z-index:1200;inset:0;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.72);backdrop-filter:blur(5px)}.preview-dialog{width:min(1000px,94vw);max-height:92vh;overflow:hidden;border-radius:18px;background:#fff;box-shadow:0 30px 80px rgba(0,0,0,.3)}.preview-dialog header,.reject-dialog header{display:flex;align-items:center;justify-content:space-between;padding:15px 18px;border-bottom:1px solid #e5e7eb}.preview-dialog header button,.reject-dialog header>button{border:0;background:transparent;color:#64748b;font-size:28px;cursor:pointer}.preview-dialog img{display:block;width:100%;height:min(72vh,760px);object-fit:contain;background:#f1f5f9}.preview-dialog p{margin:0;padding:10px;color:#64748b;font-size:12px;text-align:center}.reject-dialog{width:min(560px,94vw);padding-bottom:16px;border-radius:18px;background:#fff}.reject-dialog header>div{display:flex;flex-direction:column;gap:4px}.reject-dialog header small{color:#62806a;font-weight:800;letter-spacing:.12em}.reject-dialog header strong{font-size:20px}.reject-dialog header span{color:#64748b;font-size:12px}.reject-dialog textarea{display:block;width:calc(100% - 36px);min-height:150px;margin:18px;padding:13px;resize:vertical;border:1px solid #cbd5e1;border-radius:12px;font:inherit;box-sizing:border-box}.reject-dialog footer{display:flex;align-items:center;justify-content:space-between;padding:0 18px;color:#94a3b8;font-size:12px}.reject-dialog footer>div{display:flex;gap:8px}@media(max-width:700px){.multiview-assets-grid{grid-template-columns:1fr}.multiview-assets-grid .asset-preview{height:220px}.task-card{flex-direction:column}.card-cover{width:100%}}
.production-file-upload{display:grid;gap:12px;margin-top:12px;padding:15px;border:1px dashed #aec7b2;border-radius:12px;background:#f3f9f3}.production-file-heading{display:flex;flex-direction:column;gap:4px}.production-file-heading strong{color:#334b3b;font-size:14px}.production-file-heading span{color:#718275;font-size:12px}.production-upload-control{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 14px;border:1px solid #7ca68a;border-radius:10px;background:#fff;color:#477058;font-size:13px;font-weight:800;cursor:pointer}.production-upload-control input{display:none}.production-upload-control.uploading{opacity:.6;cursor:wait}.production-file-review{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px;padding:12px 14px;border:1px solid #dce8dd;border-radius:10px;background:#f7fbf7}.production-file-review div{display:flex;min-width:0;flex-direction:column;gap:4px}.production-file-review strong{color:#334b3b;font-size:13px}.production-file-review span{overflow:hidden;color:#718275;font-size:11px;text-overflow:ellipsis;white-space:nowrap}
</style>
