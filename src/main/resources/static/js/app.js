/* =====================================================================
   ResiHub 前端 · 原生 JS（无构建、无 CDN 依赖）
   路由：hash。数据：ResiHub 后端 /api 同源接口。
   ===================================================================== */

/* ------------------------------ 基础设施 ------------------------------ */
const API = {
    token: localStorage.getItem('rh_token') || '',
    setToken(t) { this.token = t || ''; localStorage.setItem('rh_token', this.token); },
    async call(method, path, opt = {}) {
        let url = path;
        if (opt.query) {
            const qs = new URLSearchParams();
            Object.entries(opt.query).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') qs.append(k, v); });
            const s = qs.toString();
            if (s) url += (url.includes('?') ? '&' : '?') + s;
        }
        const headers = {};
        if (this.token) headers.authorization = this.token;
        let body;
        if (opt.body) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opt.body); }
        const res = await fetch(url, { method, headers, body });
        if (res.status === 401) { API.setToken(''); throw new Error('登录已过期，请重新登录'); }
        if (res.status === 403) throw new Error('没有权限');
        const text = await res.text();
        let json;
        try { json = JSON.parse(text); } catch (e) { throw new Error('服务返回异常'); }
        if (json.success === false) throw new Error(json.errorMsg || '操作失败');
        return json;
    },
    get(p, q) { return this.call('GET', p, { query: q }); },
    post(p, b, q) { return this.call('POST', p, { body: b, query: q }); },
    put(p) { return this.call('PUT', p); }
};

const state = { me: null, route: 'home', param: null, cache: {} };

/* ------------------------------ 小工具 ------------------------------ */
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s === null || s === undefined ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const money = (cents) => {
    if (cents === null || cents === undefined) return '¥0';
    const v = cents / 100;
    return '¥' + (Number.isInteger(v) ? v : v.toFixed(2));
};

const GRADIENTS = [
    'linear-gradient(145deg,#14b88f,#0b7d61)',
    'linear-gradient(145deg,#7a63e8,#4b3fb0)',
    'linear-gradient(145deg,#f7b955,#e08a0a)',
    'linear-gradient(145deg,#f27d6b,#cf4535)',
    'linear-gradient(145deg,#4aa8f0,#1f6fb8)',
    'linear-gradient(145deg,#58c9a5,#2b9b7a)',
    'linear-gradient(145deg,#b98cf0,#7b4fc0)',
    'linear-gradient(145deg,#8fa1b3,#5d6f81)'
];
const gradOf = (seed) => {
    let h = 0;
    String(seed === undefined || seed === null ? 'x' : seed).split('').forEach(c => { h = (h * 31 + c.charCodeAt(0)) % 9973; });
    return GRADIENTS[h % GRADIENTS.length];
};
const thumb = (text, seed, cls) => {
    const t = String(text || '?').trim().slice(0, 1);
    return `<div class="thumb ${cls || ''}" style="background:${gradOf(seed)}">${esc(t)}</div>`;
};

const initial = (name) => {
    let s = String(name || 'R');
    ['业主', '管家', '维修工', '系统', '管理员'].forEach(p => {
        if (s.startsWith(p) && s.length > p.length) s = s.slice(p.length);
    });
    return s.slice(0, 1) || 'R';
};

const relTime = (s) => {
    if (!s) return '';
    const d = new Date(String(s).replace(/-/g, '/').replace('T', ' '));
    if (isNaN(d)) return '';
    const diff = (Date.now() - d.getTime()) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前';
    return `${d.getMonth() + 1}月${d.getDate()}日`;
};

const countdown = (s) => {
    if (!s) return '';
    const d = new Date(String(s).replace(/-/g, '/').replace('T', ' '));
    if (isNaN(d)) return '';
    let ms = d.getTime() - Date.now();
    if (ms <= 0) return '已截止';
    const day = Math.floor(ms / 86400000); ms -= day * 86400000;
    const h = Math.floor(ms / 3600000); ms -= h * 3600000;
    const m = Math.floor(ms / 60000);
    if (day > 0) return `${day} 天 ${h} 小时`;
    if (h > 0) return `${h} 小时 ${m} 分`;
    return `${m} 分钟`;
};

function toast(msg, type) {
    const el = document.createElement('div');
    el.className = 'toast' + (type ? ' toast--' + type : '');
    el.textContent = msg;
    $('#toasts').appendChild(el);
    setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s'; }, 2200);
    setTimeout(() => el.remove(), 2600);
}

/* ------------------------------ 图标 ------------------------------ */
const ICON = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
    cart: '<circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.4 12h11L21 7H6"/>',
    wrench: '<path d="M14.7 6.3a4 4 0 1 0 5 5L21 21l-3 .5-9.7-9.8a4 4 0 0 1-5-5L6.3 3z"/>',
    chat: '<path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-6a8 8 0 1 1 18-5z"/>',
    user: '<circle cx="12" cy="8" r="3.6"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    bell: '<path d="M18 15V10a6 6 0 1 0-12 0v5l-2 3h16z"/><path d="M10 21h4"/>',
    key: '<circle cx="8" cy="8" r="4.2"/><path d="M11 11l9 9M17 17l2 2M15 15l2 2"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 11h18"/>',
    star: '<path d="m12 3 2.7 5.8 6.3.8-4.6 4.4 1.2 6.3L12 17.4 6.4 20.3l1.2-6.3L3 9.6l6.3-.8z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    heart: '<path d="M12 20s-7-4.5-7-9.4A4.1 4.1 0 0 1 12 7.6 4.1 4.1 0 0 1 19 10.6C19 15.5 12 20 12 20z"/>',
    check: '<path d="m5 13 4.5 4.5L19 7"/>',
    x: '<path d="M6 6l12 12M18 6 6 18"/>',
    chevron: '<path d="m9 6 6 6-6 6"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    pin: '<path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/>',
    logout: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="M10 8 6 12l4 4M6 12h9"/>'
};
const icon = (name, size) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
    stroke-linecap="round" stroke-linejoin="round" ${size ? `style="width:${size}px;height:${size}px"` : ''}>${ICON[name] || ''}</svg>`;

/* ------------------------------ 状态语义 ------------------------------ */
const ORDER_STATUS = {
    0: ['待成团', 'pill--wait'], 1: ['待自提', 'pill--ok'], 2: ['已完成', 'pill--grey'],
    3: ['已退款', 'pill--danger'], 4: ['已取消', 'pill--grey']
};
const WORK_STATUS = {
    0: ['待接单', 'pill--wait'], 1: ['处理中', 'pill--info'], 2: ['待确认', 'pill--ok'],
    3: ['已完成', 'pill--grey'], 4: ['已升级', 'pill--danger'], 5: ['已取消', 'pill--grey']
};
const NOTICE_TYPE = { 0: ['公告', 'pill--grey'], 1: ['停水停电', 'pill--wait'], 2: ['活动', 'pill--ok'], 3: ['紧急', 'pill--danger'] };
const GROUP_STATUS = { 0: ['进行中', 'pill--ok'], 1: ['已成团', 'pill--info'], 2: ['已流团', 'pill--danger'], 3: ['已退款', 'pill--grey'] };
const pill = (map, key) => {
    const v = map[key] || ['未知', 'pill--grey'];
    return `<span class="pill ${v[1]}">${v[0]}</span>`;
};

const roleName = (r) => ({ 0: '居民', 1: '维修工', 2: '管家', 3: '管理员' }[r] || '居民');

/* ------------------------------ 空状态 / 骨架 ------------------------------ */
const emptyState = (text) => `<div class="empty">${icon('chat')}<p>${esc(text)}</p></div>`;
const skeleton = (n) => `<div class="skeleton">${'<div class="sk-line"></div><div class="sk-card"></div>'.repeat(n || 2)}</div>`;

/* ------------------------------ 登录 ------------------------------ */
function renderLogin(msg) {
    document.getElementById('app').innerHTML = `
    <div class="login-wrap">
      <div class="login-card">
        <div class="login-hero">
          <div class="mark">R</div>
          <h1>ResiHub 小区生活</h1>
          <p>报修 · 团购 · 邻里圈 · 访客通行，一个入口搞定</p>
        </div>
        ${msg ? `<div class="pill pill--danger" style="margin-bottom:14px">${esc(msg)}</div>` : ''}
        <div class="field">
          <label>手机号</label>
          <input class="input" id="phone" placeholder="请输入 11 位手机号" maxlength="11" inputmode="numeric">
        </div>
        <div class="field">
          <label>验证码</label>
          <div class="input-group">
            <input class="input" id="code" placeholder="6 位验证码" maxlength="6" inputmode="numeric">
            <button class="btn" id="btn-code">获取验证码</button>
          </div>
        </div>
        <button class="btn btn--primary btn--block" id="btn-login" style="margin-top:6px">登录 / 注册</button>
        <div class="sec-head" style="margin:22px 0 8px">
          <span class="kicker">Demo</span>
          <h2 style="font-size:13px">一键体验不同角色</h2>
        </div>
        <div class="demo-accounts">
          <button data-phone="13900000004">居民 · 张三</button>
          <button data-phone="13900000003">维修工 · 老李</button>
          <button data-phone="13900000002">管家 · 王姐</button>
          <button data-phone="13900000001">管理员</button>
        </div>
      </div>
    </div>`;

    $('#btn-code').onclick = async () => {
        const phone = $('#phone').value.trim();
        if (!/^1[3-9]\d{9}$/.test(phone)) return toast('手机号格式不对', 'err');
        try {
            await API.post('/user/code', null, { phone });
            const peek = await API.get('/user/code/peek', { phone });
            $('#code').value = peek.data;
            toast('验证码已自动填入（演示模式）', 'ok');
        } catch (e) { toast(e.message, 'err'); }
    };

    $('#btn-login').onclick = () => doLogin($('#phone').value.trim(), $('#code').value.trim());
    document.querySelectorAll('.demo-accounts button').forEach(b => {
        b.onclick = () => doLogin(b.dataset.phone, null);
    });
}

async function doLogin(phone, code) {
    if (!/^1[3-9]\d{9}$/.test(phone)) return toast('手机号格式不对', 'err');
    try {
        let c = code;
        if (!c) {
            await API.post('/user/code', null, { phone });
            c = (await API.get('/user/code/peek', { phone })).data;
        }
        const res = await API.post('/user/login', { phone, code: c });
        API.setToken(res.data);
        state.me = (await API.get('/user/me')).data;
        toast('欢迎回来，' + state.me.nickName, 'ok');
        location.hash = '#/home';
        render();
    } catch (e) { toast(e.message, 'err'); }
}

/* ------------------------------ 外壳 ------------------------------ */
const NAV = [
    ['home', '首页'], ['group', '团购'], ['orders', '订单'], ['repair', '报修'],
    ['circle', '邻里圈'], ['activity', '活动'], ['visit', '访客'], ['me', '我的']
];
const TABS = [
    ['home', '首页', 'home'], ['group', '团购', 'cart'], ['repair', '报修', 'wrench'],
    ['circle', '邻里圈', 'chat'], ['me', '我的', 'user']
];
const ROUTE_ALIAS = { notice: 'home', 'group-detail': 'group' };

function shell(inner) {
    const me = state.me;
    const active = ROUTE_ALIAS[state.route] || state.route;
    if (!me) { renderLogin(); return; }
    document.getElementById('app').innerHTML = `
    <div class="app">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark">R</div>
          <div class="brand-text"><b>ResiHub</b><span>${esc(me.building || '未认证')} · ${roleName(me.role)}</span></div>
        </div>
        <div class="topbar-right">
          <span class="chip chip--points" title="我的积分">☀ <span class="num">${me.points || 0}</span></span>
          <span class="chip chip--ghost" id="chip-notice" title="未读公告" style="cursor:pointer">
            ${icon('bell')} <span class="num" id="notice-badge">0</span>
          </span>
          <div class="avatar" id="avatar">${esc(initial(me.nickName))}</div>
        </div>
      </header>
      <nav class="nav">
        ${NAV.map(([k, label]) => `<a href="#/${k}" class="${active === k ? 'on' : ''}">${label}</a>`).join('')}
      </nav>
      ${inner}
    </div>
    <div class="tabbar"><div class="tabbar-inner">
      ${TABS.map(([k, label, ic]) => `<a href="#/${k}" class="${active === k ? 'on' : ''}">${icon(ic)}<span>${label}</span></a>`).join('')}
    </div></div>`;

    $('#avatar').onclick = () => { location.hash = '#/me'; };
    const chipNotice = $('#chip-notice');
    if (chipNotice) chipNotice.onclick = () => { location.hash = '#/notice'; };
    refreshNoticeBadge();
}

async function refreshNoticeBadge() {
    try {
        const r = await API.get('/notice/unread');
        const el = $('#notice-badge');
        if (el) el.textContent = r.data || 0;
    } catch (e) { /* 忽略 */ }
}

/* ------------------------------ 首页 ------------------------------ */
async function viewHome() {
    const me = state.me;
    shell(`<div class="sec"><div class="skeleton"><div class="sk-card"></div><div class="sk-line"></div></div></div>`);
    try {
        const [notices, posts, orders, works, gb] = await Promise.all([
            API.get('/notice/list'), API.get('/post/hot'), API.get('/order/my'),
            API.get('/work-order/my'), API.get('/group-buy/list')
        ]);
        const myOrders = orders.data || [];
        const todoPickup = myOrders.filter(o => o.status === 1).length;
        const todoWork = (works.data || []).filter(w => w.status === 0 || w.status === 1 || w.status === 2).length;
        const openGroups = (gb.data || []).filter(g => g.status === 0).length;
        const noticeList = notices.data || [];
        const hotPosts = (posts.data || []).slice(0, 2);

        const services = [
            ['repair', '报修', 'wrench', 'linear-gradient(145deg,#14b88f,#0b7d61)'],
            ['group', '社区团购', 'cart', 'linear-gradient(145deg,#f7b955,#e08a0a)'],
            ['orders', '我的订单', 'check', 'linear-gradient(145deg,#4aa8f0,#1f6fb8)'],
            ['circle', '邻里圈', 'chat', 'linear-gradient(145deg,#7a63e8,#4b3fb0)'],
            ['activity', '社区活动', 'calendar', 'linear-gradient(145deg,#f27d6b,#cf4535)'],
            ['visit', '访客通行', 'key', 'linear-gradient(145deg,#58c9a5,#2b9b7a)'],
            ['me', '积分签到', 'star', 'linear-gradient(145deg,#b98cf0,#7b4fc0)'],
            ['notice', '社区公告', 'bell', 'linear-gradient(145deg,#8fa1b3,#5d6f81)']
        ];

        shell(`
        <section class="sec">
          <div class="card" style="background:linear-gradient(135deg,rgba(15,157,122,.10),rgba(110,86,207,.08)),var(--surface)">
            <div class="row row--between" style="align-items:flex-start">
              <div>
                <div class="tiny muted" style="letter-spacing:.14em;text-transform:uppercase;font-weight:700">Good day</div>
                <h2 style="font-size:20px;margin-top:2px">${esc(me.nickName)}，今天有什么要办的？</h2>
                <div class="small muted" style="margin-top:4px">
                  ${me.communityId ? '已认证业主' : '尚未选择小区'} · ${esc(me.building || '-')} ${esc(me.room || '')}
                </div>
              </div>
              <span class="pill ${me.audit === 1 ? 'pill--ok' : 'pill--wait'}">${me.audit === 1 ? '已认证' : '待认证'}</span>
            </div>
            <div class="row" style="margin-top:16px;gap:26px">
              <div class="stat"><b class="num">${todoPickup}</b><span>待自提</span></div>
              <div class="stat"><b class="num">${todoWork}</b><span>进行中工单</span></div>
              <div class="stat"><b class="num">${openGroups}</b><span>进行中团购</span></div>
            </div>
          </div>
        </section>

        <section class="sec">
          <div class="sec-head"><span class="kicker">Services</span><h2>社区服务</h2></div>
          <div class="tiles">
            ${services.map(([k, label, ic, g]) => `
              <a class="tile" href="#/${k}">
                <div class="tile-ic" style="background:${g}">${icon(ic)}</div>
                <span>${label}</span>
              </a>`).join('')}
          </div>
        </section>

        <section class="sec">
          <div class="sec-head"><span class="kicker">Notice</span><h2>社区公告</h2>
            <a class="more" href="#/notice">全部 ›</a></div>
          <div class="card card--flat" style="padding:10px">
            ${noticeList.length === 0 ? emptyState('暂无公告') : noticeList.slice(0, 3).map(n => `
              <a class="notice-line ${n.readFlag ? '' : 'unread'}" href="#/notice">
                <span class="tag">${pill(NOTICE_TYPE, n.type)}</span>
                <span class="t">${esc(n.title)}</span>
                ${n.readFlag ? '' : '<i class="dot"></i>'}
                <span class="tiny muted">${relTime(n.createTime)}</span>
              </a>`).join('')}
          </div>
        </section>

        <section class="sec">
          <div class="sec-head"><span class="kicker">Feed</span><h2>邻里热门</h2>
            <a class="more" href="#/circle">进圈子 ›</a></div>
          <div class="grid grid--2">
            ${hotPosts.length === 0 ? emptyState('还没有人发帖') : hotPosts.map(p => `
              <a class="card card--hover" href="#/circle">
                <div class="row" style="align-items:flex-start;gap:12px">
                  ${thumb(p.title, p.id, 'thumb--sm')}
                  <div class="item-main">
                    <div class="item-title">${esc(p.title)}</div>
                    <div class="item-sub">${esc(p.userName || '邻居')} · ${esc(p.category || '分享')}</div>
                    <div class="small muted" style="margin-top:6px">♥ <span class="num">${p.liked || 0}</span> · 评论 <span class="num">${p.comments || 0}</span></div>
                  </div>
                </div>
              </a>`).join('')}
          </div>
        </section>`);
    } catch (e) {
        shell(`<div class="sec">${emptyState(e.message)}</div>`);
    }
}

/* ------------------------------ 公告 ------------------------------ */
async function viewNotice() {
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const r = await API.get('/notice/list');
        const list = r.data || [];
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Notice</span><h2>全部公告</h2>
            <button class="btn btn--sm" id="btn-readall" style="margin-left:auto">全部已读</button></div>
          ${list.length === 0 ? emptyState('暂无公告') : list.map(n => `
            <div class="card card--hover card--flat" data-notice="${n.id}" style="margin-bottom:10px;cursor:pointer">
              <div class="row row--between">
                <div class="row" style="gap:8px">${pill(NOTICE_TYPE, n.type)}${n.top ? '<span class="pill pill--info">置顶</span>' : ''}
                  ${n.readFlag ? '' : '<i class="dot"></i>'}</div>
                <span class="tiny muted">${relTime(n.createTime)}</span>
              </div>
              <h3 style="font-size:15px;margin:9px 0 5px">${esc(n.title)}</h3>
              <p class="small muted" style="display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(n.content)}</p>
              <div class="tiny muted" style="margin-top:8px">${esc(n.publisherName || '物业')}${n.buildingTarget ? ' · 定向 ' + esc(n.buildingTarget) : ' · 全小区'}</div>
            </div>`).join('')}
          ${(state.me.role >= 2) ? `<button class="btn btn--primary btn--block" id="btn-publish">发布公告</button>` : ''}
        </section>`);

        document.querySelectorAll('[data-notice]').forEach(el => {
            el.onclick = () => openNotice(el.dataset.notice);
        });
        const ra = $('#btn-readall');
        if (ra) ra.onclick = async () => {
            try { await API.post('/notice/read-all'); toast('已全部标记为已读', 'ok'); viewNotice(); } catch (e) { toast(e.message, 'err'); }
        };
        const pb = $('#btn-publish');
        if (pb) pb.onclick = openPublishNotice;
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

async function openNotice(id) {
    try {
        const r = await API.get('/notice/' + id);
        const n = r.data;
        sheet(`<h3>${esc(n.title)}</h3>
          <div class="row" style="gap:8px;margin:10px 0">${pill(NOTICE_TYPE, n.type)}
            <span class="tiny muted">${esc(n.publisherName || '物业')} · ${relTime(n.createTime)}</span></div>
          <p style="white-space:pre-wrap;color:var(--ink-2)">${esc(n.content)}</p>`);
        refreshNoticeBadge();
    } catch (e) { toast(e.message, 'err'); }
}

function openPublishNotice() {
    sheet(`<h3>发布公告</h3>
      <div class="field" style="margin-top:14px"><label>标题</label><input class="input" id="f-title" placeholder="例如：3 幢计划停水通知"></div>
      <div class="field"><label>类型</label>
        <select class="select" id="f-type">
          <option value="0">普通公告</option><option value="1">停水停电</option>
          <option value="2">社区活动</option><option value="3">紧急</option>
        </select></div>
      <div class="field"><label>定向楼栋（留空=全小区）</label><input class="input" id="f-building" placeholder="例如 3幢"></div>
      <div class="field"><label>正文</label><textarea class="textarea" id="f-content" placeholder="写清楚时间、范围和注意事项"></textarea></div>
      <button class="btn btn--primary btn--block" id="f-submit">发布</button>`, () => {
        $('#f-submit').onclick = async () => {
            try {
                await API.post('/notice', {
                    title: $('#f-title').value.trim(), type: Number($('#f-type').value),
                    buildingTarget: $('#f-building').value.trim() || null, content: $('#f-content').value.trim()
                });
                closeSheet(); toast('公告已发布', 'ok'); viewNotice();
            } catch (e) { toast(e.message, 'err'); }
        };
    });
}

/* ------------------------------ 团购 ------------------------------ */
async function viewGroup() {
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const [gb, products] = await Promise.all([API.get('/group-buy/list'), API.get('/product/list')]);
        state.cache.products = products.data || [];
        const list = gb.data || [];
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Group buy</span><h2>社区团购</h2>
            <button class="btn btn--sm btn--primary" id="btn-open" style="margin-left:auto">我要开团</button></div>
          ${list.length === 0 ? emptyState('本小区暂时没有团购，试试自己开一个') : `<div class="grid grid--2">${list.map(g => groupCard(g)).join('')}</div>`}
        </section>`);
        $('#btn-open').onclick = openCreateGroup;
        document.querySelectorAll('[data-join]').forEach(el => {
            el.onclick = (ev) => { ev.stopPropagation(); joinGroup(el.dataset.join); };
        });
        document.querySelectorAll('[data-gb]').forEach(el => {
            el.onclick = () => openGroupDetail(el.dataset.gb);
        });
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

function groupCard(g) {
    const p = g.product || {};
    const pct = Math.min(100, Math.round((g.currentUsers || 0) / Math.max(1, g.minUsers || 1) * 100));
    const joined = g.joined;
    const closed = g.status !== 0;
    return `
    <div class="card card--hover" data-gb="${g.id}" style="cursor:pointer">
      <div class="row" style="align-items:flex-start;gap:14px">
        ${thumb(p.name || '团', g.id)}
        <div class="item-main">
          <div class="row row--between" style="gap:8px">
            <div class="item-title">${esc(p.name || '团购商品')}</div>
            ${pill(GROUP_STATUS, g.status)}
          </div>
          <div class="item-sub">${esc(p.subTitle || '')}</div>
          <div class="row" style="gap:8px;margin-top:6px">
            <span class="price num">${money(p.price)}</span>
            <span class="strike num">${money(p.originPrice)}</span>
            <span class="tiny muted">/${esc(p.unit || '份')}</span>
          </div>
        </div>
      </div>
      <div class="progress ${pct >= 100 ? '' : 'progress--amber'}" style="margin:14px 0 8px"><i style="width:${pct}%"></i></div>
      <div class="row row--between small">
        <span class="muted">已参团 <b class="num" style="color:var(--ink)">${g.currentUsers || 0}</b> / ${g.minUsers} 人成团</span>
        <span class="muted">${closed ? '' : '剩余 ' + countdown(g.deadline)}</span>
      </div>
      <div class="row row--between" style="margin-top:12px">
        <span class="tiny muted">${icon('pin', 13)} ${esc(g.pickupPoint || '')} · 团长 ${esc(g.leaderName || '邻居')}</span>
        ${joined
            ? '<span class="pill pill--ok">已参团</span>'
            : closed
                ? `<span class="pill pill--grey">${GROUP_STATUS[g.status][0]}</span>`
                : `<button class="btn btn--primary btn--sm" data-join="${g.id}">立即参团</button>`}
      </div>
    </div>`;
}

async function joinGroup(id) {
    try {
        const r = await API.post(`/group-buy/${id}/join`);
        toast('参团成功，订单 ' + String(r.data).slice(-6), 'ok');
        setTimeout(viewGroup, 700);
    } catch (e) { toast(e.message, 'err'); }
}

async function openGroupDetail(id) {
    try {
        const r = await API.get('/group-buy/' + id);
        const g = r.data; const p = g.product || {};
        const pct = Math.min(100, Math.round((g.currentUsers || 0) / Math.max(1, g.minUsers) * 100));
        sheet(`
          <div class="row" style="gap:14px;align-items:flex-start">
            ${thumb(p.name, g.id)}
            <div class="item-main">
              <h3>${esc(p.name)}</h3>
              <div class="item-sub">${esc(p.subTitle || '')}</div>
              <div class="row" style="gap:8px;margin-top:6px">
                <span class="price num" style="font-size:18px">${money(p.price)}</span>
                <span class="strike num">${money(p.originPrice)}</span>
              </div>
            </div>
          </div>
          <div class="card card--flat" style="margin:16px 0;background:#f8fafb">
            <div class="row row--between small"><span class="muted">成团进度</span>
              <span class="num"><b>${g.currentUsers || 0}</b> / ${g.minUsers} 人</span></div>
            <div class="progress" style="margin:10px 0 12px"><i style="width:${pct}%"></i></div>
            <div class="row row--between small muted">
              <span>${icon('clock', 13)} 截止 ${String(g.deadline || '').replace('T', ' ').slice(5, 16)}</span>
              <span>${g.status === 0 ? '剩余 ' + countdown(g.deadline) : GROUP_STATUS[g.status][0]}</span>
            </div>
          </div>
          <div class="row row--between small" style="margin-bottom:8px"><span class="muted">自提点</span><span>${esc(g.pickupPoint || '-')}</span></div>
          <div class="row row--between small" style="margin-bottom:8px"><span class="muted">团长</span><span>${esc(g.leaderName || '邻居')}</span></div>
          <div class="row row--between small" style="margin-bottom:16px"><span class="muted">剩余名额</span><span class="num">${p.stock !== undefined ? p.stock : '-'} 份</span></div>
          ${g.status === 0 && !g.joined ? `<button class="btn btn--primary btn--block" id="d-join">立即参团</button>` : ''}
          ${g.joined ? '<div class="pill pill--ok">你已参团，去「订单」查看自提码</div>' : ''}
        `, () => {
            const b = $('#d-join');
            if (b) b.onclick = () => { closeSheet(); joinGroup(id); };
        });
    } catch (e) { toast(e.message, 'err'); }
}

function openCreateGroup() {
    const products = state.cache.products || [];
    sheet(`<h3>发起团购</h3>
      <div class="field" style="margin-top:14px"><label>选择商品</label>
        <select class="select" id="g-product">
          ${products.map(p => `<option value="${p.id}">${esc(p.name)} · ${money(p.price)}（库存 ${p.stock}）</option>`).join('')}
        </select></div>
      <div class="field"><label>成团人数</label><input class="input" id="g-min" type="number" value="5" min="1"></div>
      <div class="field"><label>自提点</label><input class="input" id="g-point" value="小区东门门卫室"></div>
      <div class="field"><label>截团时间（小时）</label><input class="input" id="g-hours" type="number" value="24" min="1"></div>
      <button class="btn btn--primary btn--block" id="g-submit">开团</button>`, () => {
        $('#g-submit').onclick = async () => {
            try {
                await API.post('/group-buy', null, {
                    productId: $('#g-product').value, minUsers: $('#g-min').value,
                    pickupPoint: $('#g-point').value.trim(), hours: $('#g-hours').value
                });
                closeSheet(); toast('开团成功，快去邀请邻居', 'ok'); viewGroup();
            } catch (e) { toast(e.message, 'err'); }
        };
    });
}

/* ------------------------------ 订单 ------------------------------ */
async function viewOrders() {
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const r = await API.get('/order/my');
        const list = r.data || [];
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Orders</span><h2>我的团购订单</h2></div>
          ${list.length === 0 ? emptyState('还没有订单，去团购看看') : list.map(o => `
            <div class="card card--flat" style="margin-bottom:12px">
              <div class="row row--between">
                <div class="row" style="gap:10px">${thumb(o.productName, o.productId, 'thumb--sm')}
                  <div><div class="item-title">${esc(o.productName || '团购商品')}</div>
                    <div class="tiny muted">订单号 ${esc(o.orderNo)}</div></div></div>
                ${pill(ORDER_STATUS, o.status)}
              </div>
              <div class="row row--between" style="margin-top:12px">
                <span class="price num">${money(o.amount)}</span>
                <span class="tiny muted">${icon('pin', 13)} ${esc(o.pickupPoint || '')}</span>
              </div>
              ${o.status === 1 ? `
                <div class="row row--between" style="margin-top:14px;gap:10px;flex-wrap:wrap">
                  <div class="code-box num">${esc(o.pickupCode || '')}</div>
                  <button class="btn btn--sm" data-verify="${esc(o.orderNo)}">核销自提</button>
                </div>
                <div class="tiny muted" style="margin-top:8px">到自提点出示这 6 位数字，团长或管家核销后订单完成</div>` : ''}
              ${o.status === 3 ? '<div class="tiny muted" style="margin-top:10px">未成团，金额已原路退回</div>' : ''}
            </div>`).join('')}
        </section>`);
        document.querySelectorAll('[data-verify]').forEach(b => {
            b.onclick = () => verifyOrder(b.dataset.verify);
        });
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

async function verifyOrder(orderNo) {
    if (!confirm('确认核销这笔自提订单？')) return;
    try { await API.post(`/order/${orderNo}/verify`); toast('核销成功', 'ok'); viewOrders(); }
    catch (e) { toast(e.message, 'err'); }
}

/* ------------------------------ 报修 ------------------------------ */
async function viewRepair() {
    const role = state.me.role;
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const tasks = [API.get('/work-order/my')];
        if (role >= 1) tasks.push(API.get('/work-order/pool'));
        if (role >= 1) tasks.push(API.get('/work-order/assigned'));
        if (role >= 2) tasks.push(API.get('/work-order/list'));
        const res = await Promise.all(tasks);
        const mine = res[0].data || [];
        const pool = role >= 1 ? (res[1].data || []) : [];
        const assigned = role >= 1 ? (res[2].data || []) : [];
        const all = role >= 2 ? (res[3].data || []) : [];

        const blocks = [];

        if (role === 0) {
            blocks.push(`
            <section class="sec">
              <div class="sec-head"><span class="kicker">New</span><h2>我要报修</h2></div>
              <div class="card">
                <div class="field"><label>报修类型</label>
                  <select class="select" id="w-type">
                    <option>水电</option><option>门窗</option><option>家电</option>
                    <option>公共设施</option><option>其他</option>
                  </select></div>
                <div class="field"><label>问题描述</label>
                  <textarea class="textarea" id="w-desc" placeholder="例如：厨房水槽下水道堵了，水下不去"></textarea></div>
                <button class="btn btn--primary btn--block" id="w-submit">提交报修</button>
                <div class="tiny muted" style="margin-top:10px">提交后 30 分钟无人接单会自动升级给管家</div>
              </div>
            </section>`);
        }

        blocks.push(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">${role === 0 ? 'Mine' : 'My tasks'}</span>
            <h2>${role === 0 ? '我的报修' : '我接的工单'}</h2></div>
          ${(() => {
            const list = role === 0 ? mine : assigned;
            if (!list.length) return emptyState(role === 0 ? '还没有报修记录' : '还没抢过单');
            return list.map(w => workCard(w, role === 0 ? 'user' : 'worker')).join('');
          })()}
        </section>`);

        if (role >= 1) {
            blocks.push(`
            <section class="sec">
              <div class="sec-head"><span class="kicker">Pool</span><h2>待接单工单池</h2>
                <span class="pill pill--wait" style="margin-left:auto">${pool.length} 单</span></div>
              ${pool.length === 0 ? emptyState('暂时没有待接单的工单') : pool.map(w => workCard(w, 'pool')).join('')}
            </section>`);
        }

        if (role >= 2) {
            blocks.push(`
            <section class="sec">
              <div class="sec-head"><span class="kicker">Board</span><h2>工单看板</h2></div>
              <div class="card card--flat" style="padding:8px">
                ${all.length === 0 ? emptyState('暂无工单') : all.slice(0, 20).map(w => `
                  <div class="item">
                    <div class="item-main">
                      <div class="row row--between"><div class="item-title">${esc(w.type)} · ${esc(w.description)}</div>
                        ${pill(WORK_STATUS, w.status)}</div>
                      <div class="item-sub">${esc(w.userName || '')} 报修 · ${relTime(w.createTime)}
                        ${w.assigneeName ? ' · 处理人 ' + esc(w.assigneeName) : ''}</div>
                    </div>
                  </div>`).join('')}
              </div>
            </section>`);
        }

        shell(blocks.join(''));
        const ws = $('#w-submit');
        if (ws) ws.onclick = async () => {
            try {
                await API.post('/work-order', { type: $('#w-type').value, description: $('#w-desc').value.trim(), images: '' });
                toast('报修已提交', 'ok'); viewRepair();
            } catch (e) { toast(e.message, 'err'); }
        };
        document.querySelectorAll('[data-accept]').forEach(b => {
            b.onclick = async () => {
                try { await API.post(`/work-order/${b.dataset.accept}/accept`); toast('抢单成功', 'ok'); viewRepair(); }
                catch (e) { toast(e.message, 'err'); }
            };
        });
        document.querySelectorAll('[data-finish]').forEach(b => {
            b.onclick = async () => {
                try { await API.post(`/work-order/${b.dataset.finish}/finish`); toast('已提交完工', 'ok'); viewRepair(); }
                catch (e) { toast(e.message, 'err'); }
            };
        });
        document.querySelectorAll('[data-rate]').forEach(b => {
            b.onclick = () => rateWork(b.dataset.rate);
        });
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

function workCard(w, mode) {
    const actions = [];
    if (mode === 'pool') actions.push(`<button class="btn btn--primary btn--sm" data-accept="${w.id}">抢单</button>`);
    if (mode === 'worker' && w.status === 1) actions.push(`<button class="btn btn--sm" data-finish="${w.id}">标记完工</button>`);
    if (mode === 'user' && w.status === 2) actions.push(`<button class="btn btn--primary btn--sm" data-rate="${w.id}">确认并评价</button>`);
    return `
    <div class="card card--flat" style="margin-bottom:10px">
      <div class="row row--between">
        <div class="row" style="gap:10px">
          <div class="tile-ic" style="width:34px;height:34px;border-radius:11px;background:${gradOf(w.type)};font-size:0">
            ${icon('wrench', 16)}</div>
          <div>
            <div class="item-title">${esc(w.type)} · ${esc(w.description)}</div>
            <div class="tiny muted">单号 ${w.id} · ${relTime(w.createTime)}${w.assigneeName ? ' · ' + esc(w.assigneeName) : ''}</div>
          </div>
        </div>
        <div class="row" style="gap:8px;flex:0 0 auto">${pill(WORK_STATUS, w.status)}${actions.join('')}</div>
      </div>
      ${w.rating ? `<div class="small muted" style="margin-top:10px">评分 ${'★'.repeat(w.rating)}${'☆'.repeat(5 - w.rating)} ${esc(w.comment || '')}</div>` : ''}
    </div>`;
}

function rateWork(id) {
    sheet(`<h3>确认完工并评价</h3>
      <div class="field" style="margin-top:16px"><label>满意度</label>
        <select class="select" id="r-star">
          <option value="5">★★★★★ 非常满意</option><option value="4">★★★★ 满意</option>
          <option value="3">★★★ 一般</option><option value="2">★★ 不满意</option><option value="1">★ 很不满意</option>
        </select></div>
      <div class="field"><label>评价内容</label><input class="input" id="r-comment" placeholder="师傅上门很快…"></div>
      <button class="btn btn--primary btn--block" id="r-submit">提交评价（+5 积分）</button>`, () => {
        $('#r-submit').onclick = async () => {
            try {
                await API.post(`/work-order/${id}/rate`, null,
                    { rating: $('#r-star').value, comment: $('#r-comment').value.trim() });
                closeSheet(); toast('评价成功，积分 +5', 'ok'); refreshMe(); viewRepair();
            } catch (e) { toast(e.message, 'err'); }
        };
    });
}

/* ------------------------------ 邻里圈 ------------------------------ */
async function viewCircle() {
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const r = await API.get('/post/list');
        const list = r.data || [];
        const cats = ['全部', '闲置', '求助', '分享', '拼车'];
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Neighbors</span><h2>邻里圈</h2>
            <button class="btn btn--sm btn--primary" id="btn-post" style="margin-left:auto">发动态</button></div>
          <div class="row wrap" style="gap:8px;margin-bottom:14px">
            ${cats.map(c => `<button class="btn btn--sm ${c === '全部' ? 'btn--dark' : 'btn--ghost'}" data-cat="${c}">${c}</button>`).join('')}
          </div>
          ${list.length === 0 ? emptyState('还没有动态，发一条打个招呼') : list.map(p => postCard(p)).join('')}
        </section>`);

        $('#btn-post').onclick = openCreatePost;
        document.querySelectorAll('[data-cat]').forEach(b => {
            b.onclick = async () => {
                const cat = b.dataset.cat;
                const rr = await API.get('/post/list', cat === '全部' ? {} : { category: cat });
                document.querySelectorAll('[data-cat]').forEach(x => {
                    x.className = 'btn btn--sm ' + (x.dataset.cat === cat ? 'btn--dark' : 'btn--ghost');
                });
                const wrap = b.closest('.sec');
                wrap.querySelectorAll('.post-item').forEach(e => e.remove());
                const html = (rr.data || []).map(p => postCard(p)).join('') || emptyState('这个分类下还没有内容');
                wrap.insertAdjacentHTML('beforeend', html);
                bindPostActions();
            };
        });
        bindPostActions();
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

function postCard(p) {
    return `
    <div class="card card--flat post-item" style="margin-bottom:12px">
      <div class="row" style="gap:10px">
        <div class="avatar" style="width:32px;height:32px;font-size:11px;background:${gradOf(p.userName || p.userId)}">
          ${esc(initial(p.userName))}</div>
        <div><div class="item-title" style="font-size:13.5px">${esc(p.userName || '邻居')}</div>
          <div class="tiny muted">${relTime(p.createTime)} · ${esc(p.category || '分享')}</div></div>
        <span class="pill pill--grey" style="margin-left:auto">${esc(p.category || '分享')}</span>
      </div>
      <h3 style="font-size:15px;margin:12px 0 6px">${esc(p.title)}</h3>
      ${p.content ? `<p class="small muted" style="white-space:pre-wrap">${esc(p.content)}</p>` : ''}
      <div class="row" style="gap:16px;margin-top:14px">
        <button class="btn btn--sm btn--ghost" data-like="${p.id}" style="${p.isLike ? 'color:var(--rose);border-color:rgba(229,72,77,.4)' : ''}">
          ♥ <span class="num">${p.liked || 0}</span></button>
        <button class="btn btn--sm btn--ghost" data-comment="${p.id}">💬 <span class="num">${p.comments || 0}</span></button>
      </div>
    </div>`;
}

function bindPostActions() {
    document.querySelectorAll('[data-like]').forEach(b => {
        b.onclick = async () => {
            try {
                const r = await API.put('/post/like/' + b.dataset.like);
                const span = b.querySelector('span');
                const liked = !b.style.color;
                span.textContent = Number(span.textContent) + (liked ? 1 : -1);
                b.style.color = liked ? 'var(--rose)' : '';
                b.style.borderColor = liked ? 'rgba(229,72,77,.4)' : '';
            } catch (e) { toast(e.message, 'err'); }
        };
    });
    document.querySelectorAll('[data-comment]').forEach(b => {
        b.onclick = () => openComments(b.dataset.comment);
    });
}

async function openComments(postId) {
    try {
        const r = await API.get(`/post/${postId}/comments`);
        const list = r.data || [];
        sheet(`<h3>评论 ${list.length ? '(' + list.length + ')' : ''}</h3>
          <div style="margin:14px 0">
            ${list.length === 0 ? emptyState('还没有评论，说两句') : list.map(c => `
              <div class="item" style="padding:10px 0">
                <div class="avatar" style="width:28px;height:28px;font-size:10px;background:${gradOf(c.userName || c.userId)}">
                  ${esc(initial(c.userName))}</div>
                <div class="item-main"><div class="small strong">${esc(c.userName || '邻居')}</div>
                  <div class="small muted">${esc(c.content)}</div>
                  <div class="tiny muted">${relTime(c.createTime)}</div></div>
              </div>`).join('')}
          </div>
          <div class="input-group">
            <input class="input" id="c-input" placeholder="友善评论，和睦邻里">
            <button class="btn btn--primary" id="c-send">发送</button>
          </div>`, () => {
            $('#c-send').onclick = async () => {
                const v = $('#c-input').value.trim();
                if (!v) return toast('先写点什么', 'err');
                try {
                    await API.post(`/post/${postId}/comment`, null, { content: v });
                    toast('评论成功', 'ok');
                    openComments(postId); viewRefreshLater();
                } catch (e) { toast(e.message, 'err'); }
            };
        });
    } catch (e) { toast(e.message, 'err'); }
}

function viewRefreshLater() { setTimeout(() => { if (state.route === 'circle') viewCircle(); }, 400); }

function openCreatePost() {
    sheet(`<h3>发布邻里动态</h3>
      <div class="field" style="margin-top:14px"><label>分类</label>
        <select class="select" id="p-cat"><option>分享</option><option>闲置</option><option>求助</option><option>拼车</option></select></div>
      <div class="field"><label>标题</label><input class="input" id="p-title" placeholder="一句话说清"></div>
      <div class="field"><label>内容</label><textarea class="textarea" id="p-content" placeholder="详细描述，方便邻居帮你"></textarea></div>
      <button class="btn btn--primary btn--block" id="p-submit">发布（+2 积分）</button>`, () => {
        $('#p-submit').onclick = async () => {
            try {
                await API.post('/post', {
                    category: $('#p-cat').value, title: $('#p-title').value.trim(),
                    content: $('#p-content').value.trim(), images: ''
                });
                closeSheet(); toast('发布成功，积分 +2', 'ok'); refreshMe(); viewCircle();
            } catch (e) { toast(e.message, 'err'); }
        };
    });
}

/* ------------------------------ 活动 ------------------------------ */
async function viewActivity() {
    shell(`<div class="sec">${skeleton(2)}</div>`);
    try {
        const r = await API.get('/activity/list');
        const list = r.data || [];
        const isStaff = state.me.role >= 2;
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Events</span><h2>社区活动</h2>
            ${isStaff ? '<button class="btn btn--sm btn--primary" id="btn-create-act" style="margin-left:auto">发布活动</button>' : ''}</div>
          ${list.length === 0 ? emptyState('近期没有活动') : list.map(a => {
            const pct = Math.min(100, Math.round((a.signed || 0) / Math.max(1, a.quota) * 100));
            return `
            <div class="card card--flat" style="margin-bottom:12px">
              <div class="row row--between">
                <div class="row" style="gap:10px">
                  <div class="tile-ic" style="width:38px;height:38px;border-radius:12px;background:${gradOf(a.title)}">${icon('calendar')}</div>
                  <div><div class="item-title">${esc(a.title)}</div>
                    <div class="tiny muted">${String(a.startTime || '').replace('T', ' ').slice(5, 16)} 开始</div></div>
                </div>
                <span class="pill ${a.status === 1 ? 'pill--ok' : 'pill--grey'}">${a.status === 1 ? '报名中' : '已结束'}</span>
              </div>
              ${a.content ? `<p class="small muted" style="margin-top:10px">${esc(a.content)}</p>` : ''}
              <div class="progress" style="margin:14px 0 8px"><i style="width:${pct}%"></i></div>
              <div class="row row--between small muted"><span>已报名 <b class="num" style="color:var(--ink)">${a.signed || 0}</b> / ${a.quota}</span>
                <span>${a.status === 1 ? '名额有限' : '已结束'}</span></div>
              ${a.joined && a.myCode ? `<div class="row" style="margin-top:12px;gap:10px;flex-wrap:wrap">
                  <div class="code-box num">${esc(a.myCode)}</div><span class="tiny muted">现场出示核销</span></div>` : ''}
              <div class="row" style="margin-top:12px;justify-content:flex-end;gap:8px">
                ${a.status === 1 && !a.joined ? `<button class="btn btn--primary btn--sm" data-signup="${a.id}">报名参加</button>` : ''}
                ${a.joined ? '<span class="pill pill--ok">已报名</span>' : ''}
              </div>
            </div>`;
        }).join('')}
          ${isStaff ? `<div class="card card--flat" style="margin-top:6px">
            <div class="sec-head" style="margin-bottom:10px"><h2 style="font-size:14px">现场核销</h2></div>
            <div class="input-group"><input class="input" id="verify-code" placeholder="输入 6 位报名核销码" maxlength="6">
              <button class="btn btn--primary" id="btn-verify-act">核销</button></div>
          </div>` : ''}
        </section>`);

        const ca = $('#btn-create-act');
        if (ca) ca.onclick = openCreateActivity;
        document.querySelectorAll('[data-signup]').forEach(b => {
            b.onclick = async () => {
                try { await API.post(`/activity/${b.dataset.signup}/signup`); toast('报名成功，积分 +3', 'ok'); refreshMe(); viewActivity(); }
                catch (e) { toast(e.message, 'err'); }
            };
        });
        const vb = $('#btn-verify-act');
        if (vb) vb.onclick = async () => {
            const code = $('#verify-code').value.trim();
            if (!code) return toast('请输入核销码', 'err');
            try { await API.post('/activity/verify', null, { code }); toast('核销成功', 'ok'); }
            catch (e) { toast(e.message, 'err'); }
        };
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

function openCreateActivity() {
    const d = new Date(Date.now() + 3 * 86400000);
    const iso = d.toISOString().slice(0, 19);
    sheet(`<h3>发布社区活动</h3>
      <div class="field" style="margin-top:14px"><label>活动标题</label><input class="input" id="a-title" placeholder="例如：周末社区义诊"></div>
      <div class="field"><label>活动说明</label><textarea class="textarea" id="a-content" placeholder="时间、地点、参与方式"></textarea></div>
      <div class="field"><label>名额</label><input class="input" id="a-quota" type="number" value="30" min="1"></div>
      <div class="field"><label>开始时间</label><input class="input" id="a-start" value="${iso}"></div>
      <button class="btn btn--primary btn--block" id="a-submit">发布</button>`, () => {
        $('#a-submit').onclick = async () => {
            try {
                await API.post('/activity', {
                    title: $('#a-title').value.trim(), content: $('#a-content').value.trim(),
                    quota: Number($('#a-quota').value), startTime: $('#a-start').value
                });
                closeSheet(); toast('活动已发布', 'ok'); viewActivity();
            } catch (e) { toast(e.message, 'err'); }
        };
    });
}

/* ------------------------------ 访客 ------------------------------ */
async function viewVisit() {
    shell(`<div class="sec">${skeleton(1)}</div>`);
    try {
        const r = await API.get('/visit-pass/my');
        const list = r.data || [];
        shell(`
        <section class="sec">
          <div class="sec-head"><span class="kicker">Visitor</span><h2>访客通行码</h2></div>
          <div class="card">
            <div class="field"><label>访客姓名</label><input class="input" id="v-name" placeholder="例如：快递员小张"></div>
            <div class="field"><label>访客手机号</label><input class="input" id="v-phone" maxlength="11" placeholder="11 位手机号"></div>
            <div class="field"><label>有效时长</label>
              <select class="select" id="v-hours">
                <option value="1">1 小时</option><option value="4" selected>4 小时</option>
                <option value="12">12 小时</option><option value="24">24 小时</option>
              </select></div>
            <button class="btn btn--primary btn--block" id="v-submit">生成通行码</button>
            <div class="tiny muted" style="margin-top:10px">通行码一次性使用，门岗核销后立即失效</div>
          </div>
        </section>
        <section class="sec">
          <div class="sec-head"><span class="kicker">History</span><h2>我的访客记录</h2></div>
          ${list.length === 0 ? emptyState('还没有访客记录') : list.map(v => `
            <div class="card card--flat" style="margin-bottom:10px">
              <div class="row row--between">
                <div><div class="item-title">${esc(v.visitorName)}</div>
                  <div class="tiny muted">${esc(v.visitorPhone)} · ${relTime(v.createTime)}</div></div>
                <span class="pill ${v.used ? 'pill--grey' : 'pill--ok'}">${v.used ? '已核销' : '有效'}</span>
              </div>
              <div class="row row--between" style="margin-top:12px;flex-wrap:wrap;gap:10px">
                <div class="code-box num" style="font-size:17px;padding:9px 14px">${esc(v.code)}</div>
                <span class="tiny muted">有效期至 ${String(v.expireTime || '').replace('T', ' ').slice(5, 16)}</span>
              </div>
            </div>`).join('')}
        </section>`);
        $('#v-submit').onclick = async () => {
            try {
                const r2 = await API.post('/visit-pass', {
                    visitorName: $('#v-name').value.trim(), visitorPhone: $('#v-phone').value.trim()
                }, { hours: $('#v-hours').value });
                toast('通行码已生成：' + r2.data, 'ok');
                viewVisit();
            } catch (e) { toast(e.message, 'err'); }
        };
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

/* ------------------------------ 我的 ------------------------------ */
async function viewMe() {
    const me = state.me;
    shell(`<div class="sec">${skeleton(1)}</div>`);
    try {
        const [points, count, logs, noti] = await Promise.all([
            API.get('/points'), API.get('/points/sign/count'), API.get('/points/log'), API.get('/notification/list')
        ]);
        const notifications = noti.data || [];
        const logList = (logs.data || []).slice(0, 6);
        shell(`
        <section class="sec">
          <div class="card" style="background:linear-gradient(150deg,rgba(15,157,122,.12),rgba(110,86,207,.10)),var(--surface)">
            <div class="row" style="gap:14px">
              <div class="avatar" style="width:56px;height:56px;font-size:17px;background:${gradOf(me.nickName)}">
                ${esc(initial(me.nickName))}</div>
              <div class="item-main">
                <h2 style="font-size:18px">${esc(me.nickName)}</h2>
                <div class="small muted">${roleName(me.role)} · ${me.communityId ? '已选小区' : '未选小区'}
                  · ${esc(me.building || '-')} ${esc(me.room || '')}</div>
              </div>
              <span class="pill ${me.audit === 1 ? 'pill--ok' : 'pill--wait'}">${me.audit === 1 ? '已认证' : '待认证'}</span>
            </div>
            <div class="row" style="gap:30px;margin-top:18px">
              <div class="stat"><b class="num">${points.data || 0}</b><span>积分</span></div>
              <div class="stat"><b class="num">${count.data || 0}</b><span>连续签到</span></div>
              <div class="stat"><b class="num">${notifications.filter(n => !n.readFlag).length}</b><span>未读消息</span></div>
            </div>
            <div class="row" style="margin-top:16px;gap:10px">
              <button class="btn btn--primary btn--sm" id="btn-sign">每日签到 +2</button>
              <button class="btn btn--ghost btn--sm" id="btn-logout">${icon('logout', 14)} 退出登录</button>
            </div>
          </div>
        </section>

        ${me.audit !== 1 ? `
        <section class="sec">
          <div class="sec-head"><span class="kicker">Verify</span><h2>业主认证</h2></div>
          <div class="card">
            <div class="field"><label>小区</label>
              <select class="select" id="c-community">
                <option value="1">阳光花园</option><option value="2">翠苑新村</option><option value="3">西溪雅苑</option>
              </select></div>
            <div class="row" style="gap:10px">
              <div class="field" style="flex:1"><label>楼栋</label><input class="input" id="c-building" placeholder="3幢"></div>
              <div class="field" style="flex:1"><label>房号</label><input class="input" id="c-room" placeholder="1801"></div>
            </div>
            <button class="btn btn--primary btn--block" id="btn-apply">提交认证</button>
          </div>
        </section>` : ''}

        <section class="sec">
          <div class="sec-head"><span class="kicker">Points</span><h2>积分明细</h2></div>
          <div class="card card--flat" style="padding:6px 16px">
            ${logList.length === 0 ? emptyState('还没有积分记录') : logList.map(l => `
              <div class="item" style="padding:12px 0">
                <div class="item-main"><div class="small strong">${esc(l.remark || l.type)}</div>
                  <div class="tiny muted">${relTime(l.create_time || l.createTime)}</div></div>
                <span class="num ${l.points > 0 ? 'price' : 'muted'}">${l.points > 0 ? '+' : ''}${l.points}</span>
              </div>`).join('')}
          </div>
        </section>

        <section class="sec">
          <div class="sec-head"><span class="kicker">Inbox</span><h2>站内消息</h2>
            <button class="btn btn--sm btn--ghost" id="btn-readnoti" style="margin-left:auto">全部已读</button></div>
          <div class="card card--flat" style="padding:6px 16px">
            ${notifications.length === 0 ? emptyState('暂无消息') : notifications.slice(0, 8).map(n => `
              <div class="item" style="padding:12px 0">
                <div class="item-main">
                  <div class="row" style="gap:6px"><span class="small strong">${esc(n.title)}</span>
                    ${n.readFlag ? '' : '<i class="dot"></i>'}</div>
                  <div class="small muted">${esc(n.content)}</div>
                  <div class="tiny muted">${relTime(n.createTime)}</div>
                </div>
              </div>`).join('')}
          </div>
        </section>`);

        $('#btn-sign').onclick = async () => {
            try {
                const r = await API.post('/points/sign');
                toast(`签到成功，已连续 ${r.data} 天`, 'ok');
                refreshMe(); viewMe();
            } catch (e) { toast(e.message, 'err'); }
        };
        $('#btn-logout').onclick = () => {
            API.setToken(''); state.me = null; location.hash = '#/login'; renderLogin();
        };
        $('#btn-readnoti').onclick = async () => {
            try { await API.post('/notification/read-all'); toast('已全部已读', 'ok'); viewMe(); }
            catch (e) { toast(e.message, 'err'); }
        };
        const ap = $('#btn-apply');
        if (ap) ap.onclick = async () => {
            try {
                await API.post(`/user/apply`, null, {
                    communityId: $('#c-community').value,
                    building: $('#c-building').value.trim(), room: $('#c-room').value.trim()
                });
                toast('已提交，等待管家审核', 'ok');
                refreshMe(); viewMe();
            } catch (e) { toast(e.message, 'err'); }
        };
    } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

async function refreshMe() {
    try { state.me = (await API.get('/user/me')).data; } catch (e) { /* ignore */ }
}

/* ------------------------------ 弹层 ------------------------------ */
let sheetHandler = null;
function sheet(inner, after) {
    const mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML = `<div class="sheet">
        <div class="sheet-head"><h3>详情</h3><button class="x">${icon('x', 16)}</button></div>
        <div id="sheet-body">${inner}</div>
      </div>`;
    mask.addEventListener('click', (e) => { if (e.target === mask) closeSheet(); });
    mask.querySelector('.x').onclick = closeSheet;
    document.body.appendChild(mask);
    const body = mask.querySelector('#sheet-body');
    const h3 = body.querySelector('h3');
    if (h3) { mask.querySelector('.sheet-head h3').textContent = h3.textContent; h3.style.display = 'none'; }
    sheetHandler = after;
    if (after) after();
}
function closeSheet() {
    const m = document.querySelector('.mask');
    if (m) m.remove();
    sheetHandler = null;
}

/* ------------------------------ 路由 ------------------------------ */
const VIEWS = {
    home: viewHome, notice: viewNotice, group: viewGroup, orders: viewOrders,
    repair: viewRepair, circle: viewCircle, activity: viewActivity, visit: viewVisit, me: viewMe
};

function parseRoute() {
    const h = (location.hash || '#/home').replace(/^#\/?/, '');
    const [route] = h.split('?');
    return VIEWS[route] ? route : 'home';
}

async function render() {
    if (!state.me) { renderLogin(); return; }
    state.route = parseRoute();
    const fn = VIEWS[state.route] || viewHome;
    try { await fn(); } catch (e) { shell(`<div class="sec">${emptyState(e.message)}</div>`); }
}

window.addEventListener('hashchange', () => {
    closeSheet();
    render();
    window.scrollTo({ top: 0, behavior: 'smooth' });
});

/* ------------------------------ 启动 ------------------------------ */
(async function boot() {
    const params = new URLSearchParams(location.search);
    const as = params.get('as');
    try {
        if (as) {   // 演示：?as=13900000004 直接进对应角色
            await API.post('/user/code', null, { phone: as });
            const code = (await API.get('/user/code/peek', { phone: as })).data;
            const res = await API.post('/user/login', { phone: as, code });
            API.setToken(res.data);
        }
        if (API.token) {
            try { state.me = (await API.get('/user/me')).data; } catch (e) { API.setToken(''); }
        }
    } catch (e) { /* 忽略，回登录页 */ }
    if (!state.me) { renderLogin(); return; }
    await render();
})();
