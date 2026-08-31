<template>
  <el-container class="layout">
    <el-aside width="210px" class="layout-aside">
      <div class="logo">
        <el-icon :size="22" color="#409EFF"><Cup /></el-icon>
        <span>养生茶后台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#ffffff"
        class="layout-menu"
      >
        <el-menu-item v-for="item in menuRoutes" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <StoreSwitcher />
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="user-chip">
              <el-avatar :size="30" class="user-avatar">
                {{ (auth.profile?.realName || auth.profile?.username || '?').slice(0, 1) }}
              </el-avatar>
              <span class="user-name">{{ auth.profile?.realName || auth.profile?.username }}</span>
              <el-tag size="small" type="info" effect="plain">{{ auth.profile?.roleName }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import StoreSwitcher from '@/components/StoreSwitcher.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 侧边栏菜单：按当前用户权限码过滤静态路由表（meta.perm） */
const menuRoutes = computed(() => {
  const layout = router.options.routes.find((r) => r.path === '/')
  return (layout?.children ?? [])
    .filter((c) => c.meta?.title)
    .filter((c) => !c.meta?.perm || auth.hasPermission(c.meta.perm as string))
    .map((c) => ({
      path: `/${c.path}`,
      title: c.meta?.title as string,
      icon: (c.meta?.icon as string) || 'Menu',
    }))
})

const activeMenu = computed(() => route.path)

async function onUserCommand(cmd: string) {
  if (cmd !== 'logout') return
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).catch(() => null)
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.layout-aside {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.layout-menu {
  border-right: none;
  flex: 1;
}
.layout-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-avatar {
  background-color: #409eff;
  color: #fff;
  font-size: 14px;
}
.user-name {
  font-size: 14px;
  color: #303133;
}
.layout-main {
  background: #f5f7fa;
  padding: 16px;
  overflow: auto;
}
</style>
