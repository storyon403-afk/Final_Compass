<script setup>
import { onMounted, ref } from 'vue'
import { aiApi, aiCenterApi } from '../api'
import { aiCenterSettings as settings, loadAiCenterSettings } from '../aiCenterSettings'
import SafeHtml from './SafeHtml.vue'
import SafeMarkdown from './SafeMarkdown.vue'

const emit = defineEmits(['close'])
const message = ref(''), error = ref('')
const adminDefaultProvider = ref('deepseek')
// 平台通道改为数据驱动，后续新增 Provider 只需要增加一项配置和数据库注册。
const platformForms = ref([
  { provider: 'gemini', name: 'Gemini', purpose: '视觉识别', model: 'gemini-3.6-flash', key: '', enabled: true },
  { provider: 'deepseek', name: 'DeepSeek', purpose: '文本推理', model: 'deepseek-v4-pro', key: '', enabled: true },
  { provider: 'kimi', name: 'Kimi / Moonshot', purpose: '文本推理', model: 'kimi-k3', key: '', enabled: true },
  { provider: 'qwen', name: 'Qwen / DashScope', purpose: '文本推理', model: 'qwen3.8-max', key: '', enabled: true }
])
const adminHermesKey = ref(''), adminHermesEnabled = ref(false)
const adminReviewProvider = ref('deepseek'), adminReviewModel = ref('deepseek-v4-pro'), adminReviewKey = ref(''), adminReviewEnabled = ref(true)
const guide = ref(null), vcp = ref(null), editing = ref(''), busy = ref(false)
const visionFeatures=ref({auxiliaryEnabled:true,ephemeralEnabled:true,storedEnabled:true,defaultVisionProvider:'gemini'})
const internalTestOpen = ref(false)
const usagePolicy = ref({ limitsEnabled: true, callsPerMinute: 6, platformDailyCalls: 20, platformMonthlyTokens: 100000 })

const platformStatus = id => settings.dashboard.platformProviders?.find(item => item.provider === id)

async function saveDefault() {
  try { await aiApi.savePlatformDefault(adminDefaultProvider.value); message.value = '平台默认 Provider 已更新。'; await loadAiCenterSettings() }
  catch (e) { error.value = e.message }
}
async function saveUsagePolicy() {
  try {
    await aiApi.saveUsagePolicy({ internalTestOpen: internalTestOpen.value, ...usagePolicy.value })
    message.value = '平台 API Key 使用控制已更新。'
    await loadAiCenterSettings()
  } catch (e) { error.value = e.message }
}
async function savePlatform(provider) {
  const form = platformForms.value.find(item => item.provider === provider)
  if (!form) { error.value = `不支持的平台 Provider：${provider}`; return }
  try {
    await aiApi.savePlatformKey(provider, form.model, form.key, form.enabled)
    form.key = ''
    message.value = `${form.name} ${form.purpose}通道已保存。`
    await loadAiCenterSettings()
  } catch (e) { error.value = e.message }
}
async function saveHermes() {
  try {
    await aiApi.savePlatformKey('hermes', 'hermes-agent', adminHermesKey.value, adminHermesEnabled.value)
    adminHermesKey.value = ''; message.value = 'Hermes Agent Runtime 配置已保存。'
    await loadAiCenterSettings()
  } catch (e) { error.value = e.message }
}
async function saveReviewPlatform() {
  try { await aiApi.savePlatformReviewKey(adminReviewProvider.value, adminReviewModel.value, adminReviewKey.value, adminReviewEnabled.value); adminReviewKey.value = ''; message.value = 'MultiWeb AI 平台审核通道已保存。'; await loadAiCenterSettings() }
  catch (e) { error.value = e.message }
}
async function saveVisionFeatures(){try{await aiApi.saveVisionFeatures(visionFeatures.value);message.value='用户视觉辅助开关已保存。';await loadAiCenterSettings()}catch(e){error.value=e.message}}
async function loadPages() {
  try { [guide.value, vcp.value] = await Promise.all([aiCenterApi.content('USAGE_GUIDE'), aiCenterApi.content('VCP_INTRO')]) }
  catch (e) { error.value = e.message }
}
async function savePage(key, page) {
  busy.value = true; error.value = ''
  try {
    const updated = await aiCenterApi.updateContent(key, { title: page.title, subtitle: page.subtitle, contentFormat: page.contentFormat, contentBody: page.contentBody })
    if (key === 'USAGE_GUIDE') guide.value = updated; else vcp.value = updated
    editing.value = ''; message.value = '页面内容已发布。'
  } catch (e) { error.value = e.message } finally { busy.value = false }
}

onMounted(async () => {
  if (!settings.loaded) await loadAiCenterSettings()
  adminDefaultProvider.value = settings.dashboard.defaultProvider || 'deepseek'
  internalTestOpen.value = settings.dashboard.internalTestOpen === true
  const policy=settings.dashboard.usagePolicy||{};usagePolicy.value={limitsEnabled:policy.qualified_user_limits_enabled!==false,callsPerMinute:Number(policy.calls_per_minute||6),platformDailyCalls:Number(policy.platform_daily_calls||20),platformMonthlyTokens:Number(policy.platform_monthly_tokens||100000)}
  const review = settings.dashboard.platformReviewConfig || {}
  const vision=settings.dashboard.visionFeatures||{};visionFeatures.value={auxiliaryEnabled:vision.user_vision_auxiliary_enabled!==false,ephemeralEnabled:vision.user_vision_ephemeral_key_enabled!==false,storedEnabled:vision.user_vision_stored_key_enabled!==false,defaultVisionProvider:vision.default_vision_provider||'gemini'}
  adminReviewProvider.value = review.provider || 'deepseek'; adminReviewModel.value = review.model_name || 'deepseek-v4-pro'; adminReviewEnabled.value = review.enabled !== false
  await loadPages()
})
</script>

<template>
  <div class="modal-backdrop control-center-backdrop" @click.self="emit('close')">
    <section class="upload-modal control-center-modal">
      <button class="modal-close" type="button" @click="emit('close')">×</button>
      <span class="eyebrow">管理员专属</span><h2>控制中心</h2>
      <p>集中管理平台模型通道、Agent Runtime 与页面内容；普通用户看不到这些选项。</p>
      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="message" class="form-success">{{ message }}</p>

      <section class="control-center-section ai-key-usage-control">
        <h3>API Key 使用控制</h3>
        <p>按“使用资格 → 请求频率 → 平台额度”三层控制平台付费 Key；管理员不受限，用户自己的 Key 仅受频率层控制。</p>
        <div class="ai-platform-card">
          <header><b>第一层 · 使用资格</b><small>{{ internalTestOpen ? '全体登录用户' : '按月度资格' }}</small></header>
          <label class="ai-setting-consent"><input v-model="internalTestOpen" type="checkbox">全体用户开放平台 AI</label>
          <p>关闭后恢复按活跃度排名发放资格；开启后所有已登录用户获得资格。</p>
        </div>
        <div class="ai-platform-card">
          <header><b>第二、三层 · 有资格用户用量</b><small>{{ usagePolicy.limitsEnabled ? '执行限额' : '不限额模式' }}</small></header>
          <label class="ai-setting-consent"><input v-model="usagePolicy.limitsEnabled" type="checkbox">对有资格的普通用户启用限额</label>
          <template v-if="usagePolicy.limitsEnabled">
            <label>每分钟请求次数<input v-model.number="usagePolicy.callsPerMinute" type="number" min="1" max="600"></label>
            <label>每用户每日平台调用次数<input v-model.number="usagePolicy.platformDailyCalls" type="number" min="1" max="100000"></label>
            <label>每用户每月平台 Token<input v-model.number="usagePolicy.platformMonthlyTokens" type="number" min="1000" max="1000000000"></label>
          </template>
        </div>
        <div class="ai-setting-actions"><button type="button" @click="saveUsagePolicy">应用平台控制模式</button></div>
      </section>

      <section class="control-center-section">
        <h3>平台默认模型</h3>
        <label>默认 Provider
          <select v-model="adminDefaultProvider">
            <option v-for="item in settings.dashboard.providers || []" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <div class="ai-setting-actions"><button type="button" @click="saveDefault">设为平台默认</button></div>
      </section>

      <section class="control-center-section">
        <h3>双模型拍题链路</h3>
        <p>图片先由 Gemini 识别题面，再由 DeepSeek 按用户意图分析。两把 Key 分开加密保存。</p>
        <div v-for="form in platformForms" :key="form.provider" class="ai-platform-card">
          <header><b>{{ form.name }} · {{ form.purpose }}</b><small>{{ platformStatus(form.provider)?.enabled ? '已启用' : '未配置或已停用' }}</small></header>
          <label>模型<input v-model="form.model"></label>
          <label>API Key<input v-model="form.key" type="password" autocomplete="new-password"></label>
          <label class="ai-setting-consent"><input v-model="form.enabled" type="checkbox">启用此平台通道</label>
          <button :disabled="!form.key || !form.model" @click="savePlatform(form.provider)">保存 {{ form.name }} 配置</button>
        </div>
      </section>

      <section class="control-center-section">
        <h3>MultiWeb AI 平台审核</h3>
        <p>三份网页 AI 结果由用户选定的模型总结后，再调用此通道审核。允许和平台默认模型使用相同 Provider、模型或 Key。</p>
        <div class="ai-platform-card">
          <header><b>审核模型通道</b><small>{{ settings.dashboard.platformReviewConfig?.enabled ? '已启用' : '未配置或已停用' }}</small></header>
          <label>Provider<select v-model="adminReviewProvider"><option v-for="item in settings.dashboard.providers || []" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
          <label>模型<input v-model="adminReviewModel"></label>
          <label>平台审核 API Key<input v-model="adminReviewKey" type="password" autocomplete="new-password"></label>
          <label class="ai-setting-consent"><input v-model="adminReviewEnabled" type="checkbox">启用平台审核</label>
          <button :disabled="!adminReviewKey || !adminReviewModel" @click="saveReviewPlatform">保存审核配置</button>
        </div>
      </section>
      <section class="control-center-section"><h3>用户视觉辅助模型</h3><p>关闭总开关后用户界面不再显示视觉组件，后端同时拒绝视觉调用；已保存 Key 不会删除。</p><label class="ai-setting-consent"><input v-model="visionFeatures.auxiliaryEnabled" type="checkbox">允许用户配置独立视觉模型</label><label class="ai-setting-consent"><input v-model="visionFeatures.ephemeralEnabled" type="checkbox">允许临时视觉 Key</label><label class="ai-setting-consent"><input v-model="visionFeatures.storedEnabled" type="checkbox">允许保存视觉 Key</label><label>默认视觉 Provider<select v-model="visionFeatures.defaultVisionProvider"><option value="gemini">Gemini</option><option value="doubao">Doubao</option></select></label><button @click="saveVisionFeatures">保存视觉功能开关</button></section>

      <section class="control-center-section">
        <h3>Hermes Agent Runtime</h3>
        <div class="ai-platform-card">
          <header><b>Hermes Agent Gateway</b><small>{{ platformStatus('hermes')?.enabled ? '已启用' : '未配置或已停用' }}</small></header>
          <label>Hermes API Server Key<input v-model="adminHermesKey" type="password" autocomplete="new-password"></label>
          <label class="ai-setting-consent"><input v-model="adminHermesEnabled" type="checkbox">启用平台 Hermes Runtime</label>
          <button :disabled="!adminHermesKey" @click="saveHermes">保存 Hermes 配置</button>
        </div>
      </section>

      <section class="control-center-section">
        <h3>页面内容管理</h3>
        <div v-if="!guide && !vcp" class="empty-state">正在加载页面内容…</div>
        <div v-else class="ai-content-tabs">
          <button :class="{ active: editing === 'USAGE_GUIDE' }" @click="editing = editing === 'USAGE_GUIDE' ? '' : 'USAGE_GUIDE'">编辑使用说明</button>
          <button :class="{ active: editing === 'VCP_INTRO' }" @click="editing = editing === 'VCP_INTRO' ? '' : 'VCP_INTRO'">编辑 VCP 介绍</button>
        </div>
        <div v-if="editing && ((editing === 'USAGE_GUIDE' && guide) || (editing === 'VCP_INTRO' && vcp))" class="ai-content-preview">
          <template v-for="page in [editing === 'USAGE_GUIDE' ? guide : vcp]" :key="editing">
            <div class="ai-guide-render">
              <h3>{{ page.title }}</h3><p>{{ page.subtitle }}</p>
              <SafeMarkdown v-if="page.contentFormat === 'MARKDOWN'" :content="page.contentBody" />
              <SafeHtml v-else :content="page.contentBody" />
            </div>
            <form class="ai-content-editor" @submit.prevent="savePage(editing, page)">
              <label>标题<input v-model="page.title" maxlength="200"></label>
              <label>副标题<textarea v-model="page.subtitle" rows="2" maxlength="500"></textarea></label>
              <label>内容格式
                <select v-model="page.contentFormat">
                  <option value="HTML">HTML 网页</option>
                  <option value="MARKDOWN">Markdown</option>
                </select>
              </label>
              <label>{{ page.contentFormat === 'MARKDOWN' ? 'Markdown 内容' : 'HTML 网页内容' }}
                <textarea v-model="page.contentBody" rows="18" maxlength="100000" spellcheck="false"></textarea>
              </label>
              <small v-if="page.contentFormat === 'HTML'">支持常用 HTML；script、事件属性和危险链接会被过滤。</small>
              <small v-else>支持标题、列表、表格、代码、公式和图片等 Markdown 语法。</small>
              <button :disabled="busy">{{ busy ? '保存中…' : '发布更新' }}</button>
            </form>
          </template>
        </div>
      </section>
    </section>
  </div>
</template>
