<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { catalogApi, isAdmin } from '../api'

const route = useRoute()
const query = ref('')
const teachers = ref([])
const loadError = ref('')
const courses = ref([])
const showAdd = ref(false)
const adding = ref(false)
const formError = ref('')
const newTeacher = ref({ name: '', college: '数学与统计学院' })
const courseNames = { 'data-structure': '数据结构', java: 'Java 程序设计', network: '计算机网络' }
const courseInfo = computed(() => courses.value.find((item) => item.slug === route.params.courseId))
const courseName = computed(() => courseInfo.value?.name || courseNames[route.params.courseId] || '课程详情')
const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return teachers.value.filter((item) => `${item.name} ${item.college}`.toLowerCase().includes(keyword))
})
const resourceTotal = computed(() => teachers.value.reduce((sum, item) => sum + item.resources, 0))
const postTotal = computed(() => teachers.value.reduce((sum, item) => sum + item.posts, 0))

const normalizeTeacher = (item) => ({
  ...item,
  id: item.slug,
  course: courseName.value,
  resources: item.resourceCount ?? item.resources ?? 0,
  posts: item.postCount ?? item.posts ?? 0
})

async function loadTeachers(slug = route.params.courseId) {
  loadError.value = ''
  try {
    const [courseList, teacherList] = await Promise.all([catalogApi.courses(), catalogApi.teachers(slug)])
    courses.value = courseList
    teachers.value = teacherList.map(normalizeTeacher)
  } catch (error) {
    teachers.value = []
    loadError.value = error.message
  }
}

async function submitTeacher() {
  formError.value = ''
  if (!newTeacher.value.name.trim() || !newTeacher.value.college.trim()) return
  adding.value = true
  try {
    await catalogApi.addTeacher(route.params.courseId, {
      name: newTeacher.value.name.trim(),
      college: newTeacher.value.college.trim()
    })
    await loadTeachers()
    query.value = newTeacher.value.name.trim()
    newTeacher.value = { name: '', college: courseInfo.value?.college || '数学与统计学院' }
    showAdd.value = false
  } catch (error) {
    formError.value = error.message
  } finally {
    adding.value = false
  }
}

watch(() => route.params.courseId, loadTeachers, { immediate: true })
</script>

<template>
  <section class="teacher-page page-width workspace">
    <div v-if="loadError" class="empty-state">数据加载失败：{{ loadError }}。请稍后重试。</div>
    <div class="path-nav"><router-link to="/">课程</router-link><span>›</span><b>{{ courseName }}</b></div>
    <div class="teacher-page-head">
      <div>
        <span class="eyebrow">{{ courseInfo?.code || 'COURSE' }}</span>
        <h1>{{ courseName }}</h1>
        <p>{{ teachers.length }} 位老师 · {{ resourceTotal }} 份资料 · {{ postTotal }} 条讨论</p>
      </div>
      <button v-if="isAdmin" class="secondary-button" type="button" @click="showAdd = true">＋ 添加老师</button>
    </div>
    <div class="teacher-toolbar">
      <label class="clean-search teacher-search"><span>⌕</span><input v-model="query" placeholder="搜索老师姓名或学院" /><button v-if="query" type="button" @click="query = ''">×</button></label>
      <span>{{ filtered.length }} 个结果</span>
    </div>
    <div class="teacher-list-wrap">
      <div class="teacher-grid">
        <router-link v-for="teacher in filtered" :key="teacher.id" :to="`/courses/${route.params.courseId}/teachers/${teacher.id}`" class="teacher-card">
          <div class="teacher-avatar">{{ teacher.name.slice(0, 1) }}</div>
          <div class="teacher-main"><h3>{{ teacher.name }}</h3><p>{{ teacher.college }}</p></div>
          <div class="teacher-data"><span>{{ teacher.resources }} 份资料 · {{ teacher.posts }} 条讨论</span><i>→</i></div>
        </router-link>
      </div>
      <div v-if="!filtered.length" class="empty-state">没有找到这位老师。{{ isAdmin ? '你可以点击右上角添加。' : '请联系管理员补充。' }}</div>
    </div>
  </section>

  <div v-if="showAdd" class="modal-backdrop" @click.self="showAdd = false">
    <form class="upload-modal teacher-modal" @submit.prevent="submitTeacher">
      <button class="modal-close" type="button" @click="showAdd = false">×</button>
      <span class="eyebrow">{{ courseName }}</span>
      <h2>添加任课老师</h2>
      <p>提交后会创建该课程的老师圈，请先确认没有重复。</p>
      <label>老师姓名<input v-model="newTeacher.name" type="text" maxlength="40" placeholder="例如：王老师" required /></label>
      <label>所属学院<input v-model="newTeacher.college" type="text" maxlength="100" placeholder="例如：计算机学院" required /></label>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <button class="primary-button wide" type="submit" :disabled="adding">{{ adding ? '正在添加…' : '确认添加' }}</button>
    </form>
  </div>
</template>
