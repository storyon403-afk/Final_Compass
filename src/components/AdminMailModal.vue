<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { systemApi } from '../api'

const emit = defineEmits(['close'])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')
const config = ref({ host: '', port: 465, securityMode: 'SSL', username: '', credential: '', fromAddress: '', fromName: 'Finals Compass', replyTo: '', adminPassword: '' })
const testRecipient = ref('')
const templates = ref([])
const microsoft = ref({ configured: false, connected: false, active: false })
const microsoftAdminPassword = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [smtp, values, graph] = await Promise.all([systemApi.smtpConfig(), systemApi.mailTemplates(), systemApi.microsoftMail()])
    if (smtp.host) config.value = { ...config.value, host: smtp.host, port: smtp.port, securityMode: smtp.security_mode, username: smtp.username, fromAddress: smtp.from_address, fromName: smtp.from_name, replyTo: smtp.reply_to || '' }
    templates.value = values.map(item => ({ ...item, adminPassword: '' }))
    microsoft.value = graph
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}

async function connectMicrosoft() {
  busy.value = true; error.value = ''; message.value = ''
  try {
    const result = await systemApi.authorizeMicrosoftMail()
    const popup = window.open(result.authorizationUrl, 'finals-compass-microsoft-mail', 'popup,width=620,height=760')
    if (!popup) throw new Error('浏览器阻止了授权窗口，请允许本站弹出窗口')
    message.value = '请在微软窗口中完成授权。'
  } catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function testMicrosoft() {
  busy.value = true; error.value = ''; message.value = ''
  try { await systemApi.testMicrosoftMail(testRecipient.value, microsoftAdminPassword.value); message.value = 'Microsoft Graph 测试邮件发送成功，已设为当前邮件服务。'; microsoftAdminPassword.value = ''; await load() }
  catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function disconnectMicrosoft() {
  busy.value = true; error.value = ''; message.value = ''
  try { await systemApi.disconnectMicrosoftMail(microsoftAdminPassword.value); microsoftAdminPassword.value = ''; message.value = 'Microsoft 邮箱连接已解除，邮件服务已切回 SMTP。'; await load() }
  catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function oauthMessage(event) {
  if (event.origin !== window.location.origin || event.data?.type !== 'fc-mail-oauth') return
  message.value = 'Microsoft 授权成功，请发送测试邮件后启用。'
  await load()
}

async function saveSmtp() {
  busy.value = true; error.value = ''; message.value = ''
  try { await systemApi.saveSmtp(config.value); message.value = '配置已加密保存；发送测试邮件成功后才会启用。'; config.value.credential = '' }
  catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function testSmtp() {
  busy.value = true; error.value = ''; message.value = ''
  try { await systemApi.testSmtp(testRecipient.value, config.value.adminPassword); message.value = '测试邮件发送成功，SMTP已启用。' }
  catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function saveTemplate(item) {
  busy.value = true; error.value = ''; message.value = ''
  try {
    await systemApi.saveMailTemplate(item.template_type, { subject: item.subject_template, text: item.text_template, enabled: item.enabled, adminPassword: item.adminPassword })
    message.value = `${item.template_type} 模板已保存。`; item.adminPassword = ''
  } catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

onMounted(() => { window.addEventListener('message', oauthMessage); load() })
onBeforeUnmount(() => window.removeEventListener('message', oauthMessage))
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section class="upload-modal mail-admin-modal">
      <button class="modal-close" type="button" @click="emit('close')">×</button>
      <span class="eyebrow">管理员 · 安全配置</span><h2>邮件服务与模板</h2>
      <p>可选择传统 SMTP 或 Microsoft Graph OAuth2；只有测试发送成功的提供者才会启用。</p>
      <p v-if="error" class="form-error">{{ error }}</p><p v-if="message" class="form-success">{{ message }}</p>
      <div v-if="loading" class="empty-state">正在加载…</div>
      <template v-else>
        <section class="mail-provider-card" :class="{ active: microsoft.active }">
          <div><span class="eyebrow">推荐 · OAuth2</span><h3>Microsoft Graph</h3><p v-if="microsoft.connected">已连接 {{ microsoft.account_email }}<template v-if="microsoft.active"> · 当前正在使用</template></p><p v-else>{{ microsoft.configured ? '尚未连接微软邮箱' : '服务器尚未配置 Microsoft OAuth2 应用' }}</p></div>
          <button v-if="!microsoft.connected" type="button" :disabled="busy || !microsoft.configured" @click="connectMicrosoft">连接微软邮箱</button>
          <template v-else>
            <label>管理员当前密码<input v-model="microsoftAdminPassword" type="password" autocomplete="current-password" /></label>
            <button type="button" :disabled="busy || !testRecipient || !microsoftAdminPassword" @click="testMicrosoft">测试并启用 Graph</button>
            <button class="danger-button" type="button" :disabled="busy || !microsoftAdminPassword" @click="disconnectMicrosoft">解除连接</button>
          </template>
        </section>
        <div class="mail-provider-divider"><span>或使用 SMTP 授权码</span></div>
        <form class="mail-config-grid" @submit.prevent="saveSmtp">
          <label>SMTP主机<input v-model.trim="config.host" required placeholder="smtp.qq.com" /></label>
          <label>端口<input v-model.number="config.port" type="number" min="1" max="65535" required /></label>
          <label>加密方式<select v-model="config.securityMode"><option>SSL</option><option>STARTTLS</option></select></label>
          <label>SMTP用户名<input v-model.trim="config.username" autocomplete="username" required /></label>
          <label>SMTP授权码<input v-model="config.credential" type="password" autocomplete="new-password" required /></label>
          <label>发件邮箱<input v-model.trim="config.fromAddress" type="email" required /></label>
          <label>发件人名称<input v-model.trim="config.fromName" required /></label>
          <label>Reply-To<input v-model.trim="config.replyTo" type="email" /></label>
          <label>管理员当前密码<input v-model="config.adminPassword" type="password" autocomplete="current-password" required /></label>
          <button class="primary-button" type="submit" :disabled="busy">加密保存配置</button>
        </form>
        <form class="mail-test-row" @submit.prevent="testSmtp"><label>测试收件邮箱<input v-model.trim="testRecipient" type="email" required /></label><button type="submit" :disabled="busy">发送测试并启用</button></form>
        <section v-for="item in templates" :key="item.template_type" class="mail-template-editor">
          <h3>{{ item.template_type }}</h3>
          <label>主题<input v-model="item.subject_template" maxlength="200" /></label>
          <label>纯文本正文<textarea v-model="item.text_template" rows="8"></textarea></label>
          <label class="announcement-toggle"><input v-model="item.enabled" type="checkbox" /><span><b>启用此模板</b></span></label>
          <label>管理员当前密码<input v-model="item.adminPassword" type="password" autocomplete="current-password" /></label>
          <button type="button" :disabled="busy" @click="saveTemplate(item)">保存模板</button>
        </section>
      </template>
    </section>
  </div>
</template>
