import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type { MemberDetail, MemberQuery, MemberVO } from '@/types/member'

/** 会员分页（menu:member，手机号脱敏） */
export const pageMembersApi = (query: MemberQuery) =>
  http.get<PageResult<MemberVO>>('/user/admin/members', { params: query })

/** 会员详情（概览 + 地址 + 最近积分流水） */
export const getMemberDetailApi = (id: number) => http.get<MemberDetail>(`/user/admin/members/${id}`)

/** 会员启停（member:edit，敏感仅超管） */
export const updateMemberStatusApi = (id: number, status: number) =>
  http.put<void>(`/user/admin/members/${id}/status`, undefined, { params: { status } })

export type { MemberDetail, MemberQuery, MemberVO }
