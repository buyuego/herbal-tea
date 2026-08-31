<template>
  <el-dialog
    :model-value="modelValue"
    :title="editing ? '编辑商品' : '新建商品'"
    width="680px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 240px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="商品名" prop="name">
        <el-input v-model="form.name" placeholder="商品名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="副标题">
        <el-input v-model="form.subtitle" placeholder="一句话卖点（可空）" maxlength="200" />
      </el-form-item>
      <el-form-item label="配方">
        <el-input v-model="form.formula" placeholder="配方说明（可空）" maxlength="500" />
      </el-form-item>
      <el-form-item label="主图 URL" prop="mainImage">
        <el-input v-model="form.mainImage" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="轮播图">
        <div class="images-row">
          <el-input
            v-for="(_, i) in form.images"
            :key="i"
            v-model="form.images[i]"
            placeholder="https://..."
            style="margin-bottom: 6px"
          >
            <template #append>
              <el-button link type="danger" @click="form.images.splice(i, 1)">删</el-button>
            </template>
          </el-input>
          <el-button size="small" @click="form.images.push('')">+ 添加轮播图</el-button>
        </div>
      </el-form-item>
      <el-form-item label="建议零售价" prop="suggestedPrice">
        <el-input-number v-model="form.suggestedPrice" :min="0.01" :precision="2" :step="1" />
      </el-form-item>
      <el-form-item v-if="canViewCost" label="成本价" prop="costPrice">
        <el-input-number v-model="form.costPrice" :min="0" :precision="2" :step="0.5" />
      </el-form-item>

      <el-divider content-position="left">SKU（规格 / 价格 / 库存）</el-divider>
      <el-table v-if="!editing" :data="form.skus" size="small" border>
        <el-table-column label="SKU 编码" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.skuCode" placeholder="如 HT-001-500ml" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="规格 JSON" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.specsText" placeholder='{"规格":"500ml"}' size="small" />
          </template>
        </el-table-column>
        <el-table-column label="售价" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.price" :min="0.01" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column v-if="canViewCost" label="成本" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.costPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="库存" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.stock" :min="0" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="" width="50">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.skus.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="!editing" size="small" style="margin-top: 8px" @click="addSkuRow">+ 添加 SKU</el-button>
      <el-alert v-else type="info" :closable="false" show-icon>
        SKU 在「SKU/库存」入口维护；此处编辑不影响已有 SKU。
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { createProductApi, updateCatalogApi } from '@/api/product'
import type { Product, ProductCategory } from '@/types/product'

const props = defineProps<{
  modelValue: boolean
  editing: Product | null
  categories: ProductCategory[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const auth = useAuthStore()
const canViewCost = computed(() => auth.hasPermission('product:cost:view'))

interface SkuRow {
  skuCode: string
  specsText: string
  price: number
  costPrice: number
  stock: number
}

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<{
  categoryId: number | undefined
  name: string
  subtitle: string
  formula: string
  mainImage: string
  images: string[]
  suggestedPrice: number
  costPrice: number
  skus: SkuRow[]
}>({
  categoryId: undefined,
  name: '',
  subtitle: '',
  formula: '',
  mainImage: '',
  images: [],
  suggestedPrice: 0,
  costPrice: 0,
  skus: [],
})

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入商品名', trigger: 'blur' }],
  mainImage: [{ required: true, message: '请输入主图 URL', trigger: 'blur' }],
  suggestedPrice: [{ required: true, message: '请输入建议零售价', trigger: 'blur' }],
}

function addSkuRow() {
  form.skus.push({ skuCode: '', specsText: '', price: 0, costPrice: 0, stock: 0 })
}

function onOpen() {
  const e = props.editing
  if (e) {
    form.categoryId = e.categoryId
    form.name = e.name
    form.subtitle = e.subtitle ?? ''
    form.formula = e.formula ?? ''
    form.mainImage = e.mainImage
    form.images = e.images ? JSON.parse(e.images) : []
    form.suggestedPrice = Number(e.suggestedPrice)
    form.costPrice = Number(e.costPrice)
    form.skus = []
  } else {
    form.categoryId = undefined
    form.name = ''
    form.subtitle = ''
    form.formula = ''
    form.mainImage = ''
    form.images = []
    form.suggestedPrice = 0
    form.costPrice = 0
    form.skus = []
    addSkuRow()
  }
}

function parseSpecs(text: string): Record<string, unknown> | undefined {
  if (!text.trim()) return undefined
  try {
    const v = JSON.parse(text)
    return typeof v === 'object' && v !== null ? v : undefined
  } catch {
    return undefined
  }
}

async function onSubmit() {
  await formRef.value?.validate().catch(() => null)
  if (!formRef.value) return
  try {
    const payload: Record<string, unknown> = {
      categoryId: form.categoryId,
      name: form.name,
      subtitle: form.subtitle || null,
      formula: form.formula || null,
      mainImage: form.mainImage,
      images: form.images.filter((x) => x.trim()).length ? form.images.filter((x) => x.trim()) : null,
      suggestedPrice: form.suggestedPrice,
      costPrice: form.costPrice,
    }
    saving.value = true
    if (props.editing) {
      await updateCatalogApi(props.editing.id, payload)
      ElMessage.success('商品已更新（本店上架待复核）')
    } else {
      // 校验 SKU
      for (const [i, s] of form.skus.entries()) {
        if (!s.skuCode.trim()) throw new BizError(40000, `第 ${i + 1} 行 SKU 编码不能为空`)
        if (s.price <= 0) throw new BizError(40000, `第 ${i + 1} 行 SKU 售价须大于 0`)
        if (canViewCost.value && s.costPrice < 0) throw new BizError(40000, `第 ${i + 1} 行 SKU 成本不能为负`)
        if (s.stock < 0) throw new BizError(40000, `第 ${i + 1} 行 SKU 库存不能为负`)
        const specs = parseSpecs(s.specsText)
        if (s.specsText.trim() && !specs) throw new BizError(40000, `第 ${i + 1} 行规格 JSON 格式不正确`)
      }
      payload.skus = form.skus.map((s) => ({
        skuCode: s.skuCode.trim(),
        specs: parseSpecs(s.specsText) ?? null,
        price: s.price,
        costPrice: s.costPrice,
        stock: s.stock,
      }))
      await createProductApi(payload)
      ElMessage.success('商品已创建')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.images-row {
  display: flex;
  flex-direction: column;
  width: 100%;
}
</style>
