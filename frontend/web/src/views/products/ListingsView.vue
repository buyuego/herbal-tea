<template>
  <div class="listings-view">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="loadListings">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="1">上架中</el-radio-button>
        <el-radio-button :value="0">已下架</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :loading="loading" @click="loadListings">刷新</el-button>
      <el-button v-if="canEdit" type="primary" class="btn-new" @click="openListingDialog">+ 本店上架</el-button>
    </div>

    <el-table :data="listings" v-loading="loading" stripe>
      <el-table-column label="主图" width="70">
        <template #default="{ row }">
          <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" style="width: 40px; height: 40px; border-radius: 4px" />
          <el-icon v-else><Picture /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="productName" label="商品" min-width="160" />
      <el-table-column prop="skuCode" label="SKU" width="130" />
      <el-table-column label="规格" min-width="130">
        <template #default="{ row }">
          <span v-if="row.specs">{{ formatSpecs(row.specs) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="本店售价" width="100" align="right">
        <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="建议区间" width="120" align="right">
        <template #default="{ row }">
          <span class="muted">
            ¥{{ (Number(row.suggestedPrice) * 0.8).toFixed(2) }} ~ ¥{{ (Number(row.suggestedPrice) * 1.2).toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="70" align="center" />
      <el-table-column prop="dailyQuota" label="日配额" width="70" align="center" />
      <el-table-column label="目录" width="80" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="row.catalogDirty === 1" content="平台目录已更新，待复核" placement="top">
            <el-tag type="warning" size="small">待复核</el-tag>
          </el-tooltip>
          <el-tag v-else type="success" size="small" effect="plain">已同步</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" type="primary" link @click="openPriceDialog(row)">改价</el-button>
          <el-button
            v-if="canEdit"
            :type="row.status === 1 ? 'danger' : 'success'"
            link
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && listings.length === 0" description="本店暂无上架商品" :image-size="80" />

    <!-- 上架对话框 -->
    <el-dialog v-model="listingVisible" title="本店上架" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="商品" required>
          <el-select
            v-model="listingForm.productId"
            placeholder="选择平台商品"
            filterable
            style="width: 100%"
            @change="onProductChange"
          >
            <el-option v-for="p in catalog" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="catalogSkus.length" label="SKU" required>
          <el-select v-model="listingForm.skuId" placeholder="选择 SKU" style="width: 100%">
            <el-option
              v-for="s in catalogSkus"
              :key="s.id"
              :label="`${s.skuCode}（¥${Number(s.price).toFixed(2)}，库存 ${s.stock}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="本店售价" required>
          <el-input-number v-model="listingForm.price" :min="0.01" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="每日配额" required>
          <el-input-number v-model="listingForm.dailyQuota" :min="1" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="listingVisible = false">取消</el-button>
        <el-button type="primary" :loading="listingSaving" @click="submitListing">上架</el-button>
      </template>
    </el-dialog>

    <!-- 改价对话框 -->
    <el-dialog v-model="priceVisible" title="本店改价" width="420px">
      <el-form label-width="100px">
        <el-form-item label="商品">
          <span>{{ priceRow?.productName }} · {{ priceRow?.skuCode }}</span>
        </el-form-item>
        <el-form-item label="新售价" required>
          <el-input-number v-model="priceForm.price" :min="0.01" :precision="2" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" :loading="priceSaving" @click="submitPrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  createStoreProductApi,
  getProductDetailApi,
  listStoreProductsApi,
  pageProductsApi,
  updateStorePriceApi,
  updateStoreProductStatusApi,
} from '@/api/product'
import type { Product, ProductSku, StoreProduct } from '@/types/product'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('product:edit'))

const loading = ref(false)
const listings = ref<StoreProduct[]>([])
const statusFilter = ref<number | undefined>(undefined)

const listingVisible = ref(false)
const listingSaving = ref(false)
const catalog = ref<Product[]>([])
const catalogSkus = ref<ProductSku[]>([])
const listingForm = reactive({ productId: undefined as number | undefined, skuId: undefined as number | undefined, price: 0, dailyQuota: 10 })

const priceVisible = ref(false)
const priceSaving = ref(false)
const priceRow = ref<StoreProduct | null>(null)
const priceForm = reactive({ price: 0 })

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

async function loadListings() {
  loading.value = true
  try {
    listings.value = await listStoreProductsApi(statusFilter.value)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '上架列表加载失败')
  } finally {
    loading.value = false
  }
}

async function openListingDialog() {
  listingVisible.value = true
  listingForm.productId = undefined
  listingForm.skuId = undefined
  listingForm.price = 0
  listingForm.dailyQuota = 10
  catalogSkus.value = []
  // 拉取在售商品目录（仅首屏，避免每次弹窗都请求）
  if (catalog.value.length === 0) {
    try {
      const page = await pageProductsApi({ status: 1, page: 1, size: 100 })
      catalog.value = page.records
    } catch (e) {
      ElMessage.error(e instanceof BizError ? e.message : '商品目录加载失败')
    }
  }
}

async function onProductChange(productId: number | undefined) {
  listingForm.skuId = undefined
  catalogSkus.value = []
  if (!productId) return
  try {
    const detail = await getProductDetailApi(productId)
    catalogSkus.value = detail.skus.filter((s) => s.status === 1)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : 'SKU 加载失败')
  }
}

async function submitListing() {
  if (!listingForm.productId || !listingForm.skuId) {
    ElMessage.warning('请选择商品与 SKU')
    return
  }
  if (listingForm.price <= 0) {
    ElMessage.warning('售价须大于 0')
    return
  }
  listingSaving.value = true
  try {
    await createStoreProductApi({
      productId: listingForm.productId,
      skuId: listingForm.skuId,
      price: listingForm.price,
      dailyQuota: listingForm.dailyQuota,
    })
    ElMessage.success('上架成功')
    listingVisible.value = false
    await loadListings()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '上架失败')
  } finally {
    listingSaving.value = false
  }
}

function openPriceDialog(row: StoreProduct) {
  priceRow.value = row
  priceForm.price = Number(row.price)
  priceVisible.value = true
}

async function submitPrice() {
  if (!priceRow.value) return
  if (priceForm.price <= 0) {
    ElMessage.warning('售价须大于 0')
    return
  }
  priceSaving.value = true
  try {
    await updateStorePriceApi(priceRow.value.id, priceForm.price)
    ElMessage.success('售价已更新')
    priceVisible.value = false
    await loadListings()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '改价失败')
  } finally {
    priceSaving.value = false
  }
}

async function toggleStatus(row: StoreProduct) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateStoreProductStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已上架' : '已下架')
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

onMounted(loadListings)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}
.btn-new {
  margin-left: auto;
}
.muted {
  color: #909399;
}
</style>
