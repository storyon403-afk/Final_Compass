<script setup>
const emit = defineEmits(['close', 'open'])

const sections = [
  { key: 'moderation', title: '内容审核', description: '批准或拒绝复习资料、匿名讨论与指南参考，批准后立即公开。' },
  { key: 'beta-access', title: '登录验证', description: '核对内测申请邮箱，亲自确认并发放账号与临时密码。' },
  { key: 'mail', title: 'SMTP 与邮件', description: '配置邮件服务（Graph / SMTP）与邮件模板。' },
  { key: 'mcp', title: 'MCP 管理中心', description: '管理 MCP 服务器连接与工具配置。' },
  { key: 'ai-feedback', title: 'AI Skill 优化', description: '查看用户对 AI 能力的反馈并跟进优化。' },
  { key: 'announcement', title: '公告管理', description: '编辑登录公告内容，控制是否向用户展示。' },
  { key: 'modules', title: '模块开放与维护', description: '临时关闭课程导航、AI Center 或 CET，并编辑用户看到的维护说明。' },
  { key: 'suspend', title: '暂挂体验管理', description: '管理暂挂名单与恢复体验操作。' },
  { key: 'survey', title: '调查问卷管理', description: '维护问卷题目并查看回收结果。' }
]
</script>

<template>
  <div class="modal-backdrop global-control-center" @click.self="emit('close')">
    <section class="upload-modal control-center-modal" role="dialog" aria-modal="true" aria-labelledby="global-control-center-title">
      <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <span class="eyebrow">管理员 · 系统管理</span>
      <h2 id="global-control-center-title">控制中心</h2>
      <p>集中管理站点运营功能；选择一项后会在上层打开对应的管理窗口。</p>
      <div class="global-control-center-list">
        <button v-for="section in sections" :key="section.key" type="button" @click="emit('open', section.key)">
          <span><b>{{ section.title }}</b><small>{{ section.description }}</small></span><i>›</i>
        </button>
      </div>
    </section>
  </div>
</template>
