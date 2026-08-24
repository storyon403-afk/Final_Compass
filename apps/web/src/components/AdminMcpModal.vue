<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { mcpAdminApi } from '../api'

const emit = defineEmits(['close'])
const loading = ref(true), busy = ref(false), error = ref(''), message = ref('')
const data = ref({ servers: [], discoveredTools: [], approvals: [] })
const diffs = ref({})
const serverDraft = ref({ serverKey: '', name: '', description: '', transportType: 'STREAMABLE_HTTP', endpointUri: '', authMode: 'NONE', credentialReference: '', authorizationEndpoint: '', tokenEndpoint: '', clientId: '', scopes: '', stdioCommandText: '', stdioWorkingDirectory: '', allowedHostsText: '', enabled: true })

async function load() {
  loading.value = true; error.value = ''
  try {
    data.value = await mcpAdminApi.overview()
    data.value.discoveredTools = (data.value.discoveredTools || []).map(tool => ({
      ...tool, toolKey: tool.remote_tool_name.replace(/[^A-Za-z0-9_-]/g, '_'), version: '1.0.0',
      riskLevel: 'MEDIUM', permissionsText: 'MCP_TOOL_USE'
    }))
  } catch (reason) { error.value = reason.message } finally { loading.value = false }
}
async function discover(server) {
  busy.value = true; error.value = ''
  try { const result = await mcpAdminApi.discover(server.server_key); message.value = `发现 ${result.toolCount} 个工具，${result.staleBindings} 个绑定已失效`; await load(); await showDiff(server) }
  catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function showDiff(server) {
  try { diffs.value[server.server_key] = await mcpAdminApi.diff(server.server_key) }
  catch (reason) { error.value = reason.message }
}
async function connect(server) {
  busy.value = true; error.value = ''
  try {
    const result = await mcpAdminApi.authorize(server.server_key)
    if (!window.open(result.authorizationUrl, 'finals-compass-mcp-oauth', 'popup,width=640,height=760')) throw new Error('浏览器阻止了授权窗口')
    message.value = '请在授权窗口完成 MCP 连接。'
  } catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function disconnect(server) {
  busy.value = true; error.value = ''
  try { await mcpAdminApi.disconnectOAuth(server.server_key); message.value = `${server.server_key} OAuth 已断开`; await load() }
  catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function requestApproval(tool) {
  busy.value = true; error.value = ''
  try {
    await mcpAdminApi.requestApproval({ discoveredToolId: tool.id, toolKey: tool.toolKey, version: tool.version,
      riskLevel: tool.riskLevel, permissions: tool.permissionsText.split(',').map(v => v.trim()).filter(Boolean) })
    message.value = '已提交审批，需管理员再次确认后才会发布。'; await load()
  } catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function decide(item, approve) {
  busy.value = true; error.value = ''
  try { await mcpAdminApi.decide(item.id, approve, item.reviewNote || ''); message.value = approve ? '工具已发布并固定 Schema。' : '审批已拒绝。'; await load() }
  catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function saveServer() {
  busy.value = true; error.value = ''
  try {
    const draft = serverDraft.value
    await mcpAdminApi.saveServer({ ...draft,
      stdioCommand: draft.stdioCommandText.split('\n').map(v => v.trim()).filter(Boolean),
      allowedHosts: draft.allowedHostsText.split(',').map(v => v.trim()).filter(Boolean) })
    message.value = 'MCP Server 配置已保存，重新发现前不会发布任何工具。'; await load()
  } catch (reason) { error.value = reason.message } finally { busy.value = false }
}
async function oauthMessage(event) { if (event.origin === window.location.origin && event.data?.type === 'fc-mcp-oauth') { message.value = `${event.data.server} OAuth 已连接`; await load() } }
onMounted(() => { window.addEventListener('message', oauthMessage); load() })
onBeforeUnmount(() => window.removeEventListener('message', oauthMessage))
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section class="upload-modal mcp-admin-modal">
      <button class="modal-close" type="button" @click="emit('close')">×</button>
      <span class="eyebrow">AI Runtime · 管理员</span><h2>MCP 管理中心</h2>
      <p>远端发现不会自动发布；Schema 变化会暂停绑定，审批后才允许模型调用。</p>
      <p v-if="error" class="form-error">{{ error }}</p><p v-if="message" class="form-success">{{ message }}</p>
      <div v-if="loading" class="empty-state">正在加载…</div>
      <template v-else>
        <details class="mail-template-editor"><summary><b>新增或更新 MCP Server</b></summary>
          <form class="mail-config-grid" @submit.prevent="saveServer">
            <label>Server Key<input v-model.trim="serverDraft.serverKey" required placeholder="knowledge-mcp" /></label><label>名称<input v-model.trim="serverDraft.name" required /></label>
            <label>Transport<select v-model="serverDraft.transportType"><option>STREAMABLE_HTTP</option><option>STDIO</option></select></label><label>认证<select v-model="serverDraft.authMode"><option>NONE</option><option>PLATFORM_OAUTH</option><option>USER_OAUTH</option><option>SERVICE_TOKEN</option></select></label>
            <label v-if="serverDraft.transportType==='STREAMABLE_HTTP'">MCP Endpoint<input v-model.trim="serverDraft.endpointUri" placeholder="https://mcp.example.com/mcp" /></label><label>允许 Host（逗号分隔）<input v-model.trim="serverDraft.allowedHostsText" /></label>
            <template v-if="serverDraft.authMode.includes('OAUTH')"><label>Authorization Endpoint<input v-model.trim="serverDraft.authorizationEndpoint" /></label><label>Token Endpoint<input v-model.trim="serverDraft.tokenEndpoint" /></label><label>Client ID<input v-model.trim="serverDraft.clientId" /></label><label>Scopes<input v-model.trim="serverDraft.scopes" /></label></template>
            <template v-if="serverDraft.transportType==='STDIO'"><label>命令（每行一个参数）<textarea v-model="serverDraft.stdioCommandText" rows="4"></textarea></label><label>工作目录<input v-model.trim="serverDraft.stdioWorkingDirectory" /></label></template>
            <label v-if="serverDraft.authMode!=='NONE'">凭据引用<input v-model.trim="serverDraft.credentialReference" placeholder="vault:mcp/name" /></label><label>说明<input v-model.trim="serverDraft.description" /></label>
            <label><input v-model="serverDraft.enabled" type="checkbox" /> 启用 Server</label><button type="submit" :disabled="busy">保存 Server</button>
          </form>
        </details>
        <section v-for="server in data.servers" :key="server.id" class="mail-provider-card">
          <div><span class="eyebrow">{{ server.transport_type }} · {{ server.health_status }}</span><h3>{{ server.name }}</h3><p>{{ server.server_key }} · {{ server.auth_mode }}<template v-if="server.oauth_connected"> · OAuth 已连接</template></p></div>
          <button type="button" :disabled="busy" @click="discover(server)">重新发现</button>
          <button type="button" :disabled="busy" @click="showDiff(server)">查看变更</button>
          <button v-if="server.auth_mode.includes('OAUTH')" type="button" :disabled="busy" @click="connect(server)">连接 OAuth</button>
          <button v-if="server.auth_mode.includes('OAUTH') && server.oauth_connected" class="danger-button" type="button" :disabled="busy" @click="disconnect(server)">断开 OAuth</button>
          <ul v-if="diffs[server.server_key]" class="mcp-diff-list"><li v-for="item in diffs[server.server_key]" :key="item.name"><b>{{ item.status }}</b> {{ item.name }}</li></ul>
        </section>
        <h3>当前发现的工具</h3>
        <section v-for="tool in data.discoveredTools" :key="tool.id" class="mail-template-editor">
          <b>{{ tool.server_key }} / {{ tool.remote_tool_name }}</b><p>{{ tool.description }}</p>
          <div class="mail-config-grid"><label>平台 Tool Key<input v-model.trim="tool.toolKey" /></label><label>版本<input v-model.trim="tool.version" /></label><label>风险<select v-model="tool.riskLevel"><option>LOW</option><option>MEDIUM</option><option>HIGH</option></select></label><label>权限（逗号分隔）<input v-model.trim="tool.permissionsText" /></label></div>
          <button type="button" :disabled="busy" @click="requestApproval(tool)">提交审批</button>
        </section>
        <h3>审批队列</h3>
        <section v-for="item in data.approvals" :key="item.id" class="mail-template-editor">
          <b>{{ item.decision }} · {{ item.target_tool_key }}@{{ item.target_version }}</b><p>{{ item.server_key }} / {{ item.remote_tool_name }} · {{ item.risk_level }}</p>
          <textarea v-if="item.decision === 'PENDING'" v-model="item.reviewNote" rows="2" placeholder="审核备注"></textarea>
          <div v-if="item.decision === 'PENDING'"><button type="button" :disabled="busy" @click="decide(item,true)">批准并发布</button><button class="danger-button" type="button" :disabled="busy" @click="decide(item,false)">拒绝</button></div>
        </section>
      </template>
    </section>
  </div>
</template>
