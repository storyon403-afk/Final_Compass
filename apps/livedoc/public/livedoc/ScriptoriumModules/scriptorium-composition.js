'use strict';

(() => {
    const MIN_MODULES = 2;
    const MAX_MODULES = 6;

    function createCompositionController(context = {}) {
        const { containerModule, core } = context;
        const notificationPort = context.notificationPort || {};
        if (!containerModule || !core || typeof context.activateDocument !== 'function') {
            throw new TypeError('Composition controller requires container, core and document activation.');
        }

        let elements = context.elements || {};
        let modules = [];
        let draggedId = '';
        let abortController = null;
        let disposed = false;

        const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        })[character]);
        const makeId = (prefix) => `${prefix}-${crypto.randomUUID?.()
            || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;

        function formatSize(bytes) {
            const size = Math.max(0, Number(bytes) || 0);
            return size < 1024 * 1024
                ? `${Math.max(1, Math.round(size / 1024))} KB`
                : `${(size / 1024 / 1024).toFixed(1)} MB`;
        }

        function statusText() {
            if (modules.length < MIN_MODULES) return `还需要 ${MIN_MODULES - modules.length} 份 VPPTX`;
            return `可生成 · ${modules.length} 个模块 · ${modules.reduce((sum, item) => sum + item.slideCount, 0)} 页成员内容`;
        }

        function updateState() {
            elements['composition-count'].textContent = `${modules.length} / ${MAX_MODULES}`;
            elements['composition-status'].textContent = statusText();
            elements['composition-confirm-btn'].disabled = modules.length < MIN_MODULES;
            elements['composition-add-btn'].disabled = modules.length >= MAX_MODULES;
            elements['composition-add-btn'].textContent = modules.length >= MAX_MODULES
                ? '已达到 6 份上限'
                : modules.length ? '继续添加 VPPTX' : '选择 VPPTX';
            elements['composition-empty'].hidden = modules.length > 0;
        }

        function move(id, delta) {
            const index = modules.findIndex((item) => item.id === id);
            const target = index + delta;
            if (index < 0 || target < 0 || target >= modules.length) return;
            [modules[index], modules[target]] = [modules[target], modules[index]];
            render();
        }

        function reorder(sourceId, targetId) {
            const sourceIndex = modules.findIndex((item) => item.id === sourceId);
            const targetIndex = modules.findIndex((item) => item.id === targetId);
            if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return;
            const [source] = modules.splice(sourceIndex, 1);
            modules.splice(targetIndex, 0, source);
            render();
        }

        function createCard(item, index) {
            const card = document.createElement('article');
            card.className = 'composition-module';
            card.draggable = true;
            const order = document.createElement('span');
            order.className = 'composition-module-order';
            order.textContent = String(index + 1).padStart(2, '0');
            const fields = document.createElement('div');
            fields.className = 'composition-module-fields';
            const titleLabel = document.createElement('label');
            titleLabel.textContent = '模块标题';
            const titleInput = document.createElement('input');
            titleInput.value = item.title; titleInput.maxLength = 80;
            titleInput.addEventListener('input', () => { item.title = titleInput.value; });
            titleLabel.appendChild(titleInput);
            const presenterLabel = document.createElement('label');
            presenterLabel.textContent = '汇报人';
            const presenterInput = document.createElement('input');
            presenterInput.value = item.presenter; presenterInput.maxLength = 40; presenterInput.placeholder = '成员姓名';
            presenterInput.addEventListener('input', () => { item.presenter = presenterInput.value; });
            presenterLabel.appendChild(presenterInput);
            const meta = document.createElement('span');
            meta.className = 'composition-module-meta';
            meta.textContent = `${item.name} · ${item.slideCount} 页 · ${item.aspectRatio} · ${formatSize(item.size)}`;
            fields.append(titleLabel, presenterLabel, meta);
            const actions = document.createElement('div');
            actions.className = 'composition-module-actions';
            const up = document.createElement('button');
            up.type = 'button'; up.textContent = '↑'; up.title = '上移'; up.disabled = index === 0;
            up.addEventListener('click', () => move(item.id, -1));
            const down = document.createElement('button');
            down.type = 'button'; down.textContent = '↓'; down.title = '下移'; down.disabled = index === modules.length - 1;
            down.addEventListener('click', () => move(item.id, 1));
            const remove = document.createElement('button');
            remove.type = 'button'; remove.textContent = '×'; remove.title = '移除'; remove.className = 'remove';
            remove.addEventListener('click', () => {
                modules = modules.filter((module) => module.id !== item.id);
                render();
            });
            actions.append(up, down, remove);
            card.addEventListener('dragstart', (event) => {
                draggedId = item.id; card.classList.add('dragging');
                event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', item.id);
            });
            card.addEventListener('dragend', () => { draggedId = ''; card.classList.remove('dragging'); });
            card.addEventListener('dragover', (event) => { event.preventDefault(); card.classList.add('drop-target'); });
            card.addEventListener('dragleave', () => card.classList.remove('drop-target'));
            card.addEventListener('drop', (event) => {
                event.preventDefault(); card.classList.remove('drop-target');
                reorder(event.dataTransfer.getData('text/plain') || draggedId, item.id);
            });
            card.append(order, fields, actions);
            return card;
        }

        function render() {
            elements['composition-list'].replaceChildren(...modules.map(createCard));
            updateState();
        }

        async function parseFile(file) {
            if (!String(file?.name || '').toLowerCase().endsWith('.vpptx')) throw new Error(`${file?.name || '文件'} 不是 VPPTX`);
            const bytes = new Uint8Array(await file.arrayBuffer());
            const unpacked = await containerModule.unpack(bytes, core);
            const model = unpacked.document;
            if (model.manifest?.scene?.kind !== core.PROJECT_KINDS.SLIDE_DECK) throw new Error(`${file.name} 不是演示文档`);
            return {
                id: makeId('module'), name: file.name,
                title: String(model.manifest.title || file.name.replace(/\.vpptx$/i, '')),
                presenter: '', slideCount: model.source?.slides?.length || 0,
                aspectRatio: String(model.manifest.scene.presentation?.aspectRatio || '16 / 9'),
                size: file.size, modifiedAt: file.lastModified,
                documentId: model.manifest.id, model, resourceData: unpacked.resourceData,
            };
        }

        async function ingest(files) {
            const candidates = [...(files || [])];
            const capacity = MAX_MODULES - modules.length;
            for (const file of candidates.slice(0, capacity)) {
                try {
                    const parsed = await parseFile(file);
                    if (modules.some((item) => item.documentId === parsed.documentId
                        || (item.name === parsed.name && item.size === parsed.size))) {
                        throw new Error(`${file.name} 已经在编排中`);
                    }
                    modules.push(parsed);
                } catch (error) {
                    notificationPort.show?.(`导入失败：${error.message}`, 'error', 5000);
                }
            }
            if (candidates.length > capacity) notificationPort.show?.(`最多 6 份，已忽略多出的 ${candidates.length - capacity} 份`, 'info', 4200);
            render();
        }

        function coverSource() {
            return `<style>*{box-sizing:border-box}.vdoc-slide-scene{position:relative;width:100%;height:100%;padding:9% 8%;overflow:hidden;color:#1d2421;background:#fffdf8;font-family:"Songti SC",serif}.vdoc-slide-scene small{color:#8b5e34;font:700 13px/1 system-ui;letter-spacing:.2em}.vdoc-slide-scene h1{max-width:70%;margin:6% 0 2%;font-size:64px;font-weight:500;line-height:1.15}.vdoc-slide-scene p{color:#66706b;font-size:20px}.vdoc-slide-scene i{position:absolute;right:10%;top:18%;width:190px;height:260px;border:1px solid #8b5e34;border-radius:3px 34px 3px 34px;transform:rotate(5deg)}</style><section class="vdoc-slide-scene"><small>GROUP PRESENTATION</small><h1>小组组合演示</h1><p>${modules.length} 个成员模块 · 独立创作，统一编排</p><i></i></section>`;
        }

        function directorySource() {
            const cards = modules.map((item, index) => `<article><b>${String(index + 1).padStart(2, '0')}</b><div><strong>${escapeHtml(item.title.trim() || item.name)}</strong><span>${escapeHtml(item.presenter.trim() || '待填写汇报人')} · ${item.slideCount} 页</span></div></article>`).join('');
            return `<style>*{box-sizing:border-box}.vdoc-slide-scene{width:100%;height:100%;padding:6% 7%;overflow:hidden;color:#1d2421;background:#fffdf8;font-family:"Songti SC",serif}.vdoc-slide-scene>small{color:#8b5e34;font:700 12px/1 system-ui;letter-spacing:.18em}.vdoc-slide-scene h1{margin:2% 0 4%;font-size:44px;font-weight:500}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:18px}.grid article{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:18px;min-height:105px;padding:18px 22px;border:1px solid #c8bca8;border-radius:3px 18px 3px 18px;background:#f4eee3}.grid b{color:#8b5e34;font:500 30px/1 Georgia}.grid div{display:grid;gap:7px}.grid strong{font-size:19px}.grid span{color:#66706b;font:13px/1.4 system-ui}</style><section class="vdoc-slide-scene"><small>INTERACTIVE DIRECTORY</small><h1>总目录</h1><div class="grid">${cards}</div></section>`;
        }

        function buildDocument() {
            const importedSlides = [];
            const resources = new Map();
            const resourceMetadata = new Map();
            const styles = new Map();
            modules.forEach((module, moduleIndex) => {
                (module.model.manifest.resources || []).forEach((resource) => resourceMetadata.set(resource.id, resource));
                module.resourceData.forEach((bytes, id) => resources.set(id, bytes));
                (module.model.manifest.embeddedStyles || []).forEach((style) => styles.set(style.id, style));
                (module.model.source.slides || []).forEach((slide, slideIndex) => importedSlides.push({
                    ...slide,
                    id: `${module.id}-${slide.id || slideIndex}`,
                    name: `${module.title.trim() || module.name} · ${slide.name || `第 ${slideIndex + 1} 页`}`,
                    source: `${module.model.source.deckCss ? `<style>${module.model.source.deckCss}</style>` : ''}${slide.source}`,
                    import: { source: 'composition', moduleId: module.id, moduleIndex, originalDocumentId: module.documentId, originalSlideId: slide.id },
                }));
            });
            const firstScene = modules[0].model.manifest.scene;
            const model = core.createDocument({
                kind: core.PROJECT_KINDS.SLIDE_DECK,
                title: '小组组合演示',
                page: firstScene.page,
                presentation: firstScene.presentation,
                slides: [
                    { name: '组合封面', source: coverSource() },
                    { name: '总目录', source: directorySource() },
                    ...importedSlides,
                ],
            });
            model.manifest.resources = [...resourceMetadata.values()];
            model.manifest.embeddedStyles = [...styles.values()];
            model.manifest.styleDependencies = [...styles.keys()];
            model.manifest.import = {
                sourceFormat: 'multi-vpptx-composition',
                version: 1,
                modules: modules.map((item, index) => ({ id: item.id, order: index, title: item.title.trim() || item.name, presenter: item.presenter.trim(), documentId: item.documentId, slideCount: item.slideCount })),
            };
            return { model, resources };
        }

        function showManager() {
            elements['composition-manager'].hidden = false;
            render();
        }

        function hideManager() { elements['composition-manager'].hidden = true; }

        async function startNew() {
            modules = [];
            await context.activateDocument(core.createDocument({ kind: core.PROJECT_KINDS.SLIDE_DECK, title: '小组组合演示' }), { name: '小组组合演示.vpptx', dirty: true });
            elements['composition-manage-btn'].hidden = false;
            elements['start-menu-dropdown'].hidden = true;
            elements['start-menu-btn'].setAttribute('aria-expanded', 'false');
            showManager();
        }

        async function confirm() {
            if (modules.length < MIN_MODULES) return;
            const { model, resources } = buildDocument();
            await context.activateDocument(model, { name: '小组组合演示.vpptx', resourceData: resources, dirty: true });
            hideManager();
            notificationPort.show?.(`已生成 ${modules.length} 个模块的组合 VPPTX`, 'success', 3600);
        }

        function bind() {
            abortController = new AbortController();
            const options = { signal: abortController.signal };
            const input = elements['composition-file-input'];
            const dropzone = elements['composition-dropzone'];
            elements['new-composition-btn'].addEventListener('click', startNew, options);
            elements['composition-manage-btn'].addEventListener('click', showManager, options);
            elements['composition-close-btn'].addEventListener('click', hideManager, options);
            elements['composition-confirm-btn'].addEventListener('click', confirm, options);
            elements['composition-add-btn'].addEventListener('click', (event) => { event.stopPropagation(); input.click(); }, options);
            input.addEventListener('change', async () => { await ingest(input.files); input.value = ''; }, options);
            dropzone.addEventListener('click', (event) => { if (!event.target.closest('button')) input.click(); }, options);
            dropzone.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); input.click(); } }, options);
            dropzone.addEventListener('dragover', (event) => { event.preventDefault(); dropzone.classList.add('drag-over'); }, options);
            dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-over'), options);
            dropzone.addEventListener('drop', async (event) => { event.preventDefault(); dropzone.classList.remove('drag-over'); await ingest(event.dataTransfer.files); }, options);
            window.addEventListener('keydown', (event) => { if (event.key === 'Escape' && !elements['composition-manager'].hidden) hideManager(); }, options);
            render();
        }

        function dispose() {
            abortController?.abort();
            modules = [];
            disposed = true;
        }

        return Object.freeze({ bind, showManager, hideManager, ingest, buildDocument, dispose });
    }

    window.ScriptoriumComposition = Object.freeze({ createCompositionController });
})();
