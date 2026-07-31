<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js'

const props = defineProps<{ modelUrl: string; modelName?: string }>()
const emit = defineEmits<{ loaded: []; error: [message: string]; saveVariant: [payload: { blob: Blob; materialLabel: string }] }>()

type PresetKey = 'original' | 'ceramic' | 'gold' | 'metal' | 'wood' | 'stainedPine' | 'ppc' | 'plush' | 'acrylic' | 'resin'
type TextureSet = { baseColor: string; bump?: string; repeat?: number; bumpScale?: number }
type MaterialPreset = { key: PresetKey; label: string; hint: string; color: number; metalness: number; roughness: number; clearcoat?: number; transmission?: number; opacity?: number; sheen?: number; sheenRoughness?: number; textures?: TextureSet }
const presets: MaterialPreset[] = [
  { key: 'original', label: '原始材质', hint: '恢复 Tripo 原始贴图', color: 0xffffff, metalness: 0, roughness: .7 },
  { key: 'ceramic', label: '陶瓷釉面', hint: '温润光泽 · 适合器物', color: 0xf2d7bd, metalness: 0, roughness: .2, clearcoat: .55 },
  { key: 'gold', label: '鎏金金属', hint: '高光金属 · 礼品感', color: 0xd59a3b, metalness: .9, roughness: .22 },
  { key: 'metal', label: '拉丝金属', hint: '工业质感 · 稳重', color: 0x60656a, metalness: .92, roughness: .38 },
  { key: 'wood', label: '木质温润', hint: '参数化木色 · 快速预览', color: 0x7c4b2d, metalness: 0, roughness: .68 },
  { key: 'stainedPine', label: '染色松木', hint: '真实木纹贴图 · PBR', color: 0xffffff, metalness: 0, roughness: .58, textures: { baseColor: '/materials/stained-pine/base-color.jpg', bump: '/materials/stained-pine/height.png', repeat: 1.35, bumpScale: .17 } },
  { key: 'ppc', label: 'PPC 高精硬塑', hint: '注塑微纹 · 精密分件感', color: 0xd5d9d7, metalness: 0, roughness: .34, clearcoat: .08 },
  { key: 'plush', label: '全毛绒', hint: '纤维渲染 · 可自选颜色', color: 0xe9ad83, metalness: 0, roughness: .93, sheen: 1, sheenRoughness: .58 },
  { key: 'acrylic', label: '透明亚克力', hint: '通透光泽 · 潮流感', color: 0xa9e7ea, metalness: 0, roughness: .1, transmission: .35, opacity: .82 },
  { key: 'resin', label: '树脂潮玩', hint: '细腻半哑 · 收藏感', color: 0xff8d75, metalness: 0, roughness: .42, clearcoat: .18 },
]

const host = ref<HTMLElement | null>(null)
const status = ref<'loading' | 'ready' | 'error'>('loading')
const selected = ref<PresetKey>('original')
const exporting = ref(false)
const saving = ref(false)
const plushColor = ref('#e9ad83')
const ppcColor = ref('#d5d9d7')
const plushDensity = ref(72)
const plushLength = ref(62)
const plushPreviewFur = ref(true)
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let root: THREE.Group | null = null
let frame = 0
const originalMaterials = new Map<THREE.Mesh, THREE.Material | THREE.Material[]>()
let generatedMaterials: THREE.Material[] = []
let generatedTextures: THREE.Texture[] = []
let previewFurShells: THREE.Mesh[] = []
const textureLoader = new THREE.TextureLoader()
const textureCache = new Map<string, THREE.Texture>()
const sourceTextureCache = new Map<string, Promise<{ map: THREE.Texture; bumpMap?: THREE.Texture }>>()
const materialMessage = ref('选中材质即可在模型表面实时预览，支持导出和保存到作品库。')

function clearPreviewFur() { previewFurShells.forEach(shell => shell.parent?.remove(shell)); previewFurShells = [] }
function disposeGenerated() { clearPreviewFur(); generatedMaterials.forEach(item => item.dispose()); generatedMaterials = []; generatedTextures.forEach(item => item.dispose()); generatedTextures = [] }
function configureTexture(texture: THREE.Texture, colorTexture = false, repeat = 1) {
  texture.colorSpace = colorTexture ? THREE.SRGBColorSpace : THREE.NoColorSpace
  texture.wrapS = THREE.RepeatWrapping; texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(repeat, repeat); texture.flipY = false; texture.needsUpdate = true
  return texture
}
async function loadTexture(url: string, colorTexture = false, repeat = 1) {
  const cacheKey = `${url}|${colorTexture ? 'srgb' : 'linear'}|${repeat}`
  const cached = textureCache.get(cacheKey)
  if (cached) return cached
  const texture = configureTexture(await textureLoader.loadAsync(url), colorTexture, repeat)
  textureCache.set(cacheKey, texture)
  return texture
}
async function loadTextureSet(set: TextureSet) {
  const cacheKey = JSON.stringify(set)
  let request = sourceTextureCache.get(cacheKey)
  if (!request) {
    request = Promise.all([
      loadTexture(set.baseColor, true, set.repeat || 1),
      set.bump ? loadTexture(set.bump, false, set.repeat || 1) : Promise.resolve(undefined),
    ]).then(([map, bumpMap]) => ({ map, bumpMap }))
    sourceTextureCache.set(cacheKey, request)
  }
  return request
}
function createPlushTextures(hex: string) {
  const size = 256
  const colorCanvas = document.createElement('canvas'); colorCanvas.width = size; colorCanvas.height = size
  const heightCanvas = document.createElement('canvas'); heightCanvas.width = size; heightCanvas.height = size
  const furMaskCanvas = document.createElement('canvas'); furMaskCanvas.width = size; furMaskCanvas.height = size
  const colorContext = colorCanvas.getContext('2d')!; const heightContext = heightCanvas.getContext('2d')!; const furMaskContext = furMaskCanvas.getContext('2d')!
  const base = new THREE.Color(hex); const dark = base.clone().multiplyScalar(.62); const light = base.clone().lerp(new THREE.Color(0xffffff), .28)
  colorContext.fillStyle = `#${base.getHexString()}`; colorContext.fillRect(0, 0, size, size)
  heightContext.fillStyle = '#808080'; heightContext.fillRect(0, 0, size, size)
  furMaskContext.fillStyle = '#050505'; furMaskContext.fillRect(0, 0, size, size)
  // 成簇短纤维：颜色、凹凸和外轮廓用同一组随机毛束，避免看上去像单纯噪点。
  const fibers = 1700 + Math.round(plushDensity.value * 28)
  for (let index = 0; index < fibers; index++) {
    const x = Math.random() * size; const y = Math.random() * size
    const length = 1.2 + Math.random() * (2.8 + plushLength.value / 35); const angle = (Math.random() - .5) * 1.9
    const bright = Math.random() > .52; const alpha = .08 + Math.random() * .22
    colorContext.strokeStyle = `#${(bright ? light : dark).getHexString()}${Math.round(alpha * 255).toString(16).padStart(2, '0')}`
    colorContext.lineWidth = .45 + Math.random() * .8; colorContext.beginPath(); colorContext.moveTo(x, y); colorContext.lineTo(x + Math.cos(angle) * length, y + Math.sin(angle) * length); colorContext.stroke()
    const bump = 105 + Math.floor(Math.random() * 135)
    heightContext.strokeStyle = `rgb(${bump},${bump},${bump})`; heightContext.lineWidth = .55 + Math.random() * 1.1; heightContext.beginPath(); heightContext.moveTo(x, y); heightContext.lineTo(x + Math.cos(angle) * length, y + Math.sin(angle) * length); heightContext.stroke()
    // Shell Fur 遮罩使用更长、更密的毛束；外层通过更高 alphaTest 自动只保留少量长毛。
    if (Math.random() > .08) { furMaskContext.strokeStyle = `rgba(255,255,255,${.46 + Math.random() * .54})`; furMaskContext.lineWidth = .7 + Math.random() * 1.7; furMaskContext.beginPath(); furMaskContext.moveTo(x, y); furMaskContext.lineTo(x + Math.cos(angle) * (length * (3.6 + Math.random() * 2.4)), y + Math.sin(angle) * (length * (3.6 + Math.random() * 2.4))); furMaskContext.stroke() }
  }
  const heightPixels = heightContext.getImageData(0, 0, size, size).data
  const normalPixels = new Uint8Array(size * size * 4); const roughnessPixels = new Uint8Array(size * size * 4)
  const heightAt = (x: number, y: number) => heightPixels[((y + size) % size * size + (x + size) % size) * 4] / 255
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const dx = (heightAt(x + 1, y) - heightAt(x - 1, y)) * 3.6; const dy = (heightAt(x, y + 1) - heightAt(x, y - 1)) * 3.6
    const normal = new THREE.Vector3(-dx, -dy, 1).normalize(); const at = (y * size + x) * 4; const roughness = Math.round(190 + heightAt(x, y) * 55)
    normalPixels[at] = Math.round((normal.x * .5 + .5) * 255); normalPixels[at + 1] = Math.round((normal.y * .5 + .5) * 255); normalPixels[at + 2] = Math.round((normal.z * .5 + .5) * 255); normalPixels[at + 3] = 255
    roughnessPixels[at] = roughness; roughnessPixels[at + 1] = roughness; roughnessPixels[at + 2] = roughness; roughnessPixels[at + 3] = 255
  }
  const map = configureTexture(new THREE.CanvasTexture(colorCanvas), true, 5.5)
  const normalMap = configureTexture(new THREE.DataTexture(normalPixels, size, size, THREE.RGBAFormat), false, 5.5)
  const roughnessMap = configureTexture(new THREE.DataTexture(roughnessPixels, size, size, THREE.RGBAFormat), false, 5.5)
  const furMask = configureTexture(new THREE.CanvasTexture(furMaskCanvas), false, 5.5)
  generatedTextures.push(map, normalMap, roughnessMap, furMask)
  return { map, normalMap, roughnessMap, furMask }
}
function createPpcTextures(hex: string) {
  const size = 256; const count = size * size
  const base = new THREE.Color(hex); const pixels = new Uint8Array(count * 4); const normalPixels = new Uint8Array(count * 4); const roughnessPixels = new Uint8Array(count * 4)
  // 高精硬塑的微橘皮颗粒与低反差细纹：用于打破“纯色塑料”的假感，但不会抢走产品结构细节。
  const noise = new Float32Array(count)
  for (let i = 0; i < count; i++) noise[i] = Math.random()
  const sample = (x: number, y: number) => noise[((y + size) % size) * size + ((x + size) % size)]
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const index = y * size + x; const at = index * 4; const grain = (noise[index] - .5) * .055
    const color = base.clone().offsetHSL(0, 0, grain)
    pixels[at] = Math.round(color.r * 255); pixels[at + 1] = Math.round(color.g * 255); pixels[at + 2] = Math.round(color.b * 255); pixels[at + 3] = 255
    const dx = (sample(x + 1, y) - sample(x - 1, y)) * .42; const dy = (sample(x, y + 1) - sample(x, y - 1)) * .42
    const normal = new THREE.Vector3(-dx, -dy, 1).normalize()
    normalPixels[at] = Math.round((normal.x * .5 + .5) * 255); normalPixels[at + 1] = Math.round((normal.y * .5 + .5) * 255); normalPixels[at + 2] = Math.round((normal.z * .5 + .5) * 255); normalPixels[at + 3] = 255
    const rough = Math.round(72 + noise[index] * 50)
    roughnessPixels[at] = rough; roughnessPixels[at + 1] = rough; roughnessPixels[at + 2] = rough; roughnessPixels[at + 3] = 255
  }
  const map = configureTexture(new THREE.DataTexture(pixels, size, size, THREE.RGBAFormat), true, 7)
  const normalMap = configureTexture(new THREE.DataTexture(normalPixels, size, size, THREE.RGBAFormat), false, 7)
  const roughnessMap = configureTexture(new THREE.DataTexture(roughnessPixels, size, size, THREE.RGBAFormat), false, 7)
  generatedTextures.push(map, normalMap, roughnessMap)
  return { map, normalMap, roughnessMap }
}
function createPreviewFurShells(textures: ReturnType<typeof createPlushTextures>) {
  if (!root || !plushPreviewFur.value) return
  const box = new THREE.Box3().setFromObject(root); const modelSize = Math.max(...Object.values(box.getSize(new THREE.Vector3())).map(Number), .01)
  const layers = window.innerWidth < 650 ? 5 : 9
  // 最大蓬松长度约为模型包围盒的 3.6%，轮廓上能明确看到短毛外扩。
  const maxOffset = modelSize * (.004 + plushLength.value * .00032)
  const baseMeshes: THREE.Mesh[] = []
  root.traverse(child => { if ((child as THREE.Mesh).isMesh && !child.userData.materialPreviewShell) baseMeshes.push(child as THREE.Mesh) })
  // 先收集原始网格，再追加 shell，避免 traverse 过程中把新壳层再次当成原模型而无限复制。
  baseMeshes.forEach(mesh => {
    if (!mesh.geometry.getAttribute('uv') || !mesh.parent) return
    for (let layer = 1; layer <= layers; layer++) {
      const layerRatio = layer / layers
      const shellMaterial = new THREE.MeshPhysicalMaterial({ color: new THREE.Color(plushColor.value).lerp(new THREE.Color(0xffffff), .1), roughness: 1, metalness: 0, sheen: 1, sheenColor: new THREE.Color(plushColor.value).lerp(new THREE.Color(0xffffff), .48), sheenRoughness: .72, alphaMap: textures.furMask, transparent: true, opacity: .26 + layerRatio * .26, alphaTest: .76 - layerRatio * .5, alphaToCoverage: true, depthWrite: false, side: THREE.DoubleSide })
      const offset = maxOffset * layerRatio
      shellMaterial.onBeforeCompile = shader => {
        shader.uniforms.uFurOffset = { value: offset }
        shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\ntransformed += normalize(normal) * uFurOffset;')
      }
      shellMaterial.customProgramCacheKey = () => `and-taste-plush-shell-v1-${offset.toFixed(6)}`
      const shell = mesh.clone(); shell.material = shellMaterial; shell.castShadow = false; shell.receiveShadow = false; shell.renderOrder = 10 + layer; shell.userData.materialPreviewShell = true
      mesh.parent.add(shell); previewFurShells.push(shell); generatedMaterials.push(shellMaterial)
    }
  })
}

function resize() { if (!host.value || !renderer || !camera) return; const { clientWidth: width, clientHeight: height } = host.value; renderer.setSize(width, height, false); camera.aspect = width / Math.max(1, height); camera.updateProjectionMatrix() }
function render() { if (!renderer || !scene || !camera) return; controls?.update(); renderer.render(scene, camera); frame = requestAnimationFrame(render) }
function fitCamera(object: THREE.Object3D) { if (!camera || !controls) return; const box = new THREE.Box3().setFromObject(object); const size = box.getSize(new THREE.Vector3()); const center = box.getCenter(new THREE.Vector3()); const maxSize = Math.max(size.x, size.y, size.z) || 1; const distance = maxSize * 2.15; camera.position.set(center.x + distance * .78, center.y + distance * .55, center.z + distance); camera.near = maxSize / 100; camera.far = maxSize * 100; camera.updateProjectionMatrix(); controls.target.copy(center); controls.update() }
function setup() {
  if (!host.value) return
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2)); renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.12
  host.value.replaceChildren(renderer.domElement)
  scene = new THREE.Scene(); scene.background = new THREE.Color(0x17120f)
  camera = new THREE.PerspectiveCamera(38, 1, .01, 1000)
  controls = new OrbitControls(camera, renderer.domElement); controls.enableDamping = true; controls.dampingFactor = .06; controls.autoRotate = true; controls.autoRotateSpeed = .65
  scene.add(new THREE.HemisphereLight(0xffe1c3, 0x18211d, 2.4))
  const key = new THREE.DirectionalLight(0xffd2a0, 3.4); key.position.set(4, 7, 5); scene.add(key)
  const rim = new THREE.DirectionalLight(0x7fd8c5, 2.1); rim.position.set(-4, 2, -5); scene.add(rim)
  const floor = new THREE.Mesh(new THREE.CircleGeometry(12, 72), new THREE.MeshStandardMaterial({ color: 0x211915, roughness: .95, metalness: .05 })); floor.rotation.x = -Math.PI / 2; floor.position.y = -1.2; scene.add(floor)
  resize(); window.addEventListener('resize', resize); render()
}
async function loadModel() {
  status.value = 'loading'; selected.value = 'original'; materialMessage.value = '选中材质即可在模型表面实时预览，支持导出和保存到作品库。'; originalMaterials.clear(); disposeGenerated()
  try {
    if (!host.value) await nextTick(); setup()
    const gltf = await new GLTFLoader().loadAsync(props.modelUrl)
    root = gltf.scene; root.traverse(child => { if ((child as THREE.Mesh).isMesh) { const mesh = child as THREE.Mesh; originalMaterials.set(mesh, mesh.material); mesh.castShadow = true; mesh.receiveShadow = true } })
    scene?.add(root); fitCamera(root); status.value = 'ready'; emit('loaded')
  } catch (error: any) { status.value = 'error'; emit('error', error?.message || '模型加载失败') }
}
async function applyPreset(key: PresetKey) {
  if (!root) return
  const preset = presets.find(item => item.key === key)!
  selected.value = key
  disposeGenerated()
  if (key === 'original') {
    root.traverse(child => { if ((child as THREE.Mesh).isMesh) { const mesh = child as THREE.Mesh; mesh.material = originalMaterials.get(mesh)! } })
    materialMessage.value = '已恢复 Tripo 原始材质和原始贴图。'
    return
  }
  try {
    materialMessage.value = key === 'plush' ? '正在生成毛绒纤维与柔光效果…' : key === 'ppc' ? '正在生成 PPC 注塑微表面纹理…' : (preset.textures ? '正在加载真实 PBR 木纹贴图…' : '正在应用参数化 PBR 材质…')
    const plushTextures = key === 'plush' ? createPlushTextures(plushColor.value) : undefined
    const ppcTextures = key === 'ppc' ? createPpcTextures(ppcColor.value) : undefined
    const textures = plushTextures || ppcTextures || (preset.textures ? await loadTextureSet(preset.textures) : undefined)
    if (selected.value !== key || !root) return
    let texturedMeshCount = 0; let vertexColorMeshCount = 0
    root.traverse(child => {
      if (!(child as THREE.Mesh).isMesh) return
      const mesh = child as THREE.Mesh
      const canUseUv = Boolean(mesh.geometry.getAttribute('uv'))
      const hasVertexColors = Boolean(mesh.geometry.getAttribute('color'))
      const useTextures = Boolean(textures && canUseUv)
      const baseColor = key === 'plush' ? new THREE.Color(plushColor.value) : key === 'ppc' ? new THREE.Color(ppcColor.value) : preset.color
      const material = new THREE.MeshPhysicalMaterial({
        color: baseColor, metalness: preset.metalness, roughness: preset.roughness,
        clearcoat: preset.clearcoat || 0, clearcoatRoughness: .15,
        transmission: preset.transmission || 0, opacity: preset.opacity ?? 1,
        transparent: (preset.opacity ?? 1) < 1 || !!preset.transmission,
        ior: preset.transmission ? 1.45 : 1.5, side: THREE.FrontSide,
        map: useTextures ? textures!.map : null,
        normalMap: key === 'plush' && useTextures ? plushTextures!.normalMap : key === 'ppc' && useTextures ? ppcTextures!.normalMap : null,
        roughnessMap: key === 'plush' && useTextures ? plushTextures!.roughnessMap : key === 'ppc' && useTextures ? ppcTextures!.roughnessMap : null,
        bumpMap: key === 'plush' || key === 'ppc' ? null : (useTextures ? ('bumpMap' in textures! ? textures!.bumpMap || null : null) : null),
        normalScale: key === 'plush' ? new THREE.Vector2(.68, .68) : key === 'ppc' ? new THREE.Vector2(.18, .18) : new THREE.Vector2(1, 1),
        bumpScale: useTextures && key !== 'plush' && key !== 'ppc' ? preset.textures!.bumpScale || .1 : 0,
        sheen: preset.sheen || 0, sheenRoughness: preset.sheenRoughness ?? 1,
        sheenColor: key === 'plush' ? new THREE.Color(plushColor.value).lerp(new THREE.Color(0xffffff), .26) : new THREE.Color(0xffffff),
        // 若设计部在 GLB 中导出了顶点色，则在毛绒材质下保留其渐变/花色。
        vertexColors: key === 'plush' && hasVertexColors,
      })
      mesh.material = material; generatedMaterials.push(material)
      if (useTextures) texturedMeshCount++
      if (key === 'plush' && hasVertexColors) vertexColorMeshCount++
    })
    if (key === 'plush') {
      createPreviewFurShells(plushTextures!)
      materialMessage.value = texturedMeshCount
        ? `已应用全毛绒：纤维颜色、法线、粗糙度、Sheen 柔光与 ${window.innerWidth < 650 ? 5 : 9} 层 Shell Fur 已生效${vertexColorMeshCount ? '，并保留设计部导出的顶点色。' : '。将蓬松长度调高可看到明显的轮廓毛束。'}`
        : '模型没有 UV，毛绒纤维贴图无法铺设；已保留柔光毛绒参数。建议让建模方导出带 UV 的 GLB。'
    } else if (key === 'ppc') {
      materialMessage.value = texturedMeshCount
        ? '已应用「PPC 高精硬塑」：低光泽高密度聚合物表面、微橘皮法线与粗糙度变化已生效，可用于精密硬塑摆件的视觉预览。'
        : '该模型没有可用 UV，已应用 PPC 的低光泽硬塑参数；建议使用带 UV 的 GLB 以显示微表面纹理。'
    } else {
      materialMessage.value = preset.textures
        ? (texturedMeshCount ? `已铺设「${preset.label}」真实木纹与凹凸贴图，可旋转模型查看木纹反光。` : '该模型没有可用 UV，无法铺设木纹贴图，已降级为木色参数材质。')
        : `已应用「${preset.label}」参数化 PBR 材质。`
    }
  } catch (error: any) {
    materialMessage.value = '材质贴图加载失败，已保留当前模型。'
    emit('error', error?.message || '材质贴图加载失败')
  }
}
async function buildGlb() {
  if (!root) throw new Error('模型尚未加载完成')
  // Shell Fur 是浏览器预览增强层，GLB 导出仅保留兼容性更高的 PBR 毛绒材质与贴图。
  const visibleStates = previewFurShells.map(shell => ({ shell, visible: shell.visible })); previewFurShells.forEach(shell => { shell.visible = false })
  try {
    const binary = await new Promise<ArrayBuffer>((resolve, reject) => new GLTFExporter().parse(root!, output => resolve(output as ArrayBuffer), reject, { binary: true, onlyVisible: true, maxTextureSize: 2048 }))
    return new Blob([binary], { type: 'model/gltf-binary' })
  } finally { visibleStates.forEach(({ shell, visible }) => { shell.visible = visible }) }
}
async function exportGlb() {
  if (!root || exporting.value) return; exporting.value = true
  try { const blob = await buildGlb(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `${props.modelName || 'and-taste-model'}-${presets.find(item => item.key === selected.value)?.label || '材质版'}.glb`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1200) } catch (error: any) { emit('error', error?.message || '导出材质模型失败') } finally { exporting.value = false }
}
async function saveVariant() {
  if (!root || saving.value) return; saving.value = true
  try { emit('saveVariant', { blob: await buildGlb(), materialLabel: presets.find(item => item.key === selected.value)?.label || '自定义材质' }) } catch (error: any) { emit('error', error?.message || '保存材质版本失败') } finally { saving.value = false }
}
function cleanup() {
  cancelAnimationFrame(frame); window.removeEventListener('resize', resize); controls?.dispose(); disposeGenerated()
  textureCache.forEach(texture => texture.dispose()); textureCache.clear(); sourceTextureCache.clear()
  renderer?.dispose(); renderer = null; scene = null; camera = null; controls = null; root = null
}
watch(() => props.modelUrl, () => { cleanup(); nextTick(loadModel) }, { immediate: true })
onBeforeUnmount(cleanup)
</script>

<template>
  <section class="material-studio">
    <div ref="host" class="material-canvas"></div>
    <div v-if="status==='loading'" class="studio-state"><i></i><span>正在载入 3D 模型</span></div>
    <div v-else-if="status==='error'" class="studio-state error"><b>模型暂时无法载入</b><span>请稍后重试或下载原始模型。</span></div>
    <aside v-if="status==='ready'" class="material-panel">
      <div class="material-panel-title"><span>MATERIAL LAB</span><b>为模型换一套材质</b><small>实时预览 · 可导出 GLB</small></div>
      <div class="material-preset-list"><button v-for="preset in presets" :key="preset.key" type="button" :class="['material-preset', preset.key, { active:selected===preset.key }]" @click="applyPreset(preset.key)"><i></i><span><b>{{ preset.label }}</b><small>{{ preset.hint }}</small></span></button></div>
      <div v-if="selected==='ppc'" class="ppc-controls"><label><span>PPC 基础色</span><input v-model="ppcColor" type="color" @input="applyPreset('ppc')" /><b>{{ ppcColor.toUpperCase() }}</b></label><small>低光泽注塑硬塑预览，已包含微橘皮纹理、法线和粗糙度变化。</small></div>
      <div v-if="selected==='plush'" class="plush-controls"><label><span>毛绒底色</span><input v-model="plushColor" type="color" @input="applyPreset('plush')" /><b>{{ plushColor.toUpperCase() }}</b></label><label class="plush-range"><span>纤维密度</span><input v-model.number="plushDensity" type="range" min="20" max="100" @input="applyPreset('plush')" /><b>{{ plushDensity }}%</b></label><label class="plush-range"><span>蓬松长度</span><input v-model.number="plushLength" type="range" min="20" max="100" @input="applyPreset('plush')" /><b>{{ plushLength }}%</b></label><label class="plush-switch"><input v-model="plushPreviewFur" type="checkbox" @change="applyPreset('plush')" /><span>开启边缘蓬松预览</span></label><small>已叠加纤维颜色、法线、粗糙度、柔光和多层 Shell Fur。若模型带顶点色，会保留渐变、斑点等设计细节。</small></div>
      <div class="material-panel-note">{{ materialMessage }}</div>
      <div class="material-actions"><button type="button" class="material-save" :disabled="saving" @click="saveVariant">{{ saving ? '正在保存…' : '保存到作品库' }}</button><button type="button" class="material-export" :disabled="exporting" @click="exportGlb">{{ exporting ? '导出中…' : '下载 GLB ↓' }}</button></div>
    </aside>
  </section>
</template>

<style scoped>
.material-studio{position:relative;width:100%;height:100%;min-height:480px;overflow:hidden;background:radial-gradient(circle at 50% 40%,#4a2a1d 0,#17120f 58%)}.material-canvas{position:absolute;inset:0}.material-canvas :deep(canvas){display:block;width:100%;height:100%}.studio-state{position:absolute;left:50%;top:50%;display:grid;gap:10px;place-items:center;transform:translate(-50%,-50%);padding:18px 20px;border:1px solid rgba(255,255,255,.14);border-radius:16px;background:rgba(25,17,13,.62);color:#fff;font-size:12px;backdrop-filter:blur(12px)}.studio-state i{width:28px;height:28px;border:3px solid rgba(255,255,255,.2);border-top-color:#ffd0a1;border-radius:50%;animation:spin 1s linear infinite}.studio-state.error{min-width:210px;text-align:center}.studio-state.error b{color:#ffc49c}.material-panel{position:absolute;top:15px;right:15px;width:245px;padding:15px;border:1px solid rgba(255,224,192,.22);border-radius:18px;background:rgba(27,18,14,.78);color:#fff;backdrop-filter:blur(15px)}.material-panel-title{display:grid;gap:4px;margin-bottom:12px}.material-panel-title span{color:#f2bd89;font-size:9px;font-weight:950;letter-spacing:1.4px}.material-panel-title b{font-size:15px}.material-panel-title small{color:rgba(255,255,255,.56);font-size:10px}.material-preset-list{display:grid;grid-template-columns:1fr 1fr;gap:7px}.material-preset{display:flex;align-items:center;gap:8px;padding:8px;border:1px solid rgba(255,255,255,.11);border-radius:11px;background:rgba(255,255,255,.07);color:#fff;text-align:left}.material-preset.active{border-color:#f0a468;background:rgba(220,112,58,.24)}.material-preset i{width:22px;height:22px;flex:0 0 auto;border-radius:8px;background:#ddd}.material-preset span{display:grid;gap:2px}.material-preset b{font-size:10px}.material-preset small{color:rgba(255,255,255,.52);font-size:8px;line-height:1.3}.material-preset.ceramic i{background:linear-gradient(135deg,#fff5e7,#d79e6e)}.material-preset.gold i{background:linear-gradient(135deg,#ffe2a0,#a86913)}.material-preset.metal i{background:linear-gradient(135deg,#d1d9df,#40484f)}.material-preset.wood i{background:linear-gradient(135deg,#bf7f4a,#5f341c)}.material-preset.stainedPine i{background:linear-gradient(135deg,rgba(255,255,255,.12),rgba(38,13,6,.14)),url('/materials/stained-pine/base-color.jpg') center/cover}.material-preset.ppc i{background:linear-gradient(135deg,#f0f4f2,#89938f 56%,#dce3df)}.material-preset.plush i{background:radial-gradient(circle at 28% 30%,#ffe8da 0 5%,transparent 6%),radial-gradient(circle at 65% 55%,#d8795d 0 5%,transparent 6%),radial-gradient(circle at 46% 75%,#f8b496 0 4%,transparent 5%),linear-gradient(135deg,#fac2aa,#a84738)}.material-preset.acrylic i{background:linear-gradient(135deg,#d9ffff,#61aeb5)}.material-preset.resin i{background:linear-gradient(135deg,#ffb09d,#c9503e)}.material-preset.original i{background:conic-gradient(#ffd9b5,#7560bd,#77cba7,#ffd9b5)}.ppc-controls,.plush-controls{display:grid;gap:7px;margin-top:10px;padding:9px;border:1px solid rgba(255,197,168,.22);border-radius:11px;background:rgba(255,158,116,.09)}.ppc-controls label,.plush-controls label{display:flex;align-items:center;gap:7px}.plush-controls .plush-range{gap:6px}.plush-range span{margin-right:0}.plush-range input{flex:1;accent-color:#ffab75}.plush-switch{padding-top:2px}.plush-switch input{accent-color:#ffab75}.plush-switch span{margin-right:0!important;color:#fff!important;font-size:9px!important}.ppc-controls span,.plush-controls span{margin-right:auto;color:#ffd6bd;font-size:10px;font-weight:900}.ppc-controls input,.plush-controls input{width:30px;height:25px;padding:1px;border:0;border-radius:7px;background:transparent}.ppc-controls b,.plush-controls b{color:#fff;font-size:9px;font-family:monospace}.ppc-controls small,.plush-controls small{color:rgba(255,255,255,.62);font-size:8px;line-height:1.45}.material-panel-note{margin-top:11px;color:rgba(255,255,255,.58);font-size:9px;line-height:1.55}.material-actions{display:grid;grid-template-columns:1.25fr .9fr;gap:7px;margin-top:11px}.material-export,.material-save{height:38px;border:0;border-radius:11px;font-size:10px;font-weight:950}.material-export{background:rgba(255,255,255,.12);color:#fff}.material-save{background:linear-gradient(135deg,#ffd0a1,#e37b48);color:#3c1a10}.material-export:disabled,.material-save:disabled{opacity:.6}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:650px){.material-studio{min-height:540px}.material-panel{top:auto;right:10px;bottom:10px;left:10px;width:auto}.material-preset-list{grid-template-columns:repeat(3,1fr)}.material-preset{padding:7px}.material-preset small{display:none}.material-panel-note{display:none}}
</style>
