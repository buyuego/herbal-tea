<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="商品名 / SKU 编码"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select
          v-model="query.categoryId"
          placeholder="全部分类"
          clearable
          style="width: 150px"
          @change="onSearch"
        >
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="SKU 状态" clearable style="width: 130px" @change="onSearch">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-checkbox v-model="lowStockOnly" @change="onSearch">仅看低库存预警</el-checkbox>
        <el-button type="primary" plain @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="toolbar-right">
          <el-button :icon="RefreshIcon" @click="loadInventory">刷新</el-button>
        </div>
      </div>

      <!-- 库存总览表格 -->
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="商品 / SKU" min-width="200">
          <template #default="{ row }">
            <div class="cell-main">{{ row.productName }}</div>
            <div class="cell-sub">{{ row.skuCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="140">
          <template #default="{ row }">
            <span v-if="row.specs">{{ formatSpecs(row.specs) }}</span>
            <span v-else class="cell-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column label="库存" width="110" align="center">
          <template #default="{ row }">
            <span :class="row.lowStock ? 'stock-alert' : 'stock-main'">{{ row.stock }}</span>
            <el-tag v-if="row.lowStock" type="danger" size="small" effect="plain" class="alert-tag">预警</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警阈值" width="100" align="center">
          <template #default="{ row }">{{ row.alertStock }}</template>
        </el-table-column>
        <el-table-column v-if="canViewCost" label="成本价" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.costPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="建议价" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="165">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openRecords(row)">流水</el-button>
            <template v-if="canManage">
              <el-button type="success" link @click="openAdjust(row)">调整</el-button>
              <el-button type="warning" link @click="onSetAlert(row)">设阈值</el-button>
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
          @current-change="loadInventory"
        />
      </div>
    </el-card>

    <!-- 库存调整对话框 -->
    <el-dialog v-model="adjustVisible" title="库存调整" width="460px">
      <el-form label-width="100px">
        <el-form-item label="SKU">
          <div class="dialog-static">
            {{ adjustRow?.productName }}
            <span class="cell-sub">{{ adjustRow?.skuCode }}</span>
          </div>
        </el-form-item>
        <el-form-item label="当前库存">
          <div class="dialog-static">{{ adjustRow?.stock }}</div>
        </el-form-item>
        <el-form-item label="变动类型">
          <el-radio-group v-model="adjustForm.changeType">
            <el-radio :value="1">入库</el-radio>
            <el-radio :value="3">盘点</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="adjustForm.changeType === 1 ? '入库数量' : '盘点差值'">
          <el-input-number
            v-model="adjustForm.changeQty"
            :min="adjustForm.changeType === 1 ? 1 : -999999"
            :max="999999"
            :precision="0"
          />
          <span class="form-tip">
            {{ adjustForm.changeType === 1 ? '入库为正数' : '实际盘亏填负数，盘盈填正数，不允许为 0' }}
          </span>
        </el-form-item>
        <el-form-item label="关联单号">
          <el-input v-model="adjustForm.bizNo" placeholder="采购单号等（可选）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.note" type="textarea" :rows="2" placeholder="调整原因（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确定</el-button>
      </template>
    </el-dialog>

    <!-- 库存流水抽屉 -->
    <el-drawer v-model="recordVisible" :title="recordTitle" size="60%">
      <div class="toolbar">
        <el-select
          v-model="recordQuery.changeType"
          placeholder="全部变动类型"
          clearable
          style="width: 160px"
          @change="loadRecords"
        >
          <el-option v-for="(label, code) in CHANGE_TYPE" :key="code" :label="label" :value="Number(code)" />
        </el-select>
        <el-input
          v-model="recordQuery.bizNo"
          placeholder="关联单号"
          clearable
          style="width: 180px"
          @keyup.enter="loadRecords"
          @clear="loadRecords"
        />
        <el-button type="primary" plain @click="loadRecords">查询</el-button>
      </div>
      <el-table :data="records" v-loading="recordLoading" stripe size="small">
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="CHANGE_TYPE_TAG[row.changeType] || 'info'" size="small">
              {{ CHANGE_TYPE[row.changeType] || `#${row.changeType}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.changeQty >= 0 ? 'stock-in' : 'stock-out'">
              {{ row.changeQty >= 0 ? '+' : '' }}{{ row.changeQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" width="80" align="center">
          <template #default="{ row }">{{ row.beforeStock }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="80" align="center">
          <template #default="{ row }">{{ row.afterStock }}</template>
        </el-table-column>
        <el-table-column label="关联单号" min-width="140">
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || (row.operatorId ? `#${row.operatorId}` : '-') }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">{{ row.note || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="recordQuery.page"
          v-model:page-size="recordQuery.size"
          :total="recordTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadRecords"
          @current-change="loadRecords"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh as RefreshIcon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { adjustStockApi, pageInventoryApi, pageInventoryRecordsApi, setAlertStockApi } from '@/api/inventory'
import { listCategoriesApi } from '@/api/product'
import { CHANGE_TYPE, CHANGE_TYPE_TAG } from '@/types/inventory'
import type { InventoryRecordVO, InventoryVO } from '@/types/inventory'
import type { ProductCategory } from '@/types/product'

const auth = useAuthStore()
/** 仓管：入库/盘点/设阈值 */
const canManage = computed(() => auth.hasPermission('inventory:manage'))
/** 成本价为敏感字段，仅超管/财务可见 */
const canViewCost = computed(() => auth.hasPermission('product:cost:view'))

const loading = ref(false)
const rows = ref<InventoryVO[]>([])
const total = ref(0)
const categories = ref<ProductCategory[]>([])
const lowStockOnly = ref(false)

const query = reactive<{ keyword?: string; categoryId?: number; status?: number; page: number; size: number }>({
  keyword: '',
  categoryId: undefined,
  status: undefined,
  page: 1,
  size: 10,
})

async function loadInventory() {
  loading.value = true
  try {
    const page = await pageInventoryApi({
      keyword: query.keyword || undefined,
      categoryId: query.categoryId,
      status: query.status,
      lowStockOnly: lowStockOnly.value ? 1 : undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '库存列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadInventory()
}

function onReset() {
  query.keyword = ''
  query.categoryId = undefined
  query.status = undefined
  lowStockOnly.value = false
  onSearch()
}

// ==================== 库存调整 ====================

const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustRow = ref<InventoryVO | null>(null)
const adjustForm = reactive({ changeType: 1, changeQty: 1, bizNo: '', note: '' })

function openAdjust(row: InventoryVO) {
  adjustRow.value = row
  adjustForm.changeType = 1
  adjustForm.changeQty = 1
  adjustForm.bizNo = ''
  adjustForm.note = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  const row = adjustRow.value
  if (!row) return
  if (!adjustForm.changeQty || adjustForm.changeQty === 0) {
    ElMessage.warning(adjustForm.changeType === 1 ? '入库数量必须大于 0' : '盘点差值不能为 0')
    return
  }
  adjusting.value = true
  try {
    await adjustStockApi({
      skuId: row.skuId,
      changeType: adjustForm.changeType,
      changeQty: adjustForm.changeQty,
      bizNo: adjustForm.bizNo || undefined,
      note: adjustForm.note || undefined,
    })
    ElMessage.success('库存调整成功')
    adjustVisible.value = false
    await loadInventory()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '库存调整失败')
  } finally {
    adjusting.value = false
  }
}

// ==================== 预警阈值 ====================

async function onSetAlert(row: InventoryVO) {
  try {
    const { value } = await ElMessageBox.prompt(
      `设置「${row.productName} / ${row.skuCode}」的低库存预警阈值（当前库存 ${row.stock}）`,
      '库存预警阈值',
      {
        inputValue: String(row.alertStock ?? 10),
        inputPattern: /^\d{1,7}$/,
        inputErrorMessage: '阈值须为 0 ~ 9999999 的整数',
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      },
    )
    await setAlertStockApi(row.skuId, Number(value))
    ElMessage.success('预警阈值已更新')
    await loadInventory()
  } catch (e) {
    if (e instanceof BizError) {
      ElMessage.error(e.message)
    }
    // ElMessageBox 取消（'cancel' / 'close'）不提示
  }
}

// ==================== 库存流水 ====================

const recordVisible = ref(false)
const recordLoading = ref(false)
const records = ref<InventoryRecordVO[]>([])
const recordTotal = ref(0)
const recordSkuId = ref<number | undefined>(undefined)
const recordTitle = ref('库存流水')
const recordQuery = reactive<{ bizNo?: string; changeType?: number; page: number; size: number }>({
  bizNo: '',
  changeType: undefined,
  page: 1,
  size: 10,
})

function openRecords(row: InventoryVO) {
  recordSkuId.value = row.skuId
  recordTitle.value = `库存流水 · ${row.productName} / ${row.skuCode}`
  recordQuery.bizNo = ''
  recordQuery.changeType = undefined
  recordQuery.page = 1
  recordVisible.value = true
  loadRecords()
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await pageInventoryRecordsApi({
      skuId: recordSkuId.value,
      bizNo: recordQuery.bizNo || undefined,
      changeType: recordQuery.changeType,
      page: recordQuery.page,
      size: recordQuery.size,
    })
    records.value = page.records
    recordTotal.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '库存流水加载失败')
  } finally {
    recordLoading.value = false
  }
}

// ==================== 工具 ====================

function formatSpecs(json: string): string {
  try {
    const v = JSON.parse(json)
    return Object.entries(v as Record<string, unknown>)
      .map(([k, val]) => `${k}:${val}`)
      .join(' / ')
  } catch {
    return json
  }
}

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

onMounted(async () => {
  await loadInventory()
  try {
    categories.value = await listCategoriesApi()
  } catch {
    // 分类下拉失败不阻塞主表格
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
.stock-main {
  font-weight: 600;
}
.stock-alert {
  font-weight: 600;
  color: #f56c6c;
}
.alert-tag {
  margin-left: 6px;
}
.stock-in {
  color: #67c23a;
  font-weight: 600;
}
.stock-out {
  color: #f56c6c;
  font-weight: 600;
}
.dialog-static {
  line-height: 1.4;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
