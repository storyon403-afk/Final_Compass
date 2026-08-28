<script setup>
import {
  computed,
  defineAsyncComponent,
  onMounted,
  onUnmounted,
  ref,
} from "vue";
import { useRoute } from "vue-router";
import { aiApi, aiCenterApi, chatApi } from "../api";
import { agentFailureMessage, hasExplicitDivision, readableRuntimeResult, requestsKnowledge } from "../lib/runtimePresentation";
import { parseSse } from "../lib/sse";
import AiCenterSettings from "../components/AiCenterSettings.vue";
import { aiCenterSettings as settings } from "../aiCenterSettings";
const SafeMarkdown = defineAsyncComponent(
    () => import("../components/SafeMarkdown.vue"),
  ),
  route = useRoute(),
  modes = {
    CHAT: { name: "Chat Runtime", hint: "提出你的问题，结合知识库回答" },
    AGENT: { name: "Agent Runtime", hint: "描述需要本地 Agent 完成的目标（可生成文件）" },
    MULTI_WEB_AGENT: {
      name: "MultiWeb AI",
      hint: "描述需要多个网页 AI 分工、审查并汇总的目标",
    },
  },
  mode = computed(() => route.meta.runtime || "CHAT"),
  info = computed(() => modes[mode.value] || modes.CHAT),
  input = ref(""),
  busy = ref(false),
  messages = ref([]),
  attachments = ref([]),
  fileInput = ref(null),
  extensionConnected = ref(false),
  webSearchEnabled = ref(false),
  multiWebPending = ref(false),
  activitySteps = ref([]),
  currentActivity = ref(""),
  chatSessionKey = ref("");
let activityTimer = null, requestController = null, pollTimer = null;
const cancelledRunKeys = new Set();
const activeRunKey = ref("");
function releaseConversationDownloads(){
  for(const message of messages.value)for(const artifact of message.artifactLinks||[])if(artifact.url?.startsWith('blob:'))URL.revokeObjectURL(artifact.url);
}
function startActivity(){const steps=mode.value==='CHAT'?['检索知识库','匹配模型与凭据','生成回答']:mode.value==='AGENT'?['检索数据库知识','本地 Agent 执行任务',webSearchEnabled.value?'通过浏览器补充搜索':'仅使用数据库资料','整理结果与文件产物']:['启动 MultiWeb AI','打开网页 AI','分派研究与审查角色','等待各 AI 返回','融合与检查结果'];let index=0;activitySteps.value=[];currentActivity.value=steps[0];activitySteps.value.push(steps[0]);activityTimer=setInterval(()=>{if(index<steps.length-1){index++;currentActivity.value=steps[index];activitySteps.value.push(steps[index])}},1800)}
function stopActivity(){if(activityTimer)clearInterval(activityTimer);activityTimer=null;currentActivity.value=''}
function files(e) {
  for (const file of Array.from(e.target.files || []))
    if (attachments.value.length < 3 && file.size <= 20 * 1024 * 1024)
      attachments.value.push({ file, name: file.name });
  e.target.value = "";
}
async function stopRuntime(){
  if(!busy.value)return;
  requestController?.abort();
  if(activeRunKey.value){const cancelled=activeRunKey.value;cancelledRunKeys.add(cancelled);window.postMessage({source:"FINALS_COMPASS_WEBAPP",type:"STOP_RUN",runKey:cancelled},"*");try{await aiCenterApi.cancelDispatch(cancelled)}catch{}activeRunKey.value=''}
  if(pollTimer)clearTimeout(pollTimer);
  pollTimer=null;
  sessionStorage.removeItem("finals-compass.multiweb-run");multiWebPending.value=false;stopActivity();busy.value=false;messages.value.push({role:"assistant",content:"已取消当前任务。"});requestController=null;
}
async function newConversation(){
  if(activeRunKey.value){try{await aiCenterApi.cancelDispatch(activeRunKey.value)}catch{}activeRunKey.value=''}
  sessionStorage.removeItem("finals-compass.multiweb-run");releaseConversationDownloads();messages.value=[];chatSessionKey.value='';settings.ephemeralApiKey='';settings.reviewEphemeralApiKey='';settings.visionEphemeralApiKey='';
}
async function convert(content, pending) {
  const converted = [];
  for (const item of pending) {
    if(item.file.type.startsWith('image/')){
      let visionFields;
      if(settings.credentialSource==='PLATFORM'){
        const platformVision=settings.dashboard.platformProviders?.find(item=>item.provider==='gemini'&&item.enabled);
        if(!platformVision)throw new Error('管理员尚未启用平台视觉识别通道。');
        visionFields={provider:'gemini',model:platformVision.model_name,credentialSource:'PLATFORM',ephemeralApiKey:null};
      }else if(settings.credentialSource==='EPHEMERAL_BYOK'){
        if(!settings.visionEnabled)throw new Error('图片需要视觉能力。请在“仅本次使用”中启用 Gemini 或 Doubao 视觉辅助。');
        visionFields={provider:settings.visionProvider,model:settings.visionModel,credentialSource:settings.visionCredentialSource,ephemeralApiKey:settings.visionCredentialSource==='EPHEMERAL_BYOK'?settings.visionEphemeralApiKey:null};
      }else throw new Error('已保存主 Key 暂未绑定独立视觉链路。请改用平台额度，或选择“仅本次使用”并启用视觉辅助。');
      const vision=await aiApi.analyzeVision(item.file,visionFields);
      converted.push(`\n\n## 图片识别：${vision.fileName}\n\n${vision.markdown}`);
    }else{
      const doc = await aiApi.convertAttachment(item.file);
      converted.push(`\n\n## 附件：${doc.fileName}\n\n${doc.markdown}`);
    }
  }
  return `${content || "请分析附件"}${converted.join("")}`;
}
async function sendChat(goal) {
  if (!chatSessionKey.value) {
    const session = await chatApi.createSession();
    chatSessionKey.value = session.sessionKey;
  }
  const assistant = { role: "assistant", content: "", sources: [] };
  messages.value.push(assistant);
  requestController = new AbortController();
  const response = await chatApi.streamMessages(chatSessionKey.value, {
    message: goal,
    credentialSource: settings.credentialSource,
    provider: settings.credentialSource !== "PLATFORM" ? settings.provider : null,
    model: settings.credentialSource !== "PLATFORM" ? settings.model : null,
    ephemeralApiKey: settings.credentialSource === "EPHEMERAL_BYOK" ? settings.ephemeralApiKey : null,
  });
  for await (const { event, data } of parseSse(response)) {
    if (requestController.signal.aborted) break;
    if (event === "sources") assistant.sources = data.sources || [];
    else if (event === "delta") assistant.content += data.text || "";
    else if (event === "done") assistant.traceId = data.traceId || "";
    else if (event === "error") throw new Error(`${data.message || "Chat 服务返回错误"}${data.traceId ? `（AI Trace：${data.traceId}）` : ""}`);
  }
  if (!assistant.content) assistant.content = "（模型没有返回内容）";
}
function credentialFields(review = false) {
  const source = review ? settings.reviewCredentialSource : settings.credentialSource;
  return { credentialSource: source, provider: source === "PLATFORM" ? null : (review ? settings.reviewProvider : settings.provider), model: source === "PLATFORM" ? null : (review ? settings.reviewModel : settings.model), ephemeralApiKey: source === "EPHEMERAL_BYOK" ? (review ? settings.reviewEphemeralApiKey : settings.ephemeralApiKey) : null, credentialPurpose: review ? "MULTIWEB_REVIEW" : "MULTIWEB_SUMMARY" };
}
async function invokeInternal(message, review = false, useKnowledge = false, runKey = "") {
  if (runKey && cancelledRunKeys.has(runKey)) throw new DOMException("任务已取消", "AbortError");
  requestController = new AbortController();
  const session = await chatApi.createSession(), response = await chatApi.streamMessages(session.sessionKey, { message, useKnowledge, ...credentialFields(review) }, requestController.signal);
  let output = "";
  for await (const { event, data } of parseSse(response)) { if (event === "delta") output += data.text || ""; else if (event === "error") throw new Error(data.message || "模型调用失败"); }
  if (!output.trim()) throw new Error(review ? "审核模型没有返回内容" : "总结模型没有返回内容");
  if (runKey && cancelledRunKeys.has(runKey)) throw new DOMException("任务已取消", "AbortError");
  return output.trim();
}
async function multiWebAssignments(goal, participants) {
  if (!hasExplicitDivision(goal)) return participants.map(item => ({ ...item, assignment: goal }));
  const planned = await invokeInternal(`你是 MultiWeb AI 的任务分发 Agent。请把用户任务拆成 KIMI、DEEPSEEK、QWEN 三份可独立执行的精简提示，每份保留必要背景并明确负责部分。只输出严格 JSON 对象，键必须是 KIMI、DEEPSEEK、QWEN。\n\n用户任务：${goal}`, false, requestsKnowledge(goal));
  let parsed = {}; try { parsed = JSON.parse(planned.match(/\{[\s\S]*\}/)?.[0] || ""); } catch {}
  const fallback = { KIMI: `负责资料与事实部分：${goal}`, DEEPSEEK: `负责分析、论证与风险部分：${goal}`, QWEN: `负责结构、方案与表达部分：${goal}` };
  return participants.map(item => { const key=item.providerKey||item.provider_key; return { ...item, assignment: parsed[key] || fallback[key] || goal }; });
}
async function summarizeAndReview(runKey, goal, outputs) {
  const source = Object.entries(outputs).map(([key,value]) => `## ${key} 输出\n${String(value).slice(0,22000)}`).join("\n\n");
  messages.value.push({ role:"assistant", traceId:runKey, content:"三份网页 AI 输出已返回，正在使用总结模型合并…" });
  const summary = await invokeInternal(`请综合三份网页 AI 输出，完成原始任务。交叉核对信息、处理冲突、去除重复并补足遗漏，输出一份完整的新答案。不要提及协作过程。\n\n原始任务：${goal}\n\n${source}`, false, requestsKnowledge(goal), runKey);
  if (cancelledRunKeys.has(runKey)) return;
  messages.value.push({ role:"assistant", traceId:runKey, content:"总结稿已完成，正在使用审核模型复核…" });
  const reviewed = await invokeInternal(`请审核并修订下面的总结稿。检查是否完整回应原始任务，纠正事实冲突、逻辑漏洞、遗漏和不可靠表述。只输出审核后的最终答案。允许与总结模型相同，但必须重新检查。\n\n原始任务：${goal}\n\n总结稿：\n${summary}`, true, false, runKey);
  if (cancelledRunKeys.has(runKey)) return;
  await aiCenterApi.reportParticipant(runKey,{provider:"PLATFORM",status:"COMPLETED",result:reviewed,errorCode:null,phase:"SYNTHESIS"});
  messages.value.push({ role:"assistant", traceId:runKey, content:reviewed });
}
async function sendAgent(goal) {
  const conversationContext = messages.value.slice(0, -1).slice(-12).map(item => `${item.role==='user'?'用户':'助手'}：${item.content}`).join('\n');
  const contextualGoal = conversationContext ? `以下是本次对话上下文：\n${conversationContext}\n\n当前任务：${goal}` : goal;
  const result = await aiCenterApi.dispatch({
      runtimeType: "AGENT",
      goal: contextualGoal,
      credentialSource: settings.credentialSource,
      provider: settings.provider,
      model: settings.model,
      ephemeralApiKey: settings.credentialSource === "EPHEMERAL_BYOK" ? settings.ephemeralApiKey : null,
      allowWebSearch: webSearchEnabled.value,
      providers: [],
    }),
    runKey = result.run.run_key;
  activeRunKey.value = runKey;
  messages.value.push({
    role: "assistant",
    traceId: runKey,
    content: "Agent 正在处理…",
  });
  // Hermes may need substantial time to render and validate a large PDF/PPTX.
  // Keep this watchdog beyond the gateway's 30-minute default so the gateway
  // remains the single source of truth for HERMES_TIMEOUT.
  const deadline = Date.now() + 35 * 60 * 1000;
  for (;;) {
    if (Date.now() > deadline) throw new Error("Agent 长时间没有返回状态，请检查本地 Agent Gateway；任务已保留，可稍后按 trace 查询。");
    await new Promise((resolve) => { pollTimer = setTimeout(resolve, 2000); });
    if (activeRunKey.value !== runKey) return;
    const { run } = await aiCenterApi.dispatchRun(runKey);
    if (run.status === "RUNNING" || run.status === "WAITING_EXTENSION") continue;
    if (run.status === "WAITING_CONFIGURATION")
      throw new Error("Agent Gateway 未连接：请先启动本地 Agent（默认监听 127.0.0.1:8642）。");
    let artifacts = [];
    try { artifacts = await aiCenterApi.dispatchArtifacts(runKey); } catch {}
    const links = [];
    for (const artifact of artifacts || []) {
      const blob = await aiCenterApi.dispatchArtifactFile(runKey, artifact.id);
      links.push({ name: artifact.fileName || artifact.file_name || `产物-${artifact.id}`, url: URL.createObjectURL(blob) });
    }
    const answer = run.status === "FAILED"
      ? agentFailureMessage(run.error_code || run.errorCode)
      : readableRuntimeResult(run.response_payload);
    if (run.status !== "FAILED" && !links.length) {
      links.push({ name: "hermes-agent-report.md", url: URL.createObjectURL(new Blob([answer], { type: "text/markdown;charset=utf-8" })) });
    }
    messages.value.push({
      role: "assistant",
      traceId: runKey,
      content: answer,
      artifactLinks: links,
    });
    activeRunKey.value = "";
    return;
  }
}
async function send() {
  const content = input.value.trim(),
    pending = attachments.value.slice();
  if ((!content && !pending.length) || busy.value) return;
  messages.value.push({
    role: "user",
    content: content || "请分析附件",
    files: pending.map((x) => x.name),
  });
  input.value = "";
  attachments.value = [];
  busy.value = true;
  startActivity();
  try {
    const goal = await convert(content, pending);
    if (settings.credentialSource === "EPHEMERAL_BYOK" && !settings.ephemeralApiKey) {
      messages.value.push({ role: "assistant", content: "当前使用“临时 Key”模式，但还没有填写 API Key。请在右上角设置中输入本次使用的 API Key 后再发送。" });
      return;
    }
    if (mode.value === "MULTI_WEB_AGENT" && settings.reviewCredentialSource === "EPHEMERAL_BYOK" && !settings.reviewEphemeralApiKey) throw new Error("当前选择临时审核 Key，但尚未在 AI 菜单的“MultiWeb AI 审核”中填写 API Key。");
    if (mode.value === "MULTI_WEB_AGENT" && settings.reviewCredentialSource === "PLATFORM" && !settings.dashboard.platformReviewConfig?.enabled) throw new Error("管理员尚未配置 MultiWeb AI 平台审核模型。请让管理员配置，或在 AI 菜单中改用自己的审核 Key。");
    if (mode.value === "AGENT") await sendAgent(goal);
    else if (mode.value === "MULTI_WEB_AGENT") {
      if (!extensionConnected.value)
        throw new Error(
          "未检测到浏览器扩展。请先在 AI 菜单中下载安装，并登录要使用的网页 AI。",
        );
      const result = await aiCenterApi.dispatch({
          runtimeType: "MULTI_WEB_AGENT",
          goal,
          credentialSource: settings.credentialSource,
          providers: ["KIMI", "DEEPSEEK", "QWEN"],
        }),
        runKey = result.run.run_key,
        plannedParticipants = await multiWebAssignments(goal, result.participants);
      cancelledRunKeys.delete(runKey);
      messages.value.push({
        role: "assistant",
        traceId: runKey,
        content:
          `${hasExplicitDivision(goal)?"Finals Compass Agent 已拆分任务并分别派发":"同一完整任务已并行派发"}给 Kimi、DeepSeek 和 Qwen。登录后会自动续接；三份输出返回后依次执行总结和审核。`,
      });
      activeRunKey.value = runKey;
      multiWebPending.value = true;
      sessionStorage.setItem("finals-compass.multiweb-run", runKey);
      window.postMessage(
        {
          source: "FINALS_COMPASS_WEBAPP",
          type: "START_RUN",
          runKey,
          goal,
          participants: plannedParticipants,
        },
        "*",
      );
    } else await sendChat(goal);
  } catch (e) {
    if(e.name==='AbortError')return;
    if(activeRunKey.value){try{await aiCenterApi.cancelDispatch(activeRunKey.value)}catch{}activeRunKey.value=''}
    messages.value.push({ role: "assistant", content: e.message });
  } finally {
    if (!multiWebPending.value) { stopActivity(); busy.value = false; }
  }
}
async function extensionMessage(event) {
  const d = event.data;
  if (
    event.source !== window ||
    d?.source !== "FINALS_COMPASS_WEBAGENT_EXTENSION"
  )
    return;
  if (d.type === "READY") extensionConnected.value = true;
  if (d.type !== "PARTICIPANT_STATUS" || !d.runKey) return;
  if (cancelledRunKeys.has(d.runKey)) return;
  try {
    if (d.phase === "BATCH" && d.status === "COMPLETED") {
      const { run } = await aiCenterApi.dispatchRun(d.runKey);
      try { await summarizeAndReview(d.runKey, run.goal || "", d.result || {}); }
      finally { sessionStorage.removeItem("finals-compass.multiweb-run");activeRunKey.value="";multiWebPending.value=false;stopActivity();busy.value=false; }
      return;
    }
    const result = await aiCenterApi.reportParticipant(d.runKey, {
      provider: d.provider,
      status: d.status,
      result: d.result || null,
      errorCode: d.errorCode || null,
      phase: d.phase || "INITIAL",
    });
    if (d.status === "LOGIN_REQUIRED")
      messages.value.push({
        role: "assistant",
        content: `${d.provider} 需要登录或注册。已为你保留任务；完成登录后插件会自动继续。`,
        traceId: d.runKey,
      });
  } catch (e) {
    if(e.name==='AbortError'||cancelledRunKeys.has(d.runKey))return;
    messages.value.push({
      role: "assistant",
      content: `MultiWeb AI 状态回传失败：${e.message}`,
    });
  }
}
onMounted(() => {
  window.addEventListener("message", extensionMessage);
  window.postMessage({ source: "FINALS_COMPASS_WEBAPP", type: "PING" }, "*");
  const savedRun = sessionStorage.getItem("finals-compass.multiweb-run");
  if (mode.value === "MULTI_WEB_AGENT" && savedRun) {
    activeRunKey.value = savedRun;
    multiWebPending.value = true;busy.value = true;startActivity();
    aiCenterApi.dispatchRun(savedRun).then(({ run }) => messages.value.push({ role: "assistant", traceId: savedRun, content: run.status === "WAITING_LOGIN" ? "任务正在等待网页 AI 登录；登录后插件会自动继续。" : `已恢复 MultiWeb AI 任务：${run.status}` })).catch(() => sessionStorage.removeItem("finals-compass.multiweb-run"));
  }
});
onUnmounted(() => {
  window.removeEventListener("message", extensionMessage);
  if (pollTimer) clearTimeout(pollTimer);
  releaseConversationDownloads();
  // 页面切换或登录跳转不等于用户取消；仅“停止任务/新对话”会取消运行。
});
</script>
<template>
  <section class="runtime-chat">
    <header>
      <router-link to="/ai-center">← AI Center</router-link
      ><b>Finals Compass</b>
      <div class="runtime-chat-tools">
        <AiCenterSettings /><button type="button" @click="newConversation">
          新对话
        </button>
      </div>
    </header>
    <main>
      <section v-if="!messages.length" class="runtime-chat-empty">
        <small>{{ info.name }}</small>
        <h1>今天想学什么？</h1>
        <p v-if="mode === 'AGENT' || mode === 'MULTI_WEB_AGENT'" class="extension-state" :class="{ connected: extensionConnected }">{{extensionConnected?"通用浏览器扩展已连接":"通用浏览器扩展未连接，请在 AI 菜单中下载安装"}}</p>
      </section>
      <section v-else class="runtime-chat-thread">
        <article
          v-for="(item, index) in messages"
          :key="index"
          :class="item.role"
        >
          <div class="runtime-chat-message-meta">
            <b>{{ item.role === "user" ? "你" : "Finals Compass" }}</b
            ><small v-if="item.traceId">trace {{ item.traceId }}</small>
          </div>
          <p v-if="item.role === 'user'">{{ item.content }}</p>
          <SafeMarkdown v-else :content="item.content" />
          <div v-if="item.sources?.length" class="runtime-chat-sources"><b>参考来源</b><ul><li v-for="source in item.sources" :key="source.sourceKey || source.title">{{ source.title }}<small v-if="source.heading"> · {{ source.heading }}</small></li></ul></div>
          <div v-if="item.artifactLinks?.length" class="runtime-document-artifacts"><a v-for="artifact in item.artifactLinks" :key="artifact.url" :href="artifact.url" :download="artifact.name">下载产物 · {{ artifact.name }}</a></div>
          <ul v-if="item.files">
            <li v-for="file in item.files" :key="file">{{ file }}</li>
          </ul>
        </article>
        <div v-if="busy" class="runtime-chat-thinking"><b>{{currentActivity}}…</b><ol><li v-for="step in activitySteps" :key="step">{{step}}</li></ol><small>展示的是运行步骤，不包含模型私有推理内容。</small><button class="runtime-stop" type="button" @click="stopRuntime">停止任务</button></div>
      </section>
    </main>
    <footer>
      <div v-if="attachments.length" class="runtime-chat-files">
        <span v-for="(item, index) in attachments" :key="item.name"
          >{{ item.name }}
          <button @click="attachments.splice(index, 1)">×</button></span
        >
      </div>
      <div class="runtime-chat-box">
        <textarea
          v-model="input"
          rows="1"
          maxlength="20000"
          :placeholder="info.hint"
          @keydown.enter.exact.prevent="send"
        ></textarea>
        <div>
          <input
            ref="fileInput"
            class="visually-hidden"
            type="file"
            multiple
            @change="files"
          /><button type="button" @click="fileInput?.click()">＋</button
          ><small>{{ info.name }} · {{ settings.credentialSource }}</small
          ><label v-if="mode==='AGENT'" class="runtime-web-toggle"><input v-model="webSearchEnabled" type="checkbox"><span>Web 搜索</span></label
          ><button
            class="runtime-send"
            type="button"
            :disabled="busy || (!input.trim() && !attachments.length)"
            @click="send"
          >
            ↑
          </button>
        </div>
      </div>
      <p>AI 可能会犯错，请核对重要信息。</p>
    </footer>
  </section>
</template>
