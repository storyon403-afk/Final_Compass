<script setup>
import { computed, onMounted, ref } from 'vue'
import { aiApi, isAdmin } from '../api'

const loading = ref(true)
const error = ref('')
const message = ref('')
const dashboard = ref({
  month: '', myScore: 0, platformEligible: false, leaderboard: [], skills: [],
  platformProviders: [], savedKeys: [], encryptedStorageAvailable: false
})
const provider = ref('deepseek')
const skillId = ref('study-analysis-preview')
const input = ref('')
const apiKey = ref('')
const keyLabel = ref('我的学习 Key')
const consentToStore = ref(false)
const credentialSource = ref('EPHEMERAL_BYOK')
const result = ref('')
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
    if (dashboard.value.skills.length && !dashboard.value.skills.some((skill) => skill.id === skillId.value)) {
      skillId.value = dashboard.value.skills[0].id
    }
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}

async function saveKey() {
  message.value = ''
  error.value = ''
  if (!consentToStore.value) {
    credentialSource.value = 'EPHEMERAL_BYOK'
    message.value = '已选择不保存：Key 只会随下一次分析请求发送，请求结束后释放。'
    return
  }
  try {
    await aiApi.saveByok(provider.value, apiKey.value, keyLabel.value, true)
    apiKey.value = ''
    credentialSource.value = 'STORED_BYOK'
    message.value = 'API Key 已使用 AES-GCM 加密保存；管理员界面只显示指纹，无法查看原文。'
    await load()
  } catch (reason) { error.value = reason.message }
}

async function removeKey() {
  if (!savedForProvider.value) return
  await aiApi.deleteByok(provider.value)
  credentialSource.value = 'EPHEMERAL_BYOK'
  message.value = '已删除平台保存的密文。'
  await load()
}

async function invoke() {
  invoking.value = true
  error.value = ''
  result.value = ''
  try {
    const response = await aiApi.invoke({
      provider: provider.value,
      skillId: skillId.value,
      credentialSource: credentialSource.value,
      ephemeralApiKey: credentialSource.value === 'EPHEMERAL_BYOK' ? apiKey.value : null,
      input: input.value
    })
    result.value = response.content
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
    message.value = '平台 AI Key 已加密写入，符合资格的用户可选择平台额度。'
    await load()
  } catch (reason) { error.value = reason.message }
}

onMounted(load)
</script>

<template>
  <section class="ai-page">
    <header class="ai-hero">
      <div><span class="ai-scribble">AI BETA · 一起积累，一起探索</span><h1>学习分析实验室</h1><p>把贡献变成下个月的 AI 体验资格，也可以安全使用自己的模型密钥。</p></div>
      <div class="ai-score-note"><small>{{ dashboard.month || '本月' }} 活跃积分</small><strong>{{ dashboard.myScore }}</strong><span>{{ dashboard.platformEligible ? '本月已获得平台 AI 资格' : '上月前 20 名可获得下月资格' }}</span></div>
    </header>

    <p v-if="error" class="ai-alert error">{{ error }}</p>
    <p v-if="message" class="ai-alert">{{ message }}</p>
    <div v-if="loading" class="ai-loading">正在整理本月的贡献轨迹…</div>

    <div v-else class="ai-grid">
      <section class="ai-paper leaderboard-paper">
        <header><div><span>MONTHLY TOP 20</span><h2>本月活跃度排行榜</h2></div><button type="button" @click="load">刷新</button></header>
        <p class="ai-caption">每日登录 +1；提交资料 +2；资料审核通过再 +5；论坛或指南内容通过 +2。相同事件只计一次。</p>
        <ol v-if="dashboard.leaderboard.length" class="ai-ranking">
          <li v-for="item in dashboard.leaderboard" :key="item.user_id">
            <b>{{ item.ranking_position }}</b><span>{{ item.display_name }}</span><strong>{{ item.score }} pts</strong>
          </li>
        </ol>
        <div v-else class="ai-empty">这个月还没有积分，第一笔贡献会从这里发芽。</div>
      </section>

      <section class="ai-paper analysis-paper">
        <header><div><span>PROVIDER × SKILL</span><h2>选择你的 AI 通道</h2></div></header>
        <div class="ai-fields two-columns">
          <label>Provider<input v-model.trim="provider" maxlength="40" placeholder="deepseek" /></label>
          <label>AI Skill<select v-model="skillId"><option v-for="skill in dashboard.skills" :key="skill.id" :value="skill.id">{{ skill.name }}</option></select></label>
        </div>
        <div class="credential-cards">
          <label :class="{ selected: credentialSource === 'PLATFORM' }"><input v-model="credentialSource" type="radio" value="PLATFORM" /><b>平台 AI</b><small>{{ dashboard.platformEligible && platformProvider ? '本月可用' : '需要月度资格与管理员启用' }}</small></label>
          <label :class="{ selected: credentialSource === 'STORED_BYOK' }"><input v-model="credentialSource" type="radio" value="STORED_BYOK" :disabled="!savedForProvider" /><b>已保存的 Key</b><small>{{ savedForProvider ? `指纹 ${savedForProvider.key_fingerprint}` : '尚未保存' }}</small></label>
          <label :class="{ selected: credentialSource === 'EPHEMERAL_BYOK' }"><input v-model="credentialSource" type="radio" value="EPHEMERAL_BYOK" /><b>仅本次使用</b><small>请求结束立即释放</small></label>
        </div>
        <label class="ai-textarea">要分析的学习内容<textarea v-model="input" maxlength="8000" placeholder="粘贴你的复习目标、知识点或需要梳理的内容…"></textarea></label>
        <label v-if="credentialSource === 'EPHEMERAL_BYOK'" class="ai-key-field">本次 API Key<input v-model="apiKey" type="password" autocomplete="off" placeholder="不会写入数据库" /></label>
        <button class="ai-primary" type="button" :disabled="invoking || !input.trim()" @click="invoke">{{ invoking ? '正在安全预检…' : '开始 AI 分析' }}</button>
        <div v-if="result" class="ai-result"><span>PREVIEW RESULT</span><p>{{ result }}</p></div>
      </section>

      <section class="ai-paper key-paper">
        <header><div><span>YOUR KEY, YOUR CHOICE</span><h2>自己的 API Key</h2></div></header>
        <p>你可以不保存，每次请求结束后立即释放；也可以明确同意后加密保存。</p>
        <div class="ai-fields two-columns">
          <label>备注<input v-model="keyLabel" maxlength="80" /></label>
          <label>API Key<input v-model="apiKey" type="password" autocomplete="off" placeholder="请输入完整 Key" /></label>
        </div>
        <label class="consent-line"><input v-model="consentToStore" type="checkbox" />我同意平台加密保存此 Key，供后续请求使用</label>
        <p class="security-note">采用 AES-256-GCM 加密；管理员页面只能看到不可逆指纹。服务器仅在发起请求时短暂解密。平台不会在日志中记录 Key 或完整提示词。</p>
        <div class="ai-actions"><button class="ai-primary" type="button" :disabled="!apiKey" @click="saveKey">{{ consentToStore ? '确认并加密保存' : '确认仅本次使用' }}</button><button v-if="savedForProvider" type="button" @click="removeKey">删除已保存 Key</button></div>
        <small v-if="!dashboard.encryptedStorageAvailable" class="config-warning">当前开发环境未配置加密主密钥，因此只能使用“不保存”模式。</small>
      </section>

      <section class="ai-paper skill-paper">
        <header><div><span>SKILL DOCK</span><h2>Skill 扩展坞</h2></div></header>
        <article v-for="skill in dashboard.skills" :key="skill.id"><b>{{ skill.name }}</b><p>{{ skill.description }}</p><small>{{ skill.id }} · 最多 {{ skill.maxInputLength }} 字符</small></article>
        <p class="ai-caption">后续 Skill 通过后端注册表接入，统一经过登录、权限、输入上限、凭据选择和审计日志，不允许浏览器直接任意调用工具。</p>
      </section>

      <section v-if="isAdmin" class="ai-paper admin-paper">
        <header><div><span>ADMIN ONLY</span><h2>平台 AI 配置</h2></div></header>
        <div class="ai-fields admin-fields">
          <label>Provider<input v-model="adminProvider" maxlength="40" /></label>
          <label>模型名称<input v-model="adminModel" maxlength="120" /></label>
          <label>平台 API Key<input v-model="adminKey" type="password" autocomplete="off" /></label>
        </div>
        <label class="consent-line"><input v-model="adminEnabled" type="checkbox" />保存后立即向本月资格用户启用</label>
        <button class="ai-primary" type="button" :disabled="!adminKey" @click="savePlatform">加密保存平台 Key</button>
      </section>
    </div>
  </section>
</template>
