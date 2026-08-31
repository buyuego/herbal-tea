<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="昵称 / 手机号 / openid"
          clearable
          style="width: 220px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.status" placeholder="会员状态" clearable style="width: 130px" @change="onSearch">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" plain @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="toolbar-right">
          <el-button :icon="RefreshIcon" @click="loadMembers">刷新</el-button>
        </div>
      </div>

      <!-- 会员表格 -->
      <el-table :data="members" v-loading="loading" stripe>
        <el-table-column label="会员" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.nickname || '（未设置昵称）' }}</div>
            <div class="cell-sub">{{ maskOpenid(row.openid) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="积分余额" width="100" align="right">
          <template #default="{ row }">
            <span class="points-main">{{ row.pointsBalance }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计获得" width="100" align="right">
          <template #default="{ row }">{{ row.totalEarned }}</template>
        </el-table-column>
        <el-table-column label="有效订单" width="100" align="center">
          <template #default="{ row }">{{ row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="累计消费" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.payTotalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="最近下单" width="165">
          <template #default="{ row }">{{ formatTime(row.lastOrderAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="MEMBER_STATUS_TAG[row.status] || 'info'" size="small" effect="plain">
              {{ MEMBER_STATUS[row.status] || `#${row.status}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="canEdit"
              :type="row.status === 1 ? 'danger' : 'success'"
              link
              @click="onToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="onSearch"
          @current-change="loadMembers"
        />
      </div>
    </el-card>

    <!-- 会员详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="55%">
      <div v-if="detail" class="detail-body">
        <el-descriptions :column="2" border size="small" class="section">
          <el-descriptions-item label="会员 ID">{{ detail.member.id }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detail.member.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.member.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="MEMBER_STATUS_TAG[detail.member.status] || 'info'" size="small" effect="plain">
              {{ MEMBER_STATUS[detail.member.status] || `#${detail.member.status}` }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="积分余额">
            <span class="points-main">{{ detail.member.pointsBalance }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="累计获得 / 使用">
            {{ detail.member.totalEarned }} / {{ detail.member.totalUsed }}
          </el-descriptions-item>
          <el-descriptions-item label="有效订单">{{ detail.member.orderCount }} 单</el-descriptions-item>
          <el-descriptions-item label="累计消费">
            ¥{{ Number(detail.member.payTotalAmount).toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="最近下单">{{ formatTime(detail.member.lastOrderAt) }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatTime(detail.member.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="openid" :span="2">
            <span class="cell-sub">{{ detail.member.openid }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title mt-16">收货地址（{{ detail.addresses.length }}）</div>
        <el-table v-if="detail.addresses.length" :data="detail.addresses" size="small" stripe>
          <el-table-column label="收货人" width="100">
            <template #default="{ row }">
              {{ row.receiverName }}
              <el-tag v-if="row.isDefault === 1" type="warning" size="small" effect="plain">默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="联系电话" width="130">
            <template #default="{ row }">{{ row.phone }}</template>
          </el-table-column>
          <el-table-column label="收货地址" min-width="220">
            <template #default="{ row }">
              {{ row.province }}{{ row.city }}{{ row.district }} {{ row.detail }}
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-tip">暂无收货地址</div>

        <div class="section-title mt-16">最近积分流水（{{ detail.pointRecords.length }}）</div>
        <el-table v-if="detail.pointRecords.length" :data="detail.pointRecords" size="small" stripe>
          <el-table-column label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.points >= 0 ? 'success' : 'info'" size="small">
                {{ row.changeTypeDesc }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变动" width="90" align="center">
            <template #default="{ row }">
              <span :class="row.points >= 0 ? 'stock-in' : 'stock-out'">
                {{ row.points >= 0 ? '+' : '' }}{{ row.points }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="110" align="center">
            <template #default="{ row }">{{ row.sourceTypeDesc }}</template>
          </el-table-column>
          <el-table-column label="归属门店" width="130">
            <template #default="{ row }">{{ row.storeName || '平台' }}</template>
          </el-table-column>
          <el-table-column label="关联订单" min-width="160">
            <template #default="{ row }">{{ row.orderNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-tip">暂无积分流水</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh as RefreshIcon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { getMemberDetailApi, pageMembersApi, updateMemberStatusApi } from '@/api/member'
import { MEMBER_STATUS, MEMBER_STATUS_TAG } from '@/types/member'
import type { MemberDetail, MemberVO } from '@/types/member'

const auth = useAuthStore()
/** 会员启停为敏感操作（220 仅超管） */
const canEdit = computed(() => auth.hasPermission('member:edit'))

const loading = ref(false)
const members = ref<MemberVO[]>([])
const total = ref(0)

const query = reactive<{ keyword?: string; status?: number; page: number; size: number }>({
  keyword: '',
  status: undefined,
  page: 1,
  size: 10,
})

async function loadMembers() {
  loading.value = true
  try {
    const page = await pageMembersApi({
      keyword: query.keyword || undefined,
      status: query.status,
      page: query.page,
      size: query.size,
    })
    members.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '会员列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadMembers()
}

function onReset() {
  query.keyword = ''
  query.status = undefined
  onSearch()
}

// ==================== 启停 ====================

async function onToggleStatus(row: MemberVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `禁用后该会员将无法登录，其已签发的令牌会立即失效。确认禁用「${row.nickname || '#' + row.id}」？`
        : `确认启用「${row.nickname || '#' + row.id}」？`,
      `${action}会员`,
      { type: next === 0 ? 'warning' : 'success', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await updateMemberStatusApi(row.id, next)
    ElMessage.success(`已${action}`)
    await loadMembers()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : `${action}失败`)
  }
}

// ==================== 详情 ====================

const detailVisible = ref(false)
const detail = ref<MemberDetail | null>(null)
const detailTitle = ref('会员详情')

async function openDetail(id: number) {
  try {
    detail.value = await getMemberDetailApi(id)
    detailTitle.value = `会员详情 · ${detail.value.member.nickname || '#' + id}`
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '会员详情加载失败')
  }
}

// ==================== 工具 ====================

/** openid 仅展示前 10 位，完整值在详情抽屉 */
function maskOpenid(openid: string): string {
  if (!openid) return '-'
  return openid.length > 10 ? `${openid.slice(0, 10)}…` : openid
}

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

onMounted(loadMembers)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-right {
  margin-left: auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  font-size: 12px;
  color: #909399;
}
.points-main {
  font-weight: 600;
  color: #e6a23c;
}
.stock-in {
  color: #67c23a;
  font-weight: 600;
}
.stock-out {
  color: #f56c6c;
  font-weight: 600;
}
.mt-16 {
  margin-top: 16px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.detail-body {
  padding-bottom: 8px;
}
.empty-tip {
  padding: 12px 0;
  font-size: 13px;
  color: #909399;
}
</style>
