<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-brand">
        <el-icon :size="36" color="#409EFF"><Cup /></el-icon>
        <h1>养生茶管理后台</h1>
        <p>Herbal Tea Admin Console</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          class="login-btn"
          :loading="loading"
          @click="onSubmit"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/error'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value) return

  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success(`欢迎回来，${auth.profile?.realName || auth.profile?.username}`)
    router.push((route.query.redirect as string) || '/dashboard')
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f4ff 0%, #f5f7fa 60%, #eef7e6 100%);
}
.login-card {
  width: 400px;
  border-radius: 12px;
}
.login-brand {
  text-align: center;
  margin-bottom: 24px;
}
.login-brand h1 {
  margin: 8px 0 4px;
  font-size: 22px;
  color: #303133;
}
.login-brand p {
  margin: 0;
  font-size: 12px;
  color: #909399;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
