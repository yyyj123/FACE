import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('operations overview uses real analytics and ECharts panels', () => {
  const overview = read('../src/views/OverviewView.vue')
  const chart = read('../src/components/EChartPanel.vue')

  assert.match(overview, /getOperationsOverview/)
  assert.match(overview, /预约与实收趋势/)
  assert.match(overview, /预约状态分布/)
  assert.match(overview, /热门护理项目/)
  assert.match(overview, /会员新增趋势/)
  assert.match(overview, /技师服务负荷/)
  assert.doesNotMatch(overview, /Java 21|MySQL 8|Flyway|迁移检查/)
  assert.match(chart, /from 'echarts\/core'/)
  assert.match(chart, /ResizeObserver/)
})
