<template>
  <div class="catalog-view">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="商品名 / 副标题"
        clearable
        style="width: 200px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 160px" @change="onSearch">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="onSearch">
        <el-option label="在售" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">查询</el-button>
      <div class="toolbar-right">
        <el-button v-if="canEdit" @click="openCategoryDialog">分类管理</el-button>
        <el-button v-if="canEdit" type="primary" @click="openProductDialog()">新建商品</el-button>
      </div>
    </div>

    <!-- 商品表格 -->
    <el-table :data="products" v-loading="loading" stripe>
      <el-table-column label="主图" width="70">
        <template #default="{ row }">
          <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" style="width: 40px; height: 40px; border-radius: 4px" />
          <el-icon v-else><Picture /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="180">
        <template #default="{ row }">
          <div class="cell-main">{{ row.name }}</div>
          <div v-if="row.subtitle" class="cell-sub">{{ row.subtitle }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="建议零售价" width="110" align="right">
        <template #default="{ row }">¥{{ Number(row.suggestedPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column v-if="canViewCost" label="成本价" width="100" align="right">
        <template #default="{ row }">¥{{ Number(row.costPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '在售' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openSkuDialog(row)">SKU/库存</el-button>
          <el-button v-if="canEdit" type="primary" link @click="openProductDialog(row)">编辑</el-button>
          <el-button
            v-if="canEdit"
            :type="row.status === 1 ? 'danger' : 'success'"
            link
            @click="toggleProductStatus(row)"
          >
            {{ row.status === 1 ? '下架' : '上架' }}
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
        @change="loadProducts"
      />
    </div>

    <!-- 分类管理对话框 -->
    <el-dialog v-model="categoryDialogVisible" title="分类管理" width="520px">
      <div class="category-add">
        <el-input v-model="newCategory.name" placeholder="分类名" style="width: 180px" />
        <el-input-number v-model="newCategory.sort" :min="0" placeholder="排序" style="width: 120px" />
        <el-button type="primary" :loading="categorySaving" @click="createCategory">新增</el-button>
      </div>
      <el-table :data="categories" size="small" stripe>
        <el-table-column prop="name" label="分类名" min-width="120" />
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-if="canEdit"
              :type="row.status === 1 ? 'danger' : 'success'"
              link
              @click="toggleCategoryStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新建/编辑商品对话框 -->
    <ProductFormDialog
      v-model="productDialogVisible"
      :editing="editingProduct"
      :categories="categories"
      @saved="onProductSaved"
    />

    <!-- SKU/库存对话框 -->
    <SkuDialog v-model="skuDialogVisible" :product="skuProduct" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  createCategoryApi,
  listCategoriesApi,
  pageProductsApi,
  updateCategoryStatusApi,
  updateProductStatusApi,
} from '@/api/product'
import type { Product, ProductCategory } from '@/types/product'
import ProductFormDialog from './components/ProductFormDialog.vue'
import SkuDialog from './components/SkuDialog.vue'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('product:edit'))
const canViewCost = computed(() => auth.hasPermission('product:cost:view'))

const loading = ref(false)
const products = ref<Product[]>([])
const total = ref(0)
const categories = ref<ProductCategory[]>([])
const query = reactive({ keyword: '', categoryId: undefined as number | undefined, status: undefined as number | undefined, page: 1, size: 10 })

const categoryDialogVisible = ref(false)
const newCategory = reactive({ name: '', sort: 0 })
const categorySaving = ref(false)

const productDialogVisible = ref(false)
const editingProduct = ref<Product | null>(null)

const skuDialogVisible = ref(false)
const skuProduct = ref<Product | null>(null)

function categoryName(id: number) {
  return categories.value.find((c) => c.id === id)?.name || `#${id}`
}

async function loadCategories() {
  try {
    categories.value = await listCategoriesApi()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '分类加载失败')
  }
}

async function loadProducts() {
  loading.value = true
  try {
    const page = await pageProductsApi({ ...query, page: query.page, size: query.size })
    products.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '商品加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadProducts()
}

function openCategoryDialog() {
  categoryDialogVisible.value = true
}

async function createCategory() {
  if (!newCategory.name.trim()) {
    ElMessage.warning('请输入分类名')
    return
  }
  categorySaving.value = true
  try {
    await createCategoryApi({ name: newCategory.name.trim(), sort: newCategory.sort })
    ElMessage.success('分类已创建')
    newCategory.name = ''
    newCategory.sort = 0
    await loadCategories()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '创建失败')
  } finally {
    categorySaving.value = false
  }
}

async function toggleCategoryStatus(row: ProductCategory) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateCategoryStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

function openProductDialog(row?: Product) {
  editingProduct.value = row ?? null
  productDialogVisible.value = true
}

function onProductSaved() {
  loadProducts()
  loadCategories()
}

async function toggleProductStatus(row: Product) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateProductStatusApi(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '商品已上架' : '商品已下架')
    if (next === 1) {
      await ElMessageBox.alert('目录变更已同步到门店，本店上架待复核后生效', '提示', { type: 'info' }).catch(() => null)
    }
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '操作失败')
  }
}

function openSkuDialog(row: Product) {
  skuProduct.value = row
  skuDialogVisible.value = true
}

onMounted(() => {
  loadCategories()
  loadProducts()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.category-add {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
</style>
