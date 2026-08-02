<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const phase = ref('idle')
const video = ref(null)
const videoMuted = ref(false)
const playError = ref('')
const now = ref(new Date())
let clockTimer = null

const timeLabel = computed(() => now.value.toLocaleTimeString('zh-CN', {
  hour: '2-digit', minute: '2-digit', hour12: false
}))

function openConfirm() {
  phase.value = 'confirm'
  playError.value = ''
}

function closeConfirm() {
  if (phase.value === 'confirm') phase.value = 'idle'
}

async function beginSuspend() {
  phase.value = 'video'
  document.documentElement.classList.add('site-suspended')
  window.dispatchEvent(new CustomEvent('finals-compass:suspend'))
  await nextTick()
  try {
    video.value.currentTime = 0
    video.value.muted = videoMuted.value
    await video.value.play()
  } catch {
    playError.value = '浏览器没有允许视频自动播放，已直接进入暂挂画面。'
    enterDarkMode()
  }
}

function enterDarkMode() {
  video.value?.pause()
  phase.value = 'dark'
}

function resumeSite() {
  if (phase.value !== 'dark') return
  phase.value = 'idle'
  document.documentElement.classList.remove('site-suspended')
  window.dispatchEvent(new CustomEvent('finals-compass:resume'))
}

function toggleMute() {
  videoMuted.value = !videoMuted.value
  if (video.value) video.value.muted = videoMuted.value
}

function handleKeydown(event) {
  if (event.key !== 'Escape') return
  if (phase.value === 'confirm') closeConfirm()
  else if (phase.value === 'video') enterDarkMode()
  else if (phase.value === 'dark') resumeSite()
}

function handleVisibility() {
  if (phase.value !== 'video' || !video.value) return
  if (document.hidden) video.value.pause()
  else video.value.play().catch(enterDarkMode)
}

onMounted(() => {
  clockTimer = window.setInterval(() => { now.value = new Date() }, 15_000)
  window.addEventListener('keydown', handleKeydown)
  document.addEventListener('visibilitychange', handleVisibility)
})

onBeforeUnmount(() => {
  window.clearInterval(clockTimer)
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('visibilitychange', handleVisibility)
  document.documentElement.classList.remove('site-suspended')
})
</script>

<template>
  <button v-if="phase === 'idle'" class="suspend-rail" type="button" aria-label="暂时挂起网页" @click="openConfirm">
    <span>◐</span><b>暂挂</b>
  </button>

  <Transition name="suspend-panel">
    <div v-if="phase === 'confirm'" class="suspend-confirm-layer" @click.self="closeConfirm">
      <section class="suspend-confirm" role="dialog" aria-modal="true" aria-labelledby="suspend-title">
        <button class="suspend-close" type="button" aria-label="关闭" @click="closeConfirm">×</button>
        <span class="suspend-kicker">PAUSE · NOT LEAVE</span>
        <h2 id="suspend-title">要把页面暂时挂起吗？</h2>
        <p>适合去休息一下，或先在纸上慢慢算一会儿。页面、登录和正在填写的内容都会留在原处。</p>
        <div class="suspend-note"><i></i><span>确认后播放一小段画面，随后进入低亮暂挂状态。</span></div>
        <div class="suspend-actions">
          <button type="button" @click="closeConfirm">继续使用</button>
          <button class="suspend-primary" type="button" @click="beginSuspend">确认暂挂</button>
        </div>
      </section>
    </div>
  </Transition>

  <Transition name="suspend-fade">
    <section v-if="phase === 'video'" class="suspend-video-layer" aria-label="暂挂过渡视频">
      <video ref="video" src="/media/suspend-transition.mp4" playsinline preload="auto" @ended="enterDarkMode" />
      <div class="suspend-video-shade"></div>
      <span class="suspend-video-caption">正在收起页面里的声音与光…</span>
      <div class="suspend-video-actions">
        <button type="button" @click="toggleMute">{{ videoMuted ? '打开声音' : '静音' }}</button>
        <button type="button" @click="enterDarkMode">跳过</button>
      </div>
    </section>
  </Transition>

  <Transition name="suspend-dark">
    <section v-if="phase === 'dark'" class="suspend-dark-layer" role="button" tabindex="0" aria-label="网页已暂挂，点击恢复" @click="resumeSite" @keydown.enter="resumeSite" @keydown.space.prevent="resumeSite">
      <div class="suspend-ambient ambient-one"></div>
      <div class="suspend-ambient ambient-two"></div>
      <div class="suspend-dark-note">
        <span>STAY A WHILE</span>
        <time>{{ timeLabel }}</time>
        <p>网页在这里等你。</p>
        <small v-if="playError">{{ playError }}</small>
      </div>
      <div class="suspend-resume-hint"><i></i>点击任意位置恢复</div>
    </section>
  </Transition>
</template>

<style scoped>
.suspend-rail { position: fixed; z-index: 88; right: 0; top: 46%; width: 34px; min-height: 104px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 10px 7px; border: 1px solid var(--border); border-right: 0; border-radius: 14px 0 0 14px; background: color-mix(in srgb, var(--surface) 92%, transparent); color: var(--text-3); box-shadow: -8px 8px 28px rgba(35,42,37,.08); backdrop-filter: blur(14px); cursor: pointer; transition: width .2s ease, color .2s ease, background .2s ease; }
.suspend-rail:hover { width: 42px; color: var(--text); background: var(--surface); }
.suspend-rail span { font-size: 15px; }
.suspend-rail b { font-size: 11px; font-weight: 500; letter-spacing: .18em; writing-mode: vertical-rl; }
.suspend-confirm-layer { position: fixed; z-index: 200; inset: 0; display: flex; align-items: center; justify-content: flex-end; padding: 20px; background: rgba(13,17,14,.2); backdrop-filter: blur(5px); }
.suspend-confirm { position: relative; width: min(390px, calc(100vw - 30px)); padding: 34px 30px 28px; border: 1px solid color-mix(in srgb, var(--border) 85%, #718375); border-radius: 20px 8px 20px 13px; background: var(--surface); color: var(--text); box-shadow: 0 24px 80px rgba(19,26,21,.2); }
.suspend-close { position: absolute; top: 12px; right: 14px; border: 0; background: transparent; color: var(--text-3); font-size: 23px; cursor: pointer; }
.suspend-kicker { font: 600 9px/1 "Comic Sans MS", ui-monospace, monospace; letter-spacing: .18em; color: #718375; }
.suspend-confirm h2 { margin: 12px 0 10px; font-family: "Kaiti SC", STKaiti, KaiTi, cursive; font-size: 28px; font-weight: 500; }
.suspend-confirm > p { margin: 0; color: var(--text-3); font-size: 13px; line-height: 1.8; }
.suspend-note { display: flex; gap: 10px; align-items: flex-start; margin: 20px 0; padding: 12px 13px; border: 1px dashed var(--border); border-radius: 7px 13px 8px 11px; color: var(--text-3); font-size: 11px; line-height: 1.6; }
.suspend-note i { flex: 0 0 7px; width: 7px; height: 7px; margin-top: 5px; border-radius: 50%; background: #7f9585; box-shadow: 0 0 0 5px rgba(127,149,133,.12); }
.suspend-actions { display: flex; justify-content: flex-end; gap: 8px; }
.suspend-actions button, .suspend-video-actions button { padding: 9px 14px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); color: var(--text); cursor: pointer; }
.suspend-actions .suspend-primary { border-color: #4e6254; background: #4e6254; color: #f5f7f5; }
.suspend-video-layer, .suspend-dark-layer { position: fixed; z-index: 500; inset: 0; overflow: hidden; background: #030504; }
.suspend-video-layer video { width: 100%; height: 100%; object-fit: cover; }
.suspend-video-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,.05), rgba(0,0,0,.18)); pointer-events: none; }
.suspend-video-caption { position: absolute; left: 50%; bottom: 34px; transform: translateX(-50%); color: rgba(255,255,255,.68); font-family: "Kaiti SC", STKaiti, KaiTi, cursive; font-size: 14px; letter-spacing: .1em; white-space: nowrap; }
.suspend-video-actions { position: absolute; top: 20px; right: 20px; display: flex; gap: 8px; }
.suspend-video-actions button { border-color: rgba(255,255,255,.25); background: rgba(7,10,8,.28); color: rgba(255,255,255,.78); backdrop-filter: blur(10px); }
.suspend-dark-layer { display: grid; place-items: center; color: rgba(222,230,224,.42); cursor: pointer; outline: 0; }
.suspend-ambient { position: absolute; border-radius: 50%; filter: blur(80px); opacity: .09; animation: suspend-breathe 8s ease-in-out infinite alternate; }
.ambient-one { width: 42vw; height: 42vw; left: -22vw; top: -24vw; background: #64806c; }
.ambient-two { width: 35vw; height: 35vw; right: -19vw; bottom: -23vw; background: #6b7880; animation-delay: -4s; }
.suspend-dark-note { position: relative; z-index: 1; text-align: center; }
.suspend-dark-note > span { font: 600 8px/1 "Comic Sans MS", ui-monospace, monospace; letter-spacing: .28em; }
.suspend-dark-note time { display: block; margin: 11px 0 7px; font: 200 clamp(58px, 12vw, 130px)/1 Inter, sans-serif; letter-spacing: .08em; color: rgba(228,234,230,.24); }
.suspend-dark-note p { margin: 0; font-family: "Kaiti SC", STKaiti, KaiTi, cursive; font-size: 14px; letter-spacing: .12em; }
.suspend-dark-note small { display: block; max-width: 360px; margin-top: 12px; color: rgba(222,230,224,.28); }
.suspend-resume-hint { position: absolute; bottom: 28px; display: flex; align-items: center; gap: 9px; font-size: 10px; letter-spacing: .12em; color: rgba(222,230,224,.24); }
.suspend-resume-hint i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; animation: suspend-pulse 2s ease-in-out infinite; }
.suspend-panel-enter-active, .suspend-panel-leave-active { transition: opacity .25s ease; }
.suspend-panel-enter-active .suspend-confirm, .suspend-panel-leave-active .suspend-confirm { transition: transform .32s cubic-bezier(.22,.8,.2,1); }
.suspend-panel-enter-from, .suspend-panel-leave-to { opacity: 0; }
.suspend-panel-enter-from .suspend-confirm, .suspend-panel-leave-to .suspend-confirm { transform: translateX(40px); }
.suspend-fade-enter-active, .suspend-fade-leave-active { transition: opacity .7s ease; }
.suspend-fade-enter-from, .suspend-fade-leave-to { opacity: 0; }
.suspend-dark-enter-active { transition: opacity 1.2s ease; }
.suspend-dark-leave-active { transition: opacity .55s ease; }
.suspend-dark-enter-from, .suspend-dark-leave-to { opacity: 0; }
@keyframes suspend-breathe { to { opacity: .15; transform: scale(1.08); } }
@keyframes suspend-pulse { 50% { opacity: .25; transform: scale(.65); } }
@media (max-width: 600px) {
  .suspend-rail { top: auto; bottom: 20%; min-height: 88px; }
  .suspend-confirm-layer { align-items: flex-end; padding: 10px; }
  .suspend-confirm { width: 100%; border-radius: 18px 18px 8px 8px; }
  .suspend-video-caption { bottom: 24px; font-size: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  .suspend-ambient, .suspend-resume-hint i { animation: none; }
  .suspend-panel-enter-active, .suspend-panel-leave-active, .suspend-fade-enter-active, .suspend-fade-leave-active, .suspend-dark-enter-active, .suspend-dark-leave-active { transition-duration: .01ms; }
}
</style>
