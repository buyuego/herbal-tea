<template>
  <div class="stores-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span class="page-title">我的门店</span>
          <el-button
            v-if="!auth.currentStore && auth.stores.length === 0"
            type="info"
            size="small"
            disabled
          >
            总部账号 · 无绑定门店
          </el-button>
        </div>
      </template>

      <el-table :data="auth.stores" stripe>
        <el-table-column prop="storeNo" label="门店编号" width="110" />
        <el-table-column prop="storeName" label="门店名称" min-width="160" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isOwner === 1" type="success" size="small">店主</el-tag>
            <el-tag v-else type="info" size="small">员工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.current" type="primary" size="small">当前门店</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="!row.current"
              type="primary"
              link
              :loading="switching === row.storeId"
              @click="onSwitch(row.storeId)"
            >
              切换
            </el-button>
            <span v-else class="current-tip">已就绪</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无绑定门店（总部账号不参与门店业务）" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'

const auth = useAuthStore()
const switching = ref<number | null>(null)

async function onSwitch(storeId: number) {
  switching.value = storeId
  try {
    await auth.switchStore(storeId)
    const store = auth.stores.find((s) => s.storeId === storeId)
    ElMessage.success(`已切换到「${store?.storeName || storeId}」`)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '切换门店失败')
  } finally {
    switching.value = null
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.page-title {
  font-weight: 600;
}
.current-tip {
  color: #909399;
  font-size: 13px;
}
</style>
