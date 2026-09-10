<template>
  <div class="notification-page">
    <div class="page-header">
      <h2>通知中心</h2>
      <div class="page-actions">
        <el-radio-group v-model="filter" @change="onFilterChange">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="true">未读</el-radio-button>
          <el-radio-button :value="false">已读</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="bizType"
          placeholder="业务类型"
          clearable
          style="width: 140px"
          @change="onFilterChange"
        >
          <el-option label="订单" value="order" />
          <el-option label="退款" value="refund" />
          <el-option label="结算" value="settlement" />
          <el-option label="系统通知" value="announcement" />
        </el-select>
        <el-button
          v-if="store.unreadCount > 0"
          type="primary"
          plain
          :icon="Check"
          @click="onMarkAll"
        >
          全部已读
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="store.loading"
      :data="store.list"
      stripe
      style="width: 100%"
      empty-text="暂无通知"
      @row-click="onRowClick"
    >
      <el-table-column label="标题" min-width="180">
        <template #default="{ row }">
          <span :class="{ unread: row.isRead === 0 }">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
      <el-table-column label="业务类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="bizTypeTag(row.bizType)">{{ bizTypeLabel(row.bizType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.isRead === 0" link type="primary" @click.stop="onMark(row)">
            标为已读
          </el-button>
          <el-button link type="danger" @click.stop="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/types/notification'

const store = useNotificationStore()
const router = useRouter()
const filter = ref<boolean | undefined>(undefined)
const bizType = ref<string>('')
const page = ref(1)
const size = ref(20)

onMounted(() => {
  store.refreshSummary()
  load()
})

function load() {
  store.loadPage({
    page: page.value,
    size: size.value,
    unreadOnly: filter.value === true ? true : filter.value === false ? false : undefined,
    bizType: bizType.value || undefined,
  })
}

function onFilterChange() {
  page.value = 1
  load()
}

function onPageChange() { load() }
function onSizeChange() {
  page.value = 1
  load()
}

async function onRowClick(row: Notification) {
  if (row.isRead === 0) await store.markRead(row.id)
  if (row.link) {
    const path = row.link.split('?')[0]
    const query: Record<string, string> = {}
    if (row.link.includes('?')) {
      const params = new URLSearchParams(row.link.split('?')[1])
      params.forEach((v, k) => (query[k] = v))
    }
    router.push({ path, query })
  }
}

async function onMark(row: Notification) {
  await store.markRead(row.id)
}

async function onMarkAll() {
  await store.markAllRead()
  load()
}

async function onDelete(row: Notification) {
  await ElMessageBox.confirm(`确认删除通知 "${row.title}"？`, '提示', { type: 'warning' }).catch(() => null)
  await store.remove(row.id)
}

function bizTypeLabel(t: string) {
  return { order: '订单', refund: '退款', settlement: '结算', announcement: '公告', inventory: '库存' }[t] ?? t
}

function bizTypeTag(t: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  return ({ order: 'primary', refund: 'danger', settlement: 'success', announcement: 'warning' } as const)[t] ?? 'info'
}

function formatTime(s: string): string {
  if (!s) return ''
  const d = new Date(s.replace(' ', 'T'))
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.notification-page {
  background: #fff;
  border-radius: 6px;
  padding: 16px 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.page-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.unread {
  font-weight: 600;
  color: #303133;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>