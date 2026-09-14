<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, PieChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ option: Record<string, unknown>; label: string }>()
const host = ref<HTMLElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

function render() {
  if (!host.value) return
  if (!chart) chart = init(host.value, undefined, { renderer: 'canvas' })
  chart.setOption(props.option as EChartsCoreOption, { notMerge: true })
}

watch(() => props.option, () => void nextTick(render), { deep: true })

onMounted(() => {
  render()
  if (host.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(host.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="host" class="echart-panel" role="img" :aria-label="label" />
</template>

<style scoped>
.echart-panel{width:100%;height:340px;min-height:280px}
@media(max-width:720px){.echart-panel{height:300px}}
</style>
