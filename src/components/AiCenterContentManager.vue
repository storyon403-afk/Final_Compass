<script setup>
import{onMounted,ref}from'vue';import{aiCenterApi}from'../api';import SafeHtml from'./SafeHtml.vue';import SafeMarkdown from'./SafeMarkdown.vue';
const guide=ref(null),open=ref(false),error=ref('');
async function load(){try{guide.value=await aiCenterApi.content('USAGE_GUIDE')}catch(e){error.value=e.message}}
onMounted(load)
</script>
<template><section class="ai-content-manager"><button class="ai-guide-toggle" @click="open=!open"><span><b>使用说明</b><small>查看 AI Center 的使用方法</small></span><i>{{open?'−':'＋'}}</i></button><div v-if="open&&guide" class="ai-guide-render"><h3>{{guide.title}}</h3><p>{{guide.subtitle}}</p><SafeMarkdown v-if="guide.contentFormat==='MARKDOWN'" :content="guide.contentBody"/><SafeHtml v-else :content="guide.contentBody"/></div><p v-if="error" class="form-error">{{error}}</p></section></template>
