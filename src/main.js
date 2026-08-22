import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import TeachersView from './views/TeachersView.vue'
import TeacherCircleView from './views/TeacherCircleView.vue'
import CetView from './views/CetView.vue'
import AiCenterView from './views/AiCenterView.vue'
import AiRuntimeChatView from './views/AiRuntimeChatView.vue'
import VcpRuntimeView from './views/VcpRuntimeView.vue'
import LiveDocRuntimeView from './views/LiveDocRuntimeView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/cet', component: CetView },
    { path: '/ai-analysis', redirect: '/ai-center' },
    { path: '/ai-center', component: AiCenterView },
    { path: '/ai-center/chat', component: AiRuntimeChatView, meta: { runtime: 'CHAT' } },
    { path: '/ai-center/workflow', redirect: '/ai-center/chat' },
    { path: '/ai-center/agent', component: AiRuntimeChatView, meta: { runtime: 'AGENT' } },
    { path: '/ai-center/web-agent', component: AiRuntimeChatView, meta: { runtime: 'MULTI_WEB_AGENT' } },
    { path: '/ai-center/vcp', component: VcpRuntimeView },
    { path: '/ai-center/livedoc', component: LiveDocRuntimeView, meta: { runtime: 'LIVE_DOC' } },
    { path: '/courses/:courseId', component: TeachersView },
    { path: '/courses/:courseId/teachers/:teacherId', component: TeacherCircleView }
  ]
})

createApp(App).use(router).mount('#app')
