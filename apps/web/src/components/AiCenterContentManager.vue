<script setup>
import{ref}from'vue';import{aiCenterApi}from'../api';import SafeHtml from'./SafeHtml.vue';import SafeMarkdown from'./SafeMarkdown.vue';
const guide=ref(null),open=ref(false),loading=ref(false),error=ref('');
// 使用说明按需加载并缓存在组件内，避免每次打开 AI 菜单都请求正文。
async function show(){open.value=true;if(guide.value||loading.value)return;loading.value=true;error.value='';try{guide.value=await aiCenterApi.content('USAGE_GUIDE')}catch(e){error.value=e.message}finally{loading.value=false}}
</script>
<template>
  <section class="ai-content-manager">
    <button class="ai-guide-toggle" type="button" @click="show">
      <span><b>使用说明</b><small>在大窗口中查看 AI Center 的使用方法</small></span><i>›</i>
    </button>
  </section>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop ai-guide-backdrop" @click.self="open=false">
      <section class="upload-modal ai-guide-modal" role="dialog" aria-modal="true" aria-labelledby="ai-guide-title">
        <button class="modal-close" type="button" aria-label="关闭使用说明" @click="open=false">×</button>
        <span class="eyebrow">AI CENTER</span>
        <h2 id="ai-guide-title">{{guide?.title||'使用说明'}}</h2>
        <p v-if="guide">{{guide.subtitle}}</p>
        <p v-if="error" class="form-error">{{error}}</p>
        <div v-else-if="loading" class="empty-state">正在加载使用说明…</div>
        <article v-else-if="guide" class="ai-guide-render">
          <SafeMarkdown v-if="guide.contentFormat==='MARKDOWN'" :content="guide.contentBody"/>
          <SafeHtml v-else :content="guide.contentBody"/>
        </article>
      </section>
    </div>
  </Teleport>
</template>
