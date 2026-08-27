<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { cetApi, isAdmin } from '../api'

const level = ref('')
const mode = ref('')
const section = ref('')
const selectedItem = ref(null)
const selectedPaperKey = ref('')
const items = ref([])
const papers = ref([])
const adminItems = ref([])
const adminSections = ref([])
const adminTab = ref('PAPERS')
const selectedAdminSectionId = ref(null)
const adminPaperSearch = ref('')
const loading = ref(false)
const error = ref('')
const answer = ref('')
const submitted = ref(false)
const showTranslation = ref(false)
const audioUrl = ref('')
const exerciseAudio = ref(null)
const audioLoading = ref(false)
const showAdmin = ref(false)
const adminMessage = ref('')
const editingItemId = ref(null)
const editingPaperId = ref(null)
const audioFile = ref(null)
const practiceAudioFile = ref(null)
const groupAnswers = ref({})
const groupSubmitted = ref(false)
const showTranscript = ref(false)
const sessionAudioUrl = ref('')
const sessionAudioLoading = ref(false)
const CET_HISTORY_KEY = 'finalsCompassCetView'

const practiceSections = [
  { key: 'WRITING', icon: 'W', name: '写作', note: '审题、构思与参考范文' },
  { key: 'LISTENING_PASSAGE', icon: '◉', name: '篇章（听力）', note: '按篇章完成听力题组' },
  { key: 'WORD_BANK', icon: 'A', name: '选词填空', note: '语境、词性与搭配' },
  { key: 'MATCHING', icon: '¶', name: '段落匹配', note: '快速定位段落信息' },
  { key: 'CAREFUL_READING', icon: 'R', name: '仔细阅读', note: '作答、判分与逐题解析' },
  { key: 'TRANSLATION', icon: '译', name: '翻译', note: '汉译英与参考译文' }
]
const intensiveByLevel = {
  CET4: [
    { key: 'NEWS', icon: 'N', name: '短篇新闻', note: '逐句听辨新闻信息' },
    { key: 'LONG_CONVERSATION', icon: 'D', name: '长对话', note: '角色、场景与转折定位' },
    { key: 'LISTENING_PASSAGE', icon: 'P', name: '听力篇章', note: '篇章逻辑与关键句' }
  ],
  CET6: [
    { key: 'LONG_CONVERSATION', icon: 'D', name: '长对话', note: '角色、场景与转折定位' },
    { key: 'LISTENING_PASSAGE', icon: 'P', name: '听力篇章', note: '篇章逻辑与关键句' },
    { key: 'LECTURE', icon: 'L', name: '讲话／报道／讲座', note: '观点结构与关键句精听' }
  ]
}
const sections = computed(() => mode.value === 'PRACTICE' ? practiceSections : intensiveByLevel[level.value] || [])
const currentSection = computed(() => sections.value.find((item) => item.key === section.value))
const options = computed(() => {
  if (!selectedItem.value?.optionsJson) return []
  try { return JSON.parse(selectedItem.value.optionsJson) || [] } catch { return [] }
})
const resultCorrect = computed(() => selectedItem.value?.answerType === 'CHOICE'
  && answer.value === selectedItem.value.correctAnswer)
const groupedPracticeSections = new Set(['LISTENING_PASSAGE', 'WORD_BANK', 'MATCHING', 'CAREFUL_READING'])
const isGroupedPractice = computed(() => mode.value === 'PRACTICE' && groupedPracticeSections.has(section.value))
const stage = computed(() => selectedItem.value ? 'detail' : section.value && !selectedPaperKey.value ? 'paperselect'
  : isGroupedPractice.value ? 'session' : section.value ? 'list'
  : mode.value === 'FULL_PAPER' ? 'paperlist' : mode.value ? 'section' : level.value ? 'mode' : 'level')
const paperGroups = computed(() => {
  const groups = new Map()
  for (const item of items.value) {
    const key = `${item.examYear}-${item.examMonth}-${item.setNumber}`
    if (!groups.has(key)) groups.set(key, {
      key, examYear: item.examYear, examMonth: item.examMonth, setNumber: item.setNumber,
      title: item.paperTitle, count: 0
    })
    groups.get(key).count++
  }
  return [...groups.values()].sort((a, b) =>
    b.examYear - a.examYear || b.examMonth - a.examMonth || a.setNumber - b.setNumber)
})
const currentPaper = computed(() => paperGroups.value.find((paper) => paper.key === selectedPaperKey.value))
const activeItems = computed(() => items.value.filter((item) =>
  `${item.examYear}-${item.examMonth}-${item.setNumber}` === selectedPaperKey.value))
const answeredCount = computed(() => Object.values(groupAnswers.value).filter(Boolean).length)
const listeningTranscripts = computed(() => {
  const seen = new Set()
  return activeItems.value.filter((item) => {
    if (!item.passage || seen.has(item.passage)) return false
    seen.add(item.passage)
    return true
  })
})
const carefulPassages = computed(() => {
  const groups = []
  for (const item of activeItems.value) {
    let group = groups.find((entry) => entry.passage === item.passage)
    if (!group) {
      group = { passage: item.passage, items: [] }
      groups.push(group)
    }
    group.items.push(item)
  }
  return groups
})
const wordBankSegments = computed(() => {
  const passage = activeItems.value[0]?.passage || ''
  const byNumber = new Map(activeItems.value.map((item) => {
    const number = Number(item.title.match(/\d+/)?.[0])
    return [number, item]
  }))
  return passage.split(/(\[\d+\])/).filter(Boolean).map((text) => {
    const match = text.match(/^\[(\d+)\]$/)
    return match ? { type: 'blank', number: Number(match[1]), item: byNumber.get(Number(match[1])) } : { type: 'text', text }
  })
})
const matchingParagraphs = computed(() => {
  const passage = activeItems.value[0]?.passage || ''
  return passage.split(/\n\s*\n(?=[A-O]\))/).map((paragraph) => {
    const match = paragraph.match(/^([A-O])\)\s*([\s\S]*)$/)
    return match ? { letter: match[1], text: match[2] } : { letter: '', text: paragraph }
  }).filter((paragraph) => paragraph.text.trim())
})

const blankPaper = () => ({ level: 'CET4', examYear: 2026, examMonth: 6, setNumber: 1, title: '', sourceName: '', sourcePageUrl: '', usageNote: '' })
const paperForm = ref(blankPaper())
const blankItem = () => ({
  paperId: null, mode: 'PRACTICE', section: 'WRITING', title: '', prompt: '', passage: '',
  translation: '', analysis: '', keySentence: '', answerType: 'CHOICE',
  optionsJson: '["选项 A","选项 B","选项 C","选项 D"]', correctAnswer: 'A',
  itemOrder: 10, audioStartMs: null, audioEndMs: null
})
const itemForm = ref(blankItem())
const sectionCatalog = {
  PRACTICE: practiceSections,
  INTENSIVE: [
    { key: 'NEWS', name: '短篇新闻（四级）', note: 'CET4 精听材料' },
    { key: 'LONG_CONVERSATION', name: '长对话', note: 'CET4 / CET6 精听材料' },
    { key: 'LISTENING_PASSAGE', name: '听力篇章', note: '分类练习与精听均可使用' },
    { key: 'LECTURE', name: '讲话／报道／讲座（六级）', note: 'CET6 精听材料' }
  ]
}
const adminItemsForPaper = (paperId) => adminItems.value.filter((item) => item.paperId === paperId)
const selectedAdminSection = computed(() => adminSections.value.find((entry) => entry.id === selectedAdminSectionId.value) || null)
const selectedAdminItems = computed(() => {
  const slot = selectedAdminSection.value
  return slot ? adminItems.value.filter((item) => item.paperId === slot.paperId && item.mode === slot.mode && item.section === slot.section) : []
})
const filteredAdminPapers = computed(() => {
  const keyword = adminPaperSearch.value.trim().toLocaleLowerCase()
  if (!keyword) return papers.value
  const terms = keyword.split(/\s+/).filter(Boolean)
  return papers.value.filter((paper) => {
    const searchable = [
      paper.title, paper.level, paper.level === 'CET4' ? '四级' : '六级',
      paper.examYear, `${paper.examMonth}月`, paper.setNumber, `第${paper.setNumber}套`, paper.sourceName
    ].map((value) => String(value ?? '').toLocaleLowerCase()).join(' ')
    return terms.every((term) => searchable.includes(term))
  })
})
function sectionName(code) {
  return [...practiceSections, ...sectionCatalog.INTENSIVE].find((entry) => entry.key === code)?.name || code
}

function cetSnapshot() {
  return {
    level: level.value, mode: mode.value, section: section.value,
    paper: selectedPaperKey.value, item: selectedItem.value?.id || null
  }
}
function pushCetHistory() {
  window.history.pushState({ ...window.history.state, [CET_HISTORY_KEY]: cetSnapshot() }, '', window.location.href)
}
async function restoreCetHistory(snapshot) {
  if (!snapshot) return
  level.value = snapshot.level || ''; mode.value = snapshot.mode || ''; section.value = snapshot.section || ''
  selectedPaperKey.value = ''; selectedItem.value = null; items.value = []
  answer.value = ''; submitted.value = false; showTranslation.value = false
  groupAnswers.value = {}; groupSubmitted.value = false; showTranscript.value = false
  if (!level.value || !mode.value) return
  loading.value = true; error.value = ''
  try {
    if (mode.value === 'FULL_PAPER') papers.value = await cetApi.papers(level.value)
    else if (section.value) {
      items.value = await cetApi.items(level.value, mode.value, section.value)
      selectedPaperKey.value = snapshot.paper || ''
      selectedItem.value = snapshot.item ? items.value.find(item => item.id === snapshot.item) || null : null
    }
  } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}
function handleCetHistory(event) {
  if (event.state?.[CET_HISTORY_KEY]) restoreCetHistory(event.state[CET_HISTORY_KEY])
}
function goToRoot() {
  level.value = ''; mode.value = ''; section.value = ''; selectedPaperKey.value = ''; selectedItem.value = null
  pushCetHistory()
}
function goToSection() { selectedPaperKey.value = ''; selectedItem.value = null; pushCetHistory() }
function goToPaper() { selectedItem.value = null; pushCetHistory() }

function chooseLevel(value) { level.value = value; mode.value = ''; section.value = ''; selectedPaperKey.value = ''; selectedItem.value = null; pushCetHistory() }
async function chooseMode(value) {
  mode.value = value; section.value = ''; selectedPaperKey.value = ''; selectedItem.value = null
  pushCetHistory()
  if (value === 'FULL_PAPER') {
    loading.value = true; error.value = ''
    try { papers.value = await cetApi.papers(level.value) }
    catch (reason) { error.value = reason.message }
    finally { loading.value = false }
  }
}
async function chooseSection(value) {
  section.value = value; selectedPaperKey.value = ''; selectedItem.value = null; loading.value = true; error.value = ''
  pushCetHistory()
  groupAnswers.value = {}; groupSubmitted.value = false; showTranscript.value = false
  if (sessionAudioUrl.value.startsWith('blob:')) URL.revokeObjectURL(sessionAudioUrl.value)
  sessionAudioUrl.value = ''
  try {
    items.value = await cetApi.items(level.value, mode.value, value)
  }
  catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}
async function choosePaper(paper) {
  selectedPaperKey.value = paper.key
  pushCetHistory()
  groupAnswers.value = {}; groupSubmitted.value = false; showTranscript.value = false
  if (sessionAudioUrl.value.startsWith('blob:')) URL.revokeObjectURL(sessionAudioUrl.value)
  sessionAudioUrl.value = ''
  if (mode.value === 'PRACTICE' && section.value === 'LISTENING_PASSAGE' && activeItems.value[0]?.audioOriginalName) {
    sessionAudioLoading.value = true
    try {
      await cetApi.prepareAudioStream()
      sessionAudioUrl.value = cetApi.audioUrl(activeItems.value[0].id)
    }
    catch (reason) { error.value = reason.message }
    finally { sessionAudioLoading.value = false }
  }
}
function itemOptions(item) {
  if (!item?.optionsJson) return []
  try { return JSON.parse(item.optionsJson) || [] } catch { return [] }
}
function optionLetter(index) { return String.fromCharCode(65 + index) }
function submitGroup() {
  if (!activeItems.value.length) return
  if (answeredCount.value < activeItems.value.length
      && !window.confirm(`还有 ${activeItems.value.length - answeredCount.value} 题未作答，仍要提交吗？`)) return
  groupSubmitted.value = true
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function openItem(item) {
  selectedItem.value = item; answer.value = ''; submitted.value = false; showTranslation.value = false
  pushCetHistory()
  if (audioUrl.value.startsWith('blob:')) URL.revokeObjectURL(audioUrl.value)
  audioUrl.value = ''
  if (item.audioOriginalName) {
    audioLoading.value = true
    try {
      await cetApi.prepareAudioStream()
      audioUrl.value = cetApi.audioUrl(item.id)
    } catch { audioUrl.value = '' }
    finally { audioLoading.value = false }
  }
}
function prepareAudioSegment() {
  const player = exerciseAudio.value
  if (!player || selectedItem.value?.audioStartMs == null) return
  player.currentTime = selectedItem.value.audioStartMs / 1000
}
function enforceAudioSegment() {
  const player = exerciseAudio.value
  const endMs = selectedItem.value?.audioEndMs
  if (!player || endMs == null) return
  if (player.currentTime * 1000 >= endMs) {
    player.pause()
    player.currentTime = (selectedItem.value.audioStartMs || 0) / 1000
  }
}
function goBack() {
  if (window.history.state?.[CET_HISTORY_KEY] && stage.value !== 'level') { window.history.back(); return }
  if (selectedItem.value) { selectedItem.value = null; return }
  if (selectedPaperKey.value) { selectedPaperKey.value = ''; groupAnswers.value = {}; groupSubmitted.value = false; return }
  if (section.value) { section.value = ''; items.value = []; return }
  if (mode.value) { mode.value = ''; return }
  level.value = ''
}
function submitAnswer() { if (answer.value.trim()) submitted.value = true }
async function openAdmin() {
  showAdmin.value = true; adminMessage.value = ''; adminTab.value = 'PAPERS'; selectedAdminSectionId.value = null; adminPaperSearch.value = ''
  editingItemId.value = null; editingPaperId.value = null; itemForm.value = blankItem(); paperForm.value = blankPaper()
  await loadAdminData()
}
async function loadAdminData() {
  try {
    ;[papers.value, adminItems.value, adminSections.value] = await Promise.all([cetApi.papers(), cetApi.adminItems(), cetApi.adminSections()])
    if (!itemForm.value.paperId && papers.value.length) itemForm.value.paperId = papers.value[0].id
  } catch (reason) { adminMessage.value = reason.message }
}
async function savePaper() {
  adminMessage.value = ''
  try {
    if (editingPaperId.value) await cetApi.updatePaper(editingPaperId.value, paperForm.value)
    else await cetApi.createPaper(paperForm.value)
    adminMessage.value = editingPaperId.value ? '套卷来源已更新' : '套卷来源已添加'
    editingPaperId.value = null; paperForm.value = blankPaper(); await loadAdminData()
  } catch (reason) { adminMessage.value = reason.message }
}
function editPaper(paper) {
  editingPaperId.value = paper.id
  paperForm.value = {
    level: paper.level, examYear: paper.examYear, examMonth: paper.examMonth,
    setNumber: paper.setNumber, title: paper.title, sourceName: paper.sourceName || '',
    sourcePageUrl: paper.sourcePageUrl || '', usageNote: paper.usageNote || ''
  }
}
async function removePaper(paper) {
  if (!window.confirm(`确认删除“${paper.title}”及其全部题目吗？此操作不可撤销。`)) return
  try {
    await cetApi.removePaper(paper.id)
    adminMessage.value = '套卷已删除'; await loadAdminData()
  } catch (reason) { adminMessage.value = reason.message }
}
function cancelPaperEdit() { editingPaperId.value = null; paperForm.value = blankPaper() }
function selectAdminSection(slot) {
  selectedAdminSectionId.value = slot.id
  editingItemId.value = null
  itemForm.value = { ...blankItem(), paperId: slot.paperId, mode: slot.mode, section: slot.section }
  adminMessage.value = ''
}
function backToAdminSections() {
  selectedAdminSectionId.value = null
  editingItemId.value = null
  itemForm.value = blankItem()
  if (papers.value.length) itemForm.value.paperId = papers.value[0].id
}
async function bindPracticeAudio() {
  if (!itemForm.value.paperId || !practiceAudioFile.value) return
  adminMessage.value = ''
  try {
    await cetApi.uploadPracticeAudio(itemForm.value.paperId, practiceAudioFile.value)
    practiceAudioFile.value = null
    adminMessage.value = '分类练习／精听精讲共用音频已绑定'
    await loadAdminData()
  } catch (reason) { adminMessage.value = reason.message }
}
function editItem(item) {
  const slot = adminSections.value.find((entry) => entry.paperId === item.paperId && entry.mode === item.mode && entry.section === item.section)
  if (slot) selectedAdminSectionId.value = slot.id
  adminTab.value = 'ITEMS'
  editingItemId.value = item.id
  itemForm.value = {
    paperId: item.paperId, mode: item.mode, section: item.section, title: item.title,
    prompt: item.prompt || '', passage: item.passage || '', translation: item.translation || '',
    analysis: item.analysis || '', keySentence: item.keySentence || '', answerType: item.answerType,
    optionsJson: item.optionsJson || '', correctAnswer: item.correctAnswer || '',
    itemOrder: item.itemOrder, audioStartMs: item.audioStartMs, audioEndMs: item.audioEndMs
  }
  showAdmin.value = true
}
async function saveItem() {
  adminMessage.value = ''
  try {
    const saved = editingItemId.value
      ? (await cetApi.updateItem(editingItemId.value, itemForm.value), { id: editingItemId.value })
      : await cetApi.createItem(itemForm.value)
    if (audioFile.value) await cetApi.uploadAudio(saved.id, audioFile.value)
    adminMessage.value = editingItemId.value ? '题目已更新' : '题目已添加'
    editingItemId.value = null; audioFile.value = null
    const slot = selectedAdminSection.value
    itemForm.value = slot ? { ...blankItem(), paperId: slot.paperId, mode: slot.mode, section: slot.section } : blankItem()
    await loadAdminData()
    if (section.value) await chooseSection(section.value)
  } catch (reason) { adminMessage.value = reason.message }
}
async function clearAdminSection() {
  const slot = selectedAdminSection.value
  if (!slot || !window.confirm(`确认清空“${slot.paperTitle} / ${sectionName(slot.section)}”的全部内容吗？`)) return
  try {
    await cetApi.clearSection(slot.id)
    adminMessage.value = '当前题型内容已清空'; await loadAdminData()
    if (section.value) await chooseSection(section.value)
  } catch (reason) { adminMessage.value = reason.message }
}
function closeAdmin(event) { if (event.key === 'Escape') showAdmin.value = false }

onMounted(() => {
  window.addEventListener('keydown', closeAdmin)
  window.addEventListener('popstate', handleCetHistory)
  window.history.replaceState({ ...window.history.state, [CET_HISTORY_KEY]: cetSnapshot() }, '', window.location.href)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', closeAdmin)
  window.removeEventListener('popstate', handleCetHistory)
  if (audioUrl.value.startsWith('blob:')) URL.revokeObjectURL(audioUrl.value)
  if (sessionAudioUrl.value.startsWith('blob:')) URL.revokeObjectURL(sessionAudioUrl.value)
})
</script>

<template>
  <section class="cet-page page-width cet-enter">
    <header class="cet-header">
      <div>
        <span class="eyebrow">英语等级考试收录</span>
        <h1>CET 真题训练库</h1>
        <p>按级别、题型和年份组织练习，把一次作答拆成可以复盘的学习路径。</p>
      </div>
      <button v-if="isAdmin" class="secondary-button" type="button" @click="openAdmin">管理 CET 题库</button>
    </header>

    <nav v-if="stage !== 'level'" class="path-nav cet-path" aria-label="当前位置">
      <button type="button" @click="goToRoot">CET 题库</button>
      <template v-if="level"><span>›</span><button type="button" @click="chooseLevel(level)">{{ level === 'CET4' ? 'CET-4' : 'CET-6' }}</button></template>
      <template v-if="mode"><span>›</span><button type="button" @click="chooseMode(mode)">{{ mode === 'PRACTICE' ? '分类练习' : mode === 'INTENSIVE' ? '精听精读' : '完整套卷' }}</button></template>
      <template v-if="section"><span>›</span><button type="button" @click="goToSection">{{ currentSection?.name }}</button></template>
      <template v-if="currentPaper"><span>›</span><button type="button" @click="goToPaper">{{ currentPaper.examYear }}.{{ String(currentPaper.examMonth).padStart(2, '0') }} · 第 {{ currentPaper.setNumber }} 套</button></template>
      <template v-if="selectedItem"><span>›</span><b>{{ selectedItem.title }}</b></template>
    </nav>

    <div v-if="error" class="empty-state error-state">{{ error }}</div>

    <div v-if="stage === 'level'" class="cet-level-grid">
      <button type="button" @click="chooseLevel('CET4')"><span>4</span><div><small>COLLEGE ENGLISH TEST</small><strong>CET-4 四级</strong><p>125 分钟 · 听力、阅读、写作与翻译</p></div><i>→</i></button>
      <button type="button" @click="chooseLevel('CET6')"><span>6</span><div><small>COLLEGE ENGLISH TEST</small><strong>CET-6 六级</strong><p>130 分钟 · 强化篇章理解与学术表达</p></div><i>→</i></button>
    </div>

    <div v-else-if="stage === 'mode'" class="cet-mode-grid">
      <button type="button" @click="chooseMode('FULL_PAPER')"><span class="cet-mode-mark">01</span><strong>完整套卷</strong><p>按考试场次查看整套真题、参考答案和配套听力，适合计时模拟。</p><i>选择套卷 →</i></button>
      <button type="button" @click="chooseMode('PRACTICE')"><span class="cet-mode-mark">02</span><strong>分类练习</strong><p>按写作、听力篇章、选词填空、段落匹配、仔细阅读和翻译分类练习。</p><i>开始选题 →</i></button>
      <button type="button" @click="chooseMode('INTENSIVE')"><span class="cet-mode-mark">03</span><strong>精听精读</strong><p>定位到逐道题与关键句，反复听辨，按需查看译文和解析。</p><i>开始精听 →</i></button>
    </div>

    <div v-else-if="stage === 'paperlist'" class="cet-full-paper-wrap">
      <header class="section-heading">
        <div><h2>完整套卷下载来源</h2><p>平台不保存或分发完整套卷，只提供管理员核对过的外部来源。</p></div>
        <span>{{ papers.length }} 个来源已整理</span>
      </header>
      <div v-if="loading" class="empty-state">正在加载套卷…</div>
      <div v-else class="cet-full-paper-grid">
        <article v-for="paper in papers" :key="paper.id">
          <header><time>{{ paper.examYear }}.{{ String(paper.examMonth).padStart(2, '0') }}</time><b>SET {{ String(paper.setNumber).padStart(2, '0') }}</b></header>
          <h3>{{ paper.title }}</h3>
          <p class="cet-source-note">{{ paper.usageNote || '请前往来源页面核对并下载完整套卷。' }}</p>
          <footer><span>来源：{{ paper.sourceName || '待管理员核对' }}</span><a v-if="paper.sourcePageUrl" :href="paper.sourcePageUrl" target="_blank" rel="noopener noreferrer">前往下载并核对来源 ↗</a></footer>
        </article>
      </div>
    </div>

    <div v-else-if="stage === 'section'" class="cet-section-wrap">
      <div class="section-heading"><h2>{{ mode === 'PRACTICE' ? '选择练习模块' : '选择精听材料' }}</h2><span>{{ level }}</span></div>
      <div class="cet-section-grid">
        <button v-for="part in sections" :key="part.key" type="button" @click="chooseSection(part.key)">
          <span>{{ part.icon }}</span><div><strong>{{ part.name }}</strong><small>{{ part.note }}</small></div><i>→</i>
        </button>
      </div>
    </div>

    <div v-else-if="stage === 'paperselect'" class="cet-paper-select">
      <header class="section-heading">
        <div><h2>{{ currentSection?.name }} · 选择套卷</h2><p>默认按考试时间从近到远排列，同一场次按第一、第二、第三套排列。</p></div>
        <span>{{ paperGroups.length }} 套可用</span>
      </header>
      <div v-if="loading" class="empty-state">正在整理套卷索引…</div>
      <div v-else-if="!paperGroups.length" class="empty-state"><strong>这个模块还没有可用套卷</strong><p>录入对应题目后会自动出现在这里。</p></div>
      <div v-else class="cet-paper-choice-list">
        <button v-for="paper in paperGroups" :key="paper.key" type="button" @click="choosePaper(paper)">
          <time><b>{{ paper.examYear }}</b><span>{{ String(paper.examMonth).padStart(2, '0') }} 月</span></time>
          <div><small>{{ level }} · {{ currentSection?.name }}</small><strong>{{ paper.title }}</strong><p>第 {{ paper.setNumber }} 套 · {{ paper.count }} 道已收录</p></div>
          <i>进入练习 →</i>
        </button>
      </div>
    </div>

    <div v-else-if="stage === 'session'" class="cet-session">
      <header class="cet-session-head">
        <div>
          <span class="eyebrow">{{ level }} · 2025.12 · SET 01</span>
          <h2>{{ currentSection?.name }}</h2>
          <p v-if="section === 'LISTENING_PASSAGE'">整套音频连续播放，25 道题连续作答；提交后只核对选项答案。</p>
          <p v-else-if="section === 'WORD_BANK'">直接在原文空格中选择单词，完成后统一提交并查看整篇答案。</p>
          <p v-else>每篇文章只显示一次，问题连续排列；全部完成后统一提交并查看解析。</p>
        </div>
        <div class="cet-session-progress">
          <b>{{ answeredCount }}/{{ activeItems.length }}</b><span>已作答</span>
        </div>
      </header>

      <div v-if="loading" class="empty-state">正在准备机考题组…</div>
      <div v-else-if="!activeItems.length" class="empty-state">这个模块还没有可用题目。</div>

      <template v-else-if="section === 'LISTENING_PASSAGE'">
        <section class="cet-exam-audio">
          <div><span>FULL LISTENING TEST</span><strong>{{ sessionAudioLoading ? '音频载入中…' : '2025 年 12 月 CET-4 第一套听力' }}</strong></div>
          <audio v-if="sessionAudioUrl" :src="sessionAudioUrl" controls preload="metadata"></audio>
          <button class="cet-eye-button" type="button" :aria-pressed="showTranscript" @click="showTranscript=!showTranscript">
            <span aria-hidden="true">{{ showTranscript ? '◉' : '◎' }}</span>{{ showTranscript ? '隐藏听力原文' : '显示听力原文' }}
          </button>
        </section>

        <section v-if="showTranscript" class="cet-transcript-drawer">
          <header><span>LISTENING SCRIPT</span><b>听力原文仅辅助复盘</b></header>
          <article v-for="(entry, index) in listeningTranscripts" :key="entry.id">
            <h3>Material {{ String(index + 1).padStart(2, '0') }}</h3><p>{{ entry.passage }}</p>
          </article>
        </section>

        <section class="cet-continuous-questions">
          <article v-for="(item, index) in activeItems" :key="item.id" class="cet-group-question">
            <header><span>QUESTION {{ index + 1 }}</span><b v-if="groupSubmitted" :class="{ right: groupAnswers[item.id] === item.correctAnswer }">{{ groupAnswers[item.id] === item.correctAnswer ? '✓' : `答案 ${item.correctAnswer}` }}</b></header>
            <h3>{{ item.prompt }}</h3>
            <div class="cet-inline-options">
              <label v-for="(choice, choiceIndex) in itemOptions(item)" :key="choice" :class="{ selected: groupAnswers[item.id] === optionLetter(choiceIndex) }">
                <input v-model="groupAnswers[item.id]" type="radio" :name="`listening-${item.id}`" :value="optionLetter(choiceIndex)" :disabled="groupSubmitted" />
                <b>{{ optionLetter(choiceIndex) }}</b><span>{{ choice }}</span>
              </label>
            </div>
          </article>
        </section>
      </template>

      <template v-else-if="section === 'WORD_BANK'">
        <section class="cet-word-bank">
          <div class="cet-word-bank-list">
            <span v-for="(word, index) in itemOptions(activeItems[0])" :key="word"><b>{{ optionLetter(index) }}</b>{{ word }}</span>
          </div>
          <div class="cet-cloze-passage">
            <template v-for="(segment, index) in wordBankSegments" :key="index">
              <span v-if="segment.type === 'text'">{{ segment.text }}</span>
              <label v-else>
                <small>{{ segment.number }}</small>
                <select v-if="segment.item" v-model="groupAnswers[segment.item.id]" :disabled="groupSubmitted">
                  <option value="">选择</option>
                  <option v-for="(word, wordIndex) in itemOptions(segment.item)" :key="word" :value="optionLetter(wordIndex)">{{ optionLetter(wordIndex) }} · {{ word }}</option>
                </select>
                <b v-if="groupSubmitted" :class="{ right: groupAnswers[segment.item.id] === segment.item.correctAnswer }">{{ segment.item.correctAnswer }}</b>
              </label>
            </template>
          </div>
          <section v-if="groupSubmitted" class="cet-group-review">
            <h3>完整答案与解析</h3>
            <div class="cet-answer-strip"><span v-for="item in activeItems" :key="item.id">{{ item.title.match(/\d+/)?.[0] }}. <b>{{ item.correctAnswer }}</b></span></div>
            <ol><li v-for="item in activeItems" :key="item.id"><b>{{ item.title }}</b><p>{{ item.analysis }}</p></li></ol>
          </section>
        </section>
      </template>

      <template v-else-if="section === 'MATCHING'">
        <section class="cet-matching-session">
          <article class="cet-matching-article">
            <header><span>ARTICLE</span><b>{{ matchingParagraphs.length }} PARAGRAPHS</b></header>
            <div class="cet-matching-paragraphs">
              <section v-for="paragraph in matchingParagraphs" :key="paragraph.letter">
                <b>{{ paragraph.letter }}</b><p>{{ paragraph.text }}</p>
              </section>
            </div>
          </article>
          <section class="cet-continuous-questions">
            <article v-for="(item, index) in activeItems" :key="item.id" class="cet-group-question">
              <header><span>STATEMENT {{ index + 36 }}</span><b v-if="groupSubmitted" :class="{ right: groupAnswers[item.id] === item.correctAnswer }">{{ groupAnswers[item.id] === item.correctAnswer ? '回答正确' : `对应段落 ${item.correctAnswer}` }}</b></header>
              <h3>{{ item.prompt }}</h3>
              <div class="cet-paragraph-picker">
                <label v-for="letter in itemOptions(item)" :key="letter" :class="{ selected: groupAnswers[item.id] === letter }">
                  <input v-model="groupAnswers[item.id]" type="radio" :name="`matching-${item.id}`" :value="letter" :disabled="groupSubmitted" /><span>{{ letter }}</span>
                </label>
              </div>
              <div v-if="groupSubmitted" class="cet-question-analysis"><b>解析</b><p>{{ item.analysis }}</p></div>
            </article>
          </section>
        </section>
      </template>

      <template v-else>
        <section class="cet-reading-session">
          <article v-for="(passageGroup, passageIndex) in carefulPassages" :key="passageIndex" class="cet-reading-block">
            <header><span>PASSAGE {{ passageIndex + 1 }}</span><b>{{ passageGroup.items.length }} QUESTIONS</b></header>
            <div class="cet-reading-copy">{{ passageGroup.passage }}</div>
            <div class="cet-continuous-questions">
              <article v-for="(item, questionIndex) in passageGroup.items" :key="item.id" class="cet-group-question">
                <header><span>QUESTION {{ passageIndex * 5 + questionIndex + 46 }}</span><b v-if="groupSubmitted" :class="{ right: groupAnswers[item.id] === item.correctAnswer }">{{ groupAnswers[item.id] === item.correctAnswer ? '回答正确' : `正确答案 ${item.correctAnswer}` }}</b></header>
                <h3>{{ item.prompt }}</h3>
                <div class="cet-inline-options">
                  <label v-for="(choice, choiceIndex) in itemOptions(item)" :key="choice" :class="{ selected: groupAnswers[item.id] === optionLetter(choiceIndex) }">
                    <input v-model="groupAnswers[item.id]" type="radio" :name="`reading-${item.id}`" :value="optionLetter(choiceIndex)" :disabled="groupSubmitted" />
                    <b>{{ optionLetter(choiceIndex) }}</b><span>{{ choice }}</span>
                  </label>
                </div>
                <div v-if="groupSubmitted" class="cet-question-analysis"><b>解析</b><p>{{ item.analysis }}</p></div>
              </article>
            </div>
          </article>
        </section>
      </template>

      <footer v-if="activeItems.length" class="cet-session-submit">
        <span>已完成 {{ answeredCount }} / {{ activeItems.length }}</span>
        <button v-if="!groupSubmitted" class="primary-button" type="button" @click="submitGroup">提交本模块</button>
        <button v-else class="secondary-button" type="button" @click="groupAnswers={};groupSubmitted=false;showTranscript=false">重新作答</button>
      </footer>
    </div>

    <div v-else-if="stage === 'list'" class="cet-list-wrap">
      <header class="section-heading"><h2>{{ currentPaper?.title }} · {{ currentSection?.name }}</h2><span>{{ activeItems.length }} 道已收录</span></header>
      <div v-if="loading" class="empty-state">正在加载题库…</div>
      <div v-else-if="!activeItems.length" class="empty-state"><strong>这个模块还没有录入题目</strong><p>{{ isAdmin ? '点击“管理 CET 题库”添加第一道题。' : '题库正在持续补充。' }}</p></div>
      <div v-else class="cet-paper-list">
        <article v-for="item in activeItems" :key="item.id">
          <button type="button" @click="openItem(item)">
            <time>{{ item.examYear }}.{{ String(item.examMonth).padStart(2, '0') }}</time>
            <span><small>{{ item.paperTitle }} · 第 {{ item.setNumber }} 套</small><strong>{{ item.title }}</strong></span>
            <i>开始 →</i>
          </button>
        </article>
      </div>
    </div>

    <article v-else class="cet-exercise">
      <button class="cet-back" type="button" @click="goBack">← 返回题目列表</button>
      <header><span>{{ level }} · {{ currentSection?.name }}</span><h2>{{ selectedItem.title }}</h2><p>{{ selectedItem.paperTitle }}</p></header>

      <section v-if="mode === 'INTENSIVE' && selectedItem.audioOriginalName" class="cet-audio-panel">
        <div><span>KEY SENTENCE</span><strong>{{ audioLoading ? '正在载入音频…' : selectedItem.audioOriginalName || '管理员尚未上传音频' }}</strong></div>
        <audio
          v-if="audioUrl"
          ref="exerciseAudio"
          :src="audioUrl"
          controls
          preload="metadata"
          @loadedmetadata="prepareAudioSegment"
          @timeupdate="enforceAudioSegment"
        ></audio>
        <small v-if="selectedItem.audioStartMs != null" class="cet-audio-range">
          本题精听片段 {{ Math.floor(selectedItem.audioStartMs / 60000) }}:{{ String(Math.floor(selectedItem.audioStartMs / 1000) % 60).padStart(2, '0') }}
          — {{ Math.floor(selectedItem.audioEndMs / 60000) }}:{{ String(Math.floor(selectedItem.audioEndMs / 1000) % 60).padStart(2, '0') }}
        </small>
        <p v-if="submitted && selectedItem.keySentence"><b>关键句</b>{{ selectedItem.keySentence }}</p>
      </section>

      <section class="cet-material">
        <span>QUESTION</span>
        <h3>{{ selectedItem.prompt }}</h3>
        <p v-if="selectedItem.passage">{{ selectedItem.passage }}</p>
      </section>

      <section class="cet-answer">
        <span>YOUR ANSWER</span>
        <div v-if="selectedItem.answerType === 'CHOICE'" class="cet-options">
          <button v-for="(option, index) in options" :key="option" :class="{ selected: answer === String.fromCharCode(65 + index) }" type="button" :disabled="submitted" @click="answer=String.fromCharCode(65 + index)">
            <b>{{ String.fromCharCode(65 + index) }}</b><span>{{ option }}</span>
          </button>
        </div>
        <textarea v-else v-model="answer" :disabled="submitted" rows="8" placeholder="在这里输入你的答案…"></textarea>
        <button v-if="!submitted" class="primary-button cet-submit" type="button" :disabled="!answer.trim()" @click="submitAnswer">提交并查看解析</button>
      </section>

      <section v-if="submitted" class="cet-result" :class="{ correct: resultCorrect }">
        <div class="cet-result-title">
          <span>{{ selectedItem.answerType === 'TEXT' ? '参考答案已解锁' : resultCorrect ? '回答正确' : '需要再看一下' }}</span>
          <strong v-if="selectedItem.answerType === 'CHOICE'">正确答案 {{ selectedItem.correctAnswer }}</strong>
        </div>
        <div v-if="selectedItem.answerType === 'TEXT'" class="cet-reference"><b>参考答案</b><p>{{ selectedItem.correctAnswer }}</p></div>
        <div class="cet-analysis"><b>题目解析</b><p>{{ selectedItem.analysis || '管理员暂未录入解析。' }}</p></div>
        <button v-if="selectedItem.translation" class="secondary-button" type="button" @click="showTranslation=!showTranslation">{{ showTranslation ? '收起译文' : '查看原文译文' }}</button>
        <div v-if="showTranslation" class="cet-translation"><b>参考译文</b><p>{{ selectedItem.translation }}</p></div>
      </section>
    </article>
  </section>

  <div v-if="showAdmin" class="modal-backdrop" @click.self="showAdmin=false">
    <section class="upload-modal cet-admin-modal">
      <button class="modal-close" type="button" @click="showAdmin=false">×</button>
      <span class="eyebrow">管理员功能</span><h2>CET 题库管理</h2>
      <nav class="cet-admin-tabs"><button type="button" :class="{ active: adminTab === 'PAPERS' }" @click="adminTab='PAPERS';selectedAdminSectionId=null">套卷管理</button><button type="button" :class="{ active: adminTab === 'ITEMS' }" @click="adminTab='ITEMS'">题目管理</button></nav>
      <p v-if="adminMessage" class="form-success">{{ adminMessage }}</p>

      <section v-if="adminTab === 'PAPERS'" class="cet-admin-component">
        <header><div><h3>套卷与外部来源</h3><p>新建套卷会自动建立全部合法题型资源槽位。</p></div></header>
        <form class="cet-admin-form compact" @submit.prevent="savePaper">
          <label>级别<select v-model="paperForm.level" :disabled="editingPaperId"><option>CET4</option><option>CET6</option></select></label>
          <label>年份<input v-model.number="paperForm.examYear" type="number" min="2000" max="2100" required /></label>
          <label>月份<select v-model.number="paperForm.examMonth"><option :value="6">6 月</option><option :value="12">12 月</option></select></label>
          <label>第几套<input v-model.number="paperForm.setNumber" type="number" min="1" max="9" required /></label>
          <label class="full">试卷标题<input v-model="paperForm.title" required placeholder="例如：2026 年 6 月四级 · 第一套" /></label>
          <label>来源名称<input v-model="paperForm.sourceName" required maxlength="120" placeholder="例如：中国教育考试网" /></label>
          <label class="full">来源核对地址<input v-model="paperForm.sourcePageUrl" type="url" required maxlength="500" placeholder="https://..." /></label>
          <label class="full">来源／使用说明<textarea v-model="paperForm.usageNote" maxlength="500" rows="2" placeholder="版本、核对日期或使用范围"></textarea></label>
          <div class="cet-admin-actions full"><button class="secondary-button" type="submit">{{ editingPaperId ? '保存套卷修改' : '添加试卷' }}</button><button v-if="editingPaperId" type="button" @click="cancelPaperEdit">取消编辑</button></div>
        </form>
        <h3>已有套卷（{{ papers.length }}）</h3>
        <div class="cet-admin-paper-list">
          <article v-for="paper in papers" :key="paper.id">
            <div><b>{{ paper.title }}</b><small>{{ paper.sourceName || '来源待补充' }} · {{ adminItemsForPaper(paper.id).length }} 道练习题</small></div>
            <span><button type="button" @click="editPaper(paper)">修改</button><button class="danger-link" type="button" @click="removePaper(paper)">删除</button></span>
          </article>
        </div>
      </section>

      <section v-else class="cet-admin-component">
        <template v-if="!selectedAdminSection">
          <header><div><h3>选择试卷与题型</h3><p>每张卡片对应唯一资源槽位；空白槽位可以直接开始录入。</p></div></header>
          <label class="cet-paper-search"><span>按关键词筛选套卷</span><input v-model="adminPaperSearch" type="search" placeholder="例如：2025 四级 第一套" /><small>{{ filteredAdminPapers.length }} / {{ papers.length }} 套</small></label>
          <div v-if="!filteredAdminPapers.length" class="cet-resource-empty">没有匹配的套卷，请更换关键词。</div>
          <section v-for="paper in filteredAdminPapers" :key="paper.id" class="cet-resource-paper">
            <header><div><b>{{ paper.title }}</b><small>{{ paper.level }}</small></div><span>{{ adminItemsForPaper(paper.id).length }} 道题</span></header>
            <div class="cet-resource-grid">
              <button v-for="slot in adminSections.filter(entry => entry.paperId === paper.id)" :key="slot.id" type="button" :class="{ filled: slot.itemCount > 0 }" @click="selectAdminSection(slot)">
                <span>{{ slot.mode === 'PRACTICE' ? '分类练习' : '精听精讲' }}</span><b>{{ sectionName(slot.section) }}</b><small>{{ slot.itemCount ? `${slot.itemCount} 道内容，可修改` : '空白，点击录入' }}</small>
              </button>
            </div>
          </section>
        </template>

        <template v-else>
          <header class="cet-resource-editor-head"><button type="button" @click="backToAdminSections">← 返回题型选择</button><div><small>{{ selectedAdminSection.paperTitle }} · {{ selectedAdminSection.mode === 'PRACTICE' ? '分类练习' : '精听精讲' }}</small><h3>{{ sectionName(selectedAdminSection.section) }}</h3></div><button v-if="selectedAdminItems.length" class="danger-link" type="button" @click="clearAdminSection">清空当前题型</button></header>
          <div v-if="selectedAdminItems.length" class="cet-admin-item-list">
            <article v-for="item in selectedAdminItems" :key="item.id" :class="{ active: editingItemId === item.id }"><div><b>{{ item.title }}</b><small>排序 {{ item.itemOrder }}</small></div><span><button type="button" @click="editItem(item)">修改</button></span></article>
          </div>
          <div v-else class="cet-resource-empty">这个题型还没有内容，使用下方表单录入第一道题。</div>

          <form class="cet-admin-form" @submit.prevent="saveItem">
            <h3>{{ editingItemId ? '修改当前题目' : '录入题目' }}</h3>
            <div class="cet-fixed-mapping full"><span>所属试卷<strong>{{ selectedAdminSection.paperTitle }}</strong></span><span>训练方式<strong>{{ selectedAdminSection.mode === 'PRACTICE' ? '分类练习' : '精听精讲' }}</strong></span><span>题型<strong>{{ sectionName(selectedAdminSection.section) }} · {{ selectedAdminSection.section }}</strong></span></div>
        <label>答案类型<select v-model="itemForm.answerType"><option value="CHOICE">选择题</option><option value="TEXT">主观题</option></select></label>
        <label class="full">标题<input v-model="itemForm.title" required /></label>
        <label class="full">题目<textarea v-model="itemForm.prompt" rows="3"></textarea></label>
        <label class="full">原文<textarea v-model="itemForm.passage" rows="5"></textarea></label>
        <label class="full">选项 JSON<textarea v-model="itemForm.optionsJson" rows="3" placeholder='["选项 A","选项 B"]'></textarea></label>
        <label>正确答案<input v-model="itemForm.correctAnswer" placeholder="A 或参考答案" /></label>
        <label>排序<input v-model.number="itemForm.itemOrder" type="number" /></label>
        <label class="full">解析<textarea v-model="itemForm.analysis" rows="4"></textarea></label>
        <label class="full">译文<textarea v-model="itemForm.translation" rows="4"></textarea></label>
        <label class="full">听力关键句<textarea v-model="itemForm.keySentence" rows="2"></textarea></label>
        <label>共用音频起点（毫秒）<input v-model.number="itemForm.audioStartMs" type="number" min="0" /></label>
        <label>共用音频终点（毫秒）<input v-model.number="itemForm.audioEndMs" type="number" min="0" /></label>
        <section class="cet-practice-audio full">
          <div><b>该套练习共用音频</b><small>{{ papers.find(paper => paper.id === itemForm.paperId)?.audioOriginalName || '尚未绑定' }}</small></div>
          <label>选择整段音频<input type="file" accept="audio/*" @change="practiceAudioFile=$event.target.files[0]" /></label>
          <button class="secondary-button" type="button" :disabled="!practiceAudioFile" @click="bindPracticeAudio">绑定／替换共用音频</button>
          <p>仅供分类练习和精听精讲截取时间使用，不会出现在“完整套卷”下载模块。</p>
        </section>
        <details class="full cet-audio-override"><summary>特殊情况：为本题覆盖独立音频</summary><label>独立音频文件<input type="file" accept="audio/*" @change="audioFile=$event.target.files[0]" /></label></details>
            <button class="primary-button full" type="submit">{{ editingItemId ? '保存修改' : '添加到当前题型' }}</button>
          </form>
        </template>
      </section>
    </section>
  </div>

</template>
