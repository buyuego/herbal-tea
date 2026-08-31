<template>
  <el-dialog
    :model-value="modelValue"
    :title="`SKU / 库存 · ${product?.name ?? ''}`"
    width="860px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="loadDetail"
  >
    <el-table v-loading="loading" :data="detail?.skus ?? []" size="small" border>
      <el-table-column prop="skuCode" label="SKU 编码" min-width="130" />
      <el-table-column label="规格" min-width="150">
        <template #default="{ row }">
          <span v-if="row.specs">{{ formatSpecs(row.specs) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="售价" width="90" align="right">
        <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column v-if="canViewCost" label="成本" width="90" align="right">
        <template #default="{ row }">¥{{ Number(row.costPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="库存" width="80" align="center">
        <template #default="{ row }">
          <span :class="row.stock <= 0 ? 'stock-zero' : ''">{{ row.stock }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" :type="row.status === 1 ? 'danger' : 'success'" link @click="toggleSkuStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="canInventory" type="primary" link @click="openAdjust(row)">库存调整</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 追加 SKU -->
    <el-divider v-if="canEdit" content-position="left">追加 SKU</el-divider>
    <div v-if="canEdit" class="add-sku-row">
      <el-input v-model="newSku.skuCode" placeholder="SKU 编码（全局唯一）" style="width: 160px" size="small" />
      <el-input v-model="newSku.specsText" placeholder='{"规格":"500ml"}' style="width: 180px" size="small" />
      <el-input-number v-model="newSku.price" :min="0.01" :precision="2" size="small" placeholder="售价" />
      <el-input-number v-if="canViewCost" v-model="newSku.costPrice" :min="0" :precision="2" size="small" placeholder="成本" />
      <el-input-number v-model="newSku.stock" :min="0" size="small" placeholder="库存" />
      <el-button type="primary" size="small" :loading="addingSku" @click="addSku">追加</el-button>
    </div>

    <!-- 库存调整对话框 -->
    <el-dialog v-model="adjustVisible" title="库存调整" width="420px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="SKU">
          <el-input :model-value="adjustSku?.skuCode" disabled />
        </el-form-item>
        <el-form-item label="变动类型">
          <el-radio-group v-model="adjustForm.changeType">
            <el-radio :value="1">入库</el-radio>
            <el-radio :value="3">盘点调整</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="变动数量">
          <el-input-number v-model="adjustForm.changeQty" :min="adjustForm.changeType === 3 ? -99999 : 1" :max="999999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.note" placeholder="可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确认</el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  addSkuApi,
  adjustStockApi,
  getProductDetailApi,
  updateSkuStatusApi,
} from '@/api/product'
import type { Product, ProductDetail, ProductSku } from '@/types/product'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('product:edit'))
const canViewCost = computed(() => auth.hasPermission('product:cost:view'))
const canInventory = computed(() => auth.hasPermission('inventory:manage'))

const loading = ref(false)
const detail = ref<ProductDetail | null>(null)
const addingSku = ref(false)

const newSku = reactive({ skuCode: '', specsText: '', price: 0, costPrice: 0, stock: 0 })

const adjustVisible = ref(false)
const adjustSku = ref<ProductSku | null>(null)
const adjustForm = reactive({ changeType: 1, changeQty: 1, note: '' })
const adjusting = ref(false)

function formatSpecs(json: string): string {
  try {
    const v = JSON.parse(json)
    return Object.entries(v)
      .map(([k, val]) => `${k}:${String(val)}`)
      .join(' / ')
  } catch {
    return json
  }
}

async function loadDetail() {
  if (!props.product) return
  loading.value = true
  try {
    detail.value = await getProductDetailApi(props.product.id)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '商品详情加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleSkuStatus(row: ProductSku) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateSkuStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? 'SKU 已启用' : 'SKU 已停用')
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

async function addSku() {
  if (!props.product) return
  if (!newSku.skuCode.trim()) {
    ElMessage.warning('请输入 SKU 编码')
    return
  }
  if (newSku.price <= 0) {
    ElMessage.warning('售价须大于 0')
    return
  }
  let specs: Record<string, unknown> | null = null
  if (newSku.specsText.trim()) {
    try {
      specs = JSON.parse(newSku.specsText)
    } catch {
      ElMessage.warning('规格 JSON 格式不正确')
      return
    }
  }
  addingSku.value = true
  try {
    await addSkuApi(props.product.id, {
      skuCode: newSku.skuCode.trim(),
      specs,
      price: newSku.price,
      costPrice: newSku.costPrice,
      stock: newSku.stock,
    })
    ElMessage.success('SKU 已追加')
    newSku.skuCode = ''
    newSku.specsText = ''
    newSku.price = 0
    newSku.costPrice = 0
    newSku.stock = 0
    await loadDetail()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '追加失败')
  } finally {
    addingSku.value = false
  }
}

function openAdjust(row: ProductSku) {
  adjustSku.value = row
  adjustForm.changeType = 1
  adjustForm.changeQty = 1
  adjustForm.note = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustSku.value) return
  if (!adjustForm.changeQty || adjustForm.changeQty === 0) {
    ElMessage.warning('变动数量不能为 0')
    return
  }
  adjusting.value = true
  try {
    await adjustStockApi({
      skuId: adjustSku.value.id,
      changeType: adjustForm.changeType,
      changeQty: adjustForm.changeQty,
      note: adjustForm.note || undefined,
    })
    ElMessage.success('库存已调整')
    adjustVisible.value = false
    await loadDetail()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '调整失败')
  } finally {
    adjusting.value = false
  }
}
</script>

<style scoped>
.add-sku-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.muted {
  color: #909399;
}
.stock-zero {
  color: #f56c6c;
  font-weight: 600;
}
</style>
