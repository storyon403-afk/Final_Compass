<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { deleteQuestionVineTopic, loadQuestionVineTopics, questionVineTopics } from '../questionVineStore'

const emit = defineEmits(['close', 'jump'])
const router = useRouter()
const sequence = ref('')
const confirming = ref(false)
const message = ref('')
const error = ref('')
const busy = ref(false)
const target = computed(() => questionVineTopics.value.find(topic => topic.id === Number(sequence.value)))
const shiftedCount = computed(() => target.value ? questionVineTopics.value.filter(topic => topic.id > target.value.id).length : 0)
const unansweredTopics = computed(() => questionVineTopics.value.filter(topic => !topic.answers.length).sort((a, b) => a.id - b.id))
watch(sequence, () => { confirming.value = false })

async function confirmDelete() {
  busy.value = true; error.value = ''
  try { const removed = await deleteQuestionVineTopic(sequence.value); if (!removed) return; sequence.value = ''; confirming.value = false; message.value = `已摘除“${removed.title}”，后续叶子序号已连续重排。` }
  catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
async function jumpToTopic(topic) {
  emit('jump')
  await router.push({ path: '/question-vine', hash: `#vine-topic-${topic.id}` })
  window.setTimeout(() => document.getElementById(`vine-topic-${topic.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 80)
}
onMounted(() => loadQuestionVineTopics().catch(cause => { error.value = cause.message }))
</script>

<template>
  <div class="modal-backdrop module-admin-backdrop" @click.self="emit('close')">
    <section class="upload-modal control-center-modal vine-admin-modal" role="dialog" aria-modal="true" aria-labelledby="vine-admin-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <span class="eyebrow">管理员 · 内容治理</span><h2 id="vine-admin-title">问题藤叶片管理</h2>
      <p>按叶片序号定位并摘除违规内容。删除后，比它新的叶片会自动向前补位，序号始终连续。</p>
      <section class="control-center-section vine-unanswered-section">
        <header><div><h3>待回应叶片</h3><p>还没有收到任何同学回答的问题。</p></div><b>{{ unansweredTopics.length }}</b></header>
        <div v-if="unansweredTopics.length" class="vine-unanswered-list">
          <button v-for="topic in unansweredTopics" :key="topic.uid" type="button" @click="jumpToTopic(topic)">
            <span>#{{ String(topic.id).padStart(4, '0') }}</span><b>{{ topic.title }}</b><time>{{ topic.date }}</time><i>定位 ↗</i>
          </button>
        </div>
        <p v-else class="vine-unanswered-empty">目前每片叶子都已收到回答。</p>
      </section>
      <section class="control-center-section vine-admin-delete">
        <h3>摘除序号叶子</h3>
        <label>叶片序号<div class="vine-admin-sequence"><span>#</span><input v-model="sequence" type="number" min="1" :max="questionVineTopics.length" inputmode="numeric" placeholder="例如 0004" /></div></label>
        <article v-if="target" class="vine-admin-preview">
          <header><b>#{{ String(target.id).padStart(4, '0') }}</b><span>{{ target.category }} · {{ target.date }}</span></header>
          <h4>{{ target.title }}</h4><p>{{ target.body }}</p>
          <small>包含 {{ target.answers.length }} 条回答 · 删除后 {{ shiftedCount }} 片后续叶子的序号将自动减 1</small>
        </article>
        <p v-else-if="sequence" class="form-error">没有找到这个序号的叶子。</p>
        <p v-if="error" class="form-error">{{ error }}</p>
        <p v-if="message" class="form-success">{{ message }}</p>
        <button v-if="!confirming" class="danger-button" type="button" :disabled="!target" @click="confirming = true">摘除这片叶子</button>
        <div v-else class="vine-admin-confirm"><p><b>确认永久摘除 #{{ String(target.id).padStart(4, '0') }}？</b><small>问题正文和全部回答会一起删除，此操作不能撤销。</small></p><div><button type="button" :disabled="busy" @click="confirming = false">取消</button><button class="danger-button" type="button" :disabled="busy" @click="confirmDelete">{{ busy ? '正在摘除…' : '确认摘除并重排' }}</button></div></div>
      </section>
    </section>
  </div>
</template>
