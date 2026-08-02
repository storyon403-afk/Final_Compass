<script setup>
import { onMounted, ref } from 'vue'
import { surveyApi } from '../api'

const emit = defineEmits(['close'])
const questions = ref([])
const answers = ref({})
const overallSuggestion = ref('')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const success = ref('')

onMounted(async () => {
  try {
    questions.value = await surveyApi.questions()
    answers.value = Object.fromEntries(questions.value.map(item => [item.id, { rating: 0, suggestion: '' }]))
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
})

async function submit() {
  error.value = ''
  if (questions.value.some(item => !answers.value[item.id]?.rating)) {
    error.value = '请为每个问题选择一个评分'
    return
  }
  busy.value = true
  try {
    const payload = questions.value.map(item => ({ questionId: item.id, ...answers.value[item.id] }))
    const result = await surveyApi.submit(payload, overallSuggestion.value)
    success.value = result.message
  } catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section class="upload-modal survey-modal" role="dialog" aria-modal="true" aria-labelledby="survey-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <span class="eyebrow">内测体验调查</span><h2 id="survey-title">告诉我们真实感受</h2><p>评分没有标准答案。每道题下都可以补充具体经历或改进建议，这会直接帮助我们决定下一步优化什么。</p>
      <div v-if="loading" class="empty-state">正在加载问卷…</div>
      <div v-else-if="success" class="survey-thanks"><span>✓</span><strong>反馈已收到</strong><p>{{ success }}</p><button class="primary-button" type="button" @click="emit('close')">完成</button></div>
      <form v-else @submit.prevent="submit">
        <article v-for="(question, index) in questions" :key="question.id" class="survey-question">
          <strong>{{ index + 1 }}. {{ question.prompt }}</strong>
          <div class="rating-row"><span>很不满意</span><label v-for="score in 5" :key="score"><input v-model="answers[question.id].rating" type="radio" :name="`rating-${question.id}`" :value="score" /><b>{{ score }}</b></label><span>非常满意</span></div>
          <textarea v-model="answers[question.id].suggestion" maxlength="1000" placeholder="可选：哪里好用、哪里不顺手，或你希望怎样改进？"></textarea>
        </article>
        <label class="survey-overall"><strong>还有其他想告诉我们的内容吗？</strong><textarea v-model="overallSuggestion" maxlength="2000" placeholder="任何真实感受、功能建议或遇到的问题都可以写在这里。"></textarea></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button wide" type="submit" :disabled="busy">{{ busy ? '正在提交…' : '提交反馈' }}</button>
      </form>
    </section>
  </div>
</template>
