<script setup>
import { onMounted, ref } from 'vue'
import { suspendApi } from '../suspendApi'

const emit = defineEmits(['close'])
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const enabled = ref(true)
const playMode = ref('FIXED')
const fixedVideoId = ref(null)
const videos = ref([])
const selectedFile = ref(null)
const duration = ref(0)
const message = ref('')
const error = ref('')

function apply(data) {
  enabled.value = Boolean(data.setting.enabled)
  playMode.value = data.setting.play_mode
  fixedVideoId.value = data.setting.fixed_video_id
  videos.value = data.videos
}

async function load() {
  loading.value = true
  error.value = ''
  try { apply(await suspendApi.admin()) } catch (reason) { error.value = reason.message }
  finally { loading.value = false }
}

async function inspectFile(event) {
  selectedFile.value = event.target.files?.[0] || null
  duration.value = 0
  error.value = ''
  if (!selectedFile.value) return
  const url = URL.createObjectURL(selectedFile.value)
  const media = document.createElement('video')
  media.preload = 'metadata'
  media.onloadedmetadata = () => {
    duration.value = Math.ceil(media.duration)
    URL.revokeObjectURL(url)
    if (!Number.isFinite(duration.value) || duration.value < 1 || duration.value > 30) {
      error.value = '视频时长需在 1–30 秒之间；日常使用推荐 8–15 秒。'
    }
  }
  media.onerror = () => { URL.revokeObjectURL(url); error.value = '无法读取视频，请使用 MP4 或 WebM。' }
  media.src = url
}

async function upload() {
  if (!selectedFile.value || duration.value < 1 || duration.value > 30) return
  uploading.value = true; error.value = ''; message.value = ''
  try {
    apply(await suspendApi.upload(selectedFile.value, duration.value))
    message.value = '视频已上传，可以在固定播放中选用，或切换为随机播放。'
    selectedFile.value = null; duration.value = 0
  } catch (reason) { error.value = reason.message }
  finally { uploading.value = false }
}

async function save() {
  saving.value = true; error.value = ''; message.value = ''
  try {
    apply(await suspendApi.update({ enabled: enabled.value, playMode: playMode.value, fixedVideoId: fixedVideoId.value || null }))
    message.value = '暂挂设置已保存。'
    window.dispatchEvent(new CustomEvent('finals-compass:suspend-settings-updated'))
  } catch (reason) { error.value = reason.message }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section class="upload-modal suspend-admin-modal" role="dialog" aria-modal="true" aria-labelledby="suspend-admin-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <span class="eyebrow">管理员功能</span><h2 id="suspend-admin-title">暂挂体验管理</h2>
      <p>控制全站侧边入口和过渡视频。推荐 8–15 秒，最长 30 秒；用户始终可以跳过。</p>
      <div v-if="loading" class="empty-state">正在加载…</div>
      <template v-else>
        <label class="suspend-admin-toggle"><input v-model="enabled" type="checkbox" /><span><b>{{ enabled ? '暂挂入口已开启' : '暂挂入口已关闭' }}</b><small>关闭只隐藏入口，不删除视频和设置</small></span></label>
        <fieldset>
          <legend>播放策略</legend>
          <label><input v-model="playMode" type="radio" value="FIXED" /> 固定播放</label>
          <label><input v-model="playMode" type="radio" value="RANDOM" /> 每次随机</label>
          <select v-if="playMode === 'FIXED'" v-model="fixedVideoId">
            <option :value="null">内置默认视频</option>
            <option v-for="item in videos" :key="item.id" :value="item.id">{{ item.display_name }}（{{ item.duration_seconds }} 秒）</option>
          </select>
          <small v-if="playMode === 'RANDOM' && !videos.length">尚无上传视频，将使用内置默认视频。</small>
        </fieldset>
        <button class="primary-button wide" type="button" :disabled="saving" @click="save">{{ saving ? '正在保存…' : '保存启用与播放设置' }}</button>
        <form class="suspend-video-upload" @submit.prevent="upload">
          <h3>导入新视频</h3>
          <input type="file" accept="video/mp4,video/webm" required @change="inspectFile" />
          <small v-if="selectedFile">{{ selectedFile.name }}<template v-if="duration"> · {{ duration }} 秒 · {{ (selectedFile.size / 1024 / 1024).toFixed(1) }} MB</template></small>
          <button type="submit" :disabled="uploading || !selectedFile || duration < 1 || duration > 30">{{ uploading ? '正在上传…' : '上传视频' }}</button>
        </form>
      </template>
      <p v-if="error" class="form-error">{{ error }}</p><p v-if="message" class="form-success">{{ message }}</p>
    </section>
  </div>
</template>

<style scoped>
.suspend-admin-modal { width: min(620px, 100%); }
.suspend-admin-toggle { display: flex; align-items: center; gap: 12px; margin: 16px 0; padding: 14px; border: 1px solid var(--border); border-radius: 10px; }
.suspend-admin-toggle span { display: grid; gap: 3px; }.suspend-admin-toggle small, fieldset small { color: var(--text-3); }
fieldset { display: grid; gap: 11px; margin: 16px 0; padding: 16px; border: 1px solid var(--border); border-radius: 10px; }
fieldset legend { padding: 0 7px; font-size: 11px; } fieldset select { min-height: 42px; padding: 8px; border: 1px solid var(--border-strong); border-radius: 8px; background: var(--surface); color: var(--text); }
.suspend-video-upload { display: grid; gap: 11px; margin-top: 22px; padding-top: 20px; border-top: 1px solid var(--border); }
.suspend-video-upload h3 { margin: 0; font-size: 15px; }.suspend-video-upload input { padding: 13px; border: 1px dashed var(--border-strong); border-radius: 9px; }.suspend-video-upload button { justify-self: start; padding: 9px 15px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface-2); color: var(--text); cursor: pointer; }
</style>
