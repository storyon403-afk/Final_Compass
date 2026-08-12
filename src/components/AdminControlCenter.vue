<script setup>
import { onMounted, ref } from 'vue'
import { aiApi, aiCenterApi } from '../api'
import { aiCenterSettings as settings, loadAiCenterSettings } from '../aiCenterSettings'
import SafeHtml from './SafeHtml.vue'
import SafeMarkdown from './SafeMarkdown.vue'

const emit = defineEmits(['close'])
const message = ref(''), error = ref('')
const adminDefaultProvider = ref('deepseek')
const adminGeminiModel = ref('gemini-3.6-flash'), adminGeminiKey = ref(''), adminGeminiEnabled = ref(true)
const adminDeepseekModel = ref('deepseek-v4-flash'), adminDeepseekKey = ref(''), adminDeepseekEnabled = ref(true)
const adminHermesKey = ref(''), adminHermesEnabled = ref(false)
const adminReviewProvider = ref('deepseek'), adminReviewModel = ref('deepseek-v4-flash'), adminReviewKey = ref(''), adminReviewEnabled = ref(true)
const guide = ref(null), vcp = ref(null), editing = ref(''), busy = ref(false)

const platformStatus = id => settings.dashboard.platformProviders?.find(item => item.provider === id)

async function saveDefault() {
  try { await aiApi.savePlatformDefault(adminDefaultProvider.value); message.value = '平台默认 Provider 已更新。'; await loadAiCenterSettings() }
  catch (e) { error.value = e.message }
}
async function savePlatform(provider) {
  const gemini = provider === 'gemini', model = gemini ? adminGeminiModel.value : adminDeepseekModel.value
  const key = gemini ? adminGeminiKey.value : adminDeepseekKey.value
  const enabled = gemini ? adminGeminiEnabled.value : adminDeepseekEnabled.value
  try {
    await aiApi.savePlatformKey(provider, model, key, enabled)
    if (gemini) adminGeminiKey.value = ''; else adminDeepseekKey.value = ''
    message.value = `${gemini ? 'Gemini 视觉识别' : 'DeepSeek 解题'}通道已保存。`
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
  const review = settings.dashboard.platformReviewConfig || {}
  adminReviewProvider.value = review.provider || 'deepseek'; adminReviewModel.value = review.model_name || 'deepseek-v4-flash'; adminReviewEnabled.value = review.enabled !== false
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
        <div class="ai-platform-card">
          <header><b>① Gemini 视觉识别</b><small>{{ platformStatus('gemini')?.enabled ? '已启用' : '未配置或已停用' }}</small></header>
          <label>Gemini 模型<input v-model="adminGeminiModel"></label>
          <label>Google API Key<input v-model="adminGeminiKey" type="password" autocomplete="new-password"></label>
          <label class="ai-setting-consent"><input v-model="adminGeminiEnabled" type="checkbox">启用视觉识别</label>
          <button :disabled="!adminGeminiKey || !adminGeminiModel" @click="savePlatform('gemini')">保存 Gemini 配置</button>
        </div>
        <div class="ai-platform-card">
          <header><b>② DeepSeek 解题</b><small>{{ platformStatus('deepseek')?.enabled ? '已启用' : '未配置或已停用' }}</small></header>
          <label>DeepSeek 模型<input v-model="adminDeepseekModel"></label>
          <label>DeepSeek API Key<input v-model="adminDeepseekKey" type="password" autocomplete="new-password"></label>
          <label class="ai-setting-consent"><input v-model="adminDeepseekEnabled" type="checkbox">启用最终分析</label>
          <button :disabled="!adminDeepseekKey || !adminDeepseekModel" @click="savePlatform('deepseek')">保存 DeepSeek 配置</button>
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
