<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const status = ref('')
const frameReady = ref(false)
const transitionVisible = ref(true)
const transitionLeaving = ref(false)
const backPosition = ref(null)
const notePosition = ref(null)
const phoneBlocked = ref(false)
const router = useRouter()
let backDrag
let noteDrag
let suppressBackClick = false
let timer
let revealTimer
let removeTransitionTimer

function isPhoneDevice() {
  const userAgent = navigator.userAgent || ''
  if (/iPhone|iPod|Windows Phone|Android.*Mobile|Mobile.*Android/i.test(userAgent)) return true
  if (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) return false
  const coarsePointer = window.matchMedia('(pointer: coarse)').matches
  const shortEdge = Math.min(window.screen.width, window.screen.height)
  return coarsePointer && shortEdge < 600
}

async function copyCurrentLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    status.value = '链接已复制，请发送到电脑或平板打开'
  } catch {
    status.value = '复制失败，请从浏览器地址栏复制链接'
  }
  clearTimeout(timer)
  timer = window.setTimeout(() => { status.value = '' }, 3200)
}

function handleFrameLoad() {
  revealTimer = window.setTimeout(() => {
    frameReady.value = true
    transitionLeaving.value = true
    removeTransitionTimer = window.setTimeout(() => { transitionVisible.value = false }, 720)
  }, 280)
}
function onRuntimeEvent(event) {
  if (event.origin !== window.location.origin || event.data?.channel !== 'final-compass:livedoc') return
  if (event.data.type === 'close-requested') {
    router.replace('/ai-center')
    return
  }
  if (event.data.type === 'project-saved') status.value = `已保存 · ${event.data.payload?.name || 'liveDoc'}`
  if (event.data.type === 'document-exported') status.value = `已导出 · ${event.data.payload?.name || ''}`
  clearTimeout(timer)
  timer = window.setTimeout(() => { status.value = '' }, 2600)
}
function startBackDrag(event) {
  if (event.button !== 0) return
  const rect = event.currentTarget.getBoundingClientRect()
  backDrag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top, moved: false }
  event.currentTarget.setPointerCapture?.(event.pointerId)
  event.preventDefault()
}
function moveBack(event) {
  if (!backDrag || backDrag.id !== event.pointerId) return
  const dx = event.clientX - backDrag.x
  const dy = event.clientY - backDrag.y
  if (Math.hypot(dx, dy) > 4) backDrag.moved = true
  const rect = event.currentTarget.getBoundingClientRect()
  backPosition.value = {
    left: Math.max(8, Math.min(backDrag.left + dx, window.innerWidth - rect.width - 8)),
    top: Math.max(8, Math.min(backDrag.top + dy, window.innerHeight - rect.height - 8))
  }
}
function finishBackDrag(event) {
  if (!backDrag || backDrag.id !== event.pointerId) return
  suppressBackClick = backDrag.moved
  backDrag = null
  if (backPosition.value) localStorage.setItem('final-compass:livedoc-back-position', JSON.stringify(backPosition.value))
}
function startNoteDrag(event) {
  if (event.button !== 0) return
  const rect = event.currentTarget.getBoundingClientRect()
  noteDrag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top }
  event.currentTarget.setPointerCapture?.(event.pointerId)
  event.preventDefault()
}
function moveNote(event) {
  if (!noteDrag || noteDrag.id !== event.pointerId) return
  const rect = event.currentTarget.getBoundingClientRect()
  notePosition.value = {
    left: Math.max(8, Math.min(noteDrag.left + event.clientX - noteDrag.x, window.innerWidth - rect.width - 8)),
    top: Math.max(8, Math.min(noteDrag.top + event.clientY - noteDrag.y, window.innerHeight - rect.height - 8))
  }
}
function finishNoteDrag(event) {
  if (!noteDrag || noteDrag.id !== event.pointerId) return
  noteDrag = null
  if (notePosition.value) localStorage.setItem('final-compass:livedoc-note-position', JSON.stringify(notePosition.value))
}
function leaveLiveDoc() {
  if (suppressBackClick) { suppressBackClick = false; return }
  router.replace('/ai-center')
}
onMounted(() => {
  document.documentElement.classList.add('livedoc-host-active')
  phoneBlocked.value = isPhoneDevice()
  if (phoneBlocked.value) {
    transitionVisible.value = false
    return
  }
  window.addEventListener('message', onRuntimeEvent)
  try {
    const saved = JSON.parse(localStorage.getItem('final-compass:livedoc-back-position') || 'null')
    if (Number.isFinite(saved?.left) && Number.isFinite(saved?.top)) backPosition.value = saved
    const savedNote = JSON.parse(localStorage.getItem('final-compass:livedoc-note-position') || 'null')
    if (Number.isFinite(savedNote?.left) && Number.isFinite(savedNote?.top)) notePosition.value = savedNote
  } catch {}
})
onBeforeUnmount(() => {
  document.documentElement.classList.remove('livedoc-host-active')
  window.removeEventListener('message', onRuntimeEvent)
  clearTimeout(timer)
  clearTimeout(revealTimer)
  clearTimeout(removeTransitionTimer)
})
</script>

<template>
  <main class="livedoc-native-host">
    <section v-if="phoneBlocked" class="livedoc-phone-gate" aria-labelledby="livedoc-phone-title">
      <div class="livedoc-phone-sheet" aria-hidden="true"><i></i><i></i><i></i></div>
      <small>DESKTOP &amp; TABLET EXPERIENCE</small>
      <h1 id="livedoc-phone-title">liveDoc 暂不支持手机端</h1>
      <p>多栏编辑、精细排版和本地工程文件管理需要更大的操作空间。请使用电脑或平板打开，手机端不会加载编辑器和文档资源。</p>
      <div class="livedoc-phone-actions">
        <button type="button" class="primary" @click="copyCurrentLink">复制当前链接</button>
        <router-link to="/ai-center">返回 AI Center</router-link>
      </div>
    </section>
    <div
      v-if="transitionVisible"
      :class="['livedoc-entry-transition', { leaving: transitionLeaving }]"
      aria-live="polite"
      aria-label="正在进入 liveDoc"
    >
      <div class="livedoc-entry-aura"></div>
      <div class="livedoc-entry-sheet" aria-hidden="true">
        <i></i><i></i><i></i><i></i>
      </div>
      <div class="livedoc-entry-copy">
        <small>FINAL COMPASS · CREATIVE RUNTIME</small>
        <strong>liveDoc</strong>
        <span>正在展开你的创作空间</span>
      </div>
      <div class="livedoc-entry-progress"><i></i></div>
    </div>
    <button
      v-if="!phoneBlocked"
      class="livedoc-host-back"
      type="button"
      title="拖动调整位置；点击返回 AI Center"
      :style="backPosition ? { left: `${backPosition.left}px`, top: `${backPosition.top}px`, right: 'auto', bottom: 'auto' } : null"
      @pointerdown="startBackDrag"
      @pointermove="moveBack"
      @pointerup="finishBackDrag"
      @pointercancel="finishBackDrag"
      @click="leaveLiveDoc"
    >
      <span aria-hidden="true">←</span>
      <span>返回 AI Center</span>
    </button>
    <output v-if="status" class="livedoc-host-status">{{ status }}</output>
    <aside
      v-if="!phoneBlocked && frameReady"
      class="livedoc-local-note"
      title="拖动调整位置"
      :style="notePosition ? { left: `${notePosition.left}px`, top: `${notePosition.top}px`, right: 'auto', bottom: 'auto' } : null"
      @pointerdown="startNoteDrag"
      @pointermove="moveNote"
      @pointerup="finishNoteDrag"
      @pointercancel="finishNoteDrag"
    >
      草稿仅缓存在当前设备，请随时将文档（vdocx、vpptx）保存到本地
    </aside>
    <iframe
      v-if="!phoneBlocked"
      :class="{ ready: frameReady }"
      src="/livedoc/ScriptoriumModules/scriptorium.html"
      title="liveDoc · VCP Scriptorium Engine"
      allow="fullscreen"
      @load="handleFrameLoad"
    ></iframe>
  </main>
</template>

<style scoped>
.livedoc-phone-gate {
  min-height: 100dvh;
  display: grid;
  place-content: center;
  justify-items: center;
  padding: 36px 24px;
  text-align: center;
  color: #e8f1ed;
  background: radial-gradient(circle at 50% 34%, rgb(58 109 92 / 28%), transparent 36%), #101816;
}
.livedoc-phone-sheet { width: 74px; height: 94px; padding: 22px 14px; border: 1px solid #82aa9d; border-radius: 4px; background: #dce8e2; box-shadow: 0 22px 55px rgb(0 0 0 / 30%); transform: rotate(-3deg); }
.livedoc-phone-sheet i { display: block; height: 2px; margin: 0 0 12px; border-radius: 2px; background: #729287; }
.livedoc-phone-sheet i:nth-child(2) { width: 78%; }
.livedoc-phone-gate small { margin-top: 30px; color: #83a99d; font: 700 10px/1 system-ui; letter-spacing: .18em; }
.livedoc-phone-gate h1 { margin: 14px 0 12px; font: 500 30px/1.25 Georgia, "Songti SC", serif; }
.livedoc-phone-gate p { max-width: 430px; margin: 0; color: #a9bbb5; font: 14px/1.8 system-ui, sans-serif; }
.livedoc-phone-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 28px; }
.livedoc-phone-actions button, .livedoc-phone-actions a { padding: 11px 17px; border: 1px solid #526b63; border-radius: 9px; color: #dce8e4; background: transparent; font: 600 13px/1 system-ui; text-decoration: none; }
.livedoc-phone-actions button.primary { border-color: #82aa9d; color: #12201b; background: #a9c9be; }
.livedoc-local-note { position: fixed; z-index: 19; left: 18px; bottom: 16px; max-width: min(420px, calc(100vw - 36px)); padding: 9px 13px; border: 1px solid rgb(224 108 95 / 32%); border-radius: 999px; color: #e06c5f; background: transparent; box-shadow: none; font: 11px/1.4 system-ui, sans-serif; cursor: grab; touch-action: none; user-select: none; }
.livedoc-local-note:active { cursor: grabbing; }
.livedoc-host-status {
  position: fixed;
  z-index: 20;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  padding: 9px 16px;
  border: 1px solid #3b4449;
  border-radius: 999px;
  color: #f8fafc;
  background: rgb(15 23 42 / 88%);
  box-shadow: 0 12px 35px rgb(0 0 0 / 26%);
  backdrop-filter: blur(14px);
  font-size: 13px;
}
.livedoc-native-host iframe {
  opacity: 0;
  transform: scale(1.018);
  filter: blur(5px);
  transition: opacity .68s ease, transform .9s cubic-bezier(.2,.75,.2,1), filter .68s ease;
}
.livedoc-native-host iframe.ready {
  opacity: 1;
  transform: scale(1);
  filter: blur(0);
}
.livedoc-entry-transition {
  position: fixed;
  z-index: 40;
  inset: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  color: #eef6f3;
  background:
    radial-gradient(circle at 50% 42%, rgb(76 127 115 / 24%), transparent 34%),
    linear-gradient(145deg, #111917, #17231f 58%, #101715);
  transition: opacity .65s ease, visibility .65s ease;
}
.livedoc-entry-transition.leaving {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}
.livedoc-entry-aura {
  position: absolute;
  width: min(720px, 82vw);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 1px solid rgb(176 217 205 / 10%);
  box-shadow: inset 0 0 110px rgb(120 184 165 / 8%);
  animation: livedoc-aura 2.8s ease-in-out infinite;
}
.livedoc-entry-sheet {
  position: absolute;
  width: 170px;
  height: 220px;
  padding: 42px 30px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 7px 22px 7px 7px;
  background: linear-gradient(145deg, rgb(249 252 250 / 95%), rgb(218 233 227 / 86%));
  box-shadow: 0 38px 90px rgb(0 0 0 / 28%);
  transform: translateY(-34px) rotate(-5deg) scale(.72);
  opacity: 0;
  animation: livedoc-sheet .9s .08s cubic-bezier(.2,.8,.2,1) forwards;
}
.livedoc-entry-sheet::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 42px;
  height: 42px;
  border-radius: 0 20px 0 12px;
  background: linear-gradient(225deg, #9fc1b7, #eaf2ef 62%);
}
.livedoc-entry-sheet i {
  display: block;
  height: 4px;
  margin-bottom: 17px;
  border-radius: 99px;
  background: #53786d;
  transform-origin: left;
  animation: livedoc-line .52s ease both;
}
.livedoc-entry-sheet i:nth-child(1) { width: 58%; animation-delay: .48s; }
.livedoc-entry-sheet i:nth-child(2) { width: 100%; animation-delay: .58s; }
.livedoc-entry-sheet i:nth-child(3) { width: 84%; animation-delay: .68s; }
.livedoc-entry-sheet i:nth-child(4) { width: 68%; animation-delay: .78s; }
.livedoc-entry-copy {
  position: relative;
  z-index: 2;
  display: grid;
  justify-items: center;
  margin-top: 310px;
  animation: livedoc-copy .65s .48s ease both;
}
.livedoc-entry-copy small { color: #93b8ad; font: 700 10px/1 system-ui; letter-spacing: .2em; }
.livedoc-entry-copy strong { margin-top: 11px; font: 500 35px/1.1 Georgia, serif; letter-spacing: -.04em; }
.livedoc-entry-copy span { margin-top: 10px; color: #a9beb8; font: 12px/1.4 system-ui; }
.livedoc-entry-progress {
  position: absolute;
  bottom: 8vh;
  width: min(220px, 48vw);
  height: 2px;
  overflow: hidden;
  border-radius: 99px;
  background: rgb(255 255 255 / 10%);
}
.livedoc-entry-progress i {
  display: block;
  width: 45%;
  height: 100%;
  border-radius: inherit;
  background: #a4d4c6;
  animation: livedoc-progress 1.25s ease-in-out infinite;
}
@keyframes livedoc-sheet { to { opacity: 1; transform: translateY(-34px) rotate(0) scale(1); } }
@keyframes livedoc-line { from { opacity: 0; transform: scaleX(0); } }
@keyframes livedoc-copy { from { opacity: 0; transform: translateY(12px); } }
@keyframes livedoc-aura { 50% { transform: scale(1.06); opacity: .62; } }
@keyframes livedoc-progress { from { transform: translateX(-110%); } to { transform: translateX(330%); } }
.livedoc-host-back {
  position: fixed;
  z-index: 50;
  left: 18px;
  bottom: 18px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 999px;
  color: #f8fafc;
  background: rgb(32 37 42 / 92%);
  box-shadow: 0 12px 35px rgb(0 0 0 / 28%);
  backdrop-filter: blur(14px);
  cursor: pointer;
  touch-action: none;
  user-select: none;
  font: 600 13px/1 system-ui, sans-serif;
  transition: transform .18s ease, color .18s ease, border-color .18s ease, background .18s ease;
}
.livedoc-host-back:hover {
  transform: translateY(-2px);
  color: #f2a900;
  border-color: rgb(242 169 0 / 58%);
  background: rgb(42 48 53 / 96%);
}
.livedoc-host-back:focus-visible {
  outline: 2px solid #f2a900;
  outline-offset: 3px;
}
@media (max-width: 640px) {
  .livedoc-host-back span:last-child { display: none; }
  .livedoc-host-back { padding: 11px 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .livedoc-entry-aura, .livedoc-entry-sheet, .livedoc-entry-sheet i,
  .livedoc-entry-copy, .livedoc-entry-progress i { animation: none; opacity: 1; }
  .livedoc-entry-sheet { transform: translateY(-34px); }
  .livedoc-native-host iframe, .livedoc-entry-transition { transition-duration: .15s; }
}
</style>
