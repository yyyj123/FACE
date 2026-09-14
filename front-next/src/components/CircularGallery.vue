<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, Mesh, Plane, Program, Renderer, Texture, Transform } from 'ogl'
import { fallbackMediaUrl } from '../utils/format'
import { shouldUseNativeGallery } from '../utils/galleryMode'

export interface CircularGalleryItem {
  image: string
  text: string
}

const props = withDefaults(
  defineProps<{
    items: CircularGalleryItem[]
    bend?: number
    textColor?: string
    borderRadius?: number
    font?: string
    fontUrl?: string
    scrollSpeed?: number
    scrollEase?: number
    ariaLabel?: string
  }>(),
  {
    bend: 3,
    textColor: '#ffffff',
    borderRadius: 0.05,
    font: 'bold 30px Figtree',
    scrollSpeed: 2,
    scrollEase: 0.05,
    ariaLabel: '弧形图片画廊，可拖拽、滚动或使用左右方向键浏览',
  },
)

const container = ref<HTMLDivElement>()
const nativeMode = ref(true)
let gallery: GalleryApp | undefined

function debounce(callback: () => void, wait: number) {
  let timeout: number | undefined
  return () => {
    window.clearTimeout(timeout)
    timeout = window.setTimeout(callback, wait)
  }
}

function lerp(from: number, to: number, ease: number) {
  return from + (to - from) * ease
}

const DEFAULT_FONT = 'bold 30px Figtree'
const DEFAULT_FONT_URL = 'https://fonts.googleapis.com/css2?family=Figtree:wght@400;700&display=swap'

function deriveFontFamilyFromUrl(url: string) {
  const fileName = (url.split('/').pop() || 'custom-font').split('?')[0] || 'custom-font'
  const base = fileName.replace(/\.(woff2?|ttf|otf|eot)$/i, '')
  return base.replace(/[^a-zA-Z0-9-_ ]/g, '').trim() || 'CircularGalleryFont'
}

async function loadFontFromStylesheet(url: string) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Failed to fetch font stylesheet (${response.status})`)
  const cssText = await response.text()
  const faceBlocks = cssText.match(/@font-face\s*{[^}]*}/g) || []
  let family: string | null = null
  const fontFaces: FontFace[] = []

  for (const block of faceBlocks) {
    const familyMatch = block.match(/font-family:\s*['"]?([^;'"]+)['"]?/)
    const urlMatch = block.match(/url\(\s*['"]?([^'")]+)['"]?\s*\)/)
    if (!familyMatch?.[1] || !urlMatch?.[1]) continue
    family = familyMatch[1].trim()
    const descriptors: FontFaceDescriptors = {}
    const weightMatch = block.match(/font-weight:\s*([^;]+);/)
    const styleMatch = block.match(/font-style:\s*([^;]+);/)
    const rangeMatch = block.match(/unicode-range:\s*([^;]+);/)
    if (weightMatch?.[1]) descriptors.weight = weightMatch[1].trim()
    if (styleMatch?.[1]) descriptors.style = styleMatch[1].trim()
    if (rangeMatch?.[1]) descriptors.unicodeRange = rangeMatch[1].trim()
    fontFaces.push(new FontFace(family, `url(${urlMatch[1]})`, descriptors))
  }

  if (!family) throw new Error('No @font-face rule found in the stylesheet')
  await Promise.allSettled(
    fontFaces.map(async (face) => {
      await face.load()
      document.fonts.add(face)
    }),
  )
  return family
}

async function loadFontFromFile(url: string) {
  const family = deriveFontFamilyFromUrl(url)
  const fontFace = new FontFace(family, `url(${url})`)
  await fontFace.load()
  document.fonts.add(fontFace)
  return family
}

async function loadCustomFont(fontUrl: string) {
  const isStylesheet = fontUrl.includes('fonts.googleapis.com') || /\.css(\?.*)?$/i.test(fontUrl)
  return isStylesheet ? loadFontFromStylesheet(fontUrl) : loadFontFromFile(fontUrl)
}

async function resolveFont(font: string, fontUrl?: string) {
  const effectiveUrl = fontUrl || (font === DEFAULT_FONT ? DEFAULT_FONT_URL : null)
  if (!effectiveUrl) {
    if (document.fonts?.load) {
      try {
        await document.fonts.load(font)
        await document.fonts.ready
      } catch {
        // Fall through to the browser's closest available font.
      }
    }
    return font
  }

  try {
    const family = await loadCustomFont(effectiveUrl)
    const sizeMatch = font.match(/^\s*(.*?\d+px)/)
    const prefix = sizeMatch?.[1]?.trim() || 'bold 30px'
    const resolved = `${prefix} "${family}"`
    if (document.fonts?.load) {
      try {
        await document.fonts.load(resolved)
      } catch {
        // The loaded FontFace is still used when canvas paints the title.
      }
    }
    return resolved
  } catch (error) {
    console.error('CircularGallery: unable to load font from', fontUrl, error)
    return font
  }
}

function getFontSize(font: string) {
  const match = font.match(/(\d+)px/)
  return match?.[1] ? Number.parseInt(match[1], 10) : 30
}

function createTextTexture(gl: any, text: string, font: string, color: string) {
  const textCanvas = document.createElement('canvas')
  const context = textCanvas.getContext('2d')
  if (!context) throw new Error('Canvas 2D context is unavailable')
  context.font = font
  const textWidth = Math.ceil(context.measureText(text).width)
  const textHeight = Math.ceil(getFontSize(font) * 1.2)
  textCanvas.width = textWidth + 20
  textCanvas.height = textHeight + 20
  context.font = font
  context.fillStyle = color
  context.textBaseline = 'middle'
  context.textAlign = 'center'
  context.clearRect(0, 0, textCanvas.width, textCanvas.height)
  context.fillText(text, textCanvas.width / 2, textCanvas.height / 2)
  const texture = new Texture(gl, { generateMipmaps: false })
  texture.image = textCanvas
  return { texture, width: textCanvas.width, height: textCanvas.height }
}

class GalleryTitle {
  mesh: any

  constructor(
    gl: any,
    private plane: any,
    text: string,
    textColor: string,
    font: string,
  ) {
    const { texture, width, height } = createTextTexture(gl, text, font, textColor)
    const geometry = new Plane(gl)
    const program = new Program(gl, {
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
          if (color.a < 0.1) discard;
          gl_FragColor = color;
        }
      `,
      uniforms: { tMap: { value: texture } },
      transparent: true,
    })
    this.mesh = new Mesh(gl, { geometry, program })
    const aspect = width / height
    const titleHeight = this.plane.scale.y * 0.15
    this.mesh.scale.set(titleHeight * aspect, titleHeight, 1)
    this.mesh.position.y = -this.plane.scale.y * 0.5 - titleHeight * 0.5 - 0.05
    this.mesh.setParent(this.plane)
  }
}

class GalleryMedia {
  extra = 0
  plane: any
  title: GalleryTitle
  width = 0
  widthTotal = 0
  x = 0
  private isBefore = false
  private isAfter = false

  constructor(
    private options: {
      geometry: any
      gl: any
      image: string
      index: number
      length: number
      scene: any
      screen: { width: number; height: number }
      text: string
      viewport: { width: number; height: number }
      bend: number
      textColor: string
      borderRadius: number
      font: string
    },
  ) {
    this.createShader()
    this.createMesh()
    this.title = new GalleryTitle(
      options.gl,
      this.plane,
      options.text,
      options.textColor,
      options.font,
    )
    this.onResize()
  }

  private createShader() {
    const texture = new Texture(this.options.gl, { generateMipmaps: false })
    const program = new Program(this.options.gl, {
      depthTest: false,
      depthWrite: false,
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
          p.z = (sin(p.x * 4.0 + uTime) * 1.5 + cos(p.y * 2.0 + uTime) * 1.5)
            * (0.1 + uSpeed * 0.5);
          gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
        }
      `,
      fragment: `
        precision highp float;
        uniform vec2 uImageSizes;
        uniform vec2 uPlaneSizes;
        uniform sampler2D tMap;
        uniform float uBorderRadius;
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
          vec2 imageUv = vec2(
            vUv.x * ratio.x + (1.0 - ratio.x) * 0.5,
            vUv.y * ratio.y + (1.0 - ratio.y) * 0.5
          );
          vec4 color = texture2D(tMap, imageUv);
          float distance = roundedBoxSDF(
            vUv - 0.5,
            vec2(0.5 - uBorderRadius),
            uBorderRadius
          );
          float alpha = 1.0 - smoothstep(-0.002, 0.002, distance);
          gl_FragColor = vec4(color.rgb, alpha);
        }
      `,
      uniforms: {
        tMap: { value: texture },
        uPlaneSizes: { value: [0, 0] },
        uImageSizes: { value: [1, 1] },
        uSpeed: { value: 0 },
        uTime: { value: 100 * Math.random() },
        uBorderRadius: { value: this.options.borderRadius },
      },
      transparent: true,
    })
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.src = this.options.image
    image.onload = () => {
      texture.image = image
      program.uniforms.uImageSizes.value = [image.naturalWidth, image.naturalHeight]
    }
    this.plane = { program }
  }

  private createMesh() {
    this.plane = new Mesh(this.options.gl, {
      geometry: this.options.geometry,
      program: this.plane.program,
    })
    this.plane.setParent(this.options.scene)
  }

  update(
    scroll: { current: number; last: number },
    direction: 'left' | 'right',
    frameScale = 1,
  ) {
    this.plane.position.x = this.x - scroll.current - this.extra
    const position = this.plane.position.x
    const halfViewport = this.options.viewport.width / 2

    if (this.options.bend === 0) {
      this.plane.position.y = 0
      this.plane.rotation.z = 0
    } else {
      const bend = Math.abs(this.options.bend)
      const radius = (halfViewport * halfViewport + bend * bend) / (2 * bend)
      const effectiveX = Math.min(Math.abs(position), halfViewport)
      const arc = radius - Math.sqrt(radius * radius - effectiveX * effectiveX)
      if (this.options.bend > 0) {
        this.plane.position.y = -arc
        this.plane.rotation.z = -Math.sign(position) * Math.asin(effectiveX / radius)
      } else {
        this.plane.position.y = arc
        this.plane.rotation.z = Math.sign(position) * Math.asin(effectiveX / radius)
      }
    }

    const speed = (scroll.current - scroll.last) / Math.max(frameScale, 0.5)
    this.plane.program.uniforms.uTime.value += 0.04 * frameScale
    this.plane.program.uniforms.uSpeed.value = speed

    const planeOffset = this.plane.scale.x / 2
    const viewportOffset = this.options.viewport.width / 2
    this.isBefore = this.plane.position.x + planeOffset < -viewportOffset
    this.isAfter = this.plane.position.x - planeOffset > viewportOffset
    if (direction === 'right' && this.isBefore) {
      this.extra -= this.widthTotal
      this.isBefore = this.isAfter = false
    }
    if (direction === 'left' && this.isAfter) {
      this.extra += this.widthTotal
      this.isBefore = this.isAfter = false
    }
  }

  onResize(
    screen = this.options.screen,
    viewport = this.options.viewport,
  ) {
    this.options.screen = screen
    this.options.viewport = viewport
    const scale = screen.height / 1500
    this.plane.scale.y = (viewport.height * (900 * scale)) / screen.height
    this.plane.scale.x = (viewport.width * (700 * scale)) / screen.width
    this.plane.program.uniforms.uPlaneSizes.value = [this.plane.scale.x, this.plane.scale.y]
    const padding = 2
    this.width = this.plane.scale.x + padding
    this.widthTotal = this.width * this.options.length
    this.x = this.width * this.options.index
  }
}

class GalleryApp {
  private renderer: any
  private gl: any
  private camera: any
  private scene: any
  private medias: GalleryMedia[] = []
  private screen = { width: 1, height: 1 }
  private viewport = { width: 1, height: 1 }
  private scroll: { ease: number; current: number; target: number; last: number; position?: number }
  private start = 0
  private isDown = false
  private frame = 0
  private isRunning = false
  private lastFrameTime = 0
  private visibilityObserver?: IntersectionObserver
  private onCheckDebounced: () => void

  constructor(
    private container: HTMLDivElement,
    private options: {
      items: CircularGalleryItem[]
      bend: number
      textColor: string
      borderRadius: number
      font: string
      scrollSpeed: number
      scrollEase: number
    },
  ) {
    this.scroll = {
      ease: options.scrollEase,
      current: 0,
      target: 0,
      last: 0,
    }
    this.onCheckDebounced = debounce(() => this.onCheck(), 200)
    const deviceDpr = Math.min(window.devicePixelRatio || 1, 2)
    const surfacePixels = Math.max(
      this.container.clientWidth * this.container.clientHeight,
      1,
    )
    const budgetDpr = Math.sqrt(4_000_000 / surfacePixels)
    this.renderer = new Renderer({
      alpha: true,
      antialias: true,
      dpr: Math.max(1, Math.min(deviceDpr, budgetDpr)),
    })
    this.gl = this.renderer.gl
    this.gl.clearColor(0, 0, 0, 0)
    this.container.appendChild(this.gl.canvas as HTMLCanvasElement)
    this.camera = new Camera(this.gl)
    this.camera.fov = 45
    this.camera.position.z = 20
    this.scene = new Transform()
    this.onResize()
    const geometry = new Plane(this.gl, { heightSegments: 50, widthSegments: 100 })
    const repeatedItems = options.items.concat(options.items)
    this.medias = repeatedItems.map(
      (item, index) =>
        new GalleryMedia({
          geometry,
          gl: this.gl,
          image: item.image,
          index,
          length: repeatedItems.length,
          scene: this.scene,
          screen: this.screen,
          text: item.text,
          viewport: this.viewport,
          bend: options.bend,
          textColor: options.textColor,
          borderRadius: options.borderRadius,
          font: options.font,
        }),
    )
    this.addEventListeners()
    this.observeVisibility()
  }

  private onResize = () => {
    this.screen = {
      width: Math.max(this.container.clientWidth, 1),
      height: Math.max(this.container.clientHeight, 1),
    }
    this.renderer.setSize(this.screen.width, this.screen.height)
    this.camera.perspective({ aspect: this.screen.width / this.screen.height })
    const fov = (this.camera.fov * Math.PI) / 180
    const height = 2 * Math.tan(fov / 2) * this.camera.position.z
    this.viewport = { width: height * this.camera.aspect, height }
    this.medias.forEach((media) => media.onResize(this.screen, this.viewport))
  }

  private onPointerDown = (event: PointerEvent) => {
    if (!event.isPrimary || event.button !== 0) return
    this.isDown = true
    this.scroll.position = this.scroll.current
    this.start = event.clientX
    this.container.setPointerCapture?.(event.pointerId)
  }

  private onPointerMove = (event: PointerEvent) => {
    if (!this.isDown || !event.isPrimary) return
    const coalescedEvents = event.getCoalescedEvents?.()
    const currentEvent = coalescedEvents?.[coalescedEvents.length - 1] || event
    const distance = (this.start - currentEvent.clientX) * (this.options.scrollSpeed * 0.025)
    this.scroll.target = (this.scroll.position ?? 0) + distance
  }

  private onPointerUp = (event: PointerEvent) => {
    if (!event.isPrimary || !this.isDown) return
    this.isDown = false
    this.container.releasePointerCapture?.(event.pointerId)
    this.onCheck()
  }

  private onWheel = (event: WheelEvent & { wheelDelta?: number; detail?: number }) => {
    const delta = event.deltaY || event.wheelDelta || event.detail || 0
    this.scroll.target += (delta > 0 ? this.options.scrollSpeed : -this.options.scrollSpeed) * 0.2
    this.onCheckDebounced()
  }

  private onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      this.scroll.target += this.options.scrollSpeed * 5
      this.onCheckDebounced()
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      this.scroll.target -= this.options.scrollSpeed * 5
      this.onCheckDebounced()
    } else if (event.key === 'Home') {
      event.preventDefault()
      this.scroll.target = 0
      this.onCheckDebounced()
    }
  }

  private onCheck() {
    const width = this.medias[0]?.width
    if (!width) return
    const itemIndex = Math.round(Math.abs(this.scroll.target) / width)
    const item = width * itemIndex
    this.scroll.target = this.scroll.target < 0 ? -item : item
  }

  private update = (timestamp: number) => {
    if (!this.isRunning) return
    const elapsed = this.lastFrameTime
      ? Math.min(Math.max(timestamp - this.lastFrameTime, 4), 34)
      : 1000 / 60
    const frameScale = elapsed / (1000 / 60)
    const frameEase = 1 - Math.pow(1 - this.scroll.ease, frameScale)
    this.scroll.current = lerp(this.scroll.current, this.scroll.target, frameEase)
    const direction = this.scroll.current > this.scroll.last ? 'right' : 'left'
    this.medias.forEach((media) => media.update(this.scroll, direction, frameScale))
    this.renderer.render({ scene: this.scene, camera: this.camera })
    this.scroll.last = this.scroll.current
    this.lastFrameTime = timestamp
    this.frame = window.requestAnimationFrame(this.update)
  }

  private startRendering() {
    if (this.isRunning) return
    this.isRunning = true
    this.lastFrameTime = 0
    this.frame = window.requestAnimationFrame(this.update)
  }

  private stopRendering() {
    if (!this.isRunning) return
    this.isRunning = false
    window.cancelAnimationFrame(this.frame)
  }

  private observeVisibility() {
    if (!('IntersectionObserver' in window)) {
      this.startRendering()
      return
    }
    this.visibilityObserver = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting && document.visibilityState === 'visible') {
          this.startRendering()
        } else {
          this.stopRendering()
        }
      },
      { rootMargin: '160px 0px' },
    )
    this.visibilityObserver.observe(this.container)
  }

  private onVisibilityChange = () => {
    if (document.visibilityState === 'hidden') {
      this.stopRendering()
      return
    }
    const bounds = this.container.getBoundingClientRect()
    if (bounds.bottom >= -160 && bounds.top <= window.innerHeight + 160) {
      this.startRendering()
    }
  }

  private addEventListeners() {
    window.addEventListener('resize', this.onResize)
    document.addEventListener('visibilitychange', this.onVisibilityChange)
    this.container.addEventListener('pointerdown', this.onPointerDown)
    this.container.addEventListener('pointermove', this.onPointerMove)
    this.container.addEventListener('pointerup', this.onPointerUp)
    this.container.addEventListener('pointercancel', this.onPointerUp)
    this.container.addEventListener('wheel', this.onWheel, { passive: true })
    this.container.addEventListener('keydown', this.onKeyDown)
  }

  destroy() {
    this.stopRendering()
    this.visibilityObserver?.disconnect()
    window.removeEventListener('resize', this.onResize)
    document.removeEventListener('visibilitychange', this.onVisibilityChange)
    this.container.removeEventListener('pointerdown', this.onPointerDown)
    this.container.removeEventListener('pointermove', this.onPointerMove)
    this.container.removeEventListener('pointerup', this.onPointerUp)
    this.container.removeEventListener('pointercancel', this.onPointerUp)
    this.container.removeEventListener('wheel', this.onWheel)
    this.container.removeEventListener('keydown', this.onKeyDown)
    ;(this.gl.canvas as HTMLCanvasElement).remove()
  }
}

let galleryVersion = 0

async function createGallery() {
  const version = ++galleryVersion
  gallery?.destroy()
  gallery = undefined
  if (nativeMode.value || !props.items.length) return
  await nextTick()
  if (!container.value || version !== galleryVersion) return
  const resolvedFont = await resolveFont(props.font, props.fontUrl)
  if (!container.value || version !== galleryVersion) return
  document.documentElement.classList.remove('no-js')
  try {
    gallery = new GalleryApp(container.value, {
      items: props.items,
      bend: props.bend,
      textColor: props.textColor,
      borderRadius: props.borderRadius,
      font: resolvedFont,
      scrollSpeed: props.scrollSpeed,
      scrollEase: props.scrollEase,
    })
  } catch (error) {
    console.error('CircularGallery: unable to initialize WebGL', error)
    nativeMode.value = true
  }
}

function updateGalleryMode() {
  nativeMode.value = shouldUseNativeGallery({
    width: window.innerWidth,
  })
}

function handleNativeImageError(event: Event, index: number) {
  const image = event.currentTarget
  if (!(image instanceof HTMLImageElement)) return
  const fallback = fallbackMediaUrl(index)
  if (image.getAttribute('src') !== fallback) image.src = fallback
}

onMounted(() => {
  window.addEventListener('resize', updateGalleryMode)
  updateGalleryMode()
  void createGallery()
})
watch(
  () => [
    props.items,
    props.bend,
    props.textColor,
    props.borderRadius,
    props.font,
    props.fontUrl,
    props.scrollSpeed,
    props.scrollEase,
    nativeMode.value,
  ],
  createGallery,
  { deep: true },
)
onBeforeUnmount(() => {
  gallery?.destroy()
  window.removeEventListener('resize', updateGalleryMode)
})
</script>

<template>
  <div
    v-if="nativeMode"
    class="native-gallery"
    role="region"
    :aria-label="ariaLabel"
  >
    <ul class="native-gallery-list">
      <li v-for="(item, index) in items" :key="`${item.image}-${item.text}`">
        <figure>
          <img
            :src="item.image"
            :alt="item.text"
            :loading="index < 2 ? 'eager' : 'lazy'"
            decoding="async"
            @error="handleNativeImageError($event, index)"
          />
          <figcaption>{{ item.text }}</figcaption>
        </figure>
      </li>
    </ul>
  </div>
  <div
    v-else
    ref="container"
    class="circular-gallery"
    tabindex="0"
    role="region"
    :aria-label="ariaLabel"
  >
    <ul class="sr-only">
      <li v-for="item in items" :key="`${item.image}-${item.text}`">{{ item.text }}</li>
    </ul>
  </div>
</template>

<style scoped>
.circular-gallery {
  width: 100%;
  height: 100%;
  overflow: hidden;
  cursor: grab;
  touch-action: pan-y;
  user-select: none;
}

.circular-gallery:active {
  cursor: grabbing;
}

.circular-gallery:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 4px;
}

.native-gallery {
  width: 100%;
  height: 100%;
}

.native-gallery-list {
  height: 100%;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: min(78vw, 360px);
  align-items: start;
  gap: 16px;
  margin: 0;
  padding: 12px max(20px, calc((100vw - 1180px) / 2)) 24px;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scroll-snap-type: inline mandatory;
  scrollbar-width: thin;
  list-style: none;
}

.native-gallery-list li {
  min-width: 0;
  scroll-snap-align: start;
}

.native-gallery-list figure {
  display: grid;
  gap: 14px;
  margin: 0;
}

.native-gallery-list img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
  border-radius: 12px;
  background: #f2eef0;
}

.native-gallery-list figcaption {
  color: #251b21;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.5;
  text-wrap: pretty;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (max-width: 760px) {
  .native-gallery-list {
    grid-auto-columns: min(78vw, 320px);
    gap: 14px;
    padding-inline: 20px;
  }
}
</style>
