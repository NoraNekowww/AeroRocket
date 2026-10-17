import shutil

shutil.copyfile('/var/www/portal/index.html', '/var/www/portal/index.html.bak')

with open('/var/www/portal/index.html', 'r', encoding='utf-8') as f:
    content = f.read()

card_snippet = """
        <!-- 项目 7: AeroRocket 水火箭弹道模拟系统 (需登录注册用户专享) -->
        <a href="/water-rocket/" class="card" id="card-water-rocket">
            <span class="badge locked" id="card-water-rocket-badge">需登录</span>
            <div>
                <div class="card-icon">🚀</div>
                <h2>AeroRocket 水火箭弹道</h2>
                <p>可乐瓶水火箭动力学仿真 APK：两相流喷射、ECMWF 实时全球风场同步、目标反推发射参数优化。注册用户专享下载。</p>
            </div>
            <span class="btn" id="card-water-rocket-btn">进入下载 &rarr;</span>
        </a>
"""

if 'id="card-water-rocket"' not in content:
    target_card = '        <a href="/esp32/" class="card" id="card-esp32">'
    idx = content.find(target_card)
    if idx != -1:
        end_idx = content.find('</a>', idx) + 4
        content = content[:end_idx] + card_snippet + content[end_idx:]

    target_els = "        esp32Btn: document.getElementById('card-esp32-btn'),"
    els_snippet = """        esp32Btn: document.getElementById('card-esp32-btn'),
        waterRocketCard: document.getElementById('card-water-rocket'),
        waterRocketBadge: document.getElementById('card-water-rocket-badge'),
        waterRocketBtn: document.getElementById('card-water-rocket-btn'),"""
    content = content.replace(target_els, els_snippet)

    target_render = "        // 管理员专属快捷方式: 必须已登录且 role=admin 才显示"
    render_snippet = """        // 水火箭卡片三态
        if (signedIn) {
            els.waterRocketBadge.textContent = '已授权 · 下载';
            els.waterRocketBadge.classList.remove('locked');
            els.waterRocketBtn.textContent = '进入下载 →';
        } else {
            els.waterRocketBadge.textContent = '需登录';
            els.waterRocketBadge.classList.add('locked');
            els.waterRocketBtn.textContent = '登录后下载 →';
        }

        // 管理员专属快捷方式: 必须已登录且 role=admin 才显示"""
    content = content.replace(target_render, render_snippet)

    target_refresh = """            if (new URLSearchParams(location.search).get('login') === 'esp32') {
                history.replaceState(null, '', location.pathname);
                if (meta.enabled) {
                    pendingHref = '/esp32/';
                    openModal('login');
                }
            }"""
    refresh_snippet = """            const loginTarget = new URLSearchParams(location.search).get('login');
            if (loginTarget === 'esp32' || loginTarget === 'water-rocket') {
                history.replaceState(null, '', location.pathname);
                if (meta.enabled) {
                    pendingHref = (loginTarget === 'esp32') ? '/esp32/' : '/water-rocket/';
                    openModal('login');
                }
            }"""
    content = content.replace(target_refresh, refresh_snippet)

    target_listener = """    // 未登录点 ESP32 卡片 → 打开登录框(打不开的页面就别让人进去看黑屏)
    els.esp32Card.addEventListener('click', (event) => {
        if (signedInNow || !meta.enabled) return;   // 已登录 / 账号系统没开 → 正常跳转
        event.preventDefault();
        pendingHref = '/esp32/';                    // 登录完直接进控制台, 而不是被扔去日程表
        openModal('login');
    });"""
    listener_snippet = """    // 未登录点 ESP32 卡片 → 打开登录框(打不开的页面就别让人进去看黑屏)
    els.esp32Card.addEventListener('click', (event) => {
        if (signedInNow || !meta.enabled) return;   // 已登录 / 账号系统没开 → 正常跳转
        event.preventDefault();
        pendingHref = '/esp32/';                    // 登录完直接进控制台, 而不是被扔去日程表
        openModal('login');
    });

    // 未登录点水火箭卡片 → 同样弹出登录框
    els.waterRocketCard.addEventListener('click', (event) => {
        if (signedInNow || !meta.enabled) return;
        event.preventDefault();
        pendingHref = '/water-rocket/';
        openModal('login');
    });"""
    content = content.replace(target_listener, listener_snippet)

    with open('/var/www/portal/index.html', 'w', encoding='utf-8') as f:
        f.write(content)
    print('Portal index.html updated successfully.')
else:
    print('Portal index.html already contains water-rocket card.')
