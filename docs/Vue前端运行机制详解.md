# Vue 前端运行机制详解（JavaFX 视角）

> 目标：不要求先掌握 Vue 语法，重点理解 `src/` 中每个文件负责什么、怎样联系、页面如何运行，以及前后端如何交互。

## 1. 总体运行模型

可以把本项目的 Vue 前端理解成 JavaFX 的：

```text
Application + Scene/FXML + Controller + ObservableProperty + HttpClient
```

```text
index.html
   ↓
main.js 创建 Vue 应用与路由
   ↓
App.vue 作为整个应用外壳
   ├─ 开场状态 → 开场画面
   ├─ 未登录   → AuthView.vue
   └─ 已登录   → 顶部导航 + router-view
                              ↓
                    根据地址显示业务页面
```

Vue 与 JavaFX 最大的思想区别是：Vue 通常不直接命令控件如何变化，而是修改状态，让界面自动根据状态重新渲染。

## 2. Vue 与 JavaFX 概念对照

| Vue | JavaFX 中的近似概念 |
|---|---|
| `.vue` 组件 | FXML 页面与 Controller 的组合 |
| `<template>` | FXML 布局 |
| `<script setup>` | Controller 字段和方法 |
| `ref()` | `SimpleStringProperty`、`SimpleBooleanProperty` |
| `computed()` | 属性 Binding 产生的派生值 |
| `watch()` | `ChangeListener` |
| `v-model` | 控件值与 Property 双向绑定 |
| `@click` | `setOnAction()` |
| `v-if` | 根据状态添加或移除节点 |
| `v-for` | 遍历集合创建控件 |
| `onMounted()` | 页面初始化完成后的加载方法 |
| `onBeforeUnmount()` | 页面销毁前清理资源 |
| Vue Router | Scene/Page 导航管理器 |
| `<router-view>` | 动态装入页面的中央容器 |
| `api.js` | 统一 HTTP Service |

## 3. `src/` 文件职责

```text
src/
├── main.js                         应用入口与路由表
├── App.vue                         根组件、登录外壳、顶部栏、全局弹窗
├── api.js                          后端 API 与共享登录状态
├── styles.css                      全局样式和响应式布局
├── views/
│   ├── AuthView.vue                邮箱验证与账号登录
│   ├── HomeView.vue                学院、专业和课程导航
│   ├── TeachersView.vue            某门课程的老师列表
│   ├── TeacherCircleView.vue       资料、讨论和复习指南
│   └── CetView.vue                 四六级套卷与练习系统
└── components/
    ├── AuthTransitionScene.vue     登录过渡动画
    ├── PdfViewer.vue               PDF 阅读器
    ├── UserSurveyModal.vue         普通用户问卷
    └── AdminSurveyModal.vue        管理员问卷管理
```

## 4. `main.js`：应用入口

`main.js` 完成三件事。

### 4.1 导入根组件、页面和样式

```js
import { createApp } from 'vue'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import './styles.css'
```

### 4.2 建立地址与页面的映射

```js
routes: [
  { path: '/', component: HomeView },
  { path: '/cet', component: CetView },
  { path: '/courses/:courseId', component: TeachersView },
  {
    path: '/courses/:courseId/teachers/:teacherId',
    component: TeacherCircleView
  }
]
```

| 地址 | 页面 |
|---|---|
| `/` | 学院、专业与课程导航 |
| `/cet` | 四六级题库 |
| `/courses/:courseId` | 某门课程的老师列表 |
| `/courses/:courseId/teachers/:teacherId` | 某个老师圈 |

`:courseId`、`:teacherId` 是动态参数，组件使用 `useRoute()` 读取，类似 JavaFX 导航时给下一个 Controller 传 ID。

### 4.3 创建并挂载应用

```js
createApp(App).use(router).mount('#app')
```

它用 `App.vue` 创建根组件，安装路由，然后把应用放进 `index.html` 的 `<div id="app">`。

## 5. `App.vue`：整个系统的主窗口

它负责：

- 开场页和登录状态。
- 顶部业务导航与账号菜单。
- 深色/浅色主题。
- 修改密码、公告、审核、登录验证和问卷弹窗。
- 当前业务页面的容器。

### 5.1 三个显示阶段

```html
<div v-if="introVisible">开场页面</div>
<AuthView v-if="!introVisible && !authenticated" />
<div v-else-if="authenticated">
  顶部导航
  <router-view />
</div>
```

```text
开场页 → 未登录时显示 AuthView → 登录后显示系统主体
```

### 5.2 响应式状态

```js
const showAccount = ref(false)
const moderationItems = ref([])
```

`ref()` 类似 JavaFX Property。JavaScript 中修改时使用 `.value`，模板中 Vue 会自动解包：

```js
showAccount.value = true
```

```html
<div v-if="showAccount">...</div>
```

### 5.3 派生值与监听

`computed()` 类似 Binding，根据其他状态计算结果；`watch()` 类似 `ChangeListener`，在主题、路由或登录令牌变化时执行操作。

### 5.4 `router-view`

```html
<main class="app-main"><router-view /></main>
```

顶部栏一直保留，只有中间业务页面按地址切换。可以理解成 JavaFX `BorderPane` 固定顶部，只替换 `center`。

## 6. `api.js`：前端与 Spring Boot 的桥梁

它相当于多个 Service 的集合：

- `authApi`：验证、登录、退出、修改密码。
- `catalogApi`：学院、课程、老师。
- `circleApi`：资料、讨论、复习指南。
- `systemApi`：公告、审核和管理员功能。
- `surveyApi`：调查问卷。
- `cetApi`：四六级套卷、题目、音频和附件。

统一的 `request()` 负责添加 Bearer Token、发送请求、解析 JSON、处理 HTTP 错误与追踪号，并在 401 时清除登录状态。

### 6.1 共享登录状态

```js
export const authSession = ref(initialSession)
export const authenticated = computed(() => Boolean(authSession.value.token))
export const isAdmin = computed(() => authSession.value.role === 'ADMIN')
```

多个组件导入同一组状态，类似多个 JavaFX Controller 共享 `SessionContext` 单例。

### 6.2 真实数据链路

```text
Vue 页面
  ↓ 调用 api.js
HTTP /api/...
  ↓
Spring Boot Controller
  ↓
Service / JdbcClient
  ↓
MySQL 或 uploads 文件
```

开发时前端为 `5173`、后端为 `8080`，Vite 将 `/api` 代理到后端。

## 7. `AuthView.vue`：登录状态机

三个阶段：

```text
request → 填邮箱和手机号
verify  → 输入验证码
login   → 输入账号和密码
```

模板通过 `v-if` 根据 `step` 显示对应表单。验证成功才进入登录阶段；输错时留在验证码页，清空输入并使用 `nextTick()` 重新聚焦，类似 `Platform.runLater()`。

验证码申请存在 `sessionStorage`，所以刷新当前标签页后仍能继续，关闭标签页后临时状态消失。

## 8. `HomeView.vue`：课程导航

页面状态形成四层导航：

```text
学院 → 专业 → 课程类型 → 课程列表
```

```js
const selectedCollege = ref('')
const selectedProgram = ref('')
const selectedType = ref('')
```

`stage` 是 `computed()`，根据这三个状态判断当前显示哪一层。

组件挂载时通过 `Promise.all()` 并行加载学院和课程。输入框使用 `v-model="query"`，专业内搜索结果通过 `computed()` 自动过滤，用户输入后列表自动刷新。

管理员添加课程时调用 `catalogApi.addCourse()`：

- 新代码：创建课程并建立专业关系。
- 相同代码和名称：复用课程，只增加专业关系。
- 相同代码、不同名称：后端拒绝冲突。

## 9. `TeachersView.vue`：课程老师列表

对应 `/courses/:courseId`。它读取 `route.params.courseId`，加载当前课程与老师，并提供搜索和管理员添加老师功能。

```js
watch(() => route.params.courseId, loadTeachers, { immediate: true })
```

含义是首次显示立即加载，课程 ID 变化时重新加载。点击老师后 `router-link` 改变地址，`router-view` 自动切换到 `TeacherCircleView`。

## 10. `TeacherCircleView.vue`：老师圈

对应 `/courses/:courseId/teachers/:teacherId`。同一个组件依靠两个路由参数显示任意课程和老师。

组件挂载时并行加载：

```text
课程名称 + 老师名称 + 资料 + 讨论 + 复习指南
```

`activeTab` 控制资料、讨论、指南三个页签。

上传资料时使用 `FormData` 发送 multipart 请求。打开资料时先请求 Blob：PDF 交给 `PdfViewer`，图片创建 Object URL，Word/PPT/压缩包直接下载。

父子组件交互示例：

```html
<PdfViewer
  :blob="pdfPreview.blob"
  :title="pdfPreview.resource.title"
  @close="pdfPreview = null"
/>
```

```text
TeacherCircleView --props--> PdfViewer
TeacherCircleView <--emit--- PdfViewer
```

## 11. `CetView.vue`：四六级业务状态机

```text
选择 CET4/CET6
       ↓
选择真题练习/精听精读/完整套卷
       ↓
选择题型和套卷
       ↓
查看题目、作答、音频与解析
```

主要状态有 `level`、`mode`、`section`、`selectedPaper`、`selectedItem`、`answer`、`submitted`。虽然文件很大，但仍是“事件改变状态 → 必要时请求 API → 模板根据状态显示”的同一模式。

音频使用 HTTP Range 流式播放；片段练习通过 `audioStartMs` 和 `audioEndMs` 控制开始、结束时间，类似 JavaFX `MediaPlayer` 时间监听。

## 12. 可复用组件

### `AuthTransitionScene.vue`

只负责登录过渡动画。父组件通过 props 传入场景，不访问后端。

### `PdfViewer.vue`

通过 props 接收 PDF Blob 和标题，通过 emit 发出关闭、下载事件。它使用 PDF.js 将每页渲染到 Canvas，失败时改用浏览器 iframe。卸载时销毁任务并释放 Object URL。

### `UserSurveyModal.vue`

加载问卷、维护评分与建议、提交结果，并通过 `emit('close')` 通知父组件关闭。

### `AdminSurveyModal.vue`

查看实时反馈、计算平均分、编辑问题并定时刷新；卸载时清除定时器。

## 13. `styles.css` 与数据来源

`styles.css` 是全局视觉系统，负责布局、主题、弹窗、按钮、卡片、移动端适配以及各业务页面样式。

早期原型曾使用 `data.js` 保存静态演示数据，目前该文件已删除。核心数据统一来自 Spring Boot 和 MySQL；后端不可用时前端明确显示请求失败，避免静态数据与数据库不一致。

## 14. 常见模板语法

```html
<!-- 显示变量 -->
<span>{{ course.name }}</span>

<!-- JavaScript 属性绑定 -->
<button :disabled="loading">提交</button>

<!-- 点击事件 -->
<button @click="loadData">刷新</button>

<!-- 双向绑定 -->
<input v-model="query">

<!-- 条件 -->
<div v-if="loading">加载中</div>
<div v-else>加载完成</div>

<!-- 遍历数组 -->
<article v-for="course in courses" :key="course.id">
  {{ course.name }}
</article>

<!-- 阻止传统表单刷新 -->
<form @submit.prevent="submit">
```

`@click.self` 表示只有点击弹窗背景自身才执行，点击内部内容不执行。

## 15. 完整课程交互链路

```text
1. HomeView 挂载
2. catalogApi.courses()
3. GET /api/courses
4. Spring Boot 查询 course + course_program
5. 返回 JSON
6. courses.value 被赋值
7. Vue 自动生成课程列表
8. 用户点击 router-link
9. 地址变为 /courses/course-xxx
10. router-view 切换为 TeachersView
11. TeachersView 读取 courseId 并请求老师
12. 用户点击老师
13. 地址加入 teacherId
14. router-view 切换为 TeacherCircleView
15. 并行加载资料、讨论、指南与名称
16. 修改 ref 状态
17. Vue 自动更新界面
```

## 16. 完整登录链路

```text
AuthView 输入账号密码
        ↓
authApi.login()
        ↓
POST /api/auth/login
        ↓
Spring Boot 校验账号密码
        ↓
返回 token、username、displayName、role
        ↓
保存到 authSession 和 localStorage
        ↓
加载当前账号匿名身份
        ↓
authenticated 自动变为 true
        ↓
App.vue 自动显示系统主体
```

这里没有显式调用“打开主页面”。登录状态改变后，模板自然选择系统主体。

