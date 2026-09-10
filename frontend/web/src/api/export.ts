import { http } from '@/utils/request'
import type { ExportRequest, ExportTask } from '@/types/export'

/** 创建导出任务（需 export:run 权限，仅超管） */
export const createExportTaskApi = (req: ExportRequest) =>
  http.post<ExportTask>('/export/tasks', req)

/** 我的导出任务列表（本人隔离） */
export const pageExportTasksApi = (params?: { status?: number; page?: number; size?: number }) =>
  http.get<{ records: ExportTask[]; total: number; page: number; size: number }>('/export/tasks', { params })

/** 删除任务（本人） */
export const deleteExportTaskApi = (id: number) =>
  http.delete<void>(`/export/tasks/${id}`)

/** 触发浏览器下载（直接打开下载链接） */
export const getDownloadUrl = (id: number) =>
  `/api/export/tasks/${id}/download`

/** 浏览器直接下载（window.open 或 a[download] 触发；非 SPA 直走超管权限，401 自动 retry 已带） */
export const triggerDownload = (id: number) => {
  const url = getDownloadUrl(id)
  const a = document.createElement('a')
  a.href = url
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}
