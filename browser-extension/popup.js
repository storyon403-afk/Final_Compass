function render() {
  chrome.storage.local.get(['lastStatus', 'bridgeConfig', 'bridgeEnabled', 'bridgeStatus']).then(({ lastStatus, bridgeConfig, bridgeEnabled, bridgeStatus }) => {
    if (lastStatus) document.querySelector('#state').textContent = `${lastStatus.provider || '平台'} · ${lastStatus.status || '等待中'}`
    document.querySelector('#bridge-url').value = bridgeConfig?.url || ''
    document.querySelector('#bridge-token').value = bridgeConfig?.token || ''
    document.querySelector('#bridge-enabled').checked = Boolean(bridgeEnabled)
    const status = bridgeStatus ? `${bridgeStatus.status} · ${bridgeStatus.detail || ''} · ${bridgeStatus.at || ''}` : '未启用'
    document.querySelector('#bridge-status').textContent = status
  })
}
document.querySelector('#bridge-save').addEventListener('click', () => {
  const url = document.querySelector('#bridge-url').value.trim()
  const token = document.querySelector('#bridge-token').value.trim()
  const enabled = document.querySelector('#bridge-enabled').checked
  chrome.storage.local.set({
    bridgeConfig: { url: url || 'ws://127.0.0.1:8080/ws/browser-bridge', token },
    bridgeEnabled: enabled && Boolean(token)
  }).then(() => setTimeout(render, 1200))
})
render()
