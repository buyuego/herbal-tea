<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.settleNo"
          placeholder="结算单号"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-input
          v-model="query.period"
          placeholder="结算周期（2026-08-30）"
          clearable
          style="width: 190px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.status" placeholder="结算状态" clearable style="width: 130px" @change="onSearch">
          <el-option
            v-for="(label, code) in SETTLEMENT_STATUS"
            :key="code"
            :label="label"
            :value="Number(code)"
          />
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
          <el-button type="primary" @click="openGenerateDialog">生成结算单</el-button>
        </div>
      </div>

      <!-- 结算单表格 -->
      <el-table :data="settlements" v-loading="loading" stripe>
        <el-table-column label="结算单号" width="175">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.id)">{{ row.settleNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="门店" width="120">
          <template #default="{ row }">{{ row.storeName || `#${row.storeId}` }}</template>
        </el-table-column>
        <el-table-column label="周期" width="120">
          <template #default="{ row }">
            <div class="cell-main">{{ row.period }}</div>
            <div class="cell-sub">{{ SETTLEMENT_TYPE[row.type] || `#${row.type}` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="80" align="center">
          <template #default="{ row }">{{ row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="销售总额" width="110" align="right">
          <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="平台佣金" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.commissionAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="实际到账" width="110" align="right">
          <template #default="{ row }">
            <span class="amount-main">¥{{ Number(row.finalAmount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="SETTLEMENT_STATUS_TAG[row.status] || 'info'" size="small">
              {{ row.statusDesc || SETTLEMENT_STATUS[row.status] || `#${row.status}` }}
            </el-tag>
            <el-tag v-if="row.confirmStatus === 3" type="warning" size="small" effect="plain" class="dispute-tag">
              有异议
            </el-tag>
            <div v-if="row.payoutNo" class="cell-sub">流水 {{ row.payoutNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 10"
              type="success"
              link
              @click="onConfirm(row)"
            >确认</el-button>
            <el-button
              v-if="canReview && row.status === 20"
              type="primary"
              link
              @click="onReview(row)"
            >审核通过</el-button>
            <el-button
              v-if="canPay && row.status === 30"
              type="success"
              link
              @click="onPay(row)"
            >打款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadSettlements"
          @size-change="onSearch"
        />
      </div>
    </el-card>

    <!-- 生成结算单对话框 -->
    <el-dialog v-model="generateVisible" title="生成结算单" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="结算周期" required>
          <el-date-picker
            v-model="generatePeriod"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结算日（T+1 日结）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="storeOptions.length > 1" label="门店">
          <el-select v-model="generateStoreId" placeholder="全部（按店各生成一张）" clearable style="width: 100%">
            <el-option v-for="s in storeOptions" :key="s.storeId" :label="s.storeName" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="按门店聚合该周期「已完结且未结算」的订单；平台佣金 X% 与积分成本按订单快照归集，明细分行展示（D15）。"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generateSaving" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="结算单详情" size="720px" destroy-on-close>
      <div v-if="detail" v-loading="detailLoading" class="detail-body">
        <!-- 结算单头 -->
        <el-descriptions :column="2" border size="small" title="结算单信息">
          <el-descriptions-item label="结算单号">{{ detail.settleNo }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detail.storeName || `#${detail.storeId}` }}</el-descriptions-item>
          <el-descriptions-item label="周期">{{ detail.period }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ SETTLEMENT_TYPE[detail.type] || `#${detail.type}` }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="SETTLEMENT_STATUS_TAG[detail.status] || 'info'" size="small">
              {{ detail.statusDesc || SETTLEMENT_STATUS[detail.status] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="确认维度">{{ CONFIRM_STATUS[detail.confirmStatus] ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单数">{{ detail.orderCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="自动确认">{{ formatTime(detail.autoConfirmAt) }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatTime(detail.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewedBy" label="审核人">财务 #{{ detail.reviewedBy }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.paidAt" label="打款时间">{{ formatTime(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.payoutNo" label="打款流水">{{ detail.payoutNo }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.parentSettlementId" label="关联原结算单">
            <el-button type="primary" link @click="openDetail(detail.parentSettlementId)">#{{ detail.parentSettlementId }}</el-button>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 异议说明 -->
        <el-alert
          v-if="detail.disputeNote"
          type="warning"
          :closable="false"
          :title="`门店异议（${CONFIRM_STATUS[detail.confirmStatus] ?? ''}）：${detail.disputeNote}`"
          show-icon
          class="mt-16"
        />

        <!-- 金额明细（11.2 口径） -->
        <el-descriptions :column="3" border size="small" title="金额明细" class="mt-16">
          <el-descriptions-item label="销售总额">¥{{ Number(detail.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="平台佣金">- ¥{{ Number(detail.commissionAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="积分抵扣">- ¥{{ Number(detail.pointsDeductAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="门店积分成本">- ¥{{ Number(detail.pointsCostStore).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="本店券成本">- ¥{{ Number(detail.couponCostStore).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="退款冲正">- ¥{{ Number(detail.refundAdjust).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="平台补贴(不计入)">{{ Number(detail.pointsCostPlatform).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="调整单">¥{{ Number(detail.adjustAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="实际到账">
            <span class="amount-main">¥{{ Number(detail.finalAmount).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 明细分行（D15） -->
        <div class="mt-16 section-title">结算明细（按积分来源分行）</div>
        <el-table :data="detail.items" size="small" border>
          <el-table-column label="订单号" width="170">
            <template #default="{ row }">{{ row.orderNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="项目" width="140">
            <template #default="{ row }">{{ ITEM_TYPE[row.itemType] || `#${row.itemType}` }}</template>
          </el-table-column>
          <el-table-column label="方向" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.direction === 1 ? 'success' : row.direction === 2 ? 'danger' : 'info'" size="small" effect="plain">
                {{ ITEM_DIRECTION[row.direction] || `#${row.direction}` }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="说明">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detail.items?.length" description="暂无明细" :image-size="60" />
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="detail && detail.status === 10"
            type="success"
            :loading="acting"
            @click="onConfirmFromDetail"
          >确认（进入平台审核）</el-button>
          <el-button
            v-if="detail && canReview && detail.status === 20"
            type="primary"
            :loading="acting"
            @click="onReviewFromDetail"
          >审核通过</el-button>
          <el-button
            v-if="detail && canPay && detail.status === 30"
            type="success"
            :loading="acting"
            @click="onPayFromDetail"
          >打款</el-button>
          <el-button
            v-if="detail && isStoreSide && detail.status <= 20 && detail.confirmStatus !== 3"
            type="warning"
            @click="openDisputeDialog"
          >提出异议</el-button>
          <el-button
            v-if="detail && canReconcile && detail.confirmStatus === 3"
            type="primary"
            @click="openReconcileDialog"
          >复核生成调整单</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 异议申诉对话框 -->
    <el-dialog v-model="disputeVisible" title="结算异议申诉" width="440px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="异议说明" required>
          <el-input
            v-model="disputeNote"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请说明对结算单金额的异议（如佣金计算、漏单等），平台将复核处理"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="提交后结算单标记「有异议」，不会被自动确认；平台复核后生成调整单补偿/扣减。"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="disputeVisible = false">取消</el-button>
        <el-button type="warning" :loading="disputeSaving" @click="onDispute">提交申诉</el-button>
      </template>
    </el-dialog>

    <!-- 复核生成调整单对话框 -->
    <el-dialog v-model="reconcileVisible" title="复核生成调整单" width="440px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="调整金额" required>
          <el-input-number v-model="reconcileAmount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="复核说明">
          <el-input
            v-model="reconcileRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="复核结论说明（如：核实漏计一笔订单，补偿差价）"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="原结算单累加调整金额并生成 type=3 调整单（复用确认→审核→打款流程）；异议标记复位。"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="reconcileVisible = false">取消</el-button>
        <el-button type="primary" :loading="reconcileSaving" @click="onReconcile">生成调整单</el-button>
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
import {
  confirmSettlementApi,
  disputeSettlementApi,
  generateSettlementApi,
  getSettlementDetailApi,
  pageSettlementsApi,
  paySettlementApi,
  reconcileSettlementApi,
  reviewSettlementApi,
} from '@/api/settlement'
import { CONFIRM_STATUS, ITEM_DIRECTION, ITEM_TYPE, SETTLEMENT_STATUS, SETTLEMENT_STATUS_TAG, SETTLEMENT_TYPE } from '@/types/settlement'
import type { SettlementDetail } from '@/types/settlement'
import type { StoreBinding } from '@/types/api'

const auth = useAuthStore()
const canReview = computed(() => auth.hasPermission('settlement:review'))
const canPay = computed(() => auth.hasPermission('settlement:payout'))
const canReconcile = computed(() => auth.hasPermission('settlement:reconcile'))
/** 门店侧视角（有结算查看权限但无平台审核/复核权限 = 店长），可对结算单提出异议 */
const isStoreSide = computed(() => !canReview.value && !canReconcile.value)

const loading = ref(false)
const settlements = ref<SettlementDetail[]>([])
const total = ref(0)
const query = reactive({
  settleNo: '',
  period: '',
  status: undefined as number | undefined,
  storeId: undefined as number | undefined,
  page: 1,
  size: 10,
})

const storeOptions = ref<StoreBinding[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SettlementDetail | null>(null)
const acting = ref(false)

const generateVisible = ref(false)
const generateSaving = ref(false)
const generatePeriod = ref('')
const generateStoreId = ref<number | undefined>(undefined)

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function loadSettlements() {
  loading.value = true
  try {
    const page = await pageSettlementsApi({ ...query, page: query.page, size: query.size })
    settlements.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '结算单加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadSettlements()
}

function onReset() {
  query.settleNo = ''
  query.period = ''
  query.status = undefined
  query.storeId = undefined
  onSearch()
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getSettlementDetailApi(id)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '结算详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openGenerateDialog() {
  generatePeriod.value = ''
  generateStoreId.value = undefined
  generateVisible.value = true
}

async function onGenerate() {
  if (!generatePeriod.value) {
    ElMessage.warning('请选择结算周期')
    return
  }
  generateSaving.value = true
  try {
    await generateSettlementApi(generatePeriod.value, generateStoreId.value)
    ElMessage.success('结算单已生成')
    generateVisible.value = false
    loadSettlements()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '生成结算单失败')
  } finally {
    generateSaving.value = false
  }
}

async function doAct(target: SettlementDetail, action: 'confirm' | 'review' | 'pay', refreshDetail = false) {
  try {
    if (action === 'confirm') {
      await confirmSettlementApi(target.id)
    } else if (action === 'review') {
      await reviewSettlementApi(target.id)
    } else {
      await paySettlementApi(target.id)
    }
    ElMessage.success(
      action === 'confirm' ? '已确认，进入平台审核' : action === 'review' ? '审核通过，已结算' : '打款成功',
    )
    loadSettlements()
    if (refreshDetail && detailVisible.value && detail.value) {
      detail.value = await getSettlementDetailApi(target.id)
    }
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

function onConfirm(row: SettlementDetail) {
  ElMessageBox.confirm(`确认结算单 ${row.settleNo} 无异议，进入平台审核？`, '结算确认', { type: 'info' })
    .then(() => doAct(row, 'confirm'))
    .catch(() => {})
}

function onReview(row: SettlementDetail) {
  ElMessageBox.confirm(`审核通过结算单 ${row.settleNo}（实际到账 ¥${Number(row.finalAmount).toFixed(2)}）？`, '结算审核', { type: 'warning' })
    .then(() => doAct(row, 'review'))
    .catch(() => {})
}

function onPay(row: SettlementDetail) {
  ElMessageBox.confirm(`确认打款 ¥${Number(row.finalAmount).toFixed(2)} 至门店 ${row.storeName}？`, '打款确认', { type: 'warning' })
    .then(() => doAct(row, 'pay'))
    .catch(() => {})
}

function onConfirmFromDetail() {
  if (!detail.value) return
  ElMessageBox.confirm(`确认结算单 ${detail.value.settleNo} 无异议，进入平台审核？`, '结算确认', { type: 'info' })
    .then(() => doAct(detail.value!, 'confirm', true))
    .catch(() => {})
}

function onReviewFromDetail() {
  if (!detail.value) return
  ElMessageBox.confirm(`审核通过结算单 ${detail.value.settleNo}？`, '结算审核', { type: 'warning' })
    .then(() => doAct(detail.value!, 'review', true))
    .catch(() => {})
}

function onPayFromDetail() {
  if (!detail.value) return
  ElMessageBox.confirm(`确认打款 ¥${Number(detail.value.finalAmount).toFixed(2)} 至门店 ${detail.value.storeName}？`, '打款确认', { type: 'warning' })
    .then(() => doAct(detail.value!, 'pay', true))
    .catch(() => {})
}

// ---------- 异议申诉 / 复核调整（v24） ----------

const disputeVisible = ref(false)
const disputeSaving = ref(false)
const disputeNote = ref('')

function openDisputeDialog() {
  disputeNote.value = ''
  disputeVisible.value = true
}

async function onDispute() {
  if (!detail.value) return
  if (!disputeNote.value.trim()) {
    ElMessage.warning('请填写异议说明')
    return
  }
  disputeSaving.value = true
  try {
    await disputeSettlementApi(detail.value.id, disputeNote.value.trim())
    ElMessage.success('异议已提交，等待平台复核')
    disputeVisible.value = false
    detail.value = await getSettlementDetailApi(detail.value.id)
    loadSettlements()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '提交申诉失败')
  } finally {
    disputeSaving.value = false
  }
}

const reconcileVisible = ref(false)
const reconcileSaving = ref(false)
const reconcileAmount = ref(0)
const reconcileRemark = ref('')

function openReconcileDialog() {
  reconcileAmount.value = 0
  reconcileRemark.value = ''
  reconcileVisible.value = true
}

async function onReconcile() {
  if (!detail.value) return
  if (!reconcileAmount.value || reconcileAmount.value <= 0) {
    ElMessage.warning('请输入有效的调整金额')
    return
  }
  reconcileSaving.value = true
  try {
    await reconcileSettlementApi(detail.value.id, reconcileAmount.value, reconcileRemark.value.trim() || undefined)
    ElMessage.success('调整单已生成，可走确认→审核→打款流程')
    reconcileVisible.value = false
    detail.value = await getSettlementDetailApi(detail.value.id)
    loadSettlements()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '复核调整失败')
  } finally {
    reconcileSaving.value = false
  }
}

onMounted(async () => {
  loadSettlements()
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
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-right {
  margin-left: auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  font-size: 12px;
  color: #909399;
}
.amount-main {
  font-weight: 600;
  color: #e6a23c;
}
.mt-16 {
  margin-top: 16px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.detail-body {
  padding-bottom: 8px;
}
</style>
