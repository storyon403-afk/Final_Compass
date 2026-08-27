<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isAdmin, messageApi } from '../api'
const emit = defineEmits(['close','updated'])
const router = useRouter()
const items=ref([]),selected=ref(null),loading=ref(true),error=ref(''),composing=ref(false),busy=ref(false)
const form=ref({subject:'',body:''})
async function load(){loading.value=true;try{items.value=await messageApi.inbox()}catch(e){error.value=e.message}finally{loading.value=false}}
async function open(item){selected.value=item;if(!item.read){await messageApi.read(item.id);item.read=true;emit('updated')}}
async function readAll(){await messageApi.readAll();items.value.forEach(item=>item.read=true);emit('updated')}
async function contact(){busy.value=true;error.value='';try{await messageApi.contactAdmin(form.value);form.value={subject:'',body:''};composing.value=false}catch(e){error.value=e.message}finally{busy.value=false}}
async function follow(){const link=selected.value?.linkPath;if(!link)return;emit('close');await router.push(link)}
onMounted(load)
</script>
<template><div class="modal-backdrop message-center-layer" @click.self="emit('close')"><section class="message-center-modal" role="dialog" aria-modal="true"><header><div><span>INBOX · 站内消息</span><h2>消息</h2></div><div><button v-if="!isAdmin" type="button" @click="composing=true">联系管理员</button><button type="button" @click="readAll">全部已读</button><button class="message-close" type="button" @click="emit('close')">×</button></div></header><p v-if="error" class="form-error">{{error}}</p><main><aside><p v-if="loading">正在收取消息…</p><p v-else-if="!items.length">收件箱还是空的。</p><button v-for="item in items" :key="item.id" :class="{unread:!item.read,active:selected?.id===item.id}" @click="open(item)"><i></i><span><b>{{item.subject}}</b><small>{{item.sender}} · {{item.createdAt}}</small></span></button></aside><article v-if="selected" class="message-letter"><button class="message-letter-back" type="button" @click="selected=null">← 返回收件箱</button><small>{{selected.sender}} · {{selected.createdAt}}</small><h3>{{selected.subject}}</h3><p>{{selected.body}}</p><button v-if="selected.linkPath" type="button" @click="follow">前往相关内容 ↗</button></article><article v-else class="message-letter empty"><span>选择一封消息阅读</span></article></main></section><form v-if="composing" class="upload-modal message-compose" @submit.prevent="contact"><button class="modal-close" type="button" @click="composing=false">×</button><span class="eyebrow">TO · 管理员</span><h2>联系管理员</h2><p>这封消息只会发送给管理员，用户之间不能互发私信。</p><label>主题<input v-model.trim="form.subject" maxlength="120" required></label><label>正文<textarea v-model.trim="form.body" maxlength="4000" rows="8" required></textarea></label><button class="primary-button" :disabled="busy">{{busy?'发送中…':'发送给管理员'}}</button></form></div></template>
