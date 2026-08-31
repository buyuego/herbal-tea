<template>
  <div class="roles-view">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-tip">自定义角色上限 10 个；预设角色（平台超管/店长等）数据范围与级别锁定</div>
      <div class="toolbar-right">
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新建角色</el-button>
      </div>
    </div>

    <!-- 角色表格 -->
    <el-table :data="roles" v-loading="loading" stripe>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <div class="cell-main">{{ row.name }}</div>
          <div class="cell-sub">{{ row.code }}</div>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.level === 1 ? 'warning' : 'info'" size="small">
            {{ row.level === 1 ? '平台级' : '店铺级' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据范围" width="100" align="center">
        <template #default="{ row }">
          <span>{{ DATA_SCOPE_LABEL[row.dataScope] || row.dataScope }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isPreset === 1 ? 'primary' : 'default'" size="small" effect="plain">
            {{ row.isPreset === 1 ? '预设' : '自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="管理员" width="80" align="center">
        <template #default="{ row }">{{ row.adminCount }}</template>
      </el-table-column>
      <el-table-column label="描述" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-tooltip v-if="row.id === 1" content="超管角色权限不可修改，防止误操作锁死" placement="top">
            <span><el-button type="primary" link disabled>授权</el-button></span>
          </el-tooltip>
          <el-button v-else type="primary" link @click="openAuth(row)">授权</el-button>
          <el-tooltip v-if="row.isPreset === 1" content="预设角色不可删除" placement="top">
            <span><el-button type="danger" link disabled>删除</el-button></span>
          </el-tooltip>
          <el-tooltip v-else-if="row.adminCount > 0" content="仍有管理员绑定，请先改绑后再删除" placement="top">
            <span><el-button type="danger" link disabled>删除</el-button></span>
          </el-tooltip>
          <el-button v-else type="danger" link @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建角色对话框 -->
    <el-dialog v-model="createVisible" title="新建角色" width="500px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="createForm.code" placeholder="大写下划线字母数字，2-32 位（创建后不可改）" maxlength="32" />
        </el-form-item>
        <el-form-item label="角色名" prop="name">
          <el-input v-model="createForm.name" placeholder="角色名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="createForm.level">
            <el-radio :value="1">平台级（总部）</el-radio>
            <el-radio :value="2">店铺级（门店）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="createForm.dataScope" style="width: 100%">
            <el-option v-for="opt in dataScopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑角色对话框 -->
    <el-dialog v-model="editVisible" title="编辑角色" width="500px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码">
          <el-input :model-value="editing?.code" disabled />
        </el-form-item>
        <el-form-item label="角色名" prop="name">
          <el-input v-model="editForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="editForm.level" :disabled="isPresetEditing">
            <el-radio :value="1">平台级（总部）</el-radio>
            <el-radio :value="2">店铺级（门店）</el-radio>
          </el-radio-group>
          <div v-if="isPresetEditing" class="cell-sub" style="margin-top: 4px">预设角色数据范围与级别锁定，不可修改</div>
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="editForm.dataScope" style="width: 100%" :disabled="isPresetEditing">
            <el-option v-for="opt in dataScopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 授权对话框 -->
    <el-dialog v-model="authVisible" :title="`授权 · ${authTarget?.name || ''}`" width="640px" destroy-on-close>
      <div class="auth-tip">
        <p>勾选为全量覆盖：保存后该角色下全部管理员旧登录立即失效，需重新登录生效。</p>
        <p><el-tag type="danger" size="small" effect="plain">敏感</el-tag> 标记权限仅超管角色可授予，已置灰不可勾选。</p>
      </div>
      <div class="perm-tree-wrap">
        <el-tree
          ref="permTreeRef"
          :data="permTree"
          show-checkbox
          node-key="id"
          default-expand-all
          :props="treeProps"
          :default-checked-keys="checkedPermissionIds"
        >
          <template #default="{ data }">
            <div class="perm-node">
              <span :class="['perm-name', { 'perm-menu': data.type === 1 }]">{{ data.name }}</span>
              <el-tag v-if="data.isSensitive === 1" type="danger" size="small" effect="plain" class="perm-tag">敏感</el-tag>
              <span v-if="data.type === 2" class="perm-code">按钮</span>
              <el-tooltip v-if="data.type === 3 && data.path" :content="data.path" placement="top">
                <span class="perm-code">{{ data.code }}</span>
              </el-tooltip>
              <span v-else-if="data.type === 3" class="perm-code">{{ data.code }}</span>
            </div>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="authVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAuth">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'
import {
  assignRolePermissionsApi,
  createRoleApi,
  deleteRoleApi,
  listRolesApi,
  permissionTreeApi,
  updateRoleApi,
} from '@/api/role'
import type { PermissionNode, RoleItem } from '@/types/role'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('system:role:config'))

const DATA_SCOPE_LABEL: Record<string, string> = {
  GLOBAL: '全部数据',
  MULTI_STORE: '多门店',
  SINGLE_STORE: '单门店',
}
const dataScopeOptions = [
  { label: '全部数据', value: 'GLOBAL' },
  { label: '多门店', value: 'MULTI_STORE' },
  { label: '单门店', value: 'SINGLE_STORE' },
]

const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleItem[]>([])
const editing = ref<RoleItem | null>(null)

function formatTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function loadRoles() {
  loading.value = true
  try {
    roles.value = await listRolesApi()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '角色列表加载失败')
  } finally {
    loading.value = false
  }
}

// ---- 新建 / 编辑 ----
const createVisible = ref(false)
const editVisible = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const createForm = reactive({ code: '', name: '', dataScope: 'SINGLE_STORE', level: 2 as 1 | 2, description: '' })
const editForm = reactive({ name: '', dataScope: 'SINGLE_STORE', level: 2 as 1 | 2, description: '' })

const formRules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{1,31}$/, message: '大写下划线字母数字，2-32 位', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入角色名', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
  level: [{ required: true, message: '请选择角色级别', trigger: 'change' }],
}

const isPresetEditing = computed(() => editing.value?.isPreset === 1)

function openCreate() {
  createForm.code = ''
  createForm.name = ''
  createForm.dataScope = 'SINGLE_STORE'
  createForm.level = 2
  createForm.description = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await createRoleApi({
      code: createForm.code.trim(),
      name: createForm.name.trim(),
      dataScope: createForm.dataScope as 'GLOBAL' | 'MULTI_STORE' | 'SINGLE_STORE',
      level: createForm.level,
      description: createForm.description.trim() || undefined,
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '创建失败')
  } finally {
    saving.value = false
  }
}

function openEdit(row: RoleItem) {
  editing.value = row
  editForm.name = row.name
  editForm.dataScope = row.dataScope as 'GLOBAL' | 'MULTI_STORE' | 'SINGLE_STORE'
  editForm.level = row.level as 1 | 2
  editForm.description = row.description || ''
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value || !editing.value) return
  const ok = await editFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    await updateRoleApi(editing.value.id, {
      name: editForm.name.trim(),
      dataScope: editForm.dataScope as 'GLOBAL' | 'MULTI_STORE' | 'SINGLE_STORE',
      level: editForm.level,
      description: editForm.description.trim() || undefined,
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function confirmDelete(row: RoleItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.name}（${row.code}）」吗？删除后其授权关联一并清理，不可恢复。`,
      '删除角色',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteRoleApi(row.id)
    ElMessage.success('已删除')
    loadRoles()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '删除失败')
  }
}

// ---- 授权 ----
const authVisible = ref(false)
const authTarget = ref<RoleItem | null>(null)
const permTree = ref<PermissionNode[]>([])
const permTreeRef = ref()
/** 回显勾选集合（打开对话框时一次性设置；destroy-on-close 保证每次重建） */
const checkedPermissionIds = ref<number[]>([])

/** 敏感节点仅超管角色（id=1）可勾选；授权入口已对超管禁用，此处对非超管目标一律置灰 */
const treeProps = computed(() => ({
  label: 'name',
  children: 'children',
  disabled: (data: PermissionNode) => authTarget.value !== null && authTarget.value.id !== 1 && data.isSensitive === 1,
}))

async function openAuth(row: RoleItem) {
  authTarget.value = row
  permTree.value = []
  checkedPermissionIds.value = []
  try {
    permTree.value = await permissionTreeApi()
    // 全量授权回显：permissionIds 含父节点时 el-tree 级联勾选后代（disabled 敏感节点自动跳过）
    checkedPermissionIds.value = row.permissionIds
    authVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '权限树加载失败')
  }
}

async function submitAuth() {
  if (!permTreeRef.value || !authTarget.value) return
  // 全量覆盖语义：勾选节点 + 半选父节点（父节点权限码驱动菜单显示/路由守卫，必须一并提交）
  const checked = permTreeRef.value.getCheckedKeys(false) as number[]
  const halfChecked = permTreeRef.value.getHalfCheckedKeys() as number[]
  const ids = Array.from(new Set([...checked, ...halfChecked])).sort((a, b) => a - b)
  saving.value = true
  try {
    await assignRolePermissionsApi(authTarget.value.id, { permissionIds: ids })
    ElMessage.success(`授权已保存，该角色下 ${authTarget.value.adminCount} 个管理员需重新登录生效`)
    authVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '授权保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadRoles)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar-tip {
  color: #909399;
  font-size: 13px;
}
.toolbar-right {
  margin-left: auto;
}
.cell-main {
  font-weight: 500;
}
.cell-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.auth-tip {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}
.auth-tip p {
  margin: 0;
}
.perm-tree-wrap {
  max-height: 46vh;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px 4px;
}
.perm-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.perm-name {
  color: #606266;
}
.perm-menu {
  font-weight: 600;
  color: #303133;
}
.perm-tag {
  transform: scale(0.85);
}
.perm-code {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
