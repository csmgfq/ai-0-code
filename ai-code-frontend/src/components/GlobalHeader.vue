<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">AI 一键应用生成</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="openProfileModal">
                    <SettingOutlined />
                    个人设置
                  </a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-modal
      v-model:open="profileModalVisible"
      title="个人设置"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="savingProfile"
      @ok="submitProfile"
    >
      <a-form layout="vertical">
        <a-form-item label="用户名">
          <a-input v-model:value="profileForm.userName" maxlength="20" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="头像链接">
          <a-input v-model:value="profileForm.userAvatar" placeholder="请输入头像 URL" />
        </a-form-item>
        <a-form-item label="上传头像">
          <a-upload
            :show-upload-list="false"
            accept="image/*"
            :custom-request="handleAvatarUpload"
          >
            <a-button :loading="uploadingAvatar">
              选择图片并上传
            </a-button>
          </a-upload>
          <div class="upload-tip">支持 JPG / PNG / WebP，大小不超过 5MB</div>
        </a-form-item>
        <a-form-item v-if="profileForm.userAvatar" label="头像预览">
          <a-avatar :src="profileForm.userAvatar" :size="56" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout, updateMyUser, uploadAvatar } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined, SettingOutlined } from '@ant-design/icons-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
const profileModalVisible = ref(false)
const savingProfile = ref(false)
const uploadingAvatar = ref(false)
const profileForm = reactive({
  userName: '',
  userAvatar: '',
})
// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
  
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const openProfileModal = () => {
  profileForm.userName = loginUserStore.loginUser.userName ?? ''
  profileForm.userAvatar = loginUserStore.loginUser.userAvatar ?? ''
  profileModalVisible.value = true
}

const submitProfile = async () => {
  const userName = profileForm.userName?.trim()
  if (!userName) {
    message.warning('用户名不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await updateMyUser({
      userName,
      userAvatar: profileForm.userAvatar?.trim(),
    })
    if (res.data.code === 0) {
      await loginUserStore.fetchLoginUser()
      profileModalVisible.value = false
      message.success('个人信息更新成功')
    } else {
      message.error('更新失败：' + res.data.message)
    }
  } finally {
    savingProfile.value = false
  }
}

const handleAvatarUpload = async (options: UploadRequestOption) => {
  const rawFile = options.file as File
  if (!rawFile) {
    message.error('读取上传文件失败')
    return
  }
  if (!rawFile.type.startsWith('image/')) {
    message.warning('请上传图片文件')
    return
  }
  uploadingAvatar.value = true
  try {
    const res = await uploadAvatar(rawFile)
    if (res.data.code === 0 && res.data.data) {
      profileForm.userAvatar = res.data.data
      message.success('头像上传成功，已自动回填链接')
      options.onSuccess?.(res.data)
    } else {
      message.error('上传失败：' + res.data.message)
      options.onError?.(new Error(res.data.message || '上传失败'))
    }
  } catch (error) {
    message.error('上传失败，请重试')
    options.onError?.(error as Error)
  } finally {
    uploadingAvatar.value = false
  }
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}

.upload-tip {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
</style>
