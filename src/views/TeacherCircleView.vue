<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { catalogApi, circleApi, profile, isAdmin, systemApi } from '../api'
const PdfViewer = defineAsyncComponent(() => import('../components/PdfViewer.vue'))

const route = useRoute()
const courseSlug = computed(() => route.params.courseId || 'data-structure')
const teacherSlug = computed(() => route.params.teacherId || 'lin')
const knownCourses = { 'data-structure': '数据结构', java: 'Java 程序设计', network: '计算机网络' }
const knownTeachers = { lin: '林老师', zhou: '周老师', chen: '陈老师' }
const courseName = ref(knownCourses[courseSlug.value] || '当前课程')
const teacherName = ref(knownTeachers[teacherSlug.value] || '任课老师')
const activeTab = ref('resources')
const showUpload = ref(false)
const resources = ref([])
const posts = ref([])
const postText = ref('')
const uploadTitle = ref('')
const selectedFile = ref('')
const uploadFile = ref(null)
const uploading = ref(false)
const toast = ref('')
const loadError = ref('')
const discussionDate = ref('')
const pdfPreview = ref(null)
const imagePreview = ref(null)
const guide = ref({ contentMarkdown: '', changeNote: '', updatedAt: null })
const showGuideEditor = ref(false)
const showGuideSubmit = ref(false)
const guideDraft = ref('')
const guideChangeNote = ref('')
const submissionDraft = ref('')
const approvedReferences = ref([])
const incorporatedIds = ref([])
const guideBusy = ref(false)

const resourceCount = computed(() => resources.value.length)
const guideBlocks = computed(() => guide.value.contentMarkdown.split('\n').map((line) => {
  const value = line.trim()
  if (!value) return { type: 'space', text: '' }
  if (value.startsWith('## ')) return { type: 'h2', text: value.slice(3) }
  if (value.startsWith('# ')) return { type: 'h1', text: value.slice(2) }
  if (/^[-*]\s+/.test(value)) return { type: 'bullet', text: value.replace(/^[-*]\s+/, '') }
  if (/^\d+\.\s+/.test(value)) return { type: 'number', text: value.replace(/^\d+\.\s+/, '') }
  return { type: 'paragraph', text: value }
}))

function notify(message) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 2600)
}

async function thankResource(resource) {
  try {
    const result = await circleApi.thank(resource.id, courseSlug.value, teacherSlug.value)
    resource.likes = result.thanks
    notify(result.added ? '已收藏并感谢分享者' : '你已经收藏过这份资料')
  } catch (error) { notify(error.message) }
}

async function openResource(resource, download = false) {
  const filename = resource.originalName || resource.title
  const isPdf = /\.pdf$/i.test(filename)
  const isImage = /\.(png|jpe?g)$/i.test(filename)
  if (!download && !isPdf && !isImage) {
    notify('Word、PPT 和压缩包暂不支持在线预览，已开始下载')
    return openResource(resource, true)
  }
  try {
    const blob = await circleApi.file(resource.id, courseSlug.value, teacherSlug.value, download ? 'attachment' : 'inline')
    if (download) {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = resource.originalName || resource.title
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } else if (isPdf) {
      pdfPreview.value = { blob, resource }
    } else {
      closeImagePreview()
      imagePreview.value = { url: URL.createObjectURL(blob), resource }
    }
    resource.downloads++
  } catch (error) {
    notify(error.message)
  }
}

function closeImagePreview() {
  if (imagePreview.value?.url) URL.revokeObjectURL(imagePreview.value.url)
  imagePreview.value = null
}

onBeforeUnmount(closeImagePreview)

async function loadDiscussions() {
  try {
    const items = await circleApi.discussions(courseSlug.value, teacherSlug.value, discussionDate.value)
    posts.value = items.map((item) => ({ ...item, time: new Date(item.createdAt).toLocaleString('zh-CN') }))
  } catch (error) { notify(error.message) }
}

async function removePost(post) {
  if (!window.confirm('确认删除这条帖子？删除后普通用户不可见，并会记录管理员操作。')) return
  try {
    await systemApi.removeDiscussion(post.id)
    posts.value = posts.value.filter((item) => item.id !== post.id)
    notify('帖子已删除')
  } catch (error) { notify(error.message) }
}

async function submitPost() {
  const content = postText.value.trim()
  if (!content) return
  try {
    await circleApi.postDiscussion(content, null, courseSlug.value, teacherSlug.value)
    postText.value = ''
    notify('讨论已提交，管理员审核后公开')
  } catch (error) { notify(error.message) }
}

async function submitResource() {
  if (!uploadTitle.value.trim() || !uploadFile.value || uploading.value) return
  uploading.value = true
  try {
  await circleApi.upload({ title: uploadTitle.value.trim(), file: uploadFile.value }, courseSlug.value, teacherSlug.value)
  uploadTitle.value = ''
  selectedFile.value = ''
  showUpload.value = false
  notify('资料已提交，管理员审核后公开')
  } catch (error) { notify(error.message) }
  finally { uploading.value = false }
}

async function openGuideEditor() {
  guideDraft.value = guide.value.contentMarkdown
  guideChangeNote.value = ''
  incorporatedIds.value = []
  try { approvedReferences.value = await circleApi.approvedGuideReferences(courseSlug.value, teacherSlug.value) }
  catch (error) { notify(error.message); return }
  showGuideEditor.value = true
}

async function saveGuide() {
  if (guideBusy.value || !guideChangeNote.value.trim()) return
  guideBusy.value = true
  try {
    guide.value = await circleApi.updateGuide({
      contentMarkdown: guideDraft.value,
      changeNote: guideChangeNote.value.trim(),
      incorporatedSubmissionIds: incorporatedIds.value
    }, courseSlug.value, teacherSlug.value)
    showGuideEditor.value = false
    notify('复习指南已更新')
  } catch (error) { notify(error.message) }
  finally { guideBusy.value = false }
}

async function submitGuideReference() {
  if (!submissionDraft.value.trim() || guideBusy.value) return
  guideBusy.value = true
  try {
    await circleApi.submitGuideReference(submissionDraft.value.trim(), courseSlug.value, teacherSlug.value)
    submissionDraft.value = ''
    showGuideSubmit.value = false
    notify('参考建议已提交，管理员审核后可采纳')
  } catch (error) { notify(error.message) }
  finally { guideBusy.value = false }
}

onMounted(async () => {
  try {
    const [allCourses, allTeachers, remoteResources, remotePosts, remoteGuide] = await Promise.all([
      catalogApi.courses(),
      catalogApi.teachers(courseSlug.value),
      circleApi.resources(courseSlug.value, teacherSlug.value),
      circleApi.discussions(courseSlug.value, teacherSlug.value),
      circleApi.guide(courseSlug.value, teacherSlug.value)
    ])
    courseName.value = allCourses.find((item) => item.slug === courseSlug.value)?.name || courseName.value
    teacherName.value = allTeachers.find((item) => item.slug === teacherSlug.value)?.name || teacherName.value
    resources.value = remoteResources.map((item) => ({ ...item, author: item.contributor, date: new Date(item.createdAt).toLocaleString('zh-CN'), likes: item.thanks, downloads: item.downloads, note: item.description }))
    posts.value = remotePosts.map((item) => ({ ...item, time: new Date(item.createdAt).toLocaleString('zh-CN') }))
    guide.value = remoteGuide
  } catch (error) { loadError.value = error.message }
})
</script>

<template>
  <section class="circle-header">
    <div class="page-width breadcrumb">课程广场 <span>/</span> {{ courseName }} <span>/</span> {{ teacherName }}</div>
    <div class="page-width circle-profile">
      <div class="teacher-avatar large">{{ teacherName.slice(0, 1) }}</div>
      <div><span class="eyebrow">{{ courseName }} · 老师圈</span><h1>{{ teacherName }}</h1><p>本页面内容由同学共同整理，不代表老师本人或学院官方意见。</p></div>
      <button class="primary-button" @click="showUpload = true">＋ 分享复习资料</button>
    </div>
    <div class="page-width circle-tabs">
      <button :class="{ active: activeTab === 'resources' }" @click="activeTab = 'resources'">复习资料 <span>{{ resourceCount }}</span></button>
      <button :class="{ active: activeTab === 'discussion' }" @click="activeTab = 'discussion'">同学讨论 <span>{{ posts.length }}</span></button>
      <button :class="{ active: activeTab === 'guide' }" @click="activeTab = 'guide'">复习指南</button>
    </div>
  </section>
  <div v-if="loadError" class="page-width empty-state">数据加载失败：{{ loadError }}。请稍后重试。</div>

  <section v-if="activeTab === 'resources'" class="page-width content-layout">
    <div class="content-main">
      <div class="content-toolbar"><div><h2>复习资料</h2><p>按最近更新排序</p></div><select aria-label="资料类型"><option>全部类型</option><option>复习提纲</option><option>历年资料</option></select></div>
      <article v-for="resource in resources" :key="resource.id" class="resource-card">
        <div class="file-icon">文</div>
        <div class="resource-body"><div class="resource-title"><span>{{ resource.type }}</span><h3>{{ resource.title }}</h3></div><p>{{ resource.note }}</p><div class="resource-meta">感谢 <b>{{ resource.author }}</b> 的分享 · {{ resource.date }}</div></div>
        <div class="resource-actions"><button @click="thankResource(resource)">♡ 收藏 {{ resource.likes }}</button><button @click="openResource(resource)">打开</button><button @click="openResource(resource, true)">下载 {{ resource.downloads }}</button></div>
      </article>
      <div v-if="!resources.length" class="empty-state">这个老师圈还没有资料，欢迎分享第一份复习笔记。</div>
    </div>
    <aside class="content-aside">
      <div class="notice-card"><span>置顶</span><h3>本圈复习提醒</h3><p>同学整理的重点仅供参考，具体考试范围请以任课老师的课堂通知为准。</p></div>
      <div class="thanks-card"><span>♡</span><h3>感谢分享者</h3><p>资料属于贡献者的学习成果。引用、下载和二次分享时，请保留来源并尊重原作者。</p></div>
    </aside>
  </section>

  <section v-else-if="activeTab === 'discussion'" class="page-width discussion-layout">
      <div class="forum-policy"><strong>讨论区内容边界</strong><span>此处只允许提出与知识或与老师相关的话题，其余内容均会被管理员删除。</span></div>
      <div class="discussion-filter"><label>按日期查找<input v-model="discussionDate" type="date" @change="loadDiscussions" /></label><button v-if="discussionDate" type="button" @click="discussionDate = ''; loadDiscussions()">查看全部</button></div>
      <div class="composer"><div class="anonymous-avatar">匿</div><div><textarea v-model="postText" maxlength="500" placeholder="问复习问题、补充重点，或分享你的复习方法……"></textarea><div class="composer-foot"><span>将以“{{ profile.nickname }}”匿名发布 · {{ postText.length }}/500</span><button class="primary-button small" @click="submitPost">发布讨论</button></div></div></div>
    <article v-for="post in posts" :key="post.id" class="post-card"><div class="anonymous-avatar alt">{{ post.author[0] }}</div><div class="post-body"><div><strong>{{ post.author }}</strong><span>{{ post.time }}</span></div><p>{{ post.content }}</p><footer><button @click="post.likes++">♡ {{ post.likes }}</button><button>◯ {{ post.replies }} 条回复</button><button v-if="isAdmin" class="danger-link" @click="removePost(post)">管理员删除</button></footer></div></article>
    <div v-if="!posts.length" class="empty-state">这里还没有讨论，写下第一个复习问题吧。</div>
  </section>

  <section v-else class="page-width guide-page">
    <header class="guide-head"><div><span class="eyebrow">管理员整理 · 同学共建参考</span><h2>{{ teacherName }}《{{ courseName }}》复习指南</h2></div><div><button class="secondary-button" type="button" @click="showGuideSubmit = true">提交参考建议</button><button v-if="isAdmin" class="primary-button" type="button" @click="openGuideEditor">编辑正式指南</button></div></header>
    <article v-if="guide.contentMarkdown" class="markdown-guide">
      <template v-for="(block, index) in guideBlocks" :key="index">
        <h2 v-if="block.type === 'h1'">{{ block.text }}</h2><h3 v-else-if="block.type === 'h2'">{{ block.text }}</h3>
        <div v-else-if="block.type === 'bullet'" class="markdown-list"><span>•</span><p>{{ block.text }}</p></div>
        <div v-else-if="block.type === 'number'" class="markdown-list numbered"><span>{{ index + 1 }}</span><p>{{ block.text }}</p></div>
        <div v-else-if="block.type === 'space'" class="markdown-space"></div><p v-else>{{ block.text }}</p>
      </template>
    </article>
    <div v-else class="empty-state"><strong>管理员尚未发布复习指南</strong><p>你可以先提交一份参考建议，审核后供管理员整理。</p></div>
    <footer v-if="guide.updatedAt" class="guide-change-note"><span>最近更新：{{ new Date(guide.updatedAt).toLocaleString('zh-CN') }}</span><p><b>本次调整</b>{{ guide.changeNote || '内容整理与校正' }}</p></footer>
  </section>

  <div v-if="showUpload" class="modal-backdrop" @click.self="showUpload = false">
    <form class="upload-modal" @submit.prevent="submitResource"><button class="modal-close" type="button" @click="showUpload = false">×</button><span class="eyebrow">帮助同学少走弯路</span><h2>分享复习资料</h2><p>请勿上传包含个人隐私、未经授权的付费内容或明确禁止传播的资料。</p><label>资料标题<input v-model="uploadTitle" maxlength="80" placeholder="例如：期末重点章节梳理" required /></label><label>选择文件<input type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.zip,.png,.jpg" required @change="uploadFile = $event.target.files?.[0] || null; selectedFile = uploadFile?.name || ''" /></label><div v-if="selectedFile" class="selected-file">已选择：{{ selectedFile }}</div><label class="consent"><input type="checkbox" required /> <span>我确认有权分享此资料，并同意平台以匿名昵称标注感谢。</span></label><button class="primary-button wide" type="submit" :disabled="uploading">{{ uploading ? '正在提交…' : '确认匿名分享' }}</button></form>
  </div>
  <div v-if="showGuideSubmit" class="modal-backdrop" @click.self="showGuideSubmit = false">
    <form class="upload-modal guide-submit-modal" @submit.prevent="submitGuideReference"><button class="modal-close" type="button" aria-label="关闭" @click="showGuideSubmit = false">×</button><span class="eyebrow">成员参考建议</span><h2>提交新的复习指南参考</h2><p>支持标题、列表和普通段落。提交后先由管理员审核，不会直接替换正式指南。</p><label>Markdown 内容<textarea v-model="submissionDraft" maxlength="12000" placeholder="# 建议标题&#10;&#10;- 建议复习内容&#10;- 需要补充的变化" required></textarea></label><div class="markdown-help">支持 # 标题、## 小标题、- 列表、1. 步骤</div><button class="primary-button wide" type="submit" :disabled="guideBusy">{{ guideBusy ? '正在提交…' : '提交审核' }}</button></form>
  </div>
  <div v-if="showGuideEditor" class="modal-backdrop" @click.self="showGuideEditor = false">
    <form class="upload-modal guide-editor-modal" @submit.prevent="saveGuide"><button class="modal-close" type="button" aria-label="关闭" @click="showGuideEditor = false">×</button><span class="eyebrow">仅管理员可编辑</span><h2>编辑正式复习指南</h2><p>审核通过的成员建议只作为参考，请整理后再写入正式指南。</p><div class="guide-editor-grid"><div><label>正式指南 Markdown<textarea v-model="guideDraft" maxlength="12000" placeholder="# 复习路线"></textarea></label><label>本次调整说明<input v-model="guideChangeNote" maxlength="500" placeholder="例如：根据本学期教学进度补充第三章，删除旧题型" required /></label></div><aside><strong>待采纳参考 {{ approvedReferences.length }} 条</strong><p v-if="!approvedReferences.length">当前没有审核通过、尚未处理的建议。</p><label v-for="item in approvedReferences" :key="item.id" class="reference-item"><input v-model="incorporatedIds" type="checkbox" :value="item.id" /><span><b>{{ item.author }} · #{{ item.id }}</b><small>{{ item.contentMarkdown }}</small></span></label></aside></div><button class="primary-button wide" type="submit" :disabled="guideBusy">{{ guideBusy ? '正在保存…' : '保存正式指南' }}</button></form>
  </div>
  <transition name="toast"><div v-if="toast" class="toast">✓ {{ toast }}</div></transition>
  <PdfViewer v-if="pdfPreview" :blob="pdfPreview.blob" :title="pdfPreview.resource.title" @close="pdfPreview = null" @download="openResource(pdfPreview.resource, true)" />
  <div v-if="imagePreview" class="image-preview-backdrop" role="dialog" aria-modal="true" :aria-label="`${imagePreview.resource.title} 在线预览`" @click.self="closeImagePreview">
    <section class="image-preview">
      <header><strong>{{ imagePreview.resource.title }}</strong><div><button type="button" @click="openResource(imagePreview.resource, true)">下载</button><button type="button" aria-label="关闭" @click="closeImagePreview">×</button></div></header>
      <div><img :src="imagePreview.url" :alt="imagePreview.resource.title" /></div>
    </section>
  </div>
</template>
