with open('/var/www/portal/index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 更新卡片 HTML 结构: 去除 locked 样式, 角标显示 '开源项目'
old_badge = '<span class="badge locked" id="card-water-rocket-badge">需登录</span>'
new_badge = '<span class="badge" id="card-water-rocket-badge" style="color:#38bdf8;border-color:#0284c7;">开源项目</span>'
content = content.replace(old_badge, new_badge)

# 2. 更新 render(session) 中的逻辑: 水火箭卡片始终公开免登录
old_render = """        // 水火箭卡片三态
        if (signedIn) {
            els.waterRocketBadge.textContent = '已授权 · 下载';
            els.waterRocketBadge.classList.remove('locked');
            els.waterRocketBtn.textContent = '进入下载 →';
        } else {
            els.waterRocketBadge.textContent = '需登录';
            els.waterRocketBadge.classList.add('locked');
            els.waterRocketBtn.textContent = '登录后下载 →';
        }"""

new_render = """        // 水火箭卡片: 完全开源公开, 免登录
        els.waterRocketBadge.textContent = '开源项目';
        els.waterRocketBadge.classList.remove('locked');
        els.waterRocketBtn.textContent = 'GitHub 开源 →';"""
content = content.replace(old_render, new_render)

# 3. 移除未登录拦截弹窗监听器
old_listener = """    // 未登录点水火箭卡片 → 同样弹出登录框
    els.waterRocketCard.addEventListener('click', (event) => {
        if (signedInNow || !meta.enabled) return;
        event.preventDefault();
        pendingHref = '/water-rocket/';
        openModal('login');
    });"""
content = content.replace(old_listener, "    // 水火箭卡片已完全开源免登录，直接进入")

# 4. 移除 refresh() 中的 login=water-rocket 处理
content = content.replace("loginTarget === 'water-rocket'", "false")

with open('/var/www/portal/index.html', 'w', encoding='utf-8') as f:
    f.write(content)
print('Portal index.html updated: water rocket is now completely public.')
