<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { addQuestionVineAnswer, addQuestionVineTopic, loadQuestionVineTopics, questionVineTopics as topics } from '../questionVineStore'
import VineAnswerNode from '../components/VineAnswerNode.vue'

const query = ref('')
const route = useRoute()
const selected = ref(null)
const composing = ref(false)
const answerDraft = ref('')
const replyingTo = ref(null)
const error = ref('')
const form = ref({ title: '', category: '其他求助', tags: '', body: '' })
const searchResults = computed(() => {
  const value = query.value.trim().toLowerCase()
  if (!value) return []
  const sequence = Number(value.replace(/^#/, ''))
  return topics.value.filter(topic => Number.isInteger(sequence) && sequence > 0
    ? topic.id === sequence
    : [topic.title, topic.category, ...topic.tags, topic.body].join(' ').toLowerCase().includes(value)).slice(0, 6)
})
const answerTree = computed(() => {
  if (!selected.value) return []
  const nodes = new Map(selected.value.answers.map(item => [item.id, { ...item, children: [] }]))
  const roots = []
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : null
    if (parent) parent.children.push(node); else roots.push(node)
  }
  return roots
})
const canvasHeight = computed(() => 190 + topics.value.length * 230 + 210)
const stemPath = computed(() => {
  const points = [`M 500 ${canvasHeight.value - 58}`]
  for (let index = topics.value.length; index >= 0; index--) {
    const y = 175 + index * 230
    const x = 500 + Math.sin(index * 1.26) * 42
    points.push(`Q ${x + Math.cos(index) * 62} ${y + 108} ${x} ${y}`)
  }
  return points.join(' ')
})

function openTopic(topic) { selected.value = topic; answerDraft.value = ''; replyingTo.value = null }
function focusTopic(topic) {
  nextTick(() => document.getElementById(`vine-topic-${topic.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
}
function chooseResult(topic) { focusTopic(topic); query.value = '' }
function focusRouteTopic() {
  const uid = Number(route.query.topic)
  const sequence = Number(route.hash.match(/^#vine-topic-(\d+)$/)?.[1])
  const topic = topics.value.find(item => uid ? item.uid === uid : item.id === sequence)
  if (topic) { focusTopic(topic); if (uid) openTopic(topic) }
}
async function publish() {
  const title = form.value.title.trim(), body = form.value.body.trim()
  if (!title || !body) return
  try {
    const published = await addQuestionVineTopic({ title, body, category: form.value.category, tags: form.value.tags.split(/[,，\s]+/).filter(Boolean).slice(0, 3) })
    form.value = { title: '', category: '其他求助', tags: '', body: '' }; composing.value = false
    nextTick(() => focusTopic(published))
  } catch (cause) { error.value = cause.message }
}
async function answer() {
  const content = answerDraft.value.trim()
  if (!content || !selected.value) return
  try { await addQuestionVineAnswer(selected.value.uid, content, replyingTo.value?.id || null); answerDraft.value = ''; replyingTo.value = null }
  catch (cause) { error.value = cause.message }
}

onMounted(async () => {
  try { await loadQuestionVineTopics(); focusRouteTopic() }
  catch (cause) { error.value = cause.message }
})
watch(() => route.hash, focusRouteTopic)
watch(() => route.query.topic, focusRouteTopic)
</script>

<template>
  <section class="question-vine-page vine-page-enter">
    <p v-if="error" class="vine-load-error">{{ error }}</p>
    <header class="vine-intro">
      <div><small>QUESTION VINE · SINCE 2026.10.01</small><h1>问题藤</h1><p>问题沿着时间往上生长，回答会留在叶脉里。</p></div>
      <aside class="vine-search">
        <label for="vine-search">找一片叶子</label>
        <div><span>⌕</span><input id="vine-search" v-model="query" placeholder="关键词或 #序号" autocomplete="off" /></div>
        <ol v-if="query">
          <li v-for="topic in searchResults" :key="topic.id"><button type="button" @click="chooseResult(topic)"><b>#{{ String(topic.id).padStart(4, '0') }}</b><span>{{ topic.title }}</span></button></li>
          <li v-if="!searchResults.length"><em>没有找到相似的叶子</em></li>
        </ol>
      </aside>
    </header>

    <main class="vine-garden" :style="{ height: `${canvasHeight}px` }">
      <svg class="vine-stem-canvas" :viewBox="`0 0 1000 ${canvasHeight}`" preserveAspectRatio="none" aria-hidden="true">
        <path class="vine-shadow-line" :d="stemPath" />
        <path class="vine-main-line" :d="stemPath" />
      </svg>

      <button class="vine-compose-bud" type="button" aria-label="发布新问题" @click="composing = true">
        <svg viewBox="0 0 120 128" aria-hidden="true"><path d="M59 121 C55 92 57 59 61 29"/><path d="M61 69 C34 64 22 47 27 25 C49 25 63 39 61 69Z"/><path d="M61 48 C70 25 86 15 103 18 C103 40 88 54 61 56Z"/><path d="M61 30 C50 17 55 6 63 3 C73 12 72 21 61 30Z"/></svg>
        <span><b>＋</b><em>长出一个新问题</em><small>任何困惑都可以从这里开始</small></span>
      </button>

      <article v-for="(topic, index) in topics" :id="`vine-topic-${topic.id}`" :key="topic.uid" :class="['vine-node', index % 2 ? 'right' : 'left']" :style="{ '--node-y': `${190 + index * 230}px`, '--tilt': `${index % 2 ? 1.5 : -1.5}deg`, '--enter-delay': `${Math.min(index, 8) * 72 + 300}ms` }">
        <svg class="vine-branch-line" viewBox="0 0 350 130" preserveAspectRatio="none" aria-hidden="true"><path pathLength="1" :d="index % 2 ? 'M0 94 C90 92 120 32 344 35' : 'M350 94 C260 92 230 32 6 35'"/></svg>
        <button type="button" @click="openTopic(topic)">
          <span class="vine-sequence">#{{ String(topic.id).padStart(4, '0') }}</span>
          <h2>{{ topic.title }}</h2>
          <span class="vine-meta">{{ topic.category }} · {{ topic.date }}</span>
          <span class="vine-tags"><i v-for="tag in topic.tags" :key="tag">{{ tag }}</i></span>
          <span :class="['vine-state', topic.status.toLowerCase()]">{{ topic.status === 'SOLVED' ? '已结果' : topic.answers.length ? `${topic.answers.length} 个回答` : '等一阵风来' }}</span>
        </button>
      </article>

      <footer class="vine-root">
        <svg viewBox="0 0 240 120" aria-hidden="true"><path d="M119 2 C119 30 120 48 118 68 M118 68 C86 69 61 84 34 113 M118 68 C151 70 172 86 205 113 M118 80 C99 88 92 99 82 116 M121 81 C145 87 153 101 162 117 M111 72 C77 73 50 66 18 75 M128 72 C157 74 191 66 223 78"/></svg>
        <b>根</b><time>2026.10.01</time><p>每一个认真提出的问题，<br />都可能成为后来者找到的答案。</p>
      </footer>
    </main>

    <div v-if="selected" class="vine-dialog-layer" @click.self="selected = null">
      <section class="vine-dialog" role="dialog" aria-modal="true" :aria-labelledby="`topic-title-${selected.id}`">
        <button class="vine-dialog-close" type="button" aria-label="关闭" @click="selected = null">×</button>
        <small>#{{ String(selected.id).padStart(4, '0') }} · {{ selected.category }} · {{ selected.date }}</small>
        <h2 :id="`topic-title-${selected.id}`">{{ selected.title }}</h2>
        <div class="vine-dialog-author"><span>楼主</span>{{ selected.author }}</div>
        <p class="vine-dialog-body">{{ selected.body }}</p>
        <div class="vine-answer-thread">
          <header><b>藤上的回声</b><span>{{ selected.answers.length }} 个回答</span></header>
          <p v-if="!selected.answers.length" class="vine-no-answer">还没有人回答。你可以成为第一个留下声音的人。</p>
          <VineAnswerNode v-for="item in answerTree" :key="item.id" :node="item" @reply="replyingTo = $event" />
        </div>
        <form class="vine-answer-form" @submit.prevent="answer"><label for="vine-answer">{{ replyingTo ? `回复 ${replyingTo.author}` : '写下你的友情回答' }}</label><button v-if="replyingTo" class="vine-cancel-reply" type="button" @click="replyingTo=null">取消回复</button><textarea id="vine-answer" v-model="answerDraft" maxlength="2000" :placeholder="replyingTo ? '这条内容会成为该回答下的分支回复。' : '不用完美，但希望真诚。'" required></textarea><button type="submit">{{ replyingTo ? '回复这位同学 ↗' : '让它留在藤上 ↗' }}</button></form>
      </section>
    </div>

    <div v-if="composing" class="vine-dialog-layer" @click.self="composing = false">
      <form class="vine-dialog vine-compose-dialog" @submit.prevent="publish">
        <button class="vine-dialog-close" type="button" aria-label="关闭" @click="composing = false">×</button><small>NEW LEAF · 一片新叶</small><h2>你想问什么？</h2>
        <label>问题标题<input v-model="form.title" maxlength="100" placeholder="让别人一眼看懂你的问题" required /></label>
        <div><label>分类<select v-model="form.category"><option>学习考试</option><option>竞赛组队</option><option>电脑数码</option><option>校园生活</option><option>升学就业</option><option>其他求助</option></select></label><label>标签<input v-model="form.tags" maxlength="50" placeholder="最多 3 个，空格分隔" /></label></div>
        <label>详细描述<textarea v-model="form.body" maxlength="5000" placeholder="把背景、已经尝试过的方法和真正困惑写下来。" required></textarea></label>
        <button class="vine-publish" type="submit">把问题留在藤上 ↗</button>
      </form>
    </div>
  </section>
</template>
