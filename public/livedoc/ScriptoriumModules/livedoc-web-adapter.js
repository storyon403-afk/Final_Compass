'use strict';

(() => {
    const STYLE_PACK_KEY = 'finals-compass-livedoc-style-packs-v1';
    const SVG_PACK_KEY = 'finals-compass-livedoc-svg-packs-v1';
    const subscribers = new Map();

    function emit(channel, payload) {
        (subscribers.get(channel) || new Set()).forEach((listener) => listener(payload));
    }

    function subscribe(channel, listener) {
        if (typeof listener !== 'function') return () => {};
        const listeners = subscribers.get(channel) || new Set();
        listeners.add(listener);
        subscribers.set(channel, listeners);
        return () => listeners.delete(listener);
    }

    function pickFile(accept) {
        return new Promise((resolve) => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = accept;
            input.hidden = true;
            const finish = (value) => {
                input.remove();
                resolve(value);
            };
            input.addEventListener('change', () => finish(input.files?.[0] || null), { once: true });
            document.body.appendChild(input);
            input.click();
            window.setTimeout(() => {
                if (!input.isConnected || input.files?.length) return;
                finish(null);
            }, 60000);
        });
    }

    async function projectResult(file) {
        if (!file) return { success: false, canceled: true };
        const extension = file.name.split('.').pop()?.toLowerCase();
        return {
            success: true,
            filePath: `browser://${file.name}`,
            name: file.name,
            kind: extension === 'vpptx' ? 'vpptx' : 'vdocx',
            bytes: new Uint8Array(await file.arrayBuffer()),
            size: file.size,
            modifiedAt: file.lastModified,
        };
    }

    async function importResult(file) {
        if (!file) return { success: false, canceled: true };
        const extension = file.name.split('.').pop()?.toLowerCase();
        if (extension === 'vdocx' || extension === 'vpptx') return projectResult(file);
        if (['docx', 'pptx'].includes(extension)) {
            const body = new FormData();
            body.append('file', file, file.name);
            const response = await apiFetch('/api/ai/attachments/convert', { method: 'POST', body });
            const converted = await response.json();
            return {
                success: true, filePath: null, name: file.name, kind: 'imported',
                importedKind: 'markdown', source: String(converted.markdown || ''),
                sourceFormat: 'markdown-hybrid', lineEnding: 'lf',
                importMetadata: { sourceFormat: extension, sourceName: file.name, adapter: 'markitdown-worker', truncated: Boolean(converted.truncated) },
                size: file.size, modifiedAt: file.lastModified,
            };
        }
        if (!['md', 'markdown', 'txt', 'html', 'htm', 'rtf'].includes(extension)) {
            throw new Error('支持导入 Markdown、TXT、HTML、RTF、DOCX、PPTX 和 liveDoc 工程文件。');
        }
        let content = await file.text();
        if (extension === 'rtf') content = rtfToText(content);
        return {
            success: true,
            filePath: null,
            name: file.name,
            kind: 'imported',
            importedKind: extension === 'html' || extension === 'htm' ? 'html' : extension,
            source: content,
            sourceFormat: 'markdown-hybrid',
            lineEnding: /\r\n/.test(content) ? 'crlf' : 'lf',
            importMetadata: { sourceFormat: extension, sourceName: file.name, adapter: 'livedoc-web' },
            size: file.size,
            modifiedAt: file.lastModified,
        };
    }

    function rtfToText(input) {
        return String(input || '')
            .replace(/\\'([0-9a-f]{2})/gi, (_match, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
            .replace(/\\u(-?\d+)\??/g, (_match, raw) => String.fromCharCode(Number(raw) < 0 ? Number(raw) + 65536 : Number(raw)))
            .replace(/\\par[d]?\b/g, '\n\n').replace(/\\line\b/g, '\n').replace(/\\tab\b/g, '\t')
            .replace(/\\emdash\b/g, '—').replace(/\\endash\b/g, '–')
            .replace(/\\lquote\b/g, '‘').replace(/\\rquote\b/g, '’')
            .replace(/\\ldblquote\b/g, '“').replace(/\\rdblquote\b/g, '”')
            .replace(/\\~|\\ /g, ' ').replace(/\\\{/g, '{').replace(/\\\}/g, '}').replace(/\\\\/g, '\\')
            .replace(/\{\\fonttbl[\s\S]*?\}\s*/gi, '').replace(/\{\\colortbl[\s\S]*?\}\s*/gi, '')
            .replace(/\{\\stylesheet[\s\S]*?\}\s*/gi, '').replace(/\{\\info[\s\S]*?\}\s*/gi, '')
            .replace(/\{\\\*[\s\S]*?\}\s*/g, '').replace(/\\[a-z]+-?\d*\s?/gi, '')
            .replace(/[{}]/g, '').replace(/\n[ \t]+/g, '\n').trim();
    }

    function download(name, content, type) {
        const blob = content instanceof Blob ? content : new Blob([content], { type });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = name;
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        return { success: true, filePath: `browser-download://${name}`, name, size: blob.size, modifiedAt: Date.now() };
    }

    function serverProjectId(path) {
        return String(path || '').match(/^server:\/\/project\/(\d+)$/)?.[1] || '';
    }

    function projectKind(name) {
        return String(name || '').toLowerCase().endsWith('.vpptx') ? 'vpptx' : 'vdocx';
    }

    async function apiFetch(url, options = {}) {
        const response = await fetch(url, { credentials: 'same-origin', ...options });
        if (!response.ok) {
            let message = `HTTP ${response.status}`;
            try { message = (await response.json()).error || message; } catch {}
            throw new Error(message);
        }
        return response;
    }

    function notifyHost(type, payload = {}) {
        if (window.parent === window) return;
        window.parent.postMessage({ channel: 'final-compass:livedoc', type, payload }, window.location.origin);
    }

    async function standaloneHtml(input) {
        let html = String(input || '');
        const requested = new Set();
        html.replace(/<script\b[^>]*(?:src=["']([^"']+)["']|data-vdoc-library=["']([^"']+)["'])[^>]*>[\s\S]*?<\/script>/gi,
            (tag, src, marker) => {
                const value = `${src || ''} ${marker || ''}`.toLowerCase();
                if (value.includes('three')) requested.add('three');
                if (value.includes('anime')) requested.add('anime');
                return tag;
            });
        html = html.replace(/<script\b[^>]*(?:src=["'][^"']*(?:three|anime)[^"']*["']|data-vdoc-library=["'][^"']+["'])[^>]*>[\s\S]*?<\/script>/gi, '');
        const embedded = [];
        for (const library of requested) {
            const response = await apiFetch(`/livedoc/vendor/${library}.min.js`);
            const source = (await response.text()).replace(/<\/script/gi, '<\\/script');
            embedded.push(`<script data-vdoc-embedded-library="${library}">${source}</script>`);
        }
        if (embedded.length) {
            const block = `\n${embedded.join('\n')}\n`;
            html = /<\/head>/i.test(html) ? html.replace(/<\/head>/i, () => `${block}</head>`) : block + html;
        }
        return html;
    }

    function loadJson(key, fallback = []) {
        try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); }
        catch { return fallback; }
    }

    function saveJson(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
        return { success: true };
    }

    async function readExternalResource(payload = {}) {
        const url = String(payload.url || '');
        if (!/^https?:|^blob:|^data:/i.test(url)) {
            return { success: true, collectable: false, reason: 'Web 运行时不能读取本机绝对路径', url };
        }
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const blob = await response.blob();
            return {
                success: true,
                collectable: true,
                reason: '',
                url,
                finalUrl: response.url || url,
                name: url.split('/').pop() || 'resource',
                mime: blob.type || response.headers.get('content-type') || 'application/octet-stream',
                category: (blob.type || '').startsWith('font/') ? 'fonts' : 'media',
                size: blob.size,
                bytes: new Uint8Array(await blob.arrayBuffer()),
            };
        } catch (error) {
            return { success: true, collectable: false, reason: error.message, url };
        }
    }

    const themeMedia = window.matchMedia('(prefers-color-scheme: dark)');
    themeMedia.addEventListener('change', (event) => emit('theme', event.matches ? 'dark' : 'light'));

    const api = Object.freeze({
        openWindow: async () => true,
        chooseOpen: async () => projectResult(await pickFile('.vdocx,.vpptx')),
        chooseImport: async () => importResult(await pickFile('.html,.htm,.md,.markdown,.txt,.rtf,.docx,.pptx,.vdocx,.vpptx')),
        readPath: async (filePath) => {
            const id = serverProjectId(filePath);
            if (!id) throw new Error('浏览器不能通过本机路径直接读取文件，请使用“打开”。');
            const response = await apiFetch(`/api/livedoc/projects/${id}`);
            const disposition = response.headers.get('content-disposition') || '';
            const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
            const name = encodedName ? decodeURIComponent(encodedName) : `liveDoc-${id}.vdocx`;
            const bytes = new Uint8Array(await response.arrayBuffer());
            return { success: true, filePath, name, kind: projectKind(name), bytes, size: bytes.length, modifiedAt: Date.now() };
        },
        readExternalResource,
        save: async (payload = {}) => {
            const suggested = String(payload.suggestedName || '未命名文稿.vdocx');
            const extension = suggested.toLowerCase().endsWith('.vpptx') ? '.vpptx' : '.vdocx';
            const base = suggested.replace(/\.(?:vdocx|vpptx)$/i, '') || '未命名文稿';
            const name = `${base}${extension}`;
            const bytes = new Uint8Array(payload.bytes || []);
            const currentId = payload.saveAs ? '' : serverProjectId(payload.filePath);
            try {
                const query = new URLSearchParams({ name, kind: extension.slice(1) });
                const response = await apiFetch(`/api/livedoc/projects${currentId ? `/${currentId}` : ''}?${query}`, {
                    method: currentId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: bytes
                });
                const project = await response.json();
                const result = { success: true, filePath: `server://project/${project.id}`, name: project.name, size: project.sizeBytes, modifiedAt: Date.now() };
                notifyHost('project-saved', result);
                return result;
            } catch (error) {
                console.warn('[liveDoc] server save unavailable, using download fallback', error);
                return download(name, bytes, 'application/vnd.vcp.vdoc+zip');
            }
        },
        exportRichDocument: async (payload = {}) => {
            const html = await standaloneHtml(payload.html || '');
            if (payload.format === 'pdf') {
                try {
                    const response = await apiFetch('/api/livedoc/export/pdf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ html }) });
                    const result = download(String(payload.suggestedName || 'liveDoc.pdf').replace(/\.pdf$/i, '') + '.pdf', await response.blob(), 'application/pdf');
                    notifyHost('document-exported', { format: 'pdf', name: result.name });
                    return result;
                } catch (error) {
                    const result = download(String(payload.suggestedName || 'liveDoc').replace(/\.pdf$/i, '') + '.html', html, 'text/html;charset=utf-8');
                    window.alert(`PDF 服务暂不可用（${error.message}），已导出可打印的独立 HTML。`);
                    return result;
                }
            }
            const result = download(String(payload.suggestedName || 'liveDoc.html'), html, 'text/html;charset=utf-8');
            notifyHost('document-exported', { format: 'html', name: result.name });
            return result;
        },
        listRecent: async () => {
            try {
                const response = await apiFetch('/api/livedoc/projects');
                return (await response.json()).map((project) => ({
                    path: `server://project/${project.id}`, filePath: `server://project/${project.id}`,
                    name: project.name, kind: project.documentKind,
                    size: project.sizeBytes, modifiedAt: new Date(project.updatedAt).getTime()
                }));
            } catch { return []; }
        },
        loadStylePacks: async () => loadJson(STYLE_PACK_KEY),
        saveStylePacks: async (packs) => saveJson(STYLE_PACK_KEY, packs),
        loadSvgAssetPacks: async () => loadJson(SVG_PACK_KEY),
        saveSvgAssetPacks: async (packs) => saveJson(SVG_PACK_KEY, packs),
        listSystemFonts: async () => ['system-ui', 'sans-serif', 'serif', 'monospace', 'KaiTi', 'SimSun', 'Microsoft YaHei'],
        loadAgentsList: async () => [],
        loadUserAvatar: async () => null,
        loadAgentAvatar: async () => null,
        getCurrentTheme: async () => themeMedia.matches ? 'dark' : 'light',
        windowReady: async () => true,
        minimizeWindow: () => {},
        maximizeWindow: () => document.documentElement.requestFullscreen?.(),
        unmaximizeWindow: () => document.exitFullscreen?.(),
        closeWindow: () => notifyHost('close-requested'),
        openDevTools: () => {},
        onThemeUpdated: (listener) => subscribe('theme', listener),
        onWindowMaximized: () => () => {},
        onWindowUnmaximized: () => () => {},
        onOpenPathRequest: () => () => {},
        onAgentCheckpointProposed: () => () => {},
        onAgentRequest: () => () => {},
        respondAgentRequest: () => {},
    });

    window.scriptoriumAPI = api;
    window.docxAPI = api;
    window.__LIVE_DOC_RUNTIME__ = Object.freeze({ name: 'liveDoc', engine: 'VCP Scriptorium', adapter: 'web' });
})();
