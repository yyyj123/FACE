<template>
  <div
    ref="container"
    class="circular-gallery"
    tabindex="0"
    role="region"
    :aria-label="ariaLabel"
  >
    <p class="circular-gallery__hint" aria-hidden="true">拖动浏览 · 滚轮切换</p>
  </div>
</template>

<script>
import { Camera, Mesh, Plane, Program, Renderer, Texture, Transform } from 'ogl'

const lerp = (start, end, amount) => start + (end - start) * amount
const modulo = (value, length) => ((value % length) + length) % length

function createTextTexture(gl, text) {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  const font = '600 28px system-ui, sans-serif'
  context.font = font
  const width = Math.min(760, Math.ceil(context.measureText(text).width) + 36)
  canvas.width = width
  canvas.height = 64
  context.font = font
  context.fillStyle = '#f5edf1'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(text, canvas.width / 2, canvas.height / 2, canvas.width - 20)
  const texture = new Texture(gl, { generateMipmaps: false })
  texture.image = canvas
  return { texture, width: canvas.width, height: canvas.height }
}

class GalleryMedia {
  constructor({ app, data, index, length }) {
    this.app = app
    this.data = data
    this.index = index
    this.length = length
    this.extra = 0
    this.createMesh()
    this.resize()
  }

  createMesh() {
    const texture = new Texture(this.app.gl, { generateMipmaps: true })
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.src = this.data.image
    image.onload = () => {
      texture.image = image
      this.program.uniforms.uImageSizes.value = [image.naturalWidth, image.naturalHeight]
    }

    this.program = new Program(this.app.gl, {
      depthTest: false,
      depthWrite: false,
      transparent: true,
      vertex: `
        precision highp float;
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        uniform float uTime;
        uniform float uSpeed;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          vec3 p = position;
          p.z = (sin(p.x * 4.0 + uTime) + cos(p.y * 2.0 + uTime)) * (0.08 + abs(uSpeed) * 0.32);
          gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform vec2 uImageSizes;
        uniform vec2 uPlaneSizes;
        uniform sampler2D tMap;
        varying vec2 vUv;
        float roundedBoxSDF(vec2 p, vec2 b, float r) {
          vec2 d = abs(p) - b;
          return length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0) - r;
        }
        void main() {
          vec2 ratio = vec2(
            min((uPlaneSizes.x / uPlaneSizes.y) / (uImageSizes.x / uImageSizes.y), 1.0),
            min((uPlaneSizes.y / uPlaneSizes.x) / (uImageSizes.y / uImageSizes.x), 1.0)
          );
          vec2 uv = vec2(vUv.x * ratio.x + (1.0 - ratio.x) * 0.5, vUv.y * ratio.y + (1.0 - ratio.y) * 0.5);
          vec4 color = texture2D(tMap, uv);
          float d = roundedBoxSDF(vUv - 0.5, vec2(0.445), 0.055);
          float alpha = 1.0 - smoothstep(-0.003, 0.003, d);
          gl_FragColor = vec4(color.rgb, color.a * alpha);
        }
      `,
      uniforms: {
        tMap: { value: texture },
        uImageSizes: { value: [1, 1] },
        uPlaneSizes: { value: [1, 1] },
        uTime: { value: Math.random() * 100 },
        uSpeed: { value: 0 }
      }
    })
    this.mesh = new Mesh(this.app.gl, { geometry: this.app.geometry, program: this.program })
    this.mesh.setParent(this.app.scene)
    this.createTitle()
  }

  createTitle() {
    const title = createTextTexture(this.app.gl, this.data.text)
    const program = new Program(this.app.gl, {
      depthTest: false,
      depthWrite: false,
      transparent: true,
      vertex: `
        attribute vec3 position;
        attribute vec2 uv;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform sampler2D tMap;
        varying vec2 vUv;
        void main() {
          vec4 color = texture2D(tMap, vUv);
          if (color.a < 0.05) discard;
          gl_FragColor = color;
        }
      `,
      uniforms: { tMap: { value: title.texture } }
    })
    this.titleMesh = new Mesh(this.app.gl, { geometry: new Plane(this.app.gl), program })
    this.titleAspect = title.width / title.height
    this.titleMesh.setParent(this.mesh)
  }

  resize() {
    const compact = this.app.screen.width < 760
    this.mesh.scale.y = compact ? 6.6 : 9.5
    this.mesh.scale.x = compact ? 6.4 : 13.2
    this.program.uniforms.uPlaneSizes.value = [this.mesh.scale.x, this.mesh.scale.y]
    const worldTitleHeight = compact ? 0.92 : 1.08
    const worldTitleWidth = Math.min(this.mesh.scale.x * 0.92, worldTitleHeight * this.titleAspect)
    this.titleMesh.scale.set(
      worldTitleWidth / this.mesh.scale.x,
      worldTitleHeight / this.mesh.scale.y,
      1
    )
    this.titleMesh.position.y = -0.61
    this.titleMesh.position.z = 0.03
    this.padding = compact ? 0.65 : 0.9
    this.width = this.mesh.scale.x + this.padding
    this.widthTotal = this.width * this.length
    this.x = this.width * this.index
  }

  update(scroll, direction) {
    this.mesh.position.x = this.x - scroll.current - this.extra
    const x = this.mesh.position.x
    const halfWidth = this.app.viewport.width / 2
    const bend = this.app.bend
    const radius = (halfWidth * halfWidth + bend * bend) / (2 * Math.abs(bend))
    const effectiveX = Math.min(Math.abs(x), halfWidth)
    const arc = radius - Math.sqrt(Math.max(0, radius * radius - effectiveX * effectiveX))
    this.mesh.position.y = bend > 0 ? -arc : arc
    this.mesh.rotation.z = (bend > 0 ? -1 : 1) * Math.sign(x) * Math.asin(effectiveX / radius)

    const speed = scroll.current - scroll.last
    this.program.uniforms.uTime.value += 0.035
    this.program.uniforms.uSpeed.value = speed

    const planeOffset = this.mesh.scale.x / 2
    const viewportOffset = this.app.viewport.width / 2
    if (direction === 'right' && this.mesh.position.x + planeOffset < -viewportOffset) this.extra -= this.widthTotal
    if (direction === 'left' && this.mesh.position.x - planeOffset > viewportOffset) this.extra += this.widthTotal
  }
}

class GalleryApp {
  constructor(container, options) {
    this.container = container
    this.items = options.items
    this.bend = options.bend
    this.ease = options.ease
    this.speed = options.speed
    this.onSelect = options.onSelect
    this.scroll = { current: 0, target: 0, last: 0, position: 0 }
    this.createScene()
    this.resize()
    this.geometry = new Plane(this.gl, { heightSegments: 36, widthSegments: 72 })
    const repeated = this.items.concat(this.items)
    this.medias = repeated.map((data, index) => new GalleryMedia({ app: this, data, index, length: repeated.length }))
    this.bindEvents()
    this.update()
  }

  createScene() {
    this.renderer = new Renderer({ alpha: true, antialias: true, dpr: Math.min(window.devicePixelRatio || 1, 2) })
    this.gl = this.renderer.gl
    this.gl.clearColor(0, 0, 0, 0)
    this.container.insertBefore(this.gl.canvas, this.container.firstChild)
    this.camera = new Camera(this.gl)
    this.camera.fov = 45
    this.camera.position.z = 20
    this.scene = new Transform()
  }

  resize = () => {
    this.screen = { width: this.container.clientWidth, height: this.container.clientHeight }
    this.renderer.setSize(this.screen.width, this.screen.height)
    this.camera.perspective({ aspect: this.screen.width / this.screen.height })
    const height = 2 * Math.tan((this.camera.fov * Math.PI) / 360) * this.camera.position.z
    this.viewport = { width: height * this.camera.aspect, height }
    if (this.medias) this.medias.forEach(media => media.resize())
  }

  snap = () => {
    if (!this.medias || !this.medias[0]) return
    const width = this.medias[0].width
    this.scroll.target = Math.round(this.scroll.target / width) * width
  }

  selectedIndex() {
    if (!this.items.length || !this.medias[0]) return 0
    return modulo(Math.round(this.scroll.target / this.medias[0].width), this.items.length)
  }

  wheel = event => {
    event.preventDefault()
    this.scroll.target += Math.sign(event.deltaY || event.deltaX) * this.speed
    clearTimeout(this.snapTimer)
    this.snapTimer = setTimeout(this.snap, 140)
  }

  pointerDown = event => {
    this.dragging = true
    this.moved = false
    this.scroll.position = this.scroll.current
    this.startX = event.touches ? event.touches[0].clientX : event.clientX
  }

  pointerMove = event => {
    if (!this.dragging) return
    const x = event.touches ? event.touches[0].clientX : event.clientX
    const distance = (this.startX - x) * 0.022
    this.moved = this.moved || Math.abs(this.startX - x) > 5
    this.scroll.target = this.scroll.position + distance * this.speed
  }

  pointerUp = () => {
    if (!this.dragging) return
    this.dragging = false
    this.snap()
    if (!this.moved) this.onSelect(this.items[this.selectedIndex()])
  }

  keyDown = event => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      this.scroll.target += (event.key === 'ArrowRight' ? 1 : -1) * this.medias[0].width
      this.snap()
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      this.onSelect(this.items[this.selectedIndex()])
    }
  }

  bindEvents() {
    window.addEventListener('resize', this.resize)
    this.container.addEventListener('wheel', this.wheel, { passive: false })
    this.container.addEventListener('mousedown', this.pointerDown)
    window.addEventListener('mousemove', this.pointerMove)
    window.addEventListener('mouseup', this.pointerUp)
    this.container.addEventListener('touchstart', this.pointerDown, { passive: true })
    window.addEventListener('touchmove', this.pointerMove, { passive: true })
    window.addEventListener('touchend', this.pointerUp)
    this.container.addEventListener('keydown', this.keyDown)
  }

  update = () => {
    this.scroll.current = lerp(this.scroll.current, this.scroll.target, this.ease)
    const direction = this.scroll.current > this.scroll.last ? 'right' : 'left'
    this.medias.forEach(media => media.update(this.scroll, direction))
    this.renderer.render({ scene: this.scene, camera: this.camera })
    this.scroll.last = this.scroll.current
    this.raf = requestAnimationFrame(this.update)
  }

  destroy() {
    cancelAnimationFrame(this.raf)
    clearTimeout(this.snapTimer)
    window.removeEventListener('resize', this.resize)
    this.container.removeEventListener('wheel', this.wheel)
    this.container.removeEventListener('mousedown', this.pointerDown)
    window.removeEventListener('mousemove', this.pointerMove)
    window.removeEventListener('mouseup', this.pointerUp)
    this.container.removeEventListener('touchstart', this.pointerDown)
    window.removeEventListener('touchmove', this.pointerMove)
    window.removeEventListener('touchend', this.pointerUp)
    this.container.removeEventListener('keydown', this.keyDown)
    if (this.gl.canvas.parentNode) this.gl.canvas.parentNode.removeChild(this.gl.canvas)
  }
}

export default {
  name: 'CircularGallery',
  props: {
    items: { type: Array, default: () => [] },
    bend: { type: Number, default: 2.3 },
    scrollEase: { type: Number, default: 0.055 },
    scrollSpeed: { type: Number, default: 2.4 },
    ariaLabel: { type: String, default: '护理项目循环画廊，使用左右方向键浏览，按回车查看详情' }
  },
  watch: {
    items() { this.mountGallery() }
  },
  mounted() { this.mountGallery() },
  beforeDestroy() { if (this.gallery) this.gallery.destroy() },
  methods: {
    mountGallery() {
      this.$nextTick(() => {
        if (this.gallery) this.gallery.destroy()
        if (!this.$refs.container || !this.items.length) return
        this.gallery = new GalleryApp(this.$refs.container, {
          items: this.items,
          bend: this.bend,
          ease: this.scrollEase,
          speed: this.scrollSpeed,
          onSelect: item => this.$emit('select', item)
        })
      })
    }
  }
}
</script>

<style scoped>
.circular-gallery {
  position: relative;
  width: 100%;
  height: 460px;
  overflow: hidden;
  cursor: grab;
  touch-action: pan-y;
}
.circular-gallery:active { cursor: grabbing; }
.circular-gallery:focus-visible { outline: 2px solid var(--oc-focus); outline-offset: 4px; }
.circular-gallery__hint {
  position: absolute;
  right: 18px;
  bottom: 10px;
  z-index: 1;
  margin: 0;
  color: var(--oc-text-muted);
  font-size: 12px;
  pointer-events: none;
}
@media (max-width: 760px) {
  .circular-gallery { height: 350px; }
  .circular-gallery__hint { right: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .circular-gallery__hint { display: none; }
}
</style>
