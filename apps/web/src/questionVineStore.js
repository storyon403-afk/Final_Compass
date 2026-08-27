import { ref } from 'vue'
import { questionVineApi } from './api'

export const questionVineTopics = ref([])
export async function loadQuestionVineTopics() { questionVineTopics.value = await questionVineApi.topics(); return questionVineTopics.value }
export async function addQuestionVineTopic(topic) { const created = await questionVineApi.createTopic(topic); questionVineTopics.value.unshift(created); return created }
export async function addQuestionVineAnswer(uid, content, parentAnswerId = null) { const answer = await questionVineApi.answer(uid, content, parentAnswerId); const topic = questionVineTopics.value.find(item => item.uid === uid); if (topic) topic.answers.push(answer); return answer }
export async function deleteQuestionVineTopic(sequence) { const target = questionVineTopics.value.find(topic => topic.id === Number(sequence)); if (!target) return null; await questionVineApi.deleteTopic(sequence); await loadQuestionVineTopics(); return target }
