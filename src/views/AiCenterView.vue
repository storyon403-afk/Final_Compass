<script setup>
import AiCenterSettings from '../components/AiCenterSettings.vue'
</script>

<template>
  <main class="ai-mode-center ai-center-return">
    <header class="ai-mode-bar">
      <b>AI Center</b>
      <AiCenterSettings />
    </header>

    <section class="ai-mode-carousel" aria-label="选择 Runtime">
      <router-link to="/ai-center/chat" class="ai-mode-slide">
        <div class="ai-mode-sketch"><i></i><i></i><i></i><b>C</b></div>
        <small>01</small><h2>Chat</h2><span>进入 →</span>
      </router-link>
      <router-link to="/ai-center/agent" class="ai-mode-slide">
        <div class="ai-mode-sketch agent"><i></i><i></i><i></i><b>A</b></div>
        <small>02</small><h2>Agent</h2><span>进入 →</span>
      </router-link>
      <router-link to="/ai-center/web-agent" class="ai-mode-slide">
        <div class="ai-mode-sketch web"><i></i><i></i><i></i><b>W³</b></div>
        <small>03</small><h2>MultiWeb AI</h2><span>进入 →</span>
      </router-link>
      <router-link to="/ai-center/vcp" class="ai-mode-slide">
        <div class="ai-mode-sketch vcp"><i></i><i></i><i></i><b>V</b></div>
        <small>04</small><h2>VCP</h2><span>了解 →</span>
      </router-link>
      <router-link to="/ai-center/livedoc" class="ai-mode-slide">
        <div class="ai-mode-sketch livedoc"><i></i><i></i><i></i><b>L</b></div>
        <small>05</small><h2>liveDoc</h2><span>创作 →</span>
      </router-link>
    </section>

    <div class="ai-mode-scroll-hint"><i></i><span>横向滑动选择</span></div>
  </main>
</template>
