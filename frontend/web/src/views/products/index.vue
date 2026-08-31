<template>
  <div class="products-page">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="商品目录" name="catalog">
          <CatalogView />
        </el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('product:edit') && auth.currentStore" label="本店上架" name="listings">
          <ListingsView />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import CatalogView from './CatalogView.vue'
import ListingsView from './ListingsView.vue'

const auth = useAuthStore()
const activeTab = ref('catalog')
</script>

<style scoped>
.products-page :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
</style>
