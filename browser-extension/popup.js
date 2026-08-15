function render() {
  chrome.storage.local.get(['lastStatus', 'bridgeConfig', 'bridgeStatus']).then(({ lastStatus, bridgeConfig, bridgeStatus }) => {
    if (lastStatus) document.querySelector('#state').textContent = `${lastStatus.provider || '平台'} · ${lastStatus.status || '等待中'}`
    const status = bridgeStatus ? `${bridgeStatus.status} · ${bridgeStatus.detail || ''} · ${bridgeStatus.at || ''}` : (bridgeConfig?.bindingSecret ? '已绑定，等待连接' : '尚未绑定')
    document.querySelector('#bridge-status').textContent = status
  })
}
render()
