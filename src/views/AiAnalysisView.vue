<script setup>
import { computed, onMounted, ref } from 'vue'
import { aiApi, isAdmin } from '../api'

const loading = ref(true)
const error = ref('')
const message = ref('')
const menuOpen = ref(false)
const fileInput = ref(null)
const dashboard = ref({
  month: '', myScore: 0, platformEligible: false, leaderboard: [], skills: [], providers: [],
  platformProviders: [], savedKeys: [], encryptedStorageAvailable: false
})
const provider = ref('deepseek')
const skillId = ref('auto')
const input = ref('')
const apiKey = ref('')
const keyLabel = ref('我的学习 Key')
const consentToStore = ref(false)
const credentialSource = ref('EPHEMERAL_BYOK')
const attachments = ref([])
const result = ref(null)
const invoking = ref(false)
const adminProvider = ref('deepseek')
const adminModel = ref('deepseek-chat')
const adminKey = ref('')
const adminEnabled = ref(true)

const savedForProvider = computed(() => dashboard.value.savedKeys.find((item) => item.provider === provider.value))
const platformProvider = computed(() => dashboard.value.platformProviders.find((item) => item.provider === provider.value && item.enabled))

async function load() {
  loading.value = true
  error.value = ''
  try {
    dashboard.value = await aiApi.dashboard()
    if (dashboard.value.providers?.length && !dashboard.value.providers.some((item) => item.id === provider.value)) {
      provider.value = dashboard.value.providers[0].id
    }
    if (skillId.value !== 'auto' && !dashboard.value.skills.some((skill) => skill.id === skillId.value)) skillId.value = 'auto'
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}

function chooseFiles(event) {
  const selected = Array.from(event.target.files || [])
  const existing = new Set(attachments.value.map((item) => `${item.name}:${item.size}`))
  for (const file of selected) {
    if (attachments.value.length >= 3) { error.value = '一次最多添加 3 个附件'; break }
    if (file.size > 20 * 1024 * 1024) { error.value = `${file.name} 超过 20MB`; continue }
    const key = `${file.name}:${file.size}`
    if (!existing.has(key)) attachments.value.push({ file, name: file.name, size: file.size, type: file.type, status: 'READY' })
  }
  event.target.value = ''
}

function removeAttachment(index) { attachments.value.splice(index, 1) }
function formatSize(size) { return size < 1024 * 1024 ? `${Math.max(1, Math.round(size / 1024))} KB` : `${(size / 1024 / 1024).toFixed(1)} MB` }

async function saveKey() {
  message.value = ''
  error.value = ''
  if (!consentToStore.value) {
    credentialSource.value = 'EPHEMERAL_BYOK'
    message.value = '已切换为仅本次使用，Key 不会写入数据库。'
    return
  }
  try {
    await aiApi.saveByok(provider.value, apiKey.value, keyLabel.value, true)
    apiKey.value = ''
    credentialSource.value = 'STORED_BYOK'
    message.value = 'API Key 已加密保存。'
    await load()
  } catch (reason) { error.value = reason.message }
}

async function removeKey() {
  if (!savedForProvider.value) return
  await aiApi.deleteByok(provider.value)
  credentialSource.value = 'EPHEMERAL_BYOK'
  message.value = '已删除保存的 API Key。'
  await load()
}

async function invoke() {
  if (!input.value.trim() || invoking.value) return
  invoking.value = true
  error.value = ''
  result.value = null
  try {
    const question = input.value.trim()
    const hasImage = attachments.value.some((item) => item.type.startsWith('image/'))
    const selectedProvider = dashboard.value.providers.find((item) => item.id === provider.value)
    if (hasImage && !selectedProvider?.capabilities?.includes('IMAGE')) {
      throw new Error(`当前 ${selectedProvider?.name || provider.value} 通道不支持图片，请在右上角菜单切换到支持图片的 Provider`)
    }
    const converted = []
    for (const attachment of attachments.value) {
      attachment.status = 'CONVERTING'
      try {
        const document = await aiApi.convertAttachment(attachment.file)
        attachment.status = 'DONE'
        converted.push(`\n\n## 附件：${document.fileName}\n\n${document.markdown}`)
      } catch (reason) {
        attachment.status = 'ERROR'
        throw reason
      }
    }
    const requestSkillId = hasImage ? 'math-problem-image-analysis' : attachments.value.length ? 'material-summary' : skillId.value
    const maxLength = dashboard.value.skills.find((item) => item.id === requestSkillId)?.maxInputLength || 8000
    const analysisInput = `${question}${converted.join('')}`.slice(0, maxLength)
    const response = await aiApi.invoke({
      provider: provider.value,
      skillId: requestSkillId,
      credentialSource: credentialSource.value,
      ephemeralApiKey: credentialSource.value === 'EPHEMERAL_BYOK' ? apiKey.value : null,
      input: analysisInput
    })
    result.value = { question, attachmentNames: attachments.value.map((item) => item.name), ...response }
    input.value = ''
    if (credentialSource.value === 'EPHEMERAL_BYOK') apiKey.value = ''
  } catch (reason) { error.value = reason.message }
  finally { invoking.value = false }
}

async function savePlatform() {
  error.value = ''
  message.value = ''
  try {
    await aiApi.savePlatformKey(adminProvider.value, adminModel.value, adminKey.value, adminEnabled.value)
    adminKey.value = ''
    message.value = '平台 AI 配置已加密保存。'
    await load()
  } catch (reason) { error.value = reason.message }
}

onMounted(load)
</script>

<template>
  <section class="ai-chat-page">
    <header class="ai-chat-topbar">
      <div class="ai-brand"><span>FC</span><div><b>FinalsCompass AI</b><small>BETA · 学习分析</small></div></div>
      <button class="ai-menu-trigger" type="button" aria-label="打开 AI 模块菜单" @click="menuOpen = true"><i></i><i></i><i></i></button>
    </header>

    <p v-if="error" class="ai-alert error">{{ error }}</p>
    <p v-if="message" class="ai-alert">{{ message }}</p>

    <main class="ai-conversation">
      <div v-if="loading" class="ai-loading">正在准备学习空间…</div>
      <template v-else>
        <section v-if="!result" class="ai-welcome"><h1>hello</h1></section>

        <section v-else class="ai-thread" aria-live="polite">
          <div class="ai-user-message"><span>你</span><p>{{ result.question }}</p></div>
          <div class="ai-assistant-message"><span class="ai-answer-mark">FC</span><div><b>FinalsCompass AI</b><p>{{ result.content }}</p><small>{{ result.provider }} · trace {{ result.traceId }}</small></div></div>
        </section>

        <section class="ai-composer-wrap">
          <div v-if="attachments.length" class="ai-attachment-list">
            <div v-for="(file, index) in attachments" :key="`${file.name}-${file.size}`"><span>{{ file.type.startsWith('image/') ? '▧' : file.type.startsWith('audio/') ? '♫' : '▤' }}</span><p><b>{{ file.name }}</b><small>{{ formatSize(file.size) }} · {{ file.status === 'CONVERTING' ? '正在解析' : file.status === 'DONE' ? '解析完成' : file.status === 'ERROR' ? '解析失败' : '等待发送' }}</small></p><button type="button" aria-label="移除附件" :disabled="invoking" @click="removeAttachment(index)">×</button></div>
          </div>
          <div class="ai-composer">
            <textarea v-model="input" rows="1" maxlength="8000" placeholder="向 FinalsCompass AI 工具提问…" @keydown.enter.exact.prevent="invoke"></textarea>
            <div class="ai-composer-actions">
              <input ref="fileInput" class="visually-hidden" type="file" multiple accept="image/png,image/jpeg,image/webp,audio/wav,audio/mpeg,audio/mp4,.pdf,.docx,.pptx,.xls,.xlsx,.txt,.md,.csv,.json,.xml,.html" @change="chooseFiles" />
              <button class="ai-attach" type="button" title="添加图片、文档或音频" @click="fileInput?.click()"><span>＋</span>附件</button>
              <div class="ai-composer-space"></div>
              <button class="ai-send" type="button" :disabled="invoking || !input.trim()" @click="invoke">{{ invoking ? '···' : '↑' }}</button>
            </div>
          </div>
          <p class="ai-stage-note">附件会在发送时由内置的隔离 MarkItDown Worker 转换为 Markdown，再进入现有 Skill 调用链；单个不超过 20MB，一次最多 3 个。</p>
        </section>
      </template>
    </main>

    <Transition name="ai-drawer">
      <div v-if="menuOpen" class="ai-drawer-layer" @click.self="menuOpen = false">
        <aside class="ai-drawer" aria-label="AI 模块菜单">
          <header><div><span>AI MODULE</span><h2>学习分析设置</h2></div><button type="button" aria-label="关闭菜单" @click="menuOpen = false">×</button></header>

          <section class="ai-drawer-section">
            <h3>使用规则</h3>
            <ul><li>每日登录只记一次 +1。</li><li>每份资料提交 +2，审核通过再 +5。</li><li>论坛或指南内容审核通过 +2。</li><li>上月活跃度前 20 名获得本月平台 AI 资格。</li><li>自己的 Key 可选择加密保存，也可仅本次使用。</li></ul>
          </section>

          <section class="ai-drawer-section">
            <div class="ai-drawer-heading"><h3>本月排行榜</h3><button type="button" @click="load">刷新</button></div>
            <div class="ai-my-score"><span>{{ dashboard.month }}</span><b>{{ dashboard.myScore }}</b><small>我的积分</small></div>
            <ol v-if="dashboard.leaderboard.length" class="ai-menu-ranking"><li v-for="item in dashboard.leaderboard" :key="item.user_id"><b>{{ item.ranking_position }}</b><span>{{ item.display_name }}</span><small>{{ item.score }}</small></li></ol>
            <p v-else class="ai-caption">本月还没有积分记录。</p>
          </section>

          <section class="ai-drawer-section">
            <h3>AI 通道</h3>
            <label>Provider<select v-model="provider"><option v-for="item in dashboard.providers || []" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
            <div class="ai-source-options">
              <label><input v-model="credentialSource" type="radio" value="PLATFORM" /><span><b>平台额度</b><small>{{ dashboard.platformEligible && platformProvider ? '当前可用' : '需要资格及管理员启用' }}</small></span></label>
              <label><input v-model="credentialSource" type="radio" value="STORED_BYOK" :disabled="!savedForProvider" /><span><b>已保存 Key</b><small>{{ savedForProvider ? `指纹 ${savedForProvider.key_fingerprint}` : '尚未保存' }}</small></span></label>
              <label><input v-model="credentialSource" type="radio" value="EPHEMERAL_BYOK" /><span><b>仅本次使用</b><small>不会写入数据库</small></span></label>
            </div>
            <label>API Key<input v-model="apiKey" type="password" autocomplete="off" placeholder="输入对应 Provider 的 Key" /></label>
            <label>Key 备注<input v-model="keyLabel" maxlength="80" /></label>
            <label class="ai-consent"><input v-model="consentToStore" type="checkbox" />同意平台加密保存此 Key</label>
            <div class="ai-drawer-actions"><button type="button" :disabled="!apiKey" @click="saveKey">{{ consentToStore ? '加密保存' : '仅本次使用' }}</button><button v-if="savedForProvider" type="button" @click="removeKey">删除保存</button></div>
          </section>

          <section v-if="isAdmin" class="ai-drawer-section ai-admin-settings">
            <h3>管理员 · 平台通道</h3>
            <label>Provider<input v-model="adminProvider" /></label><label>模型<input v-model="adminModel" /></label><label>平台 API Key<input v-model="adminKey" type="password" autocomplete="off" /></label>
            <label class="ai-consent"><input v-model="adminEnabled" type="checkbox" />立即向资格用户启用</label>
            <button type="button" :disabled="!adminKey" @click="savePlatform">保存平台配置</button>
          </section>
        </aside>
      </div>
    </Transition>
  </section>
</template>
