<template>
  <el-popover
    :width="380"
    placement="bottom-end"
    trigger="click"
    v-model:visible="visible"
  >
    <template #reference>
      <el-badge
        :value="store.unreadCount"
        :hidden="store.unreadCount === 0"
        :max="99"
      >
        <el-button text :icon="Bell" class="bell-btn" />
      </el-badge>
    </template>

    <div class="bell-header">
      <span>消息通知</span>
      <div class="bell-actions">
        <el-link v-if="store.unreadCount > 0" type="primary" :underline="false" @click="onMarkAll">
          全部已读
        </el-link>
        <el-link type="primary" :underline="false" @click="goAll">查看全部</el-link>
      </div>
    </div>

    <div class="bell-list">
      <div v-if="store.recent.length === 0" class="bell-empty">暂无通知</div>
      <div
        v-for="n in store.recent"
        :key="n.id"
        class="bell-item"
        :class="{ unread: n.isRead === 0 }"
        @click="onItemClick(n)"
      >
        <div class="bell-item-title">{{ n.title }}</div>
        <div class="bell-item-content">{{ n.content }}</div>
        <div class="bell-item-time">{{ formatTime(n.createdAt) }}</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/types/notification'

const store = useNotificationStore()
const router = useRouter()
const visible = ref(false)
let timer: number | null = null

onMounted(() => {
  store.refreshSummary()
  // 每 30 秒轮询一次
  timer = window.setInterval(() => store.refreshSummary(), 30_000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

async function onItemClick(n: Notification) {
  visible.value = false
  if (n.isRead === 0) {
    await store.markRead(n.id)
  }
  // 跳转（业务链接以 / 开头，如 /order?keyword=xxx）
  if (n.link) {
    const path = n.link.split('?')[0]
    const query: Record<string, string> = {}
    if (n.link.includes('?')) {
      const params = new URLSearchParams(n.link.split('?')[1])
      params.forEach((v, k) => (query[k] = v))
    }
    router.push({ path, query })
  }
}

async function onMarkAll() {
  await store.markAllRead()
}

function goAll() {
  visible.value = false
  router.push('/notification')
}

function formatTime(s: string): string {
  if (!s) return ''
  const d = new Date(s.replace(' ', 'T'))
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.bell-btn {
  font-size: 18px;
  color: #303133;
}
.bell-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  font-size: 14px;
}
.bell-actions {
  display: flex;
  gap: 12px;
  font-weight: normal;
  font-size: 12px;
}
.bell-list {
  max-height: 380px;
  overflow-y: auto;
}
.bell-empty {
  padding: 30px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.bell-item {
  padding: 10px 8px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s;
}
.bell-item:hover {
  background: #f5f7fa;
}
.bell-item.unread {
  background: #ecf5ff;
}
.bell-item-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  margin-bottom: 4px;
}
.bell-item-content {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  margin-bottom: 4px;
}
.bell-item-time {
  font-size: 11px;
  color: #909399;
}
</style>