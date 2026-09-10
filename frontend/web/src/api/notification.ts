import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type {
  Notification,
  NotificationPageQuery,
  NotificationSummary,
  BroadcastRequest,
} from '@/types/notification'

/** 铃铛摘要：未读数 + 最近 10 条（menu:notification） */
export const summaryApi = () =>
  http.get<NotificationSummary>('/notification/summary')

/** 通知页全量分页（menu:notification） */
export const pageNotificationsApi = (query: NotificationPageQuery) =>
  http.get<PageResult<Notification>>('/notification/page', { params: query })

/** 标记单条已读 */
export const markReadApi = (id: number) =>
  http.put<void>(`/notification/${id}/read`)

/** 标记全部已读 */
export const markAllReadApi = () =>
  http.put<number>('/notification/read-all')

/** 删除单条 */
export const deleteNotificationApi = (id: number) =>
  http.delete<void>(`/notification/${id}`)

/** 管理员广播（notification:manage，仅超管） */
export const broadcastApi = (req: BroadcastRequest) =>
  http.post<number>('/notification/broadcast', req)

export type {
  Notification,
  NotificationPageQuery,
  NotificationSummary,
  BroadcastRequest,
}