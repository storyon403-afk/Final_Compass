<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AuthView from './views/AuthView.vue'
import UserSurveyModal from './components/UserSurveyModal.vue'
import AdminSurveyModal from './components/AdminSurveyModal.vue'
import SuspendRest from './components/SuspendRest.vue'
import AdminSuspendModal from './components/AdminSuspendModal.vue'
import AdminMailModal from './components/AdminMailModal.vue'
import AdminMcpModal from './components/AdminMcpModal.vue'
import AdminAiFeedbackModal from './components/AdminAiFeedbackModal.vue'
import GlobalControlCenter from './components/GlobalControlCenter.vue'
import AdminModuleSettingsModal from './components/AdminModuleSettingsModal.vue'
import AdminQuestionVineModal from './components/AdminQuestionVineModal.vue'
import MessageCenterModal from './components/MessageCenterModal.vue'
import AdminMessageSettingsModal from './components/AdminMessageSettingsModal.vue'
import ModuleMaintenanceView from './views/ModuleMaintenanceView.vue'
import { authApi, authenticated, authSession, initIdentity, isAdmin, messageApi, profile, systemApi } from './api'

const route = useRoute()
const introVisible = ref(true)
const introLeaving = ref(false)
const transitionScene = ref('tree')
const showAccount = ref(false)
const showPassword = ref(false)
const showModeration = ref(false)
const showBetaAccess = ref(false)
const showAnnouncement = ref(false)
const showAnnouncementAdmin = ref(false)
const showSuspendAdmin = ref(false)
const showMailAdmin = ref(false)
const showMcpAdmin = ref(false)
const showAiFeedbackAdmin = ref(false)
const showSurvey = ref(false)
const showSurveyAdmin = ref(false)
const showControlCenter = ref(false)
const showModuleAdmin = ref(false)
const showQuestionVineAdmin = ref(false)
const showMessages = ref(false)
const showMessageAdmin = ref(false)
const unreadMessages = ref(0)
const moduleSettings = ref([])
const routeModuleKey=computed(()=>route.path.startsWith('/ai-center')?'AI_CENTER':route.path.startsWith('/cet')?'CET_PRACTICE':route.path==='/'||route.path.startsWith('/courses/')?'COURSE_NAVIGATION':null)
const activeMaintenance=computed(()=>moduleSettings.value.find(item=>item.moduleKey===routeModuleKey.value&&item.status==='MAINTENANCE'))
const currentPassword = ref('')
const newPassword = ref('')
const passwordMessage = ref('')
const moderationItems = ref([])
const moderationLoading = ref(false)
const moderationError = ref('')
const moderationBusyId = ref(null)
const betaAccessItems = ref([])
const betaAccessLoading = ref(false)
const betaAccessError = ref('')
const copiedMessage = ref('')
const announcement = ref({ content: '', enabled: false, updatedAt: null })
const announcementDraft = ref('')
const announcementEnabled = ref(true)
const announcementLoading = ref(false)
const announcementError = ref('')
const announcementMessage = ref('')
const theme = ref(localStorage.getItem('finals-compass-theme') || 'system')
const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)
const themeLabel = computed(() => ({ system: '跟随系统', light: '浅色模式', dark: '深色模式' })[theme.value])
const effectiveTheme = computed(() => theme.value === 'system' ? (systemDark.value ? 'dark' : 'light') : theme.value)
const locationLabel = computed(() => {
  if (route.path === '/') return '课程导航'
  if (route.path.startsWith('/question-vine')) return '问题藤'
  if (route.params.teacherId) return '老师圈'
  if (route.params.courseId) return '任课老师'
  return '课程导航'
})
const introImageModules = import.meta.glob('../pictures/*.{jpg,jpeg,png,webp}', { eager: true, query: '?url', import: 'default' })
const introImages = Object.values(introImageModules)
const introImage = introImages[Math.floor(Math.random() * introImages.length)]
const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
const transitionScenes = ['tree', 'waves', 'vine']
let messagePoll = null

function enterSite() {
  if (introLeaving.value) return
  transitionScene.value = transitionScenes[Math.floor(Math.random() * transitionScenes.length)]
  introLeaving.value = true
  window.setTimeout(() => { introVisible.value = false }, 1050)
}

function cycleTheme() {
  theme.value = theme.value === 'system' ? 'light' : theme.value === 'light' ? 'dark' : 'system'
}

function handleSystemTheme(event) { systemDark.value = event.matches }
function moderationLabel(type) {
  return { RESOURCE: '复习资料', DISCUSSION: '匿名讨论', GUIDE_SUBMISSION: '指南参考' }[type] || type
}
function closeOverlays(event) {
  if (event.key !== 'Escape') return
  showAccount.value = false
  showPassword.value = false
  showModeration.value = false
  showBetaAccess.value = false
  showAnnouncement.value = false
  showAnnouncementAdmin.value = false
  showSuspendAdmin.value = false
  showMailAdmin.value = false
  showMcpAdmin.value = false
  showAiFeedbackAdmin.value = false
  showSurvey.value = false
  showSurveyAdmin.value = false
  showControlCenter.value = false
  showModuleAdmin.value = false
  showQuestionVineAdmin.value = false
  showMessages.value = false
  showMessageAdmin.value = false
}

function openControlCenterSection(section) {
  switch (section) {
    case 'moderation': openModeration(); break
    case 'question-vine': showQuestionVineAdmin.value = true; break
    case 'messages': showMessageAdmin.value = true; break
    case 'beta-access': openBetaAccess(); break
    case 'mail': showMailAdmin.value = true; break
    case 'mcp': showMcpAdmin.value = true; break
    case 'ai-feedback': showAiFeedbackAdmin.value = true; break
    case 'announcement': openAnnouncementAdmin(); break
    case 'suspend': showSuspendAdmin.value = true; break
    case 'survey': showSurveyAdmin.value = true; break
    case 'modules': showModuleAdmin.value = true; break
  }
}

function closeQuestionVineAdminForJump() {
  showQuestionVineAdmin.value = false
  showControlCenter.value = false
}
async function refreshUnreadMessages() {
  if (!authenticated.value) { unreadMessages.value = 0; return }
  try { unreadMessages.value = (await messageApi.unread()).count }
  catch { unreadMessages.value = 0 }
}

async function loadAnnouncement(openForUser = true) {
  announcementError.value = ''
  try {
    announcement.value = await systemApi.announcement()
    if (openForUser && announcement.value.enabled) showAnnouncement.value = true
    return announcement.value
  } catch (error) {
    announcementError.value = error.message
    return null
  }
}

async function openAnnouncementAdmin() {
  showAccount.value = false
  showAnnouncementAdmin.value = true
  announcementLoading.value = true
  announcementMessage.value = ''
  const current = await loadAnnouncement(false)
  if (current) {
    announcementDraft.value = current.content
    announcementEnabled.value = current.enabled
  }
  announcementLoading.value = false
}

async function saveAnnouncement() {
  announcementLoading.value = true
  announcementError.value = ''
  announcementMessage.value = ''
  try {
    announcement.value = await systemApi.updateAnnouncement(announcementDraft.value, announcementEnabled.value)
    announcementMessage.value = announcement.value.enabled ? '公告已更新并启用' : '公告已关闭'
    if (!announcement.value.enabled) showAnnouncement.value = false
  } catch (error) { announcementError.value = error.message }
  finally { announcementLoading.value = false }
}

async function openBetaAccess() {
  showAccount.value = false
  showBetaAccess.value = true
  betaAccessLoading.value = true
  betaAccessError.value = ''
  try {
    betaAccessItems.value = (await systemApi.betaAccessRequests()).map(item => ({
      ...item,
      provisionUsername: item.suggested_username || '',
      provisionDisplayName: ''
    }))
  }
  catch (error) { betaAccessError.value = error.message }
  finally { betaAccessLoading.value = false }
}

function statusLabel(status) {
  return { CREATED: '正在发送', CODE_SENT: '等待用户验证', EMAIL_VERIFIED: '邮箱已验证', ACCOUNT_CREATED: '账号已创建', CREDENTIAL_SENT: '账号已发送', EXPIRED: '已失效' }[status] || status
}

async function approveAccess(item) {
  betaAccessError.value = ''
  item.provisionBusy = true
  try {
    await systemApi.approveAccess(item.id, { username: item.provisionUsername, displayName: item.provisionDisplayName, confirmed: item.provisionConfirmed, adminPassword: item.provisionAdminPassword })
    showCopiedMessage('账号已创建，临时密码已自动发送')
    await openBetaAccess()
  } catch (error) { betaAccessError.value = error.message }
  finally { item.provisionBusy = false }
}

async function suggestDisplayName(item) {
  betaAccessError.value = ''
  item.displayNameBusy = true
  try { item.provisionDisplayName = (await systemApi.suggestDisplayName()).displayName }
  catch (error) { betaAccessError.value = error.message }
  finally { item.displayNameBusy = false }
}

function showCopiedMessage(message) {
  copiedMessage.value = message
  window.setTimeout(() => { copiedMessage.value = '' }, 2200)
}

function legacyCopyText(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, textarea.value.length)
  let copied = false
  try { copied = document.execCommand('copy') }
  finally { document.body.removeChild(textarea) }
  return copied
}

async function copyText(text, message) {
  try {
    if (navigator.clipboard?.writeText && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else if (!legacyCopyText(text)) {
      throw new Error('浏览器拒绝复制')
    }
    showCopiedMessage(message)
    return true
  } catch {
    if (legacyCopyText(text)) {
      showCopiedMessage(message)
      return true
    }
    showCopiedMessage('复制失败，请手动选择复制')
    return false
  }
}

function emailHtml(text) {
  const escape = (value) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const lines = text.split('\n').map((line, index) => {
    let content = escape(line)
    if (index === 0) content = `<strong>${content}</strong>`
    else if (/^(账号|初始密码)：/.test(line)) content = `<strong>${content}</strong>`
    else content = content.replace(/(\d{6})/g, '<strong style="font-size:20px;letter-spacing:3px">$1</strong>')
    return line ? `<div>${content}</div>` : '<div><br></div>'
  }).join('')
  return `<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-size:14px;line-height:1.75;color:#222">${lines}</div>`
}

async function copyEmail(text, message) {
  try {
    if (window.isSecureContext && window.ClipboardItem && navigator.clipboard?.write) {
      await navigator.clipboard.write([new ClipboardItem({
        'text/plain': new Blob([text], { type: 'text/plain' }),
        'text/html': new Blob([emailHtml(text)], { type: 'text/html' })
      })])
      showCopiedMessage(`${message}，粘贴后会保留排版`)
    } else {
      await copyText(text, message)
    }
  } catch { await copyText(text, message) }
}

async function logout() {
  showAccount.value = false
  await authApi.logout()
}

async function changePassword() {
  passwordMessage.value = ''
  try {
    await authApi.changePassword(currentPassword.value, newPassword.value)
    authSession.value.mustChangePassword = false
    localStorage.setItem('finals-compass-session', JSON.stringify(authSession.value))
    currentPassword.value = ''
    newPassword.value = ''
    passwordMessage.value = '密码修改成功'
  } catch (error) { passwordMessage.value = error.message }
}

async function loadModeration() {
  moderationLoading.value = true
  moderationError.value = ''
  try { moderationItems.value = await systemApi.moderation() }
  catch (error) { moderationError.value = error.message }
  finally { moderationLoading.value = false }
}

async function openModeration() {
  showAccount.value = false
  showModeration.value = true
  await loadModeration()
}

async function moderate(item, decision) {
  moderationBusyId.value = `${item.item_type}-${item.id}`
  moderationError.value = ''
  try {
    await systemApi.moderate(item.item_type, item.id, decision)
    await loadModeration()
  } catch (error) { moderationError.value = error.message }
  finally { moderationBusyId.value = null }
}

watch(effectiveTheme, (value) => { document.documentElement.dataset.theme = value }, { immediate: true })
watch(theme, (value) => localStorage.setItem('finals-compass-theme', value))
watch(() => route.fullPath, () => { showAccount.value = false })
watch(() => authSession.value.token, (token, previousToken) => {
  if (token && token !== previousToken) { loadAnnouncement(true); refreshUnreadMessages() }
  else if (!token) showAnnouncement.value = false
}, { immediate: true })
watch(() => authSession.value.mustChangePassword, (required) => { if (required) showPassword.value = true }, { immediate: true })

onMounted(() => {
  mediaQuery.addEventListener('change', handleSystemTheme)
  window.addEventListener('keydown', closeOverlays)
  if (authenticated.value) initIdentity().catch(() => {})
  if(authenticated.value)systemApi.modules().then(value=>moduleSettings.value=value).catch(()=>{})
  refreshUnreadMessages()
  messagePoll = window.setInterval(refreshUnreadMessages, 30000)
})
onBeforeUnmount(() => {
  mediaQuery.removeEventListener('change', handleSystemTheme)
  window.removeEventListener('keydown', closeOverlays)
  if (messagePoll) window.clearInterval(messagePoll)
})
</script>

<template>
  <div v-if="introVisible" :class="['intro-screen', `leave-${transitionScene}`, { leaving: introLeaving }]" :style="{ '--intro-image': `url(${introImage})` }" role="button" tabindex="0" aria-label="点击进入" @click="enterSite" @keydown.enter="enterSite" @keydown.space.prevent="enterSite">
    <div class="intro-shade"></div>
    <h1>花更多时间做自己想要做的<br /><span>期末好好看一下就过了</span></h1>
  </div>

  <AuthView v-if="!introVisible && !authenticated" :scene="transitionScene" />

  <div v-else-if="authenticated" class="app-shell">
    <header class="browser-bar">
      <nav class="business-switch" aria-label="业务模块">
        <router-link to="/"><span>⌂</span>课程导航</router-link>
        <router-link to="/question-vine"><span>∿</span>问题藤</router-link>
        <router-link to="/cet"><span>EN</span>英语等级考试收录</router-link>
        <router-link to="/ai-center"><span>✦</span>AI Center</router-link>
      </nav>
      <div class="browser-account">
        <button class="avatar-button" type="button" :title="profile.nickname || authSession.displayName" @click="showAccount = !showAccount">{{ (profile.nickname || authSession.displayName).slice(0, 1) }}</button>
        <button class="more-button" type="button" aria-label="账户与设置" @click="showAccount = !showAccount">•••</button>
        <div v-if="showAccount" class="account-menu browser-menu">
          <div class="menu-identity"><b>{{ profile.nickname || authSession.displayName }}</b><small>{{ isAdmin ? '管理员' : '匿名内测用户' }}</small></div>
          <button type="button" @click="cycleTheme"><span>{{ effectiveTheme === 'dark' ? '☾' : '☀' }}</span>{{ themeLabel }}</button>
          <button class="message-menu-entry" type="button" @click="showMessages=true;showAccount=false"><span>✉</span>消息<i v-if="unreadMessages" aria-label="有新消息"></i></button>
          <button v-if="isAdmin" class="control-center-entry" type="button" @click="showControlCenter = true; showAccount = false"><span>⌘</span>控制中心</button>
          <button type="button" @click="showSurvey = true; showAccount = false">填写调查问卷</button>
          <button type="button" @click="showPassword = true; showAccount = false">修改密码</button>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </div>
    </header>
    <main class="app-main"><ModuleMaintenanceView v-if="activeMaintenance&&!isAdmin" :setting="activeMaintenance"/><router-view v-else /></main>
    <SuspendRest />
  </div>
  <AdminModuleSettingsModal v-if="showModuleAdmin" @close="showModuleAdmin=false" @updated="systemApi.modules().then(value=>moduleSettings=value)"/>
  <MessageCenterModal v-if="showMessages" @close="showMessages=false" @updated="refreshUnreadMessages" />
  <AdminMessageSettingsModal v-if="showMessageAdmin && isAdmin" @close="showMessageAdmin=false" />
  <AdminQuestionVineModal v-if="showQuestionVineAdmin && isAdmin" @close="showQuestionVineAdmin=false" @jump="closeQuestionVineAdminForJump" />

  <div v-if="showPassword" class="modal-backdrop" @click.self="!authSession.mustChangePassword && (showPassword = false)">
    <form class="upload-modal password-modal" @submit.prevent="changePassword">
      <button v-if="!authSession.mustChangePassword" class="modal-close" type="button" aria-label="关闭" @click="showPassword = false">×</button>
      <span class="eyebrow">账户安全</span><h2>{{ authSession.mustChangePassword ? '首次登录，请修改临时密码' : '修改密码' }}</h2><p>新密码至少 6 位。</p>
      <label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" required /></label>
      <label>新密码<input v-model="newPassword" type="password" minlength="6" maxlength="72" autocomplete="new-password" required /></label>
      <p v-if="passwordMessage" :class="passwordMessage === '密码修改成功' ? 'form-success' : 'form-error'">{{ passwordMessage }}</p>
      <button class="primary-button wide" type="submit">确认修改</button>
    </form>
  </div>

  <div v-if="showModeration" class="modal-backdrop" @click.self="showModeration = false">
    <section class="upload-modal moderation-modal" role="dialog" aria-modal="true" aria-labelledby="moderation-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="showModeration = false">×</button>
      <span class="eyebrow">管理员功能</span><h2 id="moderation-title">内容审核</h2><p>批准后立即公开，操作会记录管理员与时间。</p>
      <div class="moderation-toolbar"><strong>待审核 {{ moderationItems.length }} 条</strong><button type="button" :disabled="moderationLoading" @click="loadModeration">刷新</button></div>
      <p v-if="moderationError" class="form-error">{{ moderationError }}</p>
      <div v-if="moderationLoading" class="empty-state">正在加载…</div>
      <div v-else-if="!moderationItems.length" class="empty-state">当前没有待审核内容。</div>
      <article v-for="item in moderationItems" v-else :key="`${item.item_type}-${item.id}`" class="moderation-item">
        <div><span>{{ moderationLabel(item.item_type) }} · #{{ item.id }}</span><strong>{{ item.summary }}</strong><small>{{ new Date(item.created_at).toLocaleString('zh-CN') }}</small></div>
        <div><button type="button" :disabled="moderationBusyId" @click="moderate(item, 'REJECT')">拒绝</button><button class="primary-button small" type="button" :disabled="moderationBusyId" @click="moderate(item, 'APPROVE')">批准</button></div>
      </article>
    </section>
  </div>

  <div v-if="showBetaAccess" class="modal-backdrop" @click.self="showBetaAccess = false">
    <section class="upload-modal beta-access-modal" role="dialog" aria-modal="true" aria-labelledby="beta-access-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="showBetaAccess = false">×</button>
      <span class="eyebrow">管理员功能</span><h2 id="beta-access-title">登录验证</h2><p>验证码由系统自动发送；用户验证邮箱后，必须由管理员亲自确认并发放账号。</p>
      <div class="moderation-toolbar"><strong>最近 {{ betaAccessItems.length }} 条</strong><button type="button" :disabled="betaAccessLoading" @click="openBetaAccess">刷新</button></div>
      <p v-if="betaAccessError" class="form-error">{{ betaAccessError }}</p>
      <div v-if="betaAccessLoading" class="empty-state">正在加载…</div>
      <div v-else-if="!betaAccessItems.length" class="empty-state">当前还没有登录验证申请。</div>
      <article v-for="item in betaAccessItems" v-else :key="item.id" class="beta-access-item">
        <header><div><strong>{{ item.email }}</strong><small>{{ item.phone }} · {{ new Date(item.created_at).toLocaleString('zh-CN') }}</small></div><span :class="`status-${item.status.toLowerCase()}`">{{ statusLabel(item.status) }}</span></header>
        <small v-if="['CREATED', 'CODE_SENT'].includes(item.status)">有效至 {{ new Date(item.expires_at).toLocaleString('zh-CN') }}<template v-if="item.failed_attempts"> · 已输错 {{ item.failed_attempts }} 次</template></small>
        <small v-else-if="item.status === 'EMAIL_VERIFIED'">{{ item.email }} 已于 {{ new Date(item.verified_at).toLocaleString('zh-CN') }} 验证成功</small>
        <form v-if="item.status === 'EMAIL_VERIFIED'" class="account-provision-form" @submit.prevent="approveAccess(item)">
          <label class="provision-field"><span>账号</span><input v-model.trim="item.provisionUsername" placeholder="用于登录，由管理员填写" required minlength="3" maxlength="64" /></label>
          <label class="provision-field"><span>用户名</span><div class="display-name-generator"><input v-model.trim="item.provisionDisplayName" placeholder="登录后站内展示，最多32个字符" required maxlength="32" /><button type="button" :disabled="item.displayNameBusy" @click="suggestDisplayName(item)">{{ item.displayNameBusy ? '…' : '随机' }}</button></div></label>
          <label class="provision-field"><span>管理员密码</span><input v-model="item.provisionAdminPassword" type="password" autocomplete="current-password" placeholder="验证本次发放操作" required /></label>
          <small class="provision-security-note">临时密码由系统随机生成并直接发送给用户，管理员无法查看；用户首次登录必须修改。</small>
          <label><input v-model="item.provisionConfirmed" type="checkbox" required /> 我已亲自核对申请人，确认发送账号和临时密码</label>
          <button class="primary-button" type="submit" :disabled="item.provisionBusy">{{ item.provisionBusy ? '正在发送…' : '确认并发放账号' }}</button>
        </form>
      </article>
    </section>
  </div>

  <div v-if="showAnnouncement" class="announcement-backdrop" @click.self="showAnnouncement = false">
    <section class="announcement-card" role="dialog" aria-modal="true" aria-labelledby="announcement-title">
      <svg class="announcement-vine" viewBox="0 0 180 300" aria-hidden="true">
        <path class="vine-stem" d="M18 298 C12 245 65 230 38 181 C18 145 76 127 65 83 C58 54 83 29 112 14" />
        <path class="vine-branch branch-one" d="M40 184 C72 181 92 162 98 137" />
        <path class="vine-branch branch-two" d="M64 84 C95 92 119 78 130 52" />
        <path class="vine-leaf leaf-one" d="M64 223 C87 208 94 225 70 236 C55 240 50 233 64 223Z" />
        <path class="vine-leaf leaf-two" d="M43 150 C18 137 16 158 38 168 C54 173 60 160 43 150Z" />
        <g transform="translate(100 134)"><g class="vine-flower flower-one">
          <circle cx="0" cy="-9" r="8"/><circle cx="9" cy="0" r="8"/><circle cx="0" cy="9" r="8"/><circle cx="-9" cy="0" r="8"/><circle class="flower-center" cx="0" cy="0" r="4"/>
        </g></g>
        <g transform="translate(132 48)"><g class="vine-flower flower-two">
          <circle cx="0" cy="-8" r="7"/><circle cx="8" cy="0" r="7"/><circle cx="0" cy="8" r="7"/><circle cx="-8" cy="0" r="7"/><circle class="flower-center" cx="0" cy="0" r="3.5"/>
        </g>
        </g>
        <g transform="translate(114 13)"><g class="vine-flower flower-three">
          <circle cx="0" cy="-7" r="6"/><circle cx="7" cy="0" r="6"/><circle cx="0" cy="7" r="6"/><circle cx="-7" cy="0" r="6"/><circle class="flower-center" cx="0" cy="0" r="3"/>
        </g></g>
      </svg>
      <button class="announcement-close" type="button" aria-label="关闭公告" @click="showAnnouncement = false">×</button>
      <span class="announcement-kicker">A LITTLE NOTE · 公告</span>
      <h2 id="announcement-title">写给每一位同行者</h2>
      <p>{{ announcement.content }}</p>
      <div class="announcement-signature"><span>FINALs COMPASS</span><i></i><b>感谢聆听</b></div>
      <button class="announcement-confirm" type="button" @click="showAnnouncement = false">我会认真告诉你们</button>
    </section>
  </div>

  <div v-if="showAnnouncementAdmin" class="modal-backdrop" @click.self="showAnnouncementAdmin = false">
    <form class="upload-modal announcement-admin-modal" @submit.prevent="saveAnnouncement">
      <button class="modal-close" type="button" aria-label="关闭" @click="showAnnouncementAdmin = false">×</button>
      <span class="eyebrow">仅管理员可操作</span>
      <h2>公告管理</h2>
      <p>公告启用后，用户登录系统时会自动看到；关闭后保留文字，但不再弹出。</p>
      <label>公告内容<textarea v-model.trim="announcementDraft" maxlength="1000" required></textarea></label>
      <label class="announcement-toggle"><input v-model="announcementEnabled" type="checkbox" /><span><b>{{ announcementEnabled ? '公告已启用' : '公告已关闭' }}</b><small>{{ announcementEnabled ? '用户登录后自动弹出' : '用户登录后不显示' }}</small></span></label>
      <p v-if="announcementError" class="form-error">{{ announcementError }}</p>
      <p v-if="announcementMessage" class="form-success">{{ announcementMessage }}</p>
      <button class="primary-button wide" type="submit" :disabled="announcementLoading">{{ announcementLoading ? '正在保存…' : '保存公告设置' }}</button>
    </form>
  </div>
  <div v-if="copiedMessage" class="toast">{{ copiedMessage }}</div>
  <GlobalControlCenter v-if="showControlCenter" @close="showControlCenter = false" @open="openControlCenterSection" />
  <AdminSurveyModal v-if="showSurveyAdmin && isAdmin" @close="showSurveyAdmin = false" />
  <UserSurveyModal v-if="showSurvey" @close="showSurvey = false" />
  <AdminSuspendModal v-if="showSuspendAdmin && isAdmin" @close="showSuspendAdmin = false" />
  <AdminMailModal v-if="showMailAdmin && isAdmin" @close="showMailAdmin = false" />
  <AdminMcpModal v-if="showMcpAdmin && isAdmin" @close="showMcpAdmin = false" />
  <AdminAiFeedbackModal v-if="showAiFeedbackAdmin && isAdmin" @close="showAiFeedbackAdmin = false" />
</template>
