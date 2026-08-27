<script setup>
import { ref } from 'vue'
defineOptions({ name: 'VineAnswerNode' })
defineProps({ node: { type: Object, required: true }, depth: { type: Number, default: 0 } })
const emit = defineEmits(['reply'])
const collapsed = ref(false)
</script>

<template>
  <article class="vine-answer-node" :class="{ accepted: node.accepted }" :style="{ '--reply-depth': Math.min(depth, 4) }">
    <div><b>{{ node.author }}</b><span v-if="node.accepted">✓ 楼主采纳</span></div>
    <p>{{ node.content }}</p>
    <footer><button type="button">↑ 有帮助 {{ node.helpful }}</button><button type="button" @click="emit('reply', node)">回复</button><button v-if="node.children.length" type="button" @click="collapsed = !collapsed">{{ collapsed ? `展开 ${node.children.length} 条回复` : '收起回复' }}</button></footer>
    <div v-if="node.children.length && !collapsed" class="vine-reply-children">
      <VineAnswerNode v-for="child in node.children" :key="child.id" :node="child" :depth="depth + 1" @reply="emit('reply', $event)" />
    </div>
  </article>
</template>
