#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'

globalThis.self ||= globalThis

const THREE = await import('three')
const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js')
const { MeshoptDecoder } = await import('three/examples/jsm/libs/meshopt_decoder.module.js')
const { OBJExporter } = await import('three/examples/jsm/exporters/OBJExporter.js')
const { STLExporter } = await import('three/examples/jsm/exporters/STLExporter.js')

const [, , inputArg, outputArg, formatArg] = process.argv
if (!inputArg || !outputArg || !formatArg) {
  console.error('usage: node model-convert-three.mjs input.glb output.obj|output.stl OBJ|STL')
  process.exit(2)
}

const input = path.resolve(inputArg)
const output = path.resolve(outputArg)
const format = String(formatArg).toUpperCase()
if (!fs.existsSync(input)) {
  console.error(`input file not found: ${input}`)
  process.exit(2)
}
if (!['OBJ', 'STL'].includes(format)) {
  console.error(`unsupported format: ${format}`)
  process.exit(2)
}

globalThis.ProgressEvent ||= class ProgressEvent extends Event {
  constructor(type, init = {}) {
    super(type)
    this.lengthComputable = init.lengthComputable || false
    this.loaded = init.loaded || 0
    this.total = init.total || 0
  }
}

const buffer = fs.readFileSync(input)
const arrayBuffer = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength)
const loader = new GLTFLoader()
loader.setMeshoptDecoder(MeshoptDecoder)

const gltf = await new Promise((resolve, reject) => {
  loader.parse(arrayBuffer, '', resolve, reject)
})

const scene = gltf.scene || new THREE.Group()
scene.updateMatrixWorld(true)

let meshCount = 0
scene.traverse(obj => {
  if (obj.isMesh) {
    meshCount += 1
    obj.geometry?.computeVertexNormals?.()
  }
})
if (!meshCount) {
  console.error('no mesh found in GLB')
  process.exit(3)
}

fs.mkdirSync(path.dirname(output), { recursive: true })
let content
if (format === 'OBJ') {
  content = new OBJExporter().parse(scene)
} else {
  content = new STLExporter().parse(scene, { binary: false })
}
fs.writeFileSync(output, content)
console.log(`converted ${input} -> ${output} (${format}, meshes=${meshCount})`)
