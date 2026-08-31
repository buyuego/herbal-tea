<template>
  <el-dropdown v-if="auth.stores.length > 0" trigger="click" @command="onSwitch">
    <span class="store-chip">
      <el-icon color="#409EFF"><Shop /></el-icon>
      <span class="store-name">{{ auth.currentStore?.storeName || '未选择门店' }}</span>
      <el-tag v-if="auth.currentStore" size="small" type="warning" effect="light">
        {{ auth.currentStore.storeNo }}
      </el-tag>
      <el-icon><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="s in auth.stores"
          :key="s.storeId"
          :command="s.storeId"
          :disabled="s.current"
        >
          <span class="store-item">
            <span>{{ s.storeName }}</span>
            <span class="store-meta">
              {{ s.storeNo }}
              <el-tag v-if="s.isOwner === 1" size="small" type="success" effect="plain">店主</el-tag>
              <el-tag v-if="s.current" size="small" type="primary" effect="plain">当前</el-tag>
            </span>
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ArrowDown, Shop } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'

const auth = useAuthStore()

async function onSwitch(storeId: number) {
  try {
    await auth.switchStore(storeId)
    const store = auth.stores.find((s) => s.storeId === storeId)
    ElMessage.success(`已切换到「${store?.storeName || storeId}」`)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '切换门店失败')
  }
}
</script>

<style scoped>
.store-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
  padding: 4px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
}
.store-name {
  font-size: 13px;
  color: #303133;
}
.store-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.store-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
