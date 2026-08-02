<script setup>
import { nextTick, onUnmounted, ref, watch } from 'vue'
import * as pdfjs from 'pdfjs-dist/legacy/build/pdf.mjs'
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'

// The legacy build carries the compatibility shims needed by older mobile
// browsers, including Map.getOrInsertComputed and URL.parse.
pdfjs.GlobalWorkerOptions.workerSrc = `${workerUrl}?legacy=20260730`
const props = defineProps({ blob: { type: Blob, required: true }, title: { type: String, default: 'PDF 资料' } })
const emit = defineEmits(['close', 'download'])
const pages = ref(null)
const loading = ref(true)
const error = ref('')
const nativeMode = ref(false)
const nativeUrl = ref('')
let task
let documentHandle

async function renderPdf(blob) {
  if (nativeUrl.value) URL.revokeObjectURL(nativeUrl.value)
  nativeUrl.value = URL.createObjectURL(blob)
  nativeMode.value = false
  loading.value = true
  error.value = ''
  await nextTick()
  pages.value?.replaceChildren()
  try {
    task = pdfjs.getDocument({
      data: await blob.arrayBuffer(),
      cMapUrl: '/pdfjs/cmaps/',
      cMapPacked: true,
      standardFontDataUrl: '/pdfjs/standard_fonts/',
      wasmUrl: '/pdfjs/wasm/',
      useSystemFonts: true,
    })
    documentHandle = await task.promise
    for (let number = 1; number <= documentHandle.numPages; number++) {
      const page = await documentHandle.getPage(number)
      const base = page.getViewport({ scale: 1.35 })
      const ratio = Math.min(window.devicePixelRatio || 1, 2)
      const canvas = document.createElement('canvas')
      canvas.width = Math.floor(base.width * ratio)
      canvas.height = Math.floor(base.height * ratio)
      canvas.style.width = `${base.width}px`
      canvas.style.maxWidth = '100%'
      canvas.setAttribute('aria-label', `第 ${number} 页`)
      pages.value.appendChild(canvas)
      await page.render({ canvasContext: canvas.getContext('2d'), viewport: page.getViewport({ scale: 1.35 * ratio }) }).promise
      page.cleanup()
    }
  } catch (reason) {
    error.value = `内置阅读器解析失败：${reason.message}`
    nativeMode.value = true
  }
  finally { loading.value = false }
}

watch(() => props.blob, renderPdf, { immediate: true })
onUnmounted(() => {
  task?.destroy()
  documentHandle?.destroy()
  if (nativeUrl.value) URL.revokeObjectURL(nativeUrl.value)
})
</script>

<template>
  <div class="pdf-backdrop" role="dialog" aria-modal="true" aria-label="PDF 阅读器" @click.self="emit('close')">
    <section class="pdf-viewer">
      <header><div><span>站内 PDF 阅读器</span><strong>{{ title }}</strong></div><div><button type="button" @click="nativeMode = !nativeMode">{{ nativeMode ? '内置预览' : '浏览器预览' }}</button><button type="button" @click="emit('download')">下载</button><button type="button" aria-label="关闭" @click="emit('close')">×</button></div></header>
      <div v-if="loading && !nativeMode" class="pdf-status">正在解析并渲染 PDF…</div>
      <div v-if="error && !nativeMode" class="pdf-status error">{{ error }}<button type="button" @click="nativeMode = true">使用浏览器预览</button></div>
      <iframe v-if="nativeMode" class="pdf-native-frame" :src="nativeUrl" :title="`${title} 在线预览`"></iframe>
      <div v-show="!nativeMode" ref="pages" class="pdf-pages"></div>
    </section>
  </div>
</template>
