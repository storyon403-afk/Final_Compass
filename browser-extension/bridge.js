const SOURCE = 'FINALS_COMPASS_WEBAGENT_EXTENSION'
function emit(type, payload = {}) {
  window.postMessage({ source: SOURCE, type, extensionVersion: chrome.runtime.getManifest().version, ...payload }, window.location.origin)
}
emit('READY')
chrome.runtime.sendMessage({ type: 'REGISTER_PLATFORM_TAB' }).catch(() => {})
window.addEventListener('message', event => {
  if (event.source !== window || event.origin !== window.location.origin || event.data?.source !== 'FINALS_COMPASS_WEBAPP') return
  if (event.data.type === 'PING') emit('READY')
  if (event.data.type === 'BIND_BROWSER_BRIDGE') {
    chrome.runtime.sendMessage({
      type: 'BIND_BROWSER_BRIDGE',
      bindingSecret: event.data.bindingSecret,
      bridgeUrl: event.data.bridgeUrl
    }).catch(error => emit('BRIDGE_BINDING_STATUS', { status: 'FAILED', detail: error.message }))
  }
  if (event.data.type === 'START_RUN') {
    chrome.runtime.sendMessage({ type: 'START_RUN', runKey: event.data.runKey, goal: event.data.goal, participants: event.data.participants })
      .catch(error => emit('BRIDGE_ERROR', { message: error.message }))
  }
  if (event.data.type === 'STOP_RUN') {
    chrome.runtime.sendMessage({ type: 'STOP_RUN', runKey: event.data.runKey }).catch(() => {})
  }
  if (event.data.type === 'START_AGENT_BROWSER') {
    chrome.runtime.sendMessage({ type: 'START_AGENT_BROWSER', requestId: event.data.requestId, goal: event.data.goal })
      .catch(error => emit('AGENT_BROWSER_RESULT', { requestId: event.data.requestId, status: 'FAILED', message: error.message }))
  }
})
chrome.runtime.onMessage.addListener(message => {
  if (message?.type === 'BRIDGE_BINDING_STATUS') emit('BRIDGE_BINDING_STATUS', message)
  if (message?.type === 'PARTICIPANT_STATUS') emit('PARTICIPANT_STATUS', message)
  if (message?.type === 'AGENT_BROWSER_RESULT') emit('AGENT_BROWSER_RESULT', message)
})
