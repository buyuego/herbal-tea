import { http } from '@/utils/request'
import type { StoreBinding, TokenPair } from '@/types/api'

/** 我的门店列表（MULTI_STORE：current 标记当前上下文门店） */
export const myStoresApi = () => http.get<StoreBinding[]>('/store/my-stores')

/** 切换当前门店（实时查库校验，返回新双令牌：sid=目标店） */
export const switchStoreApi = (storeId: number) =>
  http.post<TokenPair>('/store/switch-store', { storeId })
