<template>
  <div class="orders-view">
    <!-- 筛选工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.orderNo"
        placeholder="订单号"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="(label, code) in ORDER_STATUS" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <!-- 订单表格 -->
    <el-table :data="orders" v-loading="loading" stripe>
      <el-table-column label="订单号" width="180">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row.id)">{{ row.orderNo }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS_TAG[row.status] || 'info'" size="small">
            {{ ORDER_STATUS[row.status] || `未知(${row.status})` }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="实付金额" width="110" align="right">
        <template #default="{ row }">¥{{ Number(row.payAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="收货人" width="100">
        <template #default="{ row }">{{ row.receiverName }}</template>
      </el-table-column>
      <el-table-column label="收货电话" width="130">
        <template #default="{ row }">{{ row.receiverPhone }}</template>
      </el-table-column>
      <el-table-column label="归属门店" width="90" align="center">
        <template #default="{ row }">#{{ row.storeId }}</template>
      </el-table-column>
      <el-table-column label="下单时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
          <el-button v-if="canShip && row.status === 30" type="primary" link @click="openShipDialog(row)">
            发货
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadOrders"
      />
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="680px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="ORDER_STATUS_TAG[detail.status] || 'info'" size="small">{{ detail.statusDesc }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="归属门店">#{{ detail.storeId }}</el-descriptions-item>
          <el-descriptions-item label="买家 ID">#{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="支付单号">{{ detail.payNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ formatTime(detail.shippedAt) }}</el-descriptions-item>
          <el-descriptions-item label="物流信息">
            {{ detail.carrier || '-' }}{{ detail.trackingNo ? ` / ${detail.trackingNo}` : '' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">商品明细</div>
        <el-table :data="detail.items" size="small" stripe>
          <el-table-column label="商品" min-width="160">
            <template #default="{ row }">
              <div class="cell-main">{{ row.name }}</div>
              <div v-if="specsText(row.specs)" class="cell-sub">{{ specsText(row.specs) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="90" align="right">
            <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="70" align="center">
            <template #default="{ row }">x{{ row.qty }}</template>
          </el-table-column>
          <el-table-column label="小计" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.subtotal).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <div class="section-title">金额</div>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="商品总额">¥{{ Number(detail.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="优惠券抵扣">¥{{ Number(detail.couponAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="积分抵扣">¥{{ Number(detail.pointsDeductAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="实付">
            <b>¥{{ Number(detail.payAmount).toFixed(2) }}</b>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">收货信息</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="收货人">{{ detail.receiverName }} / {{ detail.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ detail.receiverAddress }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">物流轨迹</div>
        <el-timeline v-if="logs.length" class="ship-timeline">
          <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="formatTime(log.createdAt)" placement="top">
            <div class="cell-main">{{ log.status }}</div>
            <div v-if="log.carrier || log.trackingNo" class="cell-sub">
              {{ log.carrier || '' }}{{ log.trackingNo ? ` / ${log.trackingNo}` : '' }}
            </div>
            <div v-if="log.note" class="cell-sub">{{ log.note }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无物流轨迹" :image-size="60" />

        <div class="drawer-footer">
          <el-button v-if="canShip && detail.status === 30" type="primary" @click="openShipDialogFromDetail">
            发货
          </el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 发货对话框 -->
    <el-dialog v-model="shipDialogVisible" title="订单发货" width="460px" destroy-on-close>
      <el-form :model="shipForm" label-width="80px">
        <el-form-item label="订单号">
          <span>{{ shipOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="快递公司" required>
          <el-input v-model="shipForm.carrier" placeholder="如：顺丰速运" maxlength="30" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.logisticsNo" placeholder="运单号" maxlength="40" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shipForm.note" type="textarea" :rows="2" placeholder="可选" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipSaving" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { getOrderDetailApi, pageOrdersApi, shipOrderApi, shippingLogsApi } from '@/api/order'
import { ORDER_STATUS, ORDER_STATUS_TAG } from '@/types/order'
import type { Order, OrderDetail, ShippingLog } from '@/types/order'

const auth = useAuthStore()
const canShip = computed(() => auth.hasPermission('order:ship'))

const loading = ref(false)
const orders = ref<Order[]>([])
const total = ref(0)
const query = reactive({
  orderNo: '',
  status: undefined as number | undefined,
  page: 1,
  size: 10,
})

const detailVisible = ref(false)
const detail = ref<OrderDetail | null>(null)
const logs = ref<ShippingLog[]>([])

const shipDialogVisible = ref(false)
const shipSaving = ref(false)
const shipOrder = ref<Order | null>(null)
const shipForm = reactive({ carrier: '', logisticsNo: '', note: '' })

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

function specsText(specs: Record<string, string> | null | undefined) {
  if (!specs) return ''
  return Object.entries(specs)
    .map(([k, v]) => `${k}:${v}`)
    .join(' / ')
}

async function loadOrders() {
  loading.value = true
  try {
    const page = await pageOrdersApi({ ...query, page: query.page, size: query.size })
    orders.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '订单加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadOrders()
}

function onReset() {
  query.orderNo = ''
  query.status = undefined
  onSearch()
}

async function openDetail(orderId: number) {
  detailVisible.value = true
  detail.value = null
  logs.value = []
  try {
    const [d, l] = await Promise.all([getOrderDetailApi(orderId), shippingLogsApi(orderId)])
    detail.value = d
    logs.value = l
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '订单详情加载失败')
  }
}

function openShipDialog(row: Order) {
  shipOrder.value = row
  shipForm.carrier = ''
  shipForm.logisticsNo = ''
  shipForm.note = ''
  shipDialogVisible.value = true
}

function openShipDialogFromDetail() {
  if (!detail.value) return
  const row: Order = {
    id: detail.value.id,
    orderNo: detail.value.orderNo,
    status: detail.value.status,
    createdAt: detail.value.createdAt,
  } as Order
  openShipDialog(row)
}

async function submitShip() {
  if (!shipForm.carrier.trim() || !shipForm.logisticsNo.trim()) {
    ElMessage.warning('请填写快递公司与物流单号')
    return
  }
  if (!shipOrder.value) return
  shipSaving.value = true
  try {
    await shipOrderApi(shipOrder.value.id, {
      carrier: shipForm.carrier.trim(),
      logisticsNo: shipForm.logisticsNo.trim(),
      note: shipForm.note.trim() || undefined,
    })
    ElMessage.success('发货成功')
    shipDialogVisible.value = false
    loadOrders()
    if (detailVisible.value) {
      const [d, l] = await Promise.all([
        getOrderDetailApi(shipOrder.value.id),
        shippingLogsApi(shipOrder.value.id),
      ])
      detail.value = d
      logs.value = l
    }
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '发货失败')
  } finally {
    shipSaving.value = false
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.section-title {
  font-weight: 600;
  margin: 16px 0 10px;
}
.ship-timeline {
  padding-left: 4px;
}
.drawer-footer {
  margin-top: 18px;
  text-align: right;
}
</style>
