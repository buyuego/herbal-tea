<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="活动名"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.type" placeholder="活动类型" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="(label, v) in PROMOTION_TYPE" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-select v-model="query.scope" placeholder="归属" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="(label, v) in PROMOTION_SCOPE" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="(label, v) in PROMOTION_STATUS" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-button type="primary" plain @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="toolbar-right">
          <el-button type="primary" :icon="PlusIcon" @click="openCreate">新建活动</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-12"
        title="叠加规则：同一订单最多命中一个活动（取优惠最大者）；活动与优惠券、积分可叠加，计价顺序为 活动 → 券 → 积分。"
      />

      <el-table :data="promotions" v-loading="loading" stripe>
        <el-table-column label="活动名" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.title }}</div>
            <div class="cell-sub">{{ row.typeDesc }} · {{ row.ruleDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="150">
          <template #default="{ row }">
            <el-tag :type="PROMOTION_SCOPE_TAG[row.scope] || 'info'" size="small" effect="plain">
              {{ row.scopeDesc }}
            </el-tag>
            <span v-if="row.storeName" class="cell-sub"> {{ row.storeName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" width="200">
          <template #default="{ row }">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="PROMOTION_STATUS_TAG[row.status] || 'info'" size="small" effect="plain">
              {{ row.statusDesc }}
            </el-tag>
            <div v-if="row.status === 1" class="cell-sub">{{ row.active ? '生效中' : '未在窗口期' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="canEdit">
              <el-button v-if="row.status === 0" type="success" link @click="onPublish(row)">发布</el-button>
              <el-button v-if="row.status === 0" type="primary" link @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 1" type="danger" link @click="onStop(row)">结束</el-button>
            </template>
            <span v-if="!canEdit" class="cell-sub">只读</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="onSearch"
          @current-change="loadPromotions"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="editVisible" :title="editing ? '编辑活动' : '新建活动'" width="580px">
      <el-form label-width="110px">
        <el-form-item label="活动名" required>
          <el-input v-model="form.title" placeholder="如：双十一满减" />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-radio-group v-model="form.type">
            <el-radio :value="1">满减</el-radio>
            <el-radio :value="2">折扣</el-radio>
            <el-radio :value="3">限时购</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归属" required>
          <el-radio-group v-model="form.scope">
            <el-radio :value="2">本店活动（店铺承担）</el-radio>
            <el-radio :value="1">平台活动（平台承担）</el-radio>
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
        <el-form-item v-if="form.type !== 2" label="优惠金额" required>
          <el-input-number v-model="form.discountAmount" :min="0.01" :precision="2" :step="5" />
          <span class="form-tip">{{ form.type === 3 ? '限时窗口内直减' : '满门槛直减' }}</span>
        </el-form-item>
        <template v-else>
          <el-form-item label="折扣率" required>
            <el-input-number v-model="discountRate" :min="0.01" :max="0.99" :precision="2" :step="0.05" />
            <span class="form-tip">0.88 = 88 折</span>
          </el-form-item>
          <el-form-item label="最高优惠">
            <el-input-number v-model="maxDiscount" :min="0" :precision="2" :step="5" />
            <span class="form-tip">0 = 不封顶</span>
          </el-form-item>
        </template>
        <el-form-item label="活动时间" required>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="规则预览">
          <span class="rule-preview">{{ rulePreview }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus as PlusIcon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  createPromotionApi,
  pagePromotionsApi,
  publishPromotionApi,
  stopPromotionApi,
  updatePromotionApi,
} from '@/api/promotion'
import {
  PROMOTION_SCOPE,
  PROMOTION_SCOPE_TAG,
  PROMOTION_STATUS,
  PROMOTION_STATUS_TAG,
  PROMOTION_TYPE,
} from '@/types/promotion'
import type { PromotionVO } from '@/types/promotion'

const auth = useAuthStore()
/** 活动写操作（新建/编辑/发布/结束）需要 marketing:promotion(212) */
const canEdit = computed(() => auth.hasPermission('marketing:promotion'))
/** 门店侧：当前上下文为某门店时只能建本店活动，且自动归属本店 */
const isStoreSide = computed(() => auth.currentStore !== null)
const storeOptions = computed(() => auth.stores)

const loading = ref(false)
const promotions = ref<PromotionVO[]>([])
const total = ref(0)

const query = reactive<{
  keyword?: string
  type?: number
  scope?: number
  status?: number
  page: number
  size: number
}>({
  keyword: '',
  type: undefined,
  scope: undefined,
  status: undefined,
  page: 1,
  size: 10,
})

async function loadPromotions() {
  loading.value = true
  try {
    const page = await pagePromotionsApi({
      keyword: query.keyword || undefined,
      type: query.type,
      scope: query.scope,
      status: query.status,
      page: query.page,
      size: query.size,
    })
    promotions.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '活动列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadPromotions()
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
const editing = ref<PromotionVO | null>(null)
const dateRange = ref<[string, string] | null>(null)
const discountRate = ref(0.88)
const maxDiscount = ref(0)

const form = reactive({
  title: '',
  type: 1,
  scope: 2,
  storeId: undefined as number | undefined,
  thresholdAmount: 0,
  discountAmount: 10,
})

/** 规则摘要预览（与后端 describeCash/describeDiscount 同口径） */
const rulePreview = computed(() => {
  const threshold = Number(form.thresholdAmount || 0)
  if (form.type === 2) {
    const fold = (discountRate.value * 10).toFixed(1)
    const head = threshold > 0 ? `满 ¥${threshold} 打 ${fold} 折` : `打 ${fold} 折`
    return maxDiscount.value > 0 ? `${head}（最高减 ¥${maxDiscount.value}）` : head
  }
  return threshold > 0 ? `满 ¥${threshold} 减 ¥${form.discountAmount}` : `立减 ¥${form.discountAmount}`
})

function openCreate() {
  editing.value = null
  form.title = ''
  form.type = 1
  form.scope = 2
  form.storeId = isStoreSide.value ? auth.currentStore?.storeId : undefined
  form.thresholdAmount = 0
  form.discountAmount = 10
  discountRate.value = 0.88
  maxDiscount.value = 0
  dateRange.value = null
  editVisible.value = true
}

function openEdit(row: PromotionVO) {
  editing.value = row
  form.title = row.title
  form.type = row.type
  form.scope = row.scope
  form.storeId = row.storeId ?? undefined
  form.thresholdAmount = Number(row.thresholdAmount ?? 0)
  form.discountAmount = Number(row.discountAmount ?? 0)
  if (row.type === 2 && row.rules) {
    try {
      const rules = JSON.parse(row.rules)
      discountRate.value = Number(rules.discountRate ?? 0.88)
      maxDiscount.value = Number(rules.maxDiscount ?? 0)
    } catch {
      discountRate.value = 0.88
      maxDiscount.value = 0
    }
  }
  dateRange.value = [row.startTime.replace('T', ' ').slice(0, 19), row.endTime.replace('T', ' ').slice(0, 19)]
  editVisible.value = true
}

async function submitEdit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写活动名')
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择活动时间')
    return
  }
  if (form.scope === 2 && !isStoreSide.value && !form.storeId) {
    ElMessage.warning('本店活动必须选择归属门店')
    return
  }
  if (form.type !== 2 && Number(form.discountAmount) <= 0) {
    ElMessage.warning('优惠金额必须大于 0')
    return
  }

  const rules =
    form.type === 2
      ? { thresholdAmount: form.thresholdAmount, discountRate: discountRate.value, maxDiscount: maxDiscount.value }
      : { thresholdAmount: form.thresholdAmount, discountAmount: form.discountAmount }

  const payload = {
    title: form.title.trim(),
    type: form.type,
    scope: form.scope,
    storeId: form.scope === 2 ? form.storeId : undefined,
    rules: JSON.stringify(rules),
    startTime: dateRange.value[0],
    endTime: dateRange.value[1],
  }

  saving.value = true
  try {
    if (editing.value) {
      await updatePromotionApi(editing.value.id, payload)
      ElMessage.success('活动已更新')
    } else {
      await createPromotionApi(payload)
      ElMessage.success('活动已创建（草稿），发布后生效')
    }
    editVisible.value = false
    await loadPromotions()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 发布 / 结束 ====================

async function onPublish(row: PromotionVO) {
  try {
    await ElMessageBox.confirm(
      `确认发布「${row.title}」？发布后进入进行中，不可再编辑。`,
      '发布活动',
      { type: 'success', confirmButtonText: '发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await publishPromotionApi(row.id)
    ElMessage.success('已发布')
    await loadPromotions()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '发布失败')
  }
}

async function onStop(row: PromotionVO) {
  try {
    await ElMessageBox.confirm(`确认结束「${row.title}」？结束后不再参与下单计价。`, '结束活动', {
      type: 'warning',
      confirmButtonText: '结束',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await stopPromotionApi(row.id)
    ElMessage.success('活动已结束')
    await loadPromotions()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

// ==================== 工具 ====================

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

onMounted(loadPromotions)
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
.mb-12 {
  margin-bottom: 12px;
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
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.rule-preview {
  font-weight: 600;
  color: #e6a23c;
}
</style>
