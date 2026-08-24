'use strict';

(() => {
    const STYLE_PACK_KEY = 'finals-compass-livedoc-style-packs-v1';
    const SVG_PACK_KEY = 'finals-compass-livedoc-svg-packs-v1';
    const LOCAL_DB_NAME = 'finals-compass-livedoc-local-v1';
    const LOCAL_DB_VERSION = 1;
    const subscribers = new Map();

    function openLocalDb() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(LOCAL_DB_NAME, LOCAL_DB_VERSION);
            request.onupgradeneeded = () => {
                const db = request.result;
                if (!db.objectStoreNames.contains('drafts')) db.createObjectStore('drafts', { keyPath: 'id' });
                if (!db.objectStoreNames.contains('handles')) db.createObjectStore('handles', { keyPath: 'id' });
            };
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error || new Error('无法打开本地草稿缓存'));
        });
    }

    async function localStore(storeName, mode, operation) {
        const db = await openLocalDb();
        try {
            return await new Promise((resolve, reject) => {
                const transaction = db.transaction(storeName, mode);
                const request = operation(transaction.objectStore(storeName));
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(request.error || new Error('本地缓存操作失败'));
                transaction.onabort = () => reject(transaction.error || new Error('本地缓存事务失败'));
            });
        } finally { db.close(); }
    }

    const localGet = (store, id) => localStore(store, 'readonly', (items) => items.get(id));
    const localPut = (store, value) => localStore(store, 'readwrite', (items) => items.put(value));
    const localDelete = (store, id) => localStore(store, 'readwrite', (items) => items.delete(id));
    const localAll = (store) => localStore(store, 'readonly', (items) => items.getAll());

    function localId() {
        return crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

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

    async function projectResult(file, filePath = '') {
        if (!file) return { success: false, canceled: true };
        const extension = file.name.split('.').pop()?.toLowerCase();
        return {
            success: true,
            filePath: filePath || `browser://${file.name}`,
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

    async function chooseLocalProject() {
        if (typeof window.showOpenFilePicker !== 'function') {
            return projectResult(await pickFile('.vdocx,.vpptx'));
        }
        try {
            const [handle] = await window.showOpenFilePicker({
                multiple: false,
                types: [{ description: 'liveDoc 工程', accept: { 'application/vnd.vcp.vdoc+zip': ['.vdocx', '.vpptx'] } }],
            });
            const id = localId();
            await localPut('handles', { id, handle });
            return projectResult(await handle.getFile(), `local-handle://${id}`);
        } catch (error) {
            if (error?.name === 'AbortError') return { success: false, canceled: true };
            throw error;
        }
    }

    async function saveToLocalFile(name, bytes, filePath, saveAs) {
        let id = !saveAs ? String(filePath || '').match(/^local-handle:\/\/([^/]+)$/)?.[1] : '';
        let handle = id ? (await localGet('handles', id))?.handle : null;
        if (handle && await handle.queryPermission?.({ mode: 'readwrite' }) !== 'granted') {
            if (await handle.requestPermission?.({ mode: 'readwrite' }) !== 'granted') handle = null;
        }
        if (!handle && typeof window.showSaveFilePicker === 'function') {
            try {
                handle = await window.showSaveFilePicker({
                    suggestedName: name,
                    types: [{ description: 'liveDoc 工程', accept: { 'application/vnd.vcp.vdoc+zip': [name.toLowerCase().endsWith('.vpptx') ? '.vpptx' : '.vdocx'] } }],
                });
            } catch (error) {
                if (error?.name === 'AbortError') return { success: false, canceled: true };
                throw error;
            }
            id = localId();
            await localPut('handles', { id, handle });
        }
        if (!handle) return download(name, bytes, 'application/vnd.vcp.vdoc+zip');
        const writable = await handle.createWritable();
        await writable.write(bytes);
        await writable.close();
        return { success: true, filePath: `local-handle://${id}`, name: handle.name || name, size: bytes.length, modifiedAt: Date.now() };
    }

    async function cacheDraft(payload = {}) {
        const bytes = new Uint8Array(payload.bytes || []);
        if (!bytes.length) return { success: false };
        const id = String(payload.documentId || 'current');
        await localPut('drafts', {
            id,
            name: String(payload.name || '未命名文稿.vdocx'),
            kind: projectKind(payload.name),
            bytes,
            sourcePath: String(payload.filePath || ''),
            dirty: payload.dirty !== false,
            updatedAt: Date.now(),
        });
        const drafts = (await localAll('drafts')).sort((a, b) => b.updatedAt - a.updatedAt);
        await Promise.all(drafts.slice(12).map((item) => localDelete('drafts', item.id)));
        notifyHost('draft-cached', { name: payload.name });
        return { success: true };
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
        chooseOpen: chooseLocalProject,
        chooseImport: async () => importResult(await pickFile('.html,.htm,.md,.markdown,.txt,.rtf,.docx,.pptx,.vdocx,.vpptx')),
        readPath: async (filePath) => {
            const draftId = String(filePath || '').match(/^local-cache:\/\/(.+)$/)?.[1];
            if (draftId) {
                const draft = await localGet('drafts', draftId);
                if (!draft) throw new Error('本地恢复缓存已不存在');
                return { success: true, filePath: draft.sourcePath || null, name: draft.name, kind: draft.kind, bytes: new Uint8Array(draft.bytes), size: draft.bytes.byteLength, modifiedAt: draft.updatedAt };
            }
            const handleId = String(filePath || '').match(/^local-handle:\/\/([^/]+)$/)?.[1];
            if (handleId) {
                const handle = (await localGet('handles', handleId))?.handle;
                if (!handle) throw new Error('本地文件授权已失效，请重新使用“打开”选择文件');
                return projectResult(await handle.getFile(), filePath);
            }
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
            const result = await saveToLocalFile(name, bytes, payload.filePath, payload.saveAs);
            if (result.success) {
                try {
                    await cacheDraft({ documentId: payload.documentId, name: result.name, filePath: result.filePath, bytes, dirty: false });
                } catch (error) {
                    console.warn('[liveDoc] saved locally but could not refresh recovery cache', error);
                }
                notifyHost('project-saved', result);
            }
            return result;
        },
        cacheDraft,
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
            const localDrafts = (await localAll('drafts').catch(() => []))
                .sort((a, b) => b.updatedAt - a.updatedAt)
                .map((draft) => ({
                    path: `local-cache://${draft.id}`, filePath: `local-cache://${draft.id}`,
                    name: `${draft.name}${draft.dirty ? ' · 恢复草稿' : ' · 本机缓存'}`,
                    kind: draft.kind, size: draft.bytes?.byteLength || 0, modifiedAt: draft.updatedAt,
                }));
            try {
                const response = await apiFetch('/api/livedoc/projects');
                const legacyProjects = (await response.json()).map((project) => ({
                    path: `server://project/${project.id}`, filePath: `server://project/${project.id}`,
                    name: `${project.name} · 云端旧项目`, kind: project.documentKind,
                    size: project.sizeBytes, modifiedAt: new Date(project.updatedAt).getTime()
                }));
                return [...localDrafts, ...legacyProjects].sort((a, b) => b.modifiedAt - a.modifiedAt);
            } catch { return localDrafts; }
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
