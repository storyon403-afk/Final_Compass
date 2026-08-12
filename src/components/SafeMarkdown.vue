<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import katex from 'katex'
import 'katex/dist/katex.min.css'

const props = defineProps({ content: { type: String, default: '' } })

marked.setOptions({ gfm: true, breaks: true })

function renderFormula(source, displayMode) {
  return katex.renderToString(source.trim(), {
    displayMode,
    throwOnError: false,
    trust: false,
    strict: 'warn',
    // MathML + HTML renders correctly on screen, but browsers copy both hidden
    // MathML text and the visual layer, producing duplicated/split formulas.
    output: 'html'
  })
}

function renderMathOutsideCode(source) {
  return source.split(/(```[\s\S]*?```|`[^`\n]+`)/g).map((part, index) => {
    if (index % 2) return part
    return part
      .replace(/\$\$([\s\S]+?)\$\$/g, (_, formula) => renderFormula(formula, true))
      .replace(/\\\[([\s\S]+?)\\\]/g, (_, formula) => renderFormula(formula, true))
      .replace(/\\\((.+?)\\\)/g, (_, formula) => renderFormula(formula, false))
      .replace(/(^|[^\\$])\$([^$\n]+?)\$/g, (_, prefix, formula) => `${prefix}${renderFormula(formula, false)}`)
  }).join('')
}

const rendered = computed(() => DOMPurify.sanitize(marked.parse(renderMathOutsideCode(props.content)), {
  USE_PROFILES: { html: true, svg: true, mathMl: true }
}))
</script>

<template>
  <div class="safe-markdown" v-html="rendered"></div>
</template>
