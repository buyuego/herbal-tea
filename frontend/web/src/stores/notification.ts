import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  summaryApi,
  pageNotificationsApi,
  markReadApi,
  markAllReadApi,
  deleteNotificationApi,
} from '@/api/notification'
import type { Notification, NotificationPageQuery } from '@/types/notification'

/** 通知 Store（v32）：顶部铃铛 + 通知页数据源 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const recent = ref<Notification[]>([])
  const list = ref<Notification[]>([])
  const total = ref(0)
  const loading = ref(false)

  /** 刷新铃铛摘要（每 30s 轮询调用） */
  async function refreshSummary() {
    try {
      const r = await summaryApi()
      if (r) {
        unreadCount.value = r.unreadCount
        recent.value = r.recent
      }
    } catch {
      // 静默：轮询不报错
    }
  }

  /** 加载通知页列表 */
  async function loadPage(query: NotificationPageQuery) {
    loading.value = true
    try {
      const r = await pageNotificationsApi(query)
      if (r) {
        list.value = r.records
        total.value = r.total
      }
    } finally {
      loading.value = false
    }
  }

  /** 标记单条已读（更新本地缓存 + 后端） */
  async function markRead(id: number) {
    await markReadApi(id)
    const item = recent.value.find((n) => n.id === id)
    if (item && item.isRead === 0) {
      item.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    const listItem = list.value.find((n) => n.id === id)
    if (listItem && listItem.isRead === 0) {
      listItem.isRead = 1
    }
  }

  /** 标记全部已读 */
  async function markAllRead() {
    const cnt = await markAllReadApi()
    unreadCount.value = 0
    for (const n of recent.value) n.isRead = 1
    for (const n of list.value) n.isRead = 1
    return cnt
  }

  /** 删除单条 */
  async function remove(id: number) {
    await deleteNotificationApi(id)
    const wasUnread = recent.value.find((n) => n.id === id)?.isRead === 0
    recent.value = recent.value.filter((n) => n.id !== id)
    list.value = list.value.filter((n) => n.id !== id)
    if (wasUnread) unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  return {
    unreadCount,
    recent,
    list,
    total,
    loading,
    refreshSummary,
    loadPage,
    markRead,
    markAllRead,
    remove,
  }
})