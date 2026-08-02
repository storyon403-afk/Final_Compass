<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { surveyApi } from '../api'

const emit = defineEmits(['close'])
const tab = ref('responses')
const overview = ref({ questions: [], submissions: [] })
const drafts = ref([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')
let refreshTimer
const average = computed(() => {
  const ratings = overview.value.submissions.flatMap(item => item.answers.map(answer => answer.rating))
  return ratings.length ? (ratings.reduce((sum, value) => sum + value, 0) / ratings.length).toFixed(1) : '—'
})

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    overview.value = await surveyApi.adminOverview()
    if (!drafts.value.length) drafts.value = overview.value.questions.filter(item => item.active).map(item => item.prompt)
    error.value = ''
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}

onMounted(async () => {
  await load()
  refreshTimer = window.setInterval(() => { if (tab.value === 'responses') load(true) }, 10000)
})
onBeforeUnmount(() => window.clearInterval(refreshTimer))

function addQuestion() { if (drafts.value.length < 12) drafts.value.push('') }
function removeQuestion(index) { if (drafts.value.length > 1) drafts.value.splice(index, 1) }
async function saveQuestions() {
  error.value = ''; message.value = ''; busy.value = true
  try {
    const result = await surveyApi.updateQuestions(drafts.value)
    message.value = result.message
    await load(true)
  } catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section class="upload-modal survey-admin-modal" role="dialog" aria-modal="true" aria-labelledby="survey-admin-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <span class="eyebrow">管理员功能</span><h2 id="survey-admin-title">调查问卷</h2><p>反馈页每 10 秒自动刷新。修改题目后，历史回答仍会保留提交时的原题内容。</p>
      <div class="survey-tabs"><button :class="{ active: tab === 'responses' }" @click="tab = 'responses'">实时反馈</button><button :class="{ active: tab === 'editor' }" @click="tab = 'editor'">编辑问卷</button></div>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div v-if="loading" class="empty-state">正在加载…</div>
      <template v-else-if="tab === 'responses'">
        <div class="survey-summary"><div><b>{{ overview.submissions.length }}</b><span>已提交</span></div><div><b>{{ average }}</b><span>平均评分 / 5</span></div><button type="button" @click="load()">立即刷新</button></div>
        <div v-if="!overview.submissions.length" class="empty-state">暂时还没有用户提交反馈。</div>
        <article v-for="submission in overview.submissions" v-else :key="submission.id" class="survey-response">
          <header><strong>{{ submission.username }}</strong><small>{{ new Date(submission.created_at).toLocaleString('zh-CN') }}</small></header>
          <div v-for="answer in submission.answers" :key="`${submission.id}-${answer.question_id}`"><span>{{ answer.question_snapshot }}</span><b>{{ answer.rating }} / 5</b><p v-if="answer.suggestion">{{ answer.suggestion }}</p></div>
          <footer v-if="submission.overall_suggestion"><b>其他建议</b><p>{{ submission.overall_suggestion }}</p></footer>
        </article>
      </template>
      <form v-else class="survey-editor" @submit.prevent="saveQuestions">
        <p>建议保持 5–8 道短问题，确保每个问题只询问一个明确主题。</p>
        <label v-for="(_, index) in drafts" :key="index"><span>问题 {{ index + 1 }}</span><div><textarea v-model="drafts[index]" maxlength="300" required></textarea><button type="button" :disabled="drafts.length === 1" @click="removeQuestion(index)">删除</button></div></label>
        <button class="secondary-button" type="button" :disabled="drafts.length >= 12" @click="addQuestion">＋ 添加问题</button>
        <p v-if="message" class="form-success">{{ message }}</p>
        <button class="primary-button wide" type="submit" :disabled="busy">{{ busy ? '正在保存…' : '发布问卷更新' }}</button>
      </form>
    </section>
  </div>
</template>
