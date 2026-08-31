<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="券名"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.type" placeholder="券类型" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, v) in COUPON_TYPE" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-select v-model="query.scope" placeholder="归属" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, v) in COUPON_SCOPE" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, v) in COUPON_STATUS" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-button type="primary" plain @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="toolbar-right">
          <el-button type="primary" :icon="PlusIcon" @click="openCreate">新建券</el-button>
        </div>
      </div>

      <!-- 券模板表格 -->
      <el-table :data="coupons" v-loading="loading" stripe>
        <el-table-column label="券名" min-width="160">
          <template #default="{ row }">
            <div class="cell-main">{{ row.name }}</div>
            <div class="cell-sub">{{ row.typeDesc }} · {{ row.scopeDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="140">
          <template #default="{ row }">
            <el-tag :type="COUPON_SCOPE_TAG[row.scope] || 'info'" size="small" effect="plain">
              {{ row.scopeDesc }}
            </el-tag>
            <span v-if="row.storeName" class="cell-sub"> {{ row.storeName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="门槛" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.thresholdAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="130">
          <template #default="{ row }">
            <span v-if="row.type === 1" class="discount-main">减 ¥{{ Number(row.discountAmount).toFixed(2) }}</span>
            <span v-else class="discount-main">{{ discountLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发行量" width="130" align="center">
          <template #default="{ row }">
            {{ row.receivedCount }} / {{ row.totalCount }}
            <div class="cell-sub">剩 {{ row.remainCount }} 张</div>
          </template>
        </el-table-column>
        <el-table-column label="限领" width="80" align="center">
          <template #default="{ row }">{{ row.perUserLimit }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="COUPON_STATUS_TAG[row.status] || 'info'" size="small" effect="plain">
              {{ row.statusDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openGrants(row)">领取记录</el-button>
            <template v-if="canEdit">
              <el-button v-if="row.status === 0" type="success" link @click="onPublish(row)">发布</el-button>
              <el-button v-if="row.status === 0" type="primary" link @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 1" type="danger" link @click="onStop(row)">停发</el-button>
            </template>
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
          layout="total, sizes, prev, pager, next"
          @size-change="onSearch"
          @current-change="loadCoupons"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="editVisible" :title="editing ? '编辑券模板' : '新建券模板'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="券名" required>
          <el-input v-model="form.name" placeholder="如：满100减20" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="券类型" required>
          <el-radio-group v-model="form.type" :disabled="!!editing">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归属" required>
          <el-radio-group v-model="form.scope" :disabled="!!editing">
            <el-radio :value="2">本店券（店铺承担）</el-radio>
            <el-radio :value="1">平台券（平台承担）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 2 && !isStoreSide" label="归属门店" required>
          <el-select v-model="form.storeId" placeholder="选择门店" style="width: 100%">
            <el-option v-for="s in storeOptions" :key="s.storeId" :label="s.storeName" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" :step="10" />
          <span class="form-tip">0 = 无门槛</span>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="优惠金额" required>
          <el-input-number v-model="form.discountAmount" :min="0.01" :precision="2" :step="5" />
        </el-form-item>
        <template v-else>
          <el-form-item label="折扣率" required>
            <el-input-number v-model="discountRate" :min="0.01" :max="0.99" :precision="2" :step="0.05" />
            <span class="form-tip">0.85 = 85 折</span>
          </el-form-item>
          <el-form-item label="最高优惠">
            <el-input-number v-model="maxDiscount" :min="0" :precision="2" :step="5" />
            <span class="form-tip">0 = 不封顶</span>
          </el-form-item>
        </template>
        <el-form-item label="发行总量" required>
          <el-input-number v-model="form.totalCount" :min="1" :precision="0" />
        </el-form-item>
        <el-form-item label="每人限领" required>
          <el-input-number v-model="form.perUserLimit" :min="1" :precision="0" />
        </el-form-item>
        <el-form-item label="有效期" required>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 领取记录抽屉 -->
    <el-drawer v-model="grantVisible" :title="grantTitle" size="60%">
      <el-table :data="grants" v-loading="grantLoading" stripe size="small">
        <el-table-column label="用户" width="90" align="center">
          <template #default="{ row }">#{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="券名" min-width="140">
          <template #default="{ row }">{{ row.couponName }}</template>
        </el-table-column>
        <el-table-column label="归属" width="120">
          <template #default="{ row }">{{ row.scopeDesc }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="USER_COUPON_STATUS_TAG[row.status] || 'info'" size="small">
              {{ row.statusDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销订单" min-width="160">
          <template #default="{ row }">{{ row.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="领取时间" width="165">
          <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
        </el-table-column>
        <el-table-column label="使用时间" width="165">
          <template #default="{ row }">{{ formatTime(row.usedAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="165">
          <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="grantPage"
          v-model:page-size="grantSize"
          :total="grantTotal"
          layout="total, prev, pager, next"
          @current-change="loadGrants"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus as PlusIcon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  createCouponApi,
  pageCouponGrantsApi,
  pageCouponsApi,
  publishCouponApi,
  stopCouponApi,
  updateCouponApi,
} from '@/api/coupon'
import {
  COUPON_SCOPE,
  COUPON_SCOPE_TAG,
  COUPON_STATUS,
  COUPON_STATUS_TAG,
  COUPON_TYPE,
  USER_COUPON_STATUS_TAG,
} from '@/types/coupon'
import type { CouponVO, UserCouponVO } from '@/types/coupon'

const auth = useAuthStore()
/** 券的写操作（新建/编辑/发布/停发）需要 marketing:coupon(211) */
const canEdit = computed(() => auth.hasPermission('marketing:coupon'))
/** 门店侧：当前上下文为某门店时只能建本店券，且自动归属本店 */
const isStoreSide = computed(() => auth.currentStore !== null)
/** 门店下拉直接用已加载的绑定列表（auth.stores） */
const storeOptions = computed(() => auth.stores)

const loading = ref(false)
const coupons = ref<CouponVO[]>([])
const total = ref(0)

const query = reactive<{ keyword?: string; type?: number; scope?: number; status?: number; page: number; size: number }>({
  keyword: '',
  type: undefined,
  scope: undefined,
  status: undefined,
  page: 1,
  size: 10,
})

async function loadCoupons() {
  loading.value = true
  try {
    const page = await pageCouponsApi({
      keyword: query.keyword || undefined,
      type: query.type,
      scope: query.scope,
      status: query.status,
      page: query.page,
      size: query.size,
    })
    coupons.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '券列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadCoupons()
}

function onReset() {
  query.keyword = ''
  query.type = undefined
  query.scope = undefined
  query.status = undefined
  onSearch()
}

// ==================== 新建 / 编辑 ====================

const editVisible = ref(false)
const saving = ref(false)
const editing = ref<CouponVO | null>(null)
const dateRange = ref<[string, string] | null>(null)
const discountRate = ref(0.85)
const maxDiscount = ref(0)

const form = reactive({
  name: '',
  type: 1,
  scope: 2,
  storeId: undefined as number | undefined,
  thresholdAmount: 0,
  discountAmount: 10,
  totalCount: 100,
  perUserLimit: 1,
})

function openCreate() {
  editing.value = null
  form.name = ''
  form.type = 1
  form.scope = isStoreSide.value ? 2 : 2
  form.storeId = isStoreSide.value ? auth.currentStore?.storeId : undefined
  form.thresholdAmount = 0
  form.discountAmount = 10
  form.totalCount = 100
  form.perUserLimit = 1
  discountRate.value = 0.85
  maxDiscount.value = 0
  dateRange.value = null
  editVisible.value = true
}

function openEdit(row: CouponVO) {
  editing.value = row
  form.name = row.name
  form.type = row.type
  form.scope = row.scope
  form.storeId = row.storeId ?? undefined
  form.thresholdAmount = Number(row.thresholdAmount)
  form.discountAmount = Number(row.discountAmount)
  form.totalCount = row.totalCount
  form.perUserLimit = row.perUserLimit
  if (row.type === 2 && row.rules) {
    try {
      const rules = JSON.parse(row.rules)
      discountRate.value = Number(rules.discountRate ?? 0.85)
      maxDiscount.value = Number(rules.maxDiscount ?? 0)
    } catch {
      discountRate.value = 0.85
      maxDiscount.value = 0
    }
  }
  dateRange.value = [row.startTime.replace('T', ' ').slice(0, 19), row.endTime.replace('T', ' ').slice(0, 19)]
  editVisible.value = true
}

async function submitEdit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写券名')
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择有效期')
    return
  }
  if (form.scope === 2 && !isStoreSide.value && !form.storeId) {
    ElMessage.warning('本店券必须选择归属门店')
    return
  }

  const payload = {
    name: form.name.trim(),
    type: form.type,
    scope: form.scope,
    storeId: form.scope === 2 ? form.storeId : undefined,
    thresholdAmount: form.thresholdAmount,
    discountAmount: form.type === 1 ? form.discountAmount : undefined,
    rules:
      form.type === 2
        ? JSON.stringify({ discountRate: discountRate.value, maxDiscount: maxDiscount.value })
        : undefined,
    totalCount: form.totalCount,
    perUserLimit: form.perUserLimit,
    startTime: dateRange.value[0],
    endTime: dateRange.value[1],
  }

  saving.value = true
  try {
    if (editing.value) {
      await updateCouponApi(editing.value.id, payload)
      ElMessage.success('券模板已更新')
    } else {
      await createCouponApi(payload)
      ElMessage.success('券模板已创建（未发布）')
    }
    editVisible.value = false
    await loadCoupons()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 发布 / 停发 ====================

async function onPublish(row: CouponVO) {
  try {
    await ElMessageBox.confirm(`确认发布「${row.name}」？发布后不可再编辑。`, '发布券模板', {
      type: 'success',
      confirmButtonText: '发布',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await publishCouponApi(row.id)
    ElMessage.success('已发布')
    await loadCoupons()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '发布失败')
  }
}

async function onStop(row: CouponVO) {
  try {
    await ElMessageBox.confirm(
      `确认停止发放「${row.name}」？已领取的券仍可使用至过期。`,
      '停止发放',
      { type: 'warning', confirmButtonText: '停止', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await stopCouponApi(row.id)
    ElMessage.success('已停止发放')
    await loadCoupons()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

// ==================== 领取记录 ====================

const grantVisible = ref(false)
const grantLoading = ref(false)
const grants = ref<UserCouponVO[]>([])
const grantTotal = ref(0)
const grantPage = ref(1)
const grantSize = ref(10)
const grantCouponId = ref<number | null>(null)
const grantTitle = ref('领取记录')

async function openGrants(row: CouponVO) {
  grantCouponId.value = row.id
  grantTitle.value = `领取记录 · ${row.name}`
  grantPage.value = 1
  grantVisible.value = true
  await loadGrants()
}

async function loadGrants() {
  if (grantCouponId.value == null) return
  grantLoading.value = true
  try {
    const page = await pageCouponGrantsApi(grantCouponId.value, grantPage.value, grantSize.value)
    grants.value = page.records
    grantTotal.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '领取记录加载失败')
  } finally {
    grantLoading.value = false
  }
}

// ==================== 工具 ====================

/** 折扣券的优惠文案：8.5 折（最高减 ¥15） */
function discountLabel(row: CouponVO): string {
  try {
    const rules = row.rules ? JSON.parse(row.rules) : {}
    const rate = Number(rules.discountRate ?? 1)
    const label = `${(rate * 10).toFixed(1)} 折`
    const cap = Number(rules.maxDiscount ?? 0)
    return cap > 0 ? `${label}（最高减 ¥${cap.toFixed(2)}）` : label
  } catch {
    return '-'
  }
}

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

onMounted(loadCoupons)
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
.discount-main {
  font-weight: 600;
  color: #e6a23c;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
