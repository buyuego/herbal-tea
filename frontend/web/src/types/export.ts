/** 导出任务（v33） */
export interface ExportTask {
  id: number
  taskNo: string
  bizType: 'product' | 'order' | 'member' | 'settlement'
  operatorName: string
  status: 0 | 10 | 20 | 30
  rowCount: number
  fileSize: number
  downloadable: boolean
  errorMsg?: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
}

/** 触发导出请求 */
export interface ExportRequest {
  bizType: 'product' | 'order' | 'member' | 'settlement'
  filter?: {
    name?: string
    categoryId?: number
    online?: boolean
    orderNo?: string
    status?: number
    storeId?: number
    dateFrom?: string
    dateTo?: string
    phone?: string
    nickname?: string
    settleNo?: string
    period?: string
  }
}

export const EXPORT_STATUS_TEXT: Record<number, string> = {
  0: '待处理',
  10: '处理中',
  20: '成功',
  30: '失败',
}
