/**
 * 退款售后（v20，B 端管理面；menu:refund 菜单 + refund:submit/approve + return:inspect 按钮）
 * 后端契约：RefundController（/api/refund，module.payment）
 */

/** 退款单分页行（GET /api/refund/admin/page，JOIN orders/stores/users/return_orders 联查） */
export interface RefundItem {
  id: number
  /** 退款单号 */
  refundNo: string
  /** 关联订单 id */
  orderId: number
  orderNo: string
  storeId: number
  storeName: string | null
  userName: string | null
  userPhone: string | null
  /** 退款金额 */
  amount: string
  reason: string | null
  /** 退款分支：1未发货直退/2在途拦截/3已签收退货 */
  refundBranch: number
  refundBranchDesc: string
  /** 状态：10待审批/20审批通过/30退款中/40已退款/50已驳回/95回退失败 */
  status: number
  statusDesc: string
  escalationStatus: number
  approvedByLevel: number | null
  approvedBy: number | null
  approvedAt: string | null
  handledAt: string | null
  createdAt: string
  /** 退货单状态（branch=3 时有值） */
  returnStatus: number | null
  returnStatusDesc: string | null
  warehouseStatus: number | null
  warehouseStatusDesc: string | null
  returnTrackingNo: string | null
}

/** 退款单详情（GET /api/refund/admin/{id}，退款单 + 订单头 + 退货单） */
export interface RefundDetail {
  id: number
  refundNo: string
  orderId: number
  orderNo: string
  storeId: number
  storeName: string | null
  userName: string | null
  userPhone: string | null
  amount: string
  reason: string | null
  refundBranch: number
  refundBranchDesc: string
  status: number
  statusDesc: string
  escalationStatus: number
  approvedByLevel: number | null
  approvedBy: number | null
  approvedAt: string | null
  rejectReason: string | null
  rejectedAt: string | null
  handledAt: string | null
  createdAt: string
  // 订单头
  payAmount: string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  paidAt: string | null
  orderWarehouseStatus: number
  orderWarehouseStatusDesc: string
  // 退货单（branch=3 时有值）
  returnId: number | null
  returnStatus: number | null
  returnStatusDesc: string | null
  returnAddress: string | null
  returnTrackingNo: string | null
  returnCarrier: string | null
  warehouseStatus: number | null
  warehouseStatusDesc: string | null
  inspectionResult: string | null
  inspectedBy: number | null
  receivedBy: number | null
  receivedAt: string | null
}

/** 退款单分页查询 */
export type RefundPageQuery = {
  refundNo?: string
  orderNo?: string
  storeId?: number
  status?: number
  refundBranch?: number
  page: number
  size: number
}

/** 退款申请（refund:submit） */
export interface RefundApplyPayload {
  orderId: number
  reason?: string
}

/** 退款驳回（refund:approve） */
export interface RejectPayload {
  reason?: string
}

/** 退货验货（return:inspect；结论枚举见 REFUND_INSPECT_RESULTS） */
export interface InspectPayload {
  result: string
  /** 破损部分退款金额（结论=破损部分退款时填写） */
  refundAmount?: number
}

/** 退款状态映射 */
export const REFUND_STATUS: Record<number, string> = {
  10: '待审批',
  20: '审批通过',
  30: '退款中',
  40: '已退款',
  50: '已驳回',
  95: '回退失败-待人工',
}

/** 退款状态 tag 类型（中式语义：成功绿、进行中蓝、待办橙、失败红） */
export const REFUND_STATUS_TAG: Record<number, 'warning' | 'primary' | 'info' | 'success' | 'danger'> = {
  10: 'warning',
  20: 'primary',
  30: 'info',
  40: 'success',
  50: 'danger',
  95: 'danger',
}

/** 退款分支映射 */
export const REFUND_BRANCH: Record<number, string> = {
  1: '未发货直退',
  2: '在途拦截',
  3: '已签收退货',
}

/** 退货单状态映射 */
export const RETURN_STATUS: Record<number, string> = {
  0: '申请中',
  1: '待寄回',
  2: '在途',
  3: '待验货',
  4: '已完结',
  5: '已取消',
}

/** 总部收货状态映射 */
export const RETURN_WAREHOUSE_STATUS: Record<number, string> = {
  1: '待收货',
  2: '已收货',
  3: '验货通过',
  4: '验货不通过',
}

/** 验货结论选项 */
export const REFUND_INSPECT_RESULTS = ['完好退全款', '破损部分退款', '非质量问题拒退'] as const
