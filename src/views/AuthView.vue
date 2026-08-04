<script setup>
import { computed, nextTick, ref } from 'vue'
import { authApi } from '../api'
import AuthTransitionScene from '../components/AuthTransitionScene.vue'

defineProps({
  scene: { type: String, default: 'tree' }
})

const savedChallenge = sessionStorage.getItem('finals-compass-beta-challenge')
const previouslyVerified = sessionStorage.getItem('finals-compass-beta-verified') === 'true'
let initialChallenge = null
try { initialChallenge = savedChallenge ? JSON.parse(savedChallenge) : null } catch { sessionStorage.removeItem('finals-compass-beta-challenge') }
const step = ref(initialChallenge ? 'verify' : previouslyVerified ? 'login' : 'request')
const challenge = ref(initialChallenge)
const email = ref(initialChallenge?.email || '')
const confirmEmail = ref('')
const phone = ref('+86')
const code = ref('')
const codeInput = ref(null)
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const normalizedPhone = computed(() => phone.value.replace(/[\s-]/g, ''))

async function submitRequest() {
  error.value = ''
  if (email.value.trim().toLowerCase() !== confirmEmail.value.trim().toLowerCase()) {
    error.value = '两次输入的邮箱不一致'
    return
  }
  if (!/^\+861[3-9]\d{9}$/.test(normalizedPhone.value)) {
    error.value = '请输入 +86 开头的中国大陆手机号'
    return
  }
  busy.value = true
  try {
    challenge.value = await authApi.requestBetaAccess(email.value, confirmEmail.value, normalizedPhone.value)
    sessionStorage.setItem('finals-compass-beta-challenge', JSON.stringify(challenge.value))
    step.value = 'verify'
  } catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

async function submitCode() {
  error.value = ''
  busy.value = true
  try {
    await authApi.verifyBetaAccess(challenge.value.requestId, challenge.value.email, code.value)
    sessionStorage.removeItem('finals-compass-beta-challenge')
    sessionStorage.setItem('finals-compass-beta-verified', 'true')
    step.value = 'login'
  } catch (reason) {
    error.value = reason.status === 400
      ? `${reason.message}，可在有效期内重新输入`
      : reason.message
    code.value = ''
    await nextTick()
    codeInput.value?.focus()
  }
  finally { busy.value = false }
}

async function submitLogin() {
  error.value = ''
  busy.value = true
  try { await authApi.login(username.value.trim(), password.value) }
  catch (reason) { error.value = reason.message }
  finally { busy.value = false }
}

function restart() {
  sessionStorage.removeItem('finals-compass-beta-challenge')
  challenge.value = null
  code.value = ''
  confirmEmail.value = ''
  error.value = ''
  step.value = 'request'
}

function openExistingLogin() {
  error.value = ''
  step.value = 'login'
}
</script>

<template>
  <main class="auth-page" :class="`auth-scene-${scene}`">
    <AuthTransitionScene :scene="scene" />
    <section class="auth-card">
      <div class="auth-progress" aria-label="验证进度">
        <span :class="{ active: step === 'request' }">1</span><i></i><span :class="{ active: step === 'verify' }">2</span><i></i><span :class="{ active: step === 'login' }">3</span>
      </div>

      <form v-if="step === 'request'" class="auth-form" @submit.prevent="submitRequest">
        <div class="auth-title"><h1>验证你的联系方式</h1><p>填写后，系统会自动将一次性验证码发送到你的邮箱。</p></div>
        <label>邮箱<input v-model.trim="email" type="email" autocomplete="email" maxlength="254" placeholder="name@example.com" required autofocus /></label>
        <label>再次确认邮箱<input v-model.trim="confirmEmail" type="email" autocomplete="email" maxlength="254" placeholder="请再次输入邮箱" required /></label>
        <label>手机号<input v-model.trim="phone" type="tel" autocomplete="tel" inputmode="tel" maxlength="14" placeholder="+8613812345678" pattern="\+861[3-9][0-9]{9}" required /><small>仅支持 +86 格式，例如 +8613812345678</small></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary-button wide" type="submit" :disabled="busy">{{ busy ? '正在提交…' : '获取邮箱验证码' }}</button>
        <button class="text-button" type="button" @click="openExistingLogin">管理员或已有内测账号登录</button>
      </form>

      <form v-else-if="step === 'verify'" class="auth-form" @submit.prevent="submitCode">
        <div class="auth-title"><span class="eyebrow">邮箱验证</span><h1>输入 6 位验证码</h1><p>验证码已自动发送至<br /><b>{{ challenge.email }}</b></p></div>
        <label>验证码<input ref="codeInput" v-model="code" class="code-input" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" placeholder="000000" required autofocus /></label>
        <div class="auth-notice">验证码 10 分钟内有效且仅可使用一次。如暂未收到，请稍候检查收件箱和垃圾邮件。</div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary-button wide" type="submit" :disabled="busy || code.length !== 6">{{ busy ? '正在验证…' : '验证并继续' }}</button>
        <button class="text-button" type="button" @click="restart">邮箱填错了？重新申请</button>
      </form>

      <form v-else class="auth-form" @submit.prevent="submitLogin">
        <div class="auth-title"><span class="success-mark">✓</span><h1>邮箱验证成功</h1><p>管理员会另行发送内测账号和初始密码，收到后即可登录。</p></div>
        <label>内测账号<input v-model="username" autocomplete="username" placeholder="请输入管理员发送的账号" required autofocus /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" required /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary-button wide" type="submit" :disabled="busy">{{ busy ? '正在登录…' : '登录内测系统' }}</button>
        <button v-if="!previouslyVerified" class="text-button" type="button" @click="restart">还没有账号？申请内测访问</button>
      </form>
    </section>
    <aside class="auth-credits" aria-label="平台致谢">
      <span>Finals Compass 开源社区提供技术支持</span>
      <span>感谢技术引路人 Micro_frank</span>
    </aside>
  </main>
</template>
