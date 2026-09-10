<template>
  <div class="page">
    <div class="filter-bar">
      <el-radio-group v-model="filterStatus" @change="onSearch">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="20">成功</el-radio-button>
        <el-radio-button :value="10">处理中</el-radio-button>
        <el-radio-button :value="30">失败</el-radio-button>
      </el-radio-group>
      <el-button style="margin-left: 12px" @click="onSearch" :icon="Refresh">刷新</el-button>
    </div>

    <el-card class="trigger-card">
      <template #header>
        <div class="card-header">
          <span>触发导出（仅超管 export:run）</span>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="业务类型">
          <el-select v-model="trigger.bizType" placeholder="请选择" style="width: 160px">
            <el-option label="商品" value="product" />
            <el-option label="订单" value="order" />
            <el-option label="会员" value="member" />
            <el-option label="结算" value="settlement" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onTrigger" :loading="triggering">立即导出</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        任务列表为本人的历史记录（仅个人可见）。worker 每 5 秒轮询处理，状态切到"成功"即可下载。
      </el-alert>
    </el-card>

    <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 16px">
      <el-table-column prop="taskNo" label="任务号" width="220" />
      <el-table-column label="业务类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ BIZ_LABEL[row.bizType] || row.bizType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TYPE[row.status] || 'info'" effect="light">
            {{ STATUS_TEXT[row.status] || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="rowCount" label="行数" width="80" align="right" />
      <el-table-column prop="fileSize" label="文件大小" width="120">
        <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMsg" style="color: #f56c6c">{{ row.errorMsg }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.downloadable"
            size="small"
            type="primary"
            link
            @click="onDownload(row.id)"
          >下载</el-button>
          <el-button
            size="small"
            type="danger"
            link
            @click="onDelete(row.id)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px"
      @current-change="onSearch"
      @size-change="onSearch"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  pageExportTasksApi,
  createExportTaskApi,
  deleteExportTaskApi,
  triggerDownload,
} from '@/api/export'
import { EXPORT_STATUS_TEXT as STATUS_TEXT } from '@/types/export'
import type { ExportRequest } from '@/types/export'

const BIZ_LABEL: Record<string, string> = {
  product: '商品',
  order: '订单',
  member: '会员',
  settlement: '结算',
}
const STATUS_TYPE: Record<number, string> = {
  0: 'info',
  10: 'warning',
  20: 'success',
  30: 'danger',
}

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterStatus = ref<number | null>(null)
const trigger = ref<ExportRequest>({ bizType: 'order' })
const triggering = ref(false)

let timer: number | null = null

function formatBytes(n: number) {
  if (!n) return '0 B'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

async function onSearch() {
  loading.value = true
  try {
    const d = await pageExportTasksApi({
      status: filterStatus.value ?? undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

async function onTrigger() {
  if (!trigger.value.bizType) {
    ElMessage.warning('请选择业务类型')
    return
  }
  triggering.value = true
  try {
    const task = await createExportTaskApi(trigger.value)
    ElMessage.success(`任务 ${task.taskNo} 已创建，worker 处理中…`)
    page.value = 1
    onSearch()
  } finally {
    triggering.value = false
  }
}

async function onDownload(id: number) {
  triggerDownload(id)
}

async function onDelete(id: number) {
  await ElMessageBox.confirm('确定删除该导出任务？文件也会被物理删除。', '提示', { type: 'warning' })
  await deleteExportTaskApi(id)
  ElMessage.success('已删除')
  onSearch()
}

onMounted(() => {
  onSearch()
  // worker 每 5s 自动处理，列表自动刷新即可（10s 一次拉）
  timer = window.setInterval(onSearch, 10_000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.page {
  padding: 16px;
}
.filter-bar {
  margin-bottom: 16px;
}
.trigger-card {
  margin-bottom: 16px;
}
.card-header {
  font-weight: 600;
}
</style>
