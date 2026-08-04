import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import TeachersView from './views/TeachersView.vue'
import TeacherCircleView from './views/TeacherCircleView.vue'
import CetView from './views/CetView.vue'
import AiAnalysisView from './views/AiAnalysisView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/cet', component: CetView },
    { path: '/ai-analysis', component: AiAnalysisView },
    { path: '/courses/:courseId', component: TeachersView },
    { path: '/courses/:courseId/teachers/:teacherId', component: TeacherCircleView }
  ]
})

createApp(App).use(router).mount('#app')
