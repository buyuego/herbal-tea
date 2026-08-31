<template>
  <div class="refund-view">
    <!-- 筛选工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.refundNo"
        placeholder="退款单号"
        clearable
        style="width: 180px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-input
        v-model="query.orderNo"
        placeholder="订单号"
        clearable
        style="width: 180px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="(label, code) in REFUND_STATUS" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-select v-model="query.refundBranch" placeholder="全部分支" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="(label, code) in REFUND_BRANCH" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-select
        v-if="storeOptions.length > 1"
        v-model="query.storeId"
        placeholder="全部门店"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option v-for="s in storeOptions" :key="s.storeId" :label="s.storeName" :value="s.storeId" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <div class="toolbar-right">
        <el-button v-if="canSubmit" type="primary" @click="openApplyDialog">发起退款</el-button>
      </div>
    </div>

    <!-- 退款单表格 -->
    <el-table :data="refunds" v-loading="loading" stripe>
      <el-table-column label="退款单号" width="170">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row.id)">{{ row.refundNo }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="订单号" width="165">
        <template #default="{ row }">{{ row.orderNo }}</template>
      </el-table-column>
      <el-table-column label="门店" width="100">
        <template #default="{ row }">{{ row.storeName || `#${row.storeId}` }}</template>
      </el-table-column>
      <el-table-column label="买家" width="110">
        <template #default="{ row }">
          <div class="cell-main">{{ row.userName || `#${row.userId ?? '-'}` }}</div>
          <div v-if="row.userPhone" class="cell-sub">{{ row.userPhone }}</div>
        </template>
      </el-table-column>
      <el-table-column label="退款金额" width="110" align="right">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="分支" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="BRANCH_TAG[row.refundBranch] || 'info'" size="small" effect="plain">
            {{ row.refundBranchDesc }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="REFUND_STATUS_TAG[row.status] || 'info'" size="small">{{ row.statusDesc }}</el-tag>
          <div v-if="row.returnStatus != null" class="cell-sub">
            {{ row.returnStatusDesc }}<template v-if="row.warehouseStatusDesc"> / {{ row.warehouseStatusDesc }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="canApprove && row.status === 10"
            type="success"
            link
            @click="onApprove(row)"
          >审批通过</el-button>
          <el-button
            v-if="canApprove && row.status === 10"
            type="danger"
            link
            @click="openRejectDialog(row)"
          >驳回</el-button>
          <el-button
            v-if="canInspect && row.status === 20 && row.refundBranch === 3 && row.warehouseStatus === 1"
            type="primary"
            link
            @click="onReceive(row)"
          >收货</el-button>
          <el-button
            v-if="canInspect && row.status === 20 && row.refundBranch === 3 && row.warehouseStatus === 2"
            type="warning"
            link
            @click="openInspectDialog(row)"
          >验货</el-button>
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
        @change="loadRefunds"
      />
    </div>

    <!-- 退款详情抽屉 -->
    <el-drawer v-model="detailVisible" title="退款售后详情" size="680px" destroy-on-close>
      <template v-if="detail">
        <div class="section-title">退款单</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="退款单号">{{ detail.refundNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="REFUND_STATUS_TAG[detail.status] || 'info'" size="small">{{ detail.statusDesc }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detail.storeName || `#${detail.storeId}` }}</el-descriptions-item>
          <el-descriptions-item label="买家">
            {{ detail.userName || '-' }}<template v-if="detail.userPhone"> / {{ detail.userPhone }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="退款分支">{{ detail.refundBranchDesc }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">
            <b>¥{{ Number(detail.amount).toFixed(2) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reason" label="退款原因" :span="2">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.approvedAt" label="审批时间">{{ formatTime(detail.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.approvedBy" label="审批人">#{{ detail.approvedBy }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="驳回原因" :span="2">{{ detail.rejectReason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectedAt" label="驳回时间">{{ formatTime(detail.rejectedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.handledAt" label="退款完成">{{ formatTime(detail.handledAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">订单信息</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="实付金额">¥{{ Number(detail.payAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="发货状态">{{ detail.orderWarehouseStatusDesc }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ detail.receiverName }} / {{ detail.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ detail.receiverAddress }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.returnId != null">
          <div class="section-title">退货单（已签收退货链路）</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="退货状态">
              {{ detail.returnStatusDesc }} / {{ detail.warehouseStatusDesc }}
            </el-descriptions-item>
            <el-descriptions-item label="退货物流">
              {{ detail.returnCarrier || '-' }}{{ detail.returnTrackingNo ? ` / ${detail.returnTrackingNo}` : '' }}
            </el-descriptions-item>
            <el-descriptions-item label="寄回地址" :span="2">{{ detail.returnAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.receivedAt" label="总部收货">
              {{ formatTime(detail.receivedAt) }}<template v-if="detail.receivedBy"> / 仓管 #{{ detail.receivedBy }}</template>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.inspectionResult" label="验货结论">
              <el-tag :type="detail.warehouseStatus === 3 ? 'success' : 'danger'" size="small">
                {{ detail.inspectionResult }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <div class="drawer-footer">
          <el-button
            v-if="canApprove && detail.status === 10"
            type="success"
            @click="onApproveFromDetail"
          >审批通过</el-button>
          <el-button
            v-if="canApprove && detail.status === 10"
            type="danger"
            @click="openRejectDialogFromDetail"
          >驳回</el-button>
          <el-button
            v-if="canInspect && detail.status === 20 && detail.refundBranch === 3 && detail.warehouseStatus === 1"
            type="primary"
            @click="onReceiveFromDetail"
          >总部收货</el-button>
          <el-button
            v-if="canInspect && detail.status === 20 && detail.refundBranch === 3 && detail.warehouseStatus === 2"
            type="warning"
            @click="openInspectDialogFromDetail"
          >退货验货</el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 发起退款对话框 -->
    <el-dialog v-model="applyVisible" title="发起退款" width="460px" destroy-on-close>
      <el-form :model="applyForm" label-width="80px">
        <el-form-item label="订单号" required>
          <el-input v-model="applyForm.orderNo" placeholder="输入已支付订单号" maxlength="40" />
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="applyForm.reason" type="textarea" :rows="2" placeholder="可选" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySaving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 驳回对话框 -->
    <el-dialog v-model="rejectVisible" title="驳回退款" width="460px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="退款单号">
          <span>{{ rejectTarget?.refundNo }}</span>
        </el-form-item>
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填，将同步恢复订单状态" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSaving" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 退货验货对话框 -->
    <el-dialog v-model="inspectVisible" title="退货验货" width="460px" destroy-on-close>
      <el-form :model="inspectForm" label-width="100px">
        <el-form-item label="退款单号">
          <span>{{ inspectTarget?.refundNo }}</span>
        </el-form-item>
        <el-form-item label="验货结论" required>
          <el-select v-model="inspectForm.result" placeholder="选择验货结论" style="width: 100%">
            <el-option v-for="r in REFUND_INSPECT_RESULTS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="inspectForm.result === '破损部分退款'" label="部分退款金额" required>
          <el-input-number
            v-model="inspectForm.refundAmount"
            :min="0.01"
            :max="Number(inspectTarget?.amount || 0)"
            :precision="2"
            :step="1"
            style="width: 100%"
          />
          <div class="form-tip">小于原退款金额 ¥{{ Number(inspectTarget?.amount || 0).toFixed(2) }}，破损部分按此金额退款</div>
        </el-form-item>
        <el-form-item v-if="inspectForm.result === '非质量问题拒退'" label="说明">
          <el-alert type="warning" :closable="false" show-icon title="验货不通过将驳回退款，订单恢复已签收" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectVisible = false">取消</el-button>
        <el-button type="primary" :loading="inspectSaving" @click="submitInspect">提交验货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { myStoresApi } from '@/api/store'
import { pageOrdersApi } from '@/api/order'
import {
  applyRefundApi,
  approveRefundApi,
  getRefundDetailApi,
  inspectRefundApi,
  pageRefundsApi,
  receiveRefundApi,
  rejectRefundApi,
} from '@/api/refund'
import {
  REFUND_BRANCH,
  REFUND_INSPECT_RESULTS,
  REFUND_STATUS,
  REFUND_STATUS_TAG,
} from '@/types/refund'
import type { RefundDetail, RefundItem } from '@/types/refund'
import type { StoreBinding } from '@/types/api'

const auth = useAuthStore()
const canSubmit = computed(() => auth.hasPermission('refund:submit'))
const canApprove = computed(() => auth.hasPermission('refund:approve'))
const canInspect = computed(() => auth.hasPermission('return:inspect'))

/** 分支 tag 配色 */
const BRANCH_TAG: Record<number, 'primary' | 'warning' | 'info'> = { 1: 'primary', 2: 'warning', 3: 'info' }

const loading = ref(false)
const refunds = ref<RefundItem[]>([])
const total = ref(0)
const query = reactive({
  refundNo: '',
  orderNo: '',
  status: undefined as number | undefined,
  refundBranch: undefined as number | undefined,
  storeId: undefined as number | undefined,
  page: 1,
  size: 10,
})

const storeOptions = ref<StoreBinding[]>([])

const detailVisible = ref(false)
const detail = ref<RefundDetail | null>(null)

const applyVisible = ref(false)
const applySaving = ref(false)
const applyForm = reactive({ orderNo: '', reason: '' })

const rejectVisible = ref(false)
const rejectSaving = ref(false)
const rejectTarget = ref<RefundItem | RefundDetail | null>(null)
const rejectReason = ref('')

const inspectVisible = ref(false)
const inspectSaving = ref(false)
const inspectTarget = ref<RefundItem | RefundDetail | null>(null)
const inspectForm = reactive({ result: '', refundAmount: undefined as number | undefined })

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function loadRefunds() {
  loading.value = true
  try {
    const page = await pageRefundsApi({ ...query, page: query.page, size: query.size })
    refunds.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '退款单加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadRefunds()
}

function onReset() {
  query.refundNo = ''
  query.orderNo = ''
  query.status = undefined
  query.refundBranch = undefined
  query.storeId = undefined
  onSearch()
}

async function openDetail(refundId: number) {
  detailVisible.value = true
  detail.value = null
  try {
    detail.value = await getRefundDetailApi(refundId)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '退款详情加载失败')
  }
}

// ---------- 发起退款 ----------
function openApplyDialog() {
  applyForm.orderNo = ''
  applyForm.reason = ''
  applyVisible.value = true
}

async function submitApply() {
  const orderNo = applyForm.orderNo.trim()
  if (!orderNo) {
    ElMessage.warning('请输入订单号')
    return
  }
  applySaving.value = true
  try {
    // 订单号 → orderId（订单号唯一，精确匹配第一条）
    const page = await pageOrdersApi({ orderNo, page: 1, size: 1 })
    const order = page.records[0]
    if (!order) {
      ElMessage.warning('未找到该订单，请确认订单号')
      return
    }
    await applyRefundApi({ orderId: order.id, reason: applyForm.reason.trim() || undefined })
    ElMessage.success('退款申请已提交，等待审批')
    applyVisible.value = false
    onSearch()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '退款申请失败')
  } finally {
    applySaving.value = false
  }
}

// ---------- 审批通过 ----------
async function onApprove(row: RefundItem) {
  const tip =
    row.refundBranch === 3
      ? '审批通过后进入退货环节：用户寄回 → 总部收货 → 验货后放款。'
      : row.refundBranch === 2
        ? '审批通过后将发起原路退款（在途拦截）。'
        : '审批通过后将发起原路退款（未发货直退）。'
  try {
    await ElMessageBox.confirm(`确认审批通过该退款单？\n${tip}`, '退款审批', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await approveRefundApi(row.id)
    ElMessage.success('审批通过')
    loadRefunds()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof BizError ? e.message : '审批失败')
  }
}

async function onApproveFromDetail() {
  if (!detail.value) return
  const d = detail.value
  const tip =
    d.refundBranch === 3
      ? '审批通过后进入退货环节：用户寄回 → 总部收货 → 验货后放款。'
      : '审批通过后将发起原路退款。'
  try {
    await ElMessageBox.confirm(`确认审批通过该退款单？\n${tip}`, '退款审批', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await approveRefundApi(d.id)
    ElMessage.success('审批通过')
    detailVisible.value = false
    loadRefunds()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof BizError ? e.message : '审批失败')
  }
}

// ---------- 驳回 ----------
function openRejectDialog(row: RefundItem) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

function openRejectDialogFromDetail() {
  rejectTarget.value = detail.value
  rejectReason.value = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectTarget.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejectSaving.value = true
  try {
    await rejectRefundApi(rejectTarget.value.id, { reason: rejectReason.value.trim() })
    ElMessage.success('已驳回，订单状态已恢复')
    rejectVisible.value = false
    detailVisible.value = false
    loadRefunds()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '驳回失败')
  } finally {
    rejectSaving.value = false
  }
}

// ---------- 退货总部收货 ----------
async function onReceive(row: RefundItem) {
  try {
    await ElMessageBox.confirm(
      '确认总部已收到退货？收货后方可进行验货。',
      '总部收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' },
    )
    await receiveRefundApi(row.id)
    ElMessage.success('已收货，等待验货')
    loadRefunds()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof BizError ? e.message : '收货失败')
  }
}

async function onReceiveFromDetail() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      '确认总部已收到退货？收货后方可进行验货。',
      '总部收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' },
    )
    await receiveRefundApi(detail.value.id)
    ElMessage.success('已收货，等待验货')
    detailVisible.value = false
    loadRefunds()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof BizError ? e.message : '收货失败')
  }
}

// ---------- 退货验货 ----------
function openInspectDialog(row: RefundItem) {
  inspectTarget.value = row
  inspectForm.result = ''
  inspectForm.refundAmount = undefined
  inspectVisible.value = true
}

function openInspectDialogFromDetail() {
  inspectTarget.value = detail.value
  inspectForm.result = ''
  inspectForm.refundAmount = undefined
  inspectVisible.value = true
}

async function submitInspect() {
  if (!inspectTarget.value) return
  if (!inspectForm.result) {
    ElMessage.warning('请选择验货结论')
    return
  }
  if (inspectForm.result === '破损部分退款' && !inspectForm.refundAmount) {
    ElMessage.warning('请填写部分退款金额')
    return
  }
  inspectSaving.value = true
  try {
    await inspectRefundApi(inspectTarget.value.id, {
      result: inspectForm.result,
      refundAmount: inspectForm.result === '破损部分退款' ? inspectForm.refundAmount : undefined,
    })
    ElMessage.success('验货完成')
    inspectVisible.value = false
    detailVisible.value = false
    loadRefunds()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '验货提交失败')
  } finally {
    inspectSaving.value = false
  }
}

onMounted(async () => {
  loadRefunds()
  try {
    storeOptions.value = await myStoresApi()
  } catch {
    storeOptions.value = []
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.toolbar-right {
  margin-left: auto;
}
.cell-main {
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.cell-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 18px 0 10px;
  color: var(--el-text-color-primary);
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.drawer-footer {
  margin-top: 20px;
  text-align: right;
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  width: 100%;
}
</style>
