<template>
  <div class="dashboard">
    <!-- 顶部聚合卡（v31 数据看板） -->
    <el-row :gutter="16" class="row-block">
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">销售额（{{ ov.periodLabel }}）</div>
          <div class="kpi-value">¥{{ fmtMoney(ov.salesAmount) }}</div>
          <div v-if="ov.salesAmountChange !== null" class="kpi-delta" :class="deltaClass(ov.salesAmountChange)">
            {{ deltaText(ov.salesAmountChange) }}
          </div>
          <div v-else class="kpi-delta muted">环比暂无对比</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">已支付订单数</div>
          <div class="kpi-value">{{ ov.orderCount }}</div>
          <div class="kpi-delta muted">单</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">客单价</div>
          <div class="kpi-value">¥{{ fmtMoney(ov.avgOrderAmount) }}</div>
          <div class="kpi-delta muted">销售额 ÷ 订单数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">退款率</div>
          <div class="kpi-value">{{ refund.rate }}%</div>
          <div class="kpi-delta muted">{{ refund.refundCount }} / {{ refund.paidCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 区间切换 -->
    <el-row :gutter="16" class="row-toolbar">
      <el-col :span="24" style="display:flex; align-items:center; gap:12px;">
        <el-radio-group v-model="period" @change="reloadAll">
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="7d">近 7 天</el-radio-button>
          <el-radio-button value="30d">近 30 天</el-radio-button>
        </el-radio-group>
        <span class="muted">概览卡基于「今日」实时计算；图表默认近 30 天</span>
        <el-button size="small" :loading="loading" @click="reloadAll">刷新</el-button>
      </el-col>
    </el-row>

    <!-- 销售曲线 + 订单漏斗 -->
    <el-row :gutter="16" class="row-block">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>销售曲线（近 30 天）</span></template>
          <div ref="chartSalesRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>订单状态漏斗（近 30 天）</span></template>
          <div ref="chartFunnelRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 品类占比 + TOP SKU -->
    <el-row :gutter="16" class="row-block">
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>品类销售占比（近 30 天）</span></template>
          <div ref="chartCategoryRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>TOP SKU 销量榜（近 30 天）</span></template>
          <el-table :data="topSkus" size="small" stripe height="320">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="productName" label="商品" min-width="120" />
            <el-table-column prop="skuName" label="规格" min-width="100" />
            <el-table-column prop="qty" label="销量" width="80" align="right" />
            <el-table-column label="销售额" width="100" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 积分曲线 + 活动命中 -->
    <el-row :gutter="16" class="row-block">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>积分发放 vs 使用（近 14 天）</span></template>
          <div ref="chartPointsRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>活动命中 TOP（近 30 天）</span></template>
          <div ref="chartPromoRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 原有便利卡：当前门店 / 绑定门店 / 账号信息 -->
    <el-row :gutter="16" class="row-block">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#409EFF"><Shop /></el-icon>
              <span>当前门店</span>
            </div>
          </template>
          <template v-if="auth.currentStore">
            <div class="store-name">{{ auth.currentStore.storeName }}</div>
            <div class="store-no">{{ auth.currentStore.storeNo }}</div>
            <el-tag v-if="auth.currentStore.isOwner === 1" type="success">店主</el-tag>
            <el-tag v-else type="info">员工</el-tag>
            <p class="tip">通过顶栏门店切换可切换当前上下文（切店状态跨刷新保持）</p>
          </template>
          <el-empty v-else description="总部账号 · 无绑定门店" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#67C23A"><OfficeBuilding /></el-icon>
              <span>绑定门店（{{ auth.stores.length }}）</span>
            </div>
          </template>
          <div v-for="s in auth.stores" :key="s.storeId" class="store-row">
            <span>{{ s.storeName }}</span>
            <span class="row-right">
              <el-tag v-if="s.isOwner === 1" size="small" type="success">店主</el-tag>
              <el-tag v-if="s.current" size="small" type="primary">当前</el-tag>
            </span>
          </div>
          <el-empty v-if="auth.stores.length === 0" description="总部账号无绑定门店" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#E6A23C"><User /></el-icon>
              <span>账号信息</span>
            </div>
          </template>
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label="账号">{{ auth.profile?.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ auth.profile?.realName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag size="small">{{ auth.profile?.roleName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="权限数">
              {{ auth.profile?.permissionCodes.length ?? 0 }} 项
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useAuthStore } from '@/stores/auth'
import {
  overviewApi as apiOverview,
  salesTrendApi as apiSalesTrend,
  orderFunnelApi as apiOrderFunnel,
  categoryShareApi as apiCategoryShare,
  topSkusApi as apiTopSkus,
  pointsTrendApi as apiPointsTrend,
  promoStatsApi as apiPromoStats,
  refundRateApi as apiRefundRate,
} from '@/api/report'
import type {
  OverviewVO,
  TrendPoint,
  FunnelItem,
  CategoryShareItem,
  TopSkuItem,
  PointsTrendPoint,
  PromoStatItem,
  RefundRateVO,
} from '@/types/report'

const auth = useAuthStore()

const period = ref<'today' | '7d' | '30d'>('today')
const loading = ref(false)

const ov = reactive<OverviewVO>({
  salesAmount: '0', orderCount: 0, avgOrderAmount: '0',
  memberCount: 0, periodLabel: '今日', salesAmountChange: null,
})
const refund = reactive<RefundRateVO>({ rate: '0.00', refundCount: 0, paidCount: 0 })

let salesData: TrendPoint[] = []
let funnelData: FunnelItem[] = []
let categoryData: CategoryShareItem[] = []
let topSkus: TopSkuItem[] = []
let pointsData: PointsTrendPoint[] = []
let promoData: PromoStatItem[] = []

const chartSalesRef = ref<HTMLDivElement>()
const chartFunnelRef = ref<HTMLDivElement>()
const chartCategoryRef = ref<HTMLDivElement>()
const chartPointsRef = ref<HTMLDivElement>()
const chartPromoRef = ref<HTMLDivElement>()
const charts: echarts.ECharts[] = []

const fmtMoney = (s: string | number | undefined | null) => Number(s || 0).toFixed(2)
const deltaClass = (v: string) => Number(v) > 0 ? 'up' : Number(v) < 0 ? 'down' : 'flat'
const deltaText = (v: string) => (Number(v) > 0 ? '↑ ' : Number(v) < 0 ? '↓ ' : '') + Math.abs(Number(v)).toFixed(2) + '%'

async function reloadAll() {
  loading.value = true
  try {
    const [ovRes, refundRes, salesRes, funnelRes, catRes, skuRes, ptsRes, proRes] = await Promise.all([
      apiOverview(period.value),
      apiRefundRate(30),
      apiSalesTrend(30),
      apiOrderFunnel(30),
      apiCategoryShare(30),
      apiTopSkus(30, 10),
      apiPointsTrend(14),
      apiPromoStats(30),
    ])
    Object.assign(ov, ovRes)
    Object.assign(refund, refundRes)
    salesData = salesRes
    funnelData = funnelRes
    categoryData = catRes
    topSkus = skuRes
    pointsData = ptsRes
    promoData = proRes
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  // 销售曲线
  if (chartSalesRef.value) {
    const c = echarts.init(chartSalesRef.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: salesData.map(p => p.date.slice(5)) },
      yAxis: { type: 'value', axisLabel: { formatter: '{value} ¥' } },
      series: [{
        type: 'line', smooth: true, areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2 },
        data: salesData.map(p => Number(p.value)),
        itemStyle: { color: '#409EFF' },
      }],
    })
    charts.push(c)
  }
  // 漏斗
  if (chartFunnelRef.value) {
    const c = echarts.init(chartFunnelRef.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 单' },
      series: [{
        type: 'funnel',
        sort: 'descending',
        left: '10%', top: 10, bottom: 10, width: '80%',
        label: { show: true, position: 'inside' },
        data: funnelData
          .filter(f => f.count > 0)
          .map(f => ({ name: f.label, value: f.count })),
      }],
    })
    charts.push(c)
  }
  // 品类饼图
  if (chartCategoryRef.value) {
    const c = echarts.init(chartCategoryRef.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        label: { formatter: '{b}\n{d}%' },
        data: categoryData.map(c => ({ name: c.name, value: Number(c.value) })),
      }],
    })
    charts.push(c)
  }
  // 积分双柱
  if (chartPointsRef.value) {
    const c = echarts.init(chartPointsRef.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { top: 0 },
      grid: { left: 50, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: pointsData.map(p => p.date.slice(5)) },
      yAxis: { type: 'value' },
      series: [
        { name: '发放', type: 'bar', data: pointsData.map(p => p.granted), itemStyle: { color: '#67C23A' } },
        { name: '使用', type: 'bar', data: pointsData.map(p => p.used),    itemStyle: { color: '#E6A23C' } },
      ],
    })
    charts.push(c)
  }
  // 活动命中柱状
  if (chartPromoRef.value) {
    const c = echarts.init(chartPromoRef.value)
    c.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 110, right: 30, top: 10, bottom: 30 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: promoData.map(p => p.name.length > 12 ? p.name.slice(0, 12) + '…' : p.name),
      },
      series: [{
        type: 'bar',
        data: promoData.map(p => p.orderCount),
        itemStyle: { color: '#409EFF' },
        label: { show: true, position: 'right', formatter: '{c} 单' },
      }],
    })
    charts.push(c)
  }
}

function handleResize() { charts.forEach(c => c.resize()) }

onMounted(() => {
  reloadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(c => c.dispose())
})

watch(period, () => reloadAll())
</script>

<style scoped>
.row-block { margin-bottom: 16px; }
.row-toolbar { margin-bottom: 12px; align-items: center; }

.kpi-card { padding: 4px 0; }
.kpi-label { color: #909399; font-size: 13px; margin-bottom: 6px; }
.kpi-value { font-size: 26px; font-weight: 600; color: #303133; line-height: 1.2; }
.kpi-delta { margin-top: 6px; font-size: 12px; }
.kpi-delta.up   { color: #67C23A; }
.kpi-delta.down { color: #F56C6C; }
.kpi-delta.flat { color: #909399; }
.kpi-delta.muted { color: #C0C4CC; }

.chart { width: 100%; height: 320px; }

.card-title {
  display: flex; align-items: center; gap: 8px; font-weight: 600;
}
.store-name { font-size: 20px; font-weight: 600; color: #303133; }
.store-no { color: #909399; font-size: 13px; margin: 4px 0 8px; }
.tip { color: #909399; font-size: 12px; margin-top: 12px; }
.store-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-bottom: 1px solid #f0f2f5;
}
.store-row:last-child { border-bottom: none; }
.row-right { display: flex; gap: 6px; }
.muted { color: #909399; font-size: 12px; }
</style>