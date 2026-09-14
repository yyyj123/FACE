<template>
  <div ref="container" class="oc-ferrofluid-bg" :style="rootStyle" aria-hidden="true"></div>
</template>

<script>
import { Mesh, Program, Renderer, Triangle } from 'ogl'

const MAX_COLORS = 8

const hexToRGB = hex => {
  const c = String(hex || '#ffffff').replace('#', '').padEnd(6, '0')
  return [
    parseInt(c.slice(0, 2), 16) / 255,
    parseInt(c.slice(2, 4), 16) / 255,
    parseInt(c.slice(4, 6), 16) / 255
  ]
}

const prepColors = input => {
  const base = (input && input.length ? input : ['#ffffff']).slice(0, MAX_COLORS)
  const arr = []
  for (let i = 0; i < MAX_COLORS; i += 1) arr.push(hexToRGB(base[Math.min(i, base.length - 1)]))
  return { arr, count: base.length }
}

const flowVec = direction => {
  switch (direction) {
    case 'up':
      return [0, 1]
    case 'left':
      return [-1, 0]
    case 'right':
      return [1, 0]
    case 'down':
    default:
      return [0, -1]
  }
}

const vertex = `
attribute vec2 position;
attribute vec2 uv;
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position, 0.0, 1.0);
}
`

const fragment = `
precision highp float;

uniform vec3 iResolution;
uniform vec2 iMouse;
uniform float iTime;
uniform vec3 uColor0;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform vec3 uColor4;
uniform vec3 uColor5;
uniform vec3 uColor6;
uniform vec3 uColor7;
uniform int uColorCount;
uniform vec2 uFlow;
uniform float uSpeed;
uniform float uScale;
uniform float uTurbulence;
uniform float uFluidity;
uniform float uRimWidth;
uniform float uSharpness;
uniform float uShimmer;
uniform float uGlow;
uniform float uOpacity;
uniform float uMouseEnabled;
uniform float uMouseStrength;
uniform float uMouseRadius;

varying vec2 vUv;

#define PI 3.14159265

vec3 palette(float h) {
  int count = uColorCount;
  if (count < 1) count = 1;
  int idx = int(floor(clamp(h, 0.0, 0.999999) * float(count)));
  if (idx <= 0) return uColor0;
  if (idx == 1) return uColor1;
  if (idx == 2) return uColor2;
  if (idx == 3) return uColor3;
  if (idx == 4) return uColor4;
  if (idx == 5) return uColor5;
  if (idx == 6) return uColor6;
  return uColor7;
}

float hash(vec3 p3) {
  p3 = fract(p3 * 0.1031);
  p3 += dot(p3, p3.zyx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

float smin(float a, float b, float k) {
  float r = exp2(-a / k) + exp2(-b / k);
  return -k * log2(r);
}

float sinlerp(float a, float b, float w) {
  return mix(a, b, (sin(w * PI - PI / 2.0) + 1.0) / 2.0);
}

float vn(vec2 p, float s, float seed) {
  vec2 cellp = floor(p / s);
  vec2 relp = mod(p, s);
  float g1 = hash(vec3(cellp, seed));
  float g2 = hash(vec3(cellp.x + 1.0, cellp.y, seed));
  float g3 = hash(vec3(cellp.x + 1.0, cellp.y + 1.0, seed));
  float g4 = hash(vec3(cellp.x, cellp.y + 1.0, seed));
  float bx = sinlerp(g1, g2, relp.x / s);
  float tx = sinlerp(g4, g3, relp.x / s);
  return sinlerp(bx, tx, relp.y / s);
}

float dbn(vec2 p, float s, float seed) {
  float o = s / 2.0;
  float n0 = vn(p, s, seed);
  float n1 = vn(p + vec2(o, o), s, seed + 0.1);
  float n2 = vn(p + vec2(-o, o), s, seed + 0.2);
  float n3 = vn(p + vec2(o, -o), s, seed + 0.3);
  float n4 = vn(p + vec2(-o, -o), s, seed + 0.4);
  return (2.0 * n0 + 1.5 * n1 + 1.25 * n2 + 1.125 * n3 + n4) / 7.0;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  float ref = 700.0 / max(uScale, 0.05);
  vec2 p = fragCoord / iResolution.y * ref;
  float spd = 200.0 * uSpeed;
  float t = iTime;
  vec2 dir = uFlow;
  vec2 perp = vec2(-dir.y, dir.x);
  float distort1 = vn(p + perp * (t * spd), 60.0, 10.0) * 50.0 * uTurbulence;
  float distort2 = vn(p - perp * (t * spd), 120.0, 15.0) * 100.0 * uTurbulence;
  float peaks = dbn(p + distort1 + dir * (t * spd * 0.5), 40.0, 1.0);
  float peaks2 = dbn(p + distort2 - dir * (t * spd * 0.5), 40.0, 0.0);
  float mapeaks = smin(peaks, peaks2, max(uFluidity, 0.001));
  float mGlow = 0.0;
  if (uMouseEnabled > 0.5) {
    vec2 mp = iMouse / iResolution.y * ref;
    float md = length(p - mp) / ref;
    float rr = max(uMouseRadius, 0.02);
    mGlow = exp(-md * md / (rr * rr)) * uMouseStrength;
  }
  float band = (uRimWidth - abs((mapeaks - 0.4) * 2.0)) * 5.0;
  float ltn = clamp(band - vn(p + dir * (t * spd * 0.5), 60.0, 12.0) * uShimmer, 0.0, 1.0);
  ltn = pow(ltn, uSharpness) * uGlow;
  ltn *= clamp(1.0 - mGlow, 0.0, 1.0);
  float h = clamp(0.5 + (peaks - peaks2) * 0.8, 0.0, 1.0);
  vec3 outc = palette(h) * ltn;
  float a = clamp(max(outc.r, max(outc.g, outc.b)), 0.0, 1.0);
  fragColor = vec4(outc, a * uOpacity);
}

void main() {
  vec4 color;
  mainImage(color, vUv * iResolution.xy);
  gl_FragColor = color;
}
`

export default {
  name: 'FerrofluidBackground',
  props: {
    colors: { type: Array, default: () => ['#f1c8d4', '#d7a18c', '#71364d'] },
    backgroundColor: { type: String, default: '#120d13' },
    speed: { type: Number, default: 0.18 },
    scale: { type: Number, default: 1.65 },
    turbulence: { type: Number, default: 0.75 },
    fluidity: { type: Number, default: 0.1 },
    rimWidth: { type: Number, default: 0.16 },
    sharpness: { type: Number, default: 2.5 },
    shimmer: { type: Number, default: 0.8 },
    glow: { type: Number, default: 1.15 },
    flowDirection: { type: String, default: 'down' },
    opacity: { type: Number, default: 0.12 },
    mouseInteraction: { type: Boolean, default: true },
    mouseStrength: { type: Number, default: 0.35 },
    mouseRadius: { type: Number, default: 0.32 },
    mouseDampening: { type: Number, default: 0.18 },
    dpr: { type: Number, default: 1.25 }
  },
  data() {
    return {
      frame: null,
      renderer: null,
      program: null,
      geometry: null,
      mesh: null,
      resizeObserver: null,
      mouseTarget: [0, 0],
      lastTime: 0,
      reduceMotion: false
    }
  },
  computed: {
    rootStyle() {
      return { backgroundColor: this.backgroundColor }
    }
  },
  mounted() {
    this.reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    this.init()
  },
  beforeDestroy() {
    this.destroy()
  },
  methods: {
    init() {
      const container = this.$refs.container
      if (!container) return

	      const renderer = new Renderer({
        dpr: Math.min(this.dpr || 1, window.devicePixelRatio || 1),
        alpha: true,
	        antialias: true
	      })
	      this._renderer = renderer
      const gl = renderer.gl
      const canvas = gl.canvas
      gl.clearColor(0, 0, 0, 0)
      canvas.style.width = '100%'
      canvas.style.height = '100%'
      canvas.style.display = 'block'
      container.appendChild(canvas)

      const { arr, count } = prepColors(this.colors)
      const uniforms = {
        iResolution: { value: [gl.drawingBufferWidth, gl.drawingBufferHeight, 1] },
        iMouse: { value: [0, 0] },
        iTime: { value: 0 },
        uColor0: { value: arr[0] },
        uColor1: { value: arr[1] },
        uColor2: { value: arr[2] },
        uColor3: { value: arr[3] },
        uColor4: { value: arr[4] },
        uColor5: { value: arr[5] },
        uColor6: { value: arr[6] },
        uColor7: { value: arr[7] },
        uColorCount: { value: count },
        uFlow: { value: flowVec(this.flowDirection) },
        uSpeed: { value: this.speed },
        uScale: { value: this.scale },
        uTurbulence: { value: this.turbulence },
        uFluidity: { value: this.fluidity },
        uRimWidth: { value: this.rimWidth },
        uSharpness: { value: this.sharpness },
        uShimmer: { value: this.shimmer },
        uGlow: { value: this.glow },
        uOpacity: { value: this.opacity },
        uMouseEnabled: { value: this.mouseInteraction && !this.reduceMotion ? 1 : 0 },
        uMouseStrength: { value: this.mouseStrength },
        uMouseRadius: { value: this.mouseRadius }
      }

	      const program = new Program(gl, { vertex, fragment, uniforms })
	      const geometry = new Triangle(gl)
	      const mesh = new Mesh(gl, { geometry, program })
	      this._program = program
	      this._geometry = geometry
	      this._mesh = mesh

      const resize = () => {
        const rect = container.getBoundingClientRect()
        renderer.setSize(Math.max(1, rect.width), Math.max(1, rect.height))
        uniforms.iResolution.value = [gl.drawingBufferWidth, gl.drawingBufferHeight, 1]
	        renderer.render({ scene: this._mesh })
      }
      resize()

      if (window.ResizeObserver) {
        this.resizeObserver = new ResizeObserver(resize)
        this.resizeObserver.observe(container)
      } else {
        window.addEventListener('resize', resize)
        this.resizeObserver = { disconnect: () => window.removeEventListener('resize', resize) }
      }

      this.onPointerMove = event => {
        const rect = container.getBoundingClientRect()
        const scale = renderer.dpr || 1
        this.mouseTarget = [(event.clientX - rect.left) * scale, (rect.height - (event.clientY - rect.top)) * scale]
      }
      if (this.mouseInteraction && !this.reduceMotion) window.addEventListener('pointermove', this.onPointerMove, { passive: true })

      const loop = time => {
	        if (!this._program || !this._mesh || !this._renderer) return
        uniforms.iTime.value = time * 0.001
        if (this.mouseDampening > 0) {
          if (!this.lastTime) this.lastTime = time
          const dt = (time - this.lastTime) / 1000
          this.lastTime = time
          const factor = Math.min(1, 1 - Math.exp(-dt / Math.max(0.0001, this.mouseDampening)))
          const current = uniforms.iMouse.value
          current[0] += (this.mouseTarget[0] - current[0]) * factor
          current[1] += (this.mouseTarget[1] - current[1]) * factor
        }
	        renderer.render({ scene: this._mesh })
        this.frame = requestAnimationFrame(loop)
      }

	      renderer.render({ scene: this._mesh })
      if (!this.reduceMotion) this.frame = requestAnimationFrame(loop)
    },
    destroy() {
      if (this.frame) cancelAnimationFrame(this.frame)
      if (this.onPointerMove) window.removeEventListener('pointermove', this.onPointerMove)
      if (this.resizeObserver) this.resizeObserver.disconnect()
      const container = this.$refs.container
	      const canvas = this._renderer && this._renderer.gl && this._renderer.gl.canvas
      if (container && canvas && canvas.parentElement === container) container.removeChild(canvas)
      ;[
	        [this._program, 'remove'],
	        [this._geometry, 'remove'],
	        [this._mesh, 'remove'],
	        [this._renderer, 'destroy']
      ].forEach(([target, method]) => {
        if (target && typeof target[method] === 'function') target[method]()
      })
	      this._renderer = null
	      this._program = null
	      this._geometry = null
	      this._mesh = null
    }
  }
}
</script>

<style scoped>
.oc-ferrofluid-bg {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}
</style>
