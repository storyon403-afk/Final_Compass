<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, isAdmin } from '../api'

const route = useRoute()
const router = useRouter()
const colleges = ref([])
const courses = ref([])
const selectedCollege = ref('')
const selectedProgram = ref('')
const selectedType = ref('')
const query = ref('')
const loadError = ref('')
const showAddCollege = ref(false)
const showAddCourse = ref(false)
const adding = ref(false)
const formError = ref('')
const newCollegeName = ref('')
const newCourse = ref({ name: '', code: '' })

const programs = computed(() => selectedCollege.value === '数学与统计学院'
  ? ['数学类', '数学与应用数学', '统计学']
  : ['未分专业'])
const courseTypes = ['专业课', '非专业课']
const stage = computed(() => !selectedCollege.value ? 'college' : !selectedProgram.value ? 'program' : !selectedType.value ? 'type' : 'courses')

function inferredProgram(course) {
  if (course.programName) return course.programName
  return /统计|概率|随机/.test(`${course.name} ${course.category}`) ? '统计学' : '数学与应用数学'
}

function inferredType(course) {
  if (course.courseType) return course.courseType
  return ['数学与统计', '专业核心课'].includes(course.category) ? '专业课' : '非专业课'
}

const visibleCourses = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return courses.value.filter((course) => {
    if (course.college !== selectedCollege.value) return false
    if (selectedProgram.value && inferredProgram(course) !== selectedProgram.value) return false
    if (selectedType.value && inferredType(course) !== selectedType.value) return false
    return !keyword || `${course.name} ${course.code || ''}`.toLowerCase().includes(keyword)
  })
})

const programSearchResults = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return []
  return courses.value.filter((course) =>
    course.college === selectedCollege.value
    && inferredProgram(course) === selectedProgram.value
    && `${course.name} ${course.code || ''}`.toLowerCase().includes(keyword))
})

function navigationQuery(college = '', program = '', type = '') {
  return {
    ...(college ? { college } : {}),
    ...(program ? { program } : {}),
    ...(type ? { type } : {})
  }
}

function syncNavigationFromRoute() {
  selectedCollege.value = typeof route.query.college === 'string' ? route.query.college : ''
  selectedProgram.value = selectedCollege.value && typeof route.query.program === 'string' ? route.query.program : ''
  selectedType.value = selectedProgram.value && typeof route.query.type === 'string' ? route.query.type : ''
  query.value = ''
}

function chooseCollege(name) {
  router.push({ path: '/', query: navigationQuery(name) })
}
function chooseProgram(name) {
  router.push({ path: '/', query: navigationQuery(selectedCollege.value, name) })
}
function chooseType(name) {
  router.push({ path: '/', query: navigationQuery(selectedCollege.value, selectedProgram.value, name) })
}
function goTo(level) {
  if (level === 'college') router.push({ path: '/' })
  if (level === 'program') router.push({ path: '/', query: navigationQuery(selectedCollege.value) })
  if (level === 'type') router.push({ path: '/', query: navigationQuery(selectedCollege.value, selectedProgram.value) })
}

async function loadData() {
  loadError.value = ''
  try { [colleges.value, courses.value] = await Promise.all([catalogApi.colleges(), catalogApi.courses()]) }
  catch (error) { loadError.value = error.message }
}

async function submitCollege() {
  if (!newCollegeName.value.trim()) return
  adding.value = true; formError.value = ''
  try {
    const created = await catalogApi.addCollege(newCollegeName.value.trim())
    await loadData(); chooseCollege(created.name); newCollegeName.value = ''; showAddCollege.value = false
  } catch (error) { formError.value = error.message }
  finally { adding.value = false }
}

async function submitCourse() {
  adding.value = true; formError.value = ''
  try {
    await catalogApi.addCourse({
      name: newCourse.value.name.trim(), code: newCourse.value.code.trim(),
      category: selectedType.value === '专业课' ? '专业核心课' : '语言与通识',
      college: selectedCollege.value, programName: selectedProgram.value, courseType: selectedType.value
    })
    await loadData(); newCourse.value = { name: '', code: '' }; showAddCourse.value = false
  } catch (error) { formError.value = error.message }
  finally { adding.value = false }
}

function closeModals(event) {
  if (event.key !== 'Escape') return
  showAddCollege.value = false
  showAddCourse.value = false
}

watch(() => route.query, syncNavigationFromRoute, { immediate: true })

onMounted(() => { loadData(); window.addEventListener('keydown', closeModals) })
onBeforeUnmount(() => window.removeEventListener('keydown', closeModals))
</script>

<template>
  <section class="workspace page-width">
    <header class="workspace-header">
      <div><span class="eyebrow">课程导航</span><h1>今天想复习什么？</h1><p>按学院、专业和课程类型找到对应的老师圈。</p></div>
      <button v-if="isAdmin && stage === 'college'" class="secondary-button" type="button" @click="showAddCollege = true">＋ 添加学院</button>
      <button v-if="isAdmin && stage === 'courses'" class="secondary-button" type="button" @click="showAddCourse = true">＋ 添加课程</button>
    </header>

    <nav v-if="selectedCollege" class="path-nav" aria-label="当前位置">
      <button type="button" @click="goTo('college')">学院</button><span>›</span>
      <button type="button" @click="goTo('program')">{{ selectedCollege }}</button>
      <template v-if="selectedProgram"><span>›</span><button type="button" @click="goTo('type')">{{ selectedProgram }}</button></template>
      <template v-if="selectedType"><span>›</span><b>{{ selectedType }}</b></template>
    </nav>

    <div v-if="loadError" class="empty-state error-state">{{ loadError }}</div>

    <div v-if="stage === 'college'" class="selection-section">
      <div class="section-heading"><h2>选择学院</h2><span>{{ colleges.length }} 个学院</span></div>
      <div class="choice-grid college-grid">
        <button v-for="college in colleges" :key="college.id" class="choice-card" type="button" @click="chooseCollege(college.name)">
          <span class="choice-icon">∑</span><span><strong>{{ college.name }}</strong><small>查看专业与课程</small></span><i>→</i>
        </button>
      </div>
    </div>

    <div v-else-if="stage === 'program'" class="selection-section">
      <div class="section-heading"><h2>选择专业</h2><span>{{ selectedCollege }}</span></div>
      <div class="choice-grid">
        <button v-for="program in programs" :key="program" class="choice-card" type="button" @click="chooseProgram(program)">
          <span class="choice-icon">{{ program === '统计学' ? 'σ' : 'ƒ' }}</span><span><strong>{{ program }}</strong><small>进入课程分类</small></span><i>→</i>
        </button>
      </div>
    </div>

    <div v-else-if="stage === 'type'" class="selection-section">
      <div class="course-toolbar"><div class="section-heading"><h2>选择课程类型</h2><span>{{ selectedProgram }}</span></div><label class="clean-search"><span>⌕</span><input v-model="query" placeholder="在本专业搜索课程名称或代码" /><button v-if="query" type="button" aria-label="清空搜索" @click="query = ''">×</button></label></div>
      <div class="choice-grid">
        <button v-for="type in courseTypes" :key="type" class="choice-card" type="button" @click="chooseType(type)">
          <span class="choice-icon">{{ type === '专业课' ? '01' : '02' }}</span><span><strong>{{ type }}</strong><small>{{ type === '专业课' ? '专业培养方案内课程' : '公共基础与通识课程' }}</small></span><i>→</i>
        </button>
      </div>
      <div v-if="query" class="course-list">
        <router-link v-for="course in programSearchResults" :key="`${course.slug}-${course.courseType}`" :to="`/courses/${course.slug}`" class="course-row">
          <span class="course-code">{{ course.code }}</span><span class="course-copy"><strong>{{ course.name }}</strong><small>{{ inferredType(course) }}</small></span><span class="row-arrow">→</span>
        </router-link>
        <div v-if="!programSearchResults.length" class="empty-state"><strong>没有找到对应课程</strong><p>请检查课程名称或代码。</p></div>
      </div>
    </div>

    <div v-else class="selection-section">
      <div class="course-toolbar"><div class="section-heading"><h2>{{ selectedType }}</h2><span>{{ visibleCourses.length }} 门课程</span></div><label class="clean-search"><span>⌕</span><input v-model="query" placeholder="搜索课程名称或代码" /><button v-if="query" type="button" aria-label="清空搜索" @click="query = ''">×</button></label></div>
      <div class="course-list">
        <router-link v-for="course in visibleCourses" :key="`${course.slug}-${course.programName}`" :to="`/courses/${course.slug}`" class="course-row">
          <span class="course-code">{{ course.code || 'COURSE' }}</span><span class="course-copy"><strong>{{ course.name }}</strong><small>{{ inferredProgram(course) }} · {{ inferredType(course) }}</small></span><span class="row-arrow">→</span>
        </router-link>
      </div>
      <div v-if="!visibleCourses.length" class="empty-state"><strong>这里还没有课程</strong><p>{{ isAdmin ? '可以使用右上角按钮添加第一门课程。' : '请联系管理员补充课程。' }}</p></div>
    </div>
  </section>

  <div v-if="showAddCollege" class="modal-backdrop" @click.self="showAddCollege = false">
    <form class="upload-modal compact-modal" @submit.prevent="submitCollege"><button class="modal-close" type="button" aria-label="关闭" @click="showAddCollege = false">×</button><span class="eyebrow">管理员功能</span><h2>添加学院</h2><p>新增后会出现在学院入口，不影响已有课程。</p><label>学院名称<input v-model="newCollegeName" maxlength="100" placeholder="例如：物理学院" required /></label><p v-if="formError" class="form-error">{{ formError }}</p><button class="primary-button wide" :disabled="adding">{{ adding ? '正在添加…' : '确认添加' }}</button></form>
  </div>

  <div v-if="showAddCourse" class="modal-backdrop" @click.self="showAddCourse = false">
    <form class="upload-modal compact-modal" @submit.prevent="submitCourse"><button class="modal-close" type="button" aria-label="关闭" @click="showAddCourse = false">×</button><span class="eyebrow">{{ selectedProgram }} · {{ selectedType }}</span><h2>添加课程</h2><p>课程代码全局唯一；若课程已存在，将直接关联到当前专业，不会重复创建。</p><label>课程名称<input v-model="newCourse.name" maxlength="80" required /></label><label>课程代码<input v-model="newCourse.code" maxlength="32" placeholder="例如：MATH101" required /></label><p v-if="formError" class="form-error">{{ formError }}</p><button class="primary-button wide" :disabled="adding">{{ adding ? '正在添加…' : '确认添加' }}</button></form>
  </div>
</template>
