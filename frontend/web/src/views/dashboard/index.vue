<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <!-- 当前门店卡片 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#409EFF"><Shop /></el-icon>
              <span>当前门店</span>
            </div>
          </template>
          <template v-if="auth.currentStore">
            <div class="store-name">{{ auth.currentStore.storeName }}</div>
            <div class="store-no">{{ auth.currentStore.storeNo }}</div>
            <el-tag v-if="auth.currentStore.isOwner === 1" type="success">店主</el-tag>
            <el-tag v-else type="info">员工</el-tag>
            <p class="tip">通过顶栏门店切换可切换当前上下文（切店状态跨刷新保持）</p>
          </template>
          <el-empty v-else description="总部账号 · 无绑定门店" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 绑定门店卡片 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#67C23A"><OfficeBuilding /></el-icon>
              <span>绑定门店（{{ auth.stores.length }}）</span>
            </div>
          </template>
          <div v-for="s in auth.stores" :key="s.storeId" class="store-row">
            <span>{{ s.storeName }}</span>
            <span class="row-right">
              <el-tag v-if="s.isOwner === 1" size="small" type="success">店主</el-tag>
              <el-tag v-if="s.current" size="small" type="primary">当前</el-tag>
            </span>
          </div>
          <el-empty v-if="auth.stores.length === 0" description="总部账号无绑定门店" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 账号信息卡片 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">
              <el-icon color="#E6A23C"><User /></el-icon>
              <span>账号信息</span>
            </div>
          </template>
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label="账号">
              {{ auth.profile?.username }}
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ auth.profile?.realName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag size="small">{{ auth.profile?.roleName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="权限数">
              {{ auth.profile?.permissionCodes.length ?? 0 }} 项
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.store-name {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.store-no {
  color: #909399;
  font-size: 13px;
  margin: 4px 0 8px;
}
.tip {
  color: #909399;
  font-size: 12px;
  margin-top: 12px;
}
.store-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}
.store-row:last-child {
  border-bottom: none;
}
.row-right {
  display: flex;
  gap: 6px;
}
</style>
