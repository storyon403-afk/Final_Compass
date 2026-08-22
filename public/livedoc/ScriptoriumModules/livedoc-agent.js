'use strict';

(() => {
    const state = { sessionKey: '', busy: false };

    function createUi() {
        const trigger = document.createElement('button');
        trigger.className = 'livedoc-agent-trigger';
        trigger.type = 'button';
        trigger.textContent = '✦ 与 Agent 共笔';

        const panel = document.createElement('aside');
        panel.className = 'livedoc-agent-panel';
        panel.hidden = true;
        panel.innerHTML = `
            <header><div><small>LIVEDOC COLLABORATOR</small><h2>与 Agent 共笔</h2></div><button type="button" aria-label="关闭">×</button></header>
            <div class="livedoc-agent-log"><p class="livedoc-agent-message status">打开或新建文档后，告诉 Agent 你想修改正文、排版或演示页面。Agent 的写操作会进入“文脉”待审区，不会直接覆盖文档。</p></div>
            <form><textarea placeholder="例如：把第二节改得更简洁，并补一张总结幻灯片…" required></textarea><div><small>修改需人工审批</small><button type="submit">提交给 Agent</button></div></form>`;
        document.body.append(trigger, panel);
        const log = panel.querySelector('.livedoc-agent-log');
        const form = panel.querySelector('form');
        const input = panel.querySelector('textarea');
        const submit = form.querySelector('button');
        trigger.addEventListener('click', () => { panel.hidden = false; input.focus(); });
        panel.querySelector('header button').addEventListener('click', () => { panel.hidden = true; });
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const goal = input.value.trim();
            if (!goal || state.busy) return;
            append(log, goal, 'user');
            input.value = '';
            state.busy = true;
            submit.disabled = true;
            try { await collaborate(goal, log); }
            catch (error) { append(log, `协作失败：${error.message}`, 'error'); }
            finally { state.busy = false; submit.disabled = false; }
        });
    }

    function append(host, text, type = '') {
        const item = document.createElement('p');
        item.className = `livedoc-agent-message ${type}`.trim();
        item.textContent = text;
        host.appendChild(item);
        host.scrollTop = host.scrollHeight;
        return item;
    }

    async function waitForAgent() {
        for (let attempt = 0; attempt < 100; attempt += 1) {
            if (window.ScriptoriumAgent?.current) return window.ScriptoriumAgent;
            await new Promise((resolve) => setTimeout(resolve, 80));
        }
        throw new Error('请先新建或打开一份 liveDoc 文档');
    }

    async function ensureSession() {
        if (state.sessionKey) return state.sessionKey;
        const response = await fetch('/api/ai-center/chat/sessions', {
            method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }
        });
        if (!response.ok) throw new Error(`无法创建 Agent 会话（HTTP ${response.status}）`);
        state.sessionKey = (await response.json()).sessionKey;
        return state.sessionKey;
    }

    async function askModel(message) {
        const sessionKey = await ensureSession();
        const response = await fetch(`/api/ai-center/chat/sessions/${encodeURIComponent(sessionKey)}/messages`, {
            method: 'POST', credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
            body: JSON.stringify({ message, credentialSource: 'PLATFORM' })
        });
        if (!response.ok || !response.body) throw new Error(`Agent Runtime 不可用（HTTP ${response.status}）`);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '', output = '';
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const frames = buffer.split('\n\n');
            buffer = frames.pop() || '';
            for (const frame of frames) {
                let event = 'message', data = '';
                frame.split('\n').forEach((line) => {
                    if (line.startsWith('event:')) event = line.slice(6).trim();
                    if (line.startsWith('data:')) data += line.slice(5).trim();
                });
                if (!data) continue;
                const parsed = JSON.parse(data);
                if (event === 'delta') output += parsed.text || '';
                if (event === 'error') throw new Error(parsed.message || 'Agent 调用失败');
            }
        }
        if (!output.trim()) throw new Error('Agent 没有返回修改方案');
        return output.trim();
    }

    function extractJson(text) {
        const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i)?.[1];
        const candidate = fenced || text.slice(text.indexOf('{'), text.lastIndexOf('}') + 1);
        return JSON.parse(candidate);
    }

    function extractPlan(text) {
        const plan = extractJson(text);
        if (!Array.isArray(plan.actions) || !plan.actions.length) throw new Error('Agent 返回的操作计划为空');
        return plan;
    }

    const READ_METHODS = Object.freeze([
        'getDocumentInfo', 'getOutline', 'getSource', 'searchSource', 'getRenderedText',
        'getViewportSource', 'getVisualContext', 'getPrHistory', 'listStylePacks',
        'getStylePack', 'listSvgAssetPacks', 'listSvgAssets', 'getSvgAsset', 'getSvgAssetPack',
        'getSection', 'getSlideCount', 'getSlide', 'getActiveSlide'
    ]);

    async function research(current, goal, info) {
        const planner = `你是 liveDoc 协作 Agent 的只读规划器。用户目标：${goal}\n文档信息：${JSON.stringify(info)}\n` +
            `可用只读工具：${READ_METHODS.join(', ')}。只输出 JSON：{"reads":[{"method":"getOutline","payload":{}}]}。` +
            `选择最多 8 个必要读取；不得请求写工具。`;
        const plan = extractJson(await askModel(planner));
        const reads = Array.isArray(plan.reads) ? plan.reads.slice(0, 8) : [];
        const results = [];
        for (const read of reads) {
            const method = String(read?.method || '');
            if (!READ_METHODS.includes(method) || typeof current[method] !== 'function') continue;
            try { results.push({ method, payload: read.payload || {}, result: await current[method](read.payload || {}) }); }
            catch (error) { results.push({ method, error: error.message }); }
        }
        return results;
    }

    function promptFor(goal, info, source, researchResults) {
        const deck = info.documentKind === 'pptx';
        return `你是 Final Compass liveDoc 的文档协作 Agent。底层是 VCP Scriptorium v5。
用户目标：${goal}

当前文档信息：${JSON.stringify(info)}
当前${deck ? '活动幻灯片 HTML' : 'Markdown-first 混合源码'}：
<<<SOURCE\n${String(source.source || '').slice(0, 60000)}\nSOURCE

只读工具返回：
${JSON.stringify(researchResults).slice(0, 80000)}

只输出严格 JSON，不要 Markdown 围栏：
{"summary":"给用户的简短说明","actions":[{"method":"submitSourcePr","payload":{...}}]}

允许的方法：
- submitSourcePr：payload 必须包含 expectedRevision=${info.revision}、maid="Final Compass Agent"、summary，以及 replacements 数组。每项为 {"target":"源码中精确存在且尽量唯一的原文","replace":"替换后的完整文本"}。${deck ? `还要包含 sourceKind="html"、slideIndex=${info.activeSlideIndex}。` : '还要包含 sourceKind="markdown-hybrid"。'}
${deck ? `- addSlide：payload 包含 maid、summary、name、source；source 必须是一张完整 HTML Scene。
- insertSlide：同 addSlide，并包含 slideIndex。
- updatePresentationConfig：payload 包含 maid、summary，以及 page 或 presentation 配置。` : ''}
不要虚构 target。需要大改时可将完整当前源码作为 target。所有写操作必须保留 maid 和 summary，以进入人工 PR 审批。`;
    }

    async function executeAction(agent, action, info, log) {
        const method = String(action.method || '');
        const allowed = info.documentKind === 'pptx'
            ? ['submitSourcePr', 'addSlide', 'insertSlide', 'deleteSlide', 'updatePresentationConfig']
            : ['submitSourcePr'];
        if (!allowed.includes(method)) throw new Error(`不允许的文档操作：${method}`);
        const payload = { ...(action.payload || {}) };
        payload.maid = payload.maid || 'Final Compass Agent';
        payload.summary = payload.summary || 'Agent 协作文档修改';
        if (method === 'submitSourcePr') payload.expectedRevision = info.revision;
        append(log, `已提交 ${method}，请在右侧“文脉”待审区检查差异并选择合并或拒绝。`, 'status');
        const resultPromise = agent.current()[method](payload);
        Promise.resolve(resultPromise).then((result) => {
            append(log, result?.success ? `提案已合并：${payload.summary}` : `提案结束：${result?.message || result?.code || '未合并'}`, result?.success ? 'status' : 'error');
        }).catch((error) => append(log, `提案执行失败：${error.message}`, 'error'));
    }

    async function collaborate(goal, log) {
        const agent = await waitForAgent();
        const current = agent.current();
        const info = await current.getDocumentInfo();
        const source = await current.getSource(info.documentKind === 'pptx'
            ? { sourceKind: 'html', slideIndex: info.activeSlideIndex }
            : { sourceKind: 'markdown-hybrid' });
        const progress = append(log, 'Agent 正在读取唯一真源并准备可审阅修改…', 'status');
        const researchResults = await research(current, goal, info);
        progress.textContent = `Agent 已完成 ${researchResults.length} 项只读检查，正在生成可审阅修改…`;
        const answer = await askModel(promptFor(goal, info, source, researchResults));
        const plan = extractPlan(answer);
        progress.textContent = plan.summary || 'Agent 已生成修改方案';
        for (const action of plan.actions) executeAction(agent, action, info, log);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', createUi, { once: true });
    else createUi();
})();
