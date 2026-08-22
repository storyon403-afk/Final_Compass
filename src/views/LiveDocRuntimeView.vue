<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const status = ref('')
const frameReady = ref(false)
const transitionVisible = ref(true)
const transitionLeaving = ref(false)
const router = useRouter()
let timer
let revealTimer
let removeTransitionTimer

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
onMounted(() => {
  document.documentElement.classList.add('livedoc-host-active')
  window.addEventListener('message', onRuntimeEvent)
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
    <button class="livedoc-host-back" type="button" title="返回 AI Center" @click="router.replace('/ai-center')">
      <span aria-hidden="true">←</span>
      <span>返回 AI Center</span>
    </button>
    <output v-if="status" class="livedoc-host-status">{{ status }}</output>
    <iframe
      :class="{ ready: frameReady }"
      src="/livedoc/ScriptoriumModules/scriptorium.html"
      title="liveDoc · VCP Scriptorium Engine"
      allow="fullscreen"
      @load="handleFrameLoad"
    ></iframe>
  </main>
</template>

<style scoped>
.livedoc-host-status {
  position: fixed;
  z-index: 20;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  padding: 9px 16px;
  border: 1px solid rgb(255 255 255 / 18%);
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
  background: rgb(15 23 42 / 86%);
  box-shadow: 0 12px 35px rgb(0 0 0 / 28%);
  backdrop-filter: blur(14px);
  cursor: pointer;
  font: 600 13px/1 system-ui, sans-serif;
  transition: transform .18s ease, background .18s ease;
}
.livedoc-host-back:hover {
  transform: translateY(-2px);
  background: rgb(30 41 59 / 94%);
}
.livedoc-host-back:focus-visible {
  outline: 2px solid #93c5fd;
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
