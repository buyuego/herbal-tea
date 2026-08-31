<template>
  <div class="staff-view">
    <!-- 筛选工具栏 -->
    <div class="toolbar">
      <el-select v-model="query.boundStatus" placeholder="全部状态" clearable style="width: 150px" @change="onSearch">
        <el-option label="正常绑定" :value="1" />
        <el-option label="已移除" :value="0" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <div class="toolbar-right">
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新建员工</el-button>
      </div>
    </div>

    <!-- 员工表格 -->
    <el-table :data="staffs" v-loading="loading" stripe>
      <el-table-column label="员工" min-width="160">
        <template #default="{ row }">
          <div class="cell-main">{{ row.realName }}</div>
          <div class="cell-sub">{{ row.username }}</div>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="140">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.isOwner === 1 ? 'warning' : 'info'" size="small">
            {{ row.isOwner === 1 ? '店主' : row.roleName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.adminStatus === 1 ? 'success' : 'danger'" size="small">
            {{ row.adminStatus === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.bindStatus === 1 ? 'primary' : 'info'" size="small" effect="plain">
            {{ row.bindStatus === 1 ? '正常绑定' : '已移除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定时间" width="165">
        <template #default="{ row }">{{ formatTime(row.boundAt) }}</template>
      </el-table-column>
      <el-table-column label="最近登录" width="165">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage && row.isOwner !== 1 && row.bindStatus === 1">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.adminStatus === 1" type="primary" link @click="openResetPassword(row)">
              重置密码
            </el-button>
            <el-button type="danger" link @click="confirmRemove(row)">移除</el-button>
          </template>
          <span v-else class="cell-sub">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadStaffs"
      />
    </div>

    <!-- 新建员工对话框 -->
    <el-dialog v-model="createVisible" title="新建员工" width="480px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="登录名" prop="username">
          <el-input v-model="createForm.username" placeholder="字母开头，3-32 位字母/数字/下划线" maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createForm.realName" placeholder="员工姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createForm.phone" placeholder="选填，11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="6-32 位，请线下告知员工" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑员工对话框 -->
    <el-dialog v-model="editVisible" title="编辑员工" width="480px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="登录名">
          <el-input :model-value="editing?.username" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="editForm.realName" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
          <div class="cell-sub" style="margin-top: 4px">禁用后该员工旧令牌即时失效</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="员工">
          <span>{{ editing?.realName }}（{{ editing?.username }}）</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位，重置后员工需重新登录" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import { createStaffApi, pageStaffApi, removeStaffApi, resetStaffPasswordApi, updateStaffApi } from '@/api/staff'
import type { StaffItem } from '@/types/staff'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('store:staff:manage'))

const loading = ref(false)
const staffs = ref<StaffItem[]>([])
const total = ref(0)
const query = reactive({
  boundStatus: undefined as number | undefined,
  page: 1,
  size: 10,
})

const saving = ref(false)

// ---- 新建 ----
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ username: '', realName: '', phone: '', password: '' })
const createRules: FormRules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/, message: '字母开头，3-32 位字母/数字/下划线', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为 6-32 位', trigger: 'blur' },
  ],
}

// ---- 编辑 / 重置 ----
const editVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({ realName: '', phone: '', status: 1 })
const editRules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

const pwdVisible = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ newPassword: '' })
const pwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为 6-32 位', trigger: 'blur' },
  ],
}

const editing = ref<StaffItem | null>(null)

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function loadStaffs() {
  loading.value = true
  try {
    const page = await pageStaffApi({ ...query, page: query.page, size: query.size })
    staffs.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '员工列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadStaffs()
}

function onReset() {
  query.boundStatus = undefined
  onSearch()
}

function openCreate() {
  createForm.username = ''
  createForm.realName = ''
  createForm.phone = ''
  createForm.password = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await createStaffApi({
      username: createForm.username.trim(),
      realName: createForm.realName.trim(),
      phone: createForm.phone.trim() || undefined,
      password: createForm.password,
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    loadStaffs()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '创建失败')
  } finally {
    saving.value = false
  }
}

function openEdit(row: StaffItem) {
  editing.value = row
  editForm.realName = row.realName
  editForm.phone = row.phone || ''
  editForm.status = row.adminStatus
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value || !editing.value) return
  const ok = await editFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await updateStaffApi(editing.value.adminId, {
      realName: editForm.realName.trim(),
      phone: editForm.phone.trim() || undefined,
      status: editForm.status,
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadStaffs()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function openResetPassword(row: StaffItem) {
  editing.value = row
  pwdForm.newPassword = ''
  pwdVisible.value = true
}

async function submitResetPassword() {
  if (!pwdFormRef.value || !editing.value) return
  const ok = await pwdFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await resetStaffPasswordApi(editing.value.adminId, { newPassword: pwdForm.newPassword })
    ElMessage.success('密码已重置，员工需重新登录')
    pwdVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '重置失败')
  } finally {
    saving.value = false
  }
}

async function confirmRemove(row: StaffItem) {
  try {
    await ElMessageBox.confirm(
      `确定移除员工「${row.realName}（${row.username}）」吗？移除后其登录立即失效，账号保留可重新添加。`,
      '移除员工',
      { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeStaffApi(row.adminId)
    ElMessage.success('已移除')
    loadStaffs()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '移除失败')
  }
}

onMounted(loadStaffs)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar-right {
  margin-left: auto;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
</style>
