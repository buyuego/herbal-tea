<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-select v-model="query.type" placeholder="流水类型" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, code) in DEPOSIT_TYPE" :key="code" :label="label" :value="Number(code)" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, code) in DEPOSIT_STATUS" :key="code" :label="label" :value="Number(code)" />
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
      </div>

      <!-- 保证金流水表格 -->
      <el-table :data="deposits" v-loading="loading" stripe>
        <el-table-column label="门店" min-width="150">
          <template #default="{ row }">
            <div class="cell-main">{{ row.storeName || `#${row.storeId}` }}</div>
            <div v-if="row.storeNo" class="cell-sub">{{ row.storeNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="DEPOSIT_TYPE_TAG[row.type] || 'info'" size="small" effect="plain">
              {{ DEPOSIT_TYPE[row.type] || `#${row.type}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">
            <span :class="row.type === 1 ? 'amount-in' : 'amount-out'">
              {{ row.type === 1 ? '+' : '-' }}¥{{ Number(row.amount).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="DEPOSIT_STATUS_TAG[row.status] || 'info'" size="small">
              {{ DEPOSIT_STATUS[row.status] || `#${row.status}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联单号" width="170">
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="缴纳时间" width="165">
          <template #default="{ row }">{{ formatTime(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="退还时间" width="165">
          <template #default="{ row }">{{ formatTime(row.refundedAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.type === 1 && row.status === 0"
              type="success"
              link
              @click="onConfirm(row)"
            >确认收款</el-button>
            <el-button
              v-if="row.type === 1 && row.status === 1"
              type="danger"
              link
              @click="onRefund(row)"
            >退还</el-button>
            <span v-if="row.type === 2" class="cell-sub">—</span>
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
          @current-change="loadDeposits"
          @size-change="onSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BizError } from '@/utils/error'
import { myStoresApi } from '@/api/store'
import { confirmDepositApi, pageDepositsApi, refundDepositApi } from '@/api/deposit'
import { DEPOSIT_STATUS, DEPOSIT_STATUS_TAG, DEPOSIT_TYPE, DEPOSIT_TYPE_TAG } from '@/types/deposit'
import type { DepositItem } from '@/types/deposit'
import type { StoreBinding } from '@/types/api'

/**
 * 保证金管理（store:deposit:confirm 221 敏感权限，路由守卫已拦截非超管）：
 * - 缴纳流水 0待处理 → 「确认收款」→ 1完成 + paid_at
 * - 已确认收款的缴纳流水 → 「退还」→ 写入 type=2 退还流水 + refunded_at
 */
const loading = ref(false)
const deposits = ref<DepositItem[]>([])
const total = ref(0)
const storeOptions = ref<StoreBinding[]>([])
const query = reactive({
  type: undefined as number | undefined,
  status: undefined as number | undefined,
  storeId: undefined as number | undefined,
  page: 1,
  size: 10,
})

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function loadDeposits() {
  loading.value = true
  try {
    const page = await pageDepositsApi({ ...query })
    deposits.value = page.records ?? []
    total.value = Number(page.total ?? 0)
  } catch (e) {
    if (e instanceof BizError) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadDeposits()
}

function onReset() {
  query.type = undefined
  query.status = undefined
  query.storeId = undefined
  onSearch()
}

async function onConfirm(row: DepositItem) {
  try {
    await ElMessageBox.confirm(
      `确认已收到门店「${row.storeName || `#${row.storeId}`}」保证金 ¥${Number(row.amount).toFixed(2)}？确认后不可撤销。`,
      '确认收款',
      { type: 'warning', confirmButtonText: '确认收款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await confirmDepositApi(row.id)
    ElMessage.success('已确认收款')
    loadDeposits()
  } catch (e) {
    if (e instanceof BizError) ElMessage.error(e.message)
  }
}

async function onRefund(row: DepositItem) {
  try {
    await ElMessageBox.confirm(
      `将向门店「${row.storeName || `#${row.storeId}`}」全额退还保证金 ¥${Number(row.amount).toFixed(2)}，并生成退还流水。确认继续？`,
      '退还保证金',
      { type: 'warning', confirmButtonText: '确认退还', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await refundDepositApi(row.id)
    ElMessage.success('退还流水已生成')
    loadDeposits()
  } catch (e) {
    if (e instanceof BizError) ElMessage.error(e.message)
  }
}

onMounted(async () => {
  try {
    storeOptions.value = await myStoresApi()
  } catch {
    storeOptions.value = []
  }
  loadDeposits()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
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
  color: var(--el-text-color-secondary);
}
.amount-in {
  color: var(--el-color-success);
  font-weight: 600;
}
.amount-out {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
