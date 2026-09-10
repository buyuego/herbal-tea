/** 站内通知类型（v32） */

export interface Notification {
  id: number
  bizType: string
  bizId: string
  title: string
  content: string
  link?: string
  isRead: number
  readAt?: string
  createdAt: string
  storeId?: number
}

export interface NotificationSummary {
  unreadCount: number
  recent: Notification[]
}

export type NotificationPageQuery = {
  page?: number
  size?: number
  unreadOnly?: boolean
  bizType?: string
}

export type BroadcastRequest = {
  targetRole?: number
  targetStoreId?: number
  title: string
  content: string
  link?: string
  bizType?: string
}