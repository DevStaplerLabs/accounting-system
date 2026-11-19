'use strict';
/* StaplerLabs Accounts: single-page front end. Talks to the Java REST API under /api. */

/* ===================== Utilities ===================== */
const $ = s => document.querySelector(s);
const nf = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const fmt = n => 'Rs. ' + nf.format(+n || 0);
const short = n => {
  n = +n || 0; const a = Math.abs(n);
  if (a >= 1e7) return 'Rs. ' + (n / 1e7).toFixed(2) + ' Cr';
  if (a >= 1e5) return 'Rs. ' + (n / 1e5).toFixed(2) + ' L';
  return fmt(n);
};
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const todayStr = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 10); };
const fyStart = d => { const y = +d.slice(0, 4), m = +d.slice(5, 7); return (m >= 4 ? y : y - 1) + '-04-01'; };
const fdate = s => s ? new Date(s + 'T00:00:00').toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '';
const qs = o => new URLSearchParams(Object.entries(o).filter(([, v]) => v !== null && v !== undefined && v !== '')).toString();
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;

const ICONS = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5zM4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5',
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  receipt: 'M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2zM9 8h6M9 12h6M9 16h3',
  swap: 'M7 7h13M16 3l4 4-4 4M17 17H4M8 13l-4 4 4 4',
  edit: 'M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  bookopen: 'M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2zM22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z',
  scale: 'M12 3v18M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0zM8 21h8',
  trend: 'M3 17l6-6 4 4 8-8M15 7h6v6',
  layers: 'M12 2l10 5-10 5L2 7zM2 17l10 5 10-5M2 12l10 5 10-5',
  percent: 'M19 5L5 19M6.5 9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM17.5 20a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  sliders: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',
  moon: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
  plus: 'M12 5v14M5 12h14',
  trash: 'M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6',
  print: 'M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2M6 14h12v8H6z',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  x: 'M18 6L6 18M6 6l12 12',
  menu: 'M3 12h18M3 6h18M3 18h18',
  key: 'M15.5 7.5l3 3M21 2l-9.6 9.6M7.5 21a5.5 5.5 0 1 0 0-11 5.5 5.5 0 0 0 0 11z',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  wallet: 'M20 7H5a2 2 0 0 1 0-4h14v4zM3 5v14a2 2 0 0 0 2 2h16V7M16 14h.01',
  down: 'M12 5v14M19 12l-7 7-7-7',
  up: 'M12 19V5M5 12l7-7 7 7',
  eye: 'M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  check: 'M20 6L9 17l-5-5',
  alert: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  back: 'M19 12H5M12 19l-7-7 7-7',
  rupee: 'M6 3h12M6 8h12M6 13l8.5 8M6 13h3a5 5 0 0 0 0-10'
};
const icon = (n, cls = '') => `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[n] || ''}"/></svg>`;

/** Indian-system amount in words, for invoices. */
function inWords(amount) {
  const a = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen',
    'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
  const b = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
  const two = x => x < 20 ? a[x] : b[Math.floor(x / 10)] + (x % 10 ? ' ' + a[x % 10] : '');
  const three = x => x >= 100 ? a[Math.floor(x / 100)] + ' Hundred' + (x % 100 ? ' ' + two(x % 100) : '') : two(x);
  let r = Math.floor(amount); const p = Math.round((amount - r) * 100); const parts = [];
  const cr = Math.floor(r / 1e7); r %= 1e7; const l = Math.floor(r / 1e5); r %= 1e5; const t = Math.floor(r / 1e3); r %= 1e3;
  if (cr) parts.push(three(cr) + ' Crore'); if (l) parts.push(two(l) + ' Lakh'); if (t) parts.push(two(t) + ' Thousand'); if (r) parts.push(three(r));
  return 'Rupees ' + (parts.join(' ') || 'Zero') + (p ? ' and ' + two(p) + ' Paise' : '') + ' Only';
}

/* ===================== API ===================== */
async function api(method, path, body) {
  let res;
  try {
    res = await fetch('/api' + path, {
      method, credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    });
  } catch (e) { throw new Error('Cannot reach the server. Make sure it is running.'); }
  let j = {};
  try { j = await res.json(); } catch (e) { /* not JSON */ }
  if (res.status === 401 && path !== '/login') {
    ME = null; renderLogin('Your session has ended. Please sign in again.');
    throw new Error('Please sign in.');
  }
  if (!j.ok) throw new Error(j.error || 'Request failed (' + res.status + ')');
  return j.data;
}
const GET = p => api('GET', p);

/* ===================== State ===================== */
let ME = null, COMPANY = null, ACC = [], PARTIES = [], DOCS = [], DOC_BACK = null;
const F = { from: null, to: null, asOn: null, q: '', ledgerAcc: null, docStatus: 'ALL', accType: 'ALL', partyKind: 'CUSTOMER' };
const canWrite = () => ME && ME.role !== 'VIEWER';
const isAdmin = () => ME && ME.role === 'ADMIN';
const ensureRange = () => { F.to = F.to || todayStr(); F.from = F.from || fyStart(F.to); };
const ensureAsOn = () => { F.asOn = F.asOn || todayStr(); };
async function loadAccounts() { ACC = await GET('/accounts'); return ACC; }
const accById = id => ACC.find(a => a.id === +id);
const accOptions = (list, sel, filter) => list.filter(filter || (() => true))
  .map(a => `<option value="${a.id}" ${a.id === +sel ? 'selected' : ''}>${esc(a.code + '  ' + a.name)}</option>`).join('');

/* ===================== Toasts, modals, tooltip ===================== */
function toast(msg, type = 'ok') {
  const t = document.createElement('div');
  t.className = 'toast ' + (type === 'err' ? 'err' : '');
  t.innerHTML = icon(type === 'err' ? 'alert' : 'check', type === 'err' ? 'bad' : 'good') + `<span>${esc(msg)}</span>`;
  $('#toasts').appendChild(t);
  setTimeout(() => t.remove(), 3800);
}

function formData(form) {
  const o = {};
  for (const el of form.elements) {
    if (!el.name) continue;
    o[el.name] = el.type === 'checkbox' ? el.checked : el.value.trim();
  }
  return o;
}

/** Opens a dialog. onSubmit may throw: the message is shown in the dialog and it stays open. */
function modal({ title, body, submit = 'Save', wide = false, danger = false, onSubmit, onOpen }) {
  return new Promise(resolve => {
    const root = $('#modal-root');
    root.innerHTML = `<div class="overlay"><form class="modal ${wide ? 'wide' : ''}" novalidate>
      <header><h3>${esc(title)}</h3><button type="button" class="icon-btn" data-close aria-label="Close">${icon('x')}</button></header>
      <div class="mbody">${body}</div>
      <footer><span class="merr" role="alert"></span><button type="button" class="btn" data-close>Cancel</button>
      ${onSubmit ? `<button type="submit" class="btn ${danger ? 'danger-fill' : 'primary'}">${esc(submit)}</button>` : ''}</footer>
    </form></div>`;
    const form = root.querySelector('form'), err = root.querySelector('.merr'), ov = root.querySelector('.overlay');
    const onKey = e => { if (e.key === 'Escape') close(false); };
    const close = v => { root.innerHTML = ''; document.removeEventListener('keydown', onKey); resolve(v); };
    document.addEventListener('keydown', onKey);
    root.querySelectorAll('[data-close]').forEach(b => b.onclick = () => close(false));
    ov.addEventListener('mousedown', e => { if (e.target === ov) close(false); });
    form.onsubmit = async e => {
      e.preventDefault();
      const btn = form.querySelector('[type=submit]'); btn.disabled = true; err.textContent = '';
      try { const r = await onSubmit(formData(form), form); close(r === undefined ? true : r); }
      catch (x) { err.textContent = x.message; }
      finally { if (btn.isConnected) btn.disabled = false; }
    };
    if (onOpen) onOpen(form);
    setTimeout(() => form.querySelector('.mbody input:not([type=hidden]):not([type=checkbox]), .mbody select, .mbody textarea')?.focus(), 30);
  });
}
const confirmBox = (title, text, submit = 'Confirm', danger = true) =>
  modal({ title, body: `<p style="margin:0">${text}</p>`, submit, danger, onSubmit: () => true });

function showTip(e, html) {
  const t = $('#tip'); t.innerHTML = html; t.classList.add('on');
  const x = Math.min(e.clientX + 14, innerWidth - t.offsetWidth - 8), y = Math.min(e.clientY + 14, innerHeight - t.offsetHeight - 8);
  t.style.left = x + 'px'; t.style.top = y + 'px';
}
const hideTip = () => $('#tip').classList.remove('on');

function exportCsv(tableId, name) {
  const rows = [...document.querySelectorAll('#' + tableId + ' tr')].map(tr => [...tr.children]
    .filter(c => !c.classList.contains('no-print'))
    .map(c => '"' + c.innerText.replace(/\s+/g, ' ').trim().replace(/"/g, '""') + '"').join(','));
  const blob = new Blob(['﻿' + rows.join('\r\n')], { type: 'text/csv' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name + '.csv'; a.click();
}

/* ===================== Shared UI pieces ===================== */
const field = (label, inner, cls = 'c3', hint = '') =>
  `<div class="field ${cls}"><label>${label}</label>${inner}${hint ? `<span class="hint">${hint}</span>` : ''}</div>`;
const rangeFields = () => { ensureRange(); return `
  <div class="field"><label>From</label><input type="date" value="${F.from}" onchange="F.from=this.value;route()"></div>
  <div class="field"><label>To</label><input type="date" value="${F.to}" onchange="F.to=this.value;route()"></div>`; };
const asOnField = () => { ensureAsOn(); return `<div class="field"><label>As on</label><input type="date" value="${F.asOn}" onchange="F.asOn=this.value;route()"></div>`; };
const reportTools = (tableId, name) => `<div class="spacer"></div>
  ${tableId ? `<button class="btn" onclick="exportCsv('${tableId}','${name}')">${icon('download')} Export CSV</button>` : ''}
  <button class="btn" onclick="print()">${icon('print')} Print</button>`;
const statusBadge = s => `<span class="badge b-${s.toLowerCase()}">${s.charAt(0) + s.slice(1).toLowerCase()}</span>`;
const emptyRow = (cols, text) => `<tr><td colspan="${cols}" class="empty">${text}</td></tr>`;
const openLedger = id => { F.ledgerAcc = id; location.hash = '#/ledger'; };

/* ===================== Login and shell ===================== */
function renderLogin(msg = '') {
  $('#modal-root').innerHTML = '';
  $('#app').innerHTML = `<div class="login">
    <div class="art">
      <div class="brand"><div class="mark"><img src="logo.png" alt="StaplerLabs logo"></div><div><b>StaplerLabs</b><small>Consulting and execution</small></div></div>
      <div><div class="eyebrow" style="margin-bottom:14px">StaplerLabs Accounts</div><h2>Your books, <em>kept in balance</em>.</h2>
        <p>The double-entry accounting system for StaplerLabs Private Limited.</p>
        <ul><li>${icon('check')} Every entry checked for equal debits and credits</li>
            <li>${icon('check')} GST sales invoices and purchase bills</li>
            <li>${icon('check')} Ledger, trial balance, profit and loss, balance sheet</li></ul></div>
      <footer>StaplerLabs Private Limited, New Delhi</footer>
    </div>
    <div class="pane"><form id="loginForm">
      <div><div class="eyebrow">Welcome back</div><h1 style="margin-top:6px">Sign in</h1><p class="muted" style="margin:6px 0 0">Use your StaplerLabs Accounts username.</p></div>
      ${field('Username', '<input name="username" autocomplete="username" required>', '')}
      ${field('Password', '<input name="password" type="password" autocomplete="current-password" required>', '')}
      <div class="err" role="alert">${esc(msg)}</div>
      <button class="btn primary">Sign in</button>
    </form></div></div>`;
  const form = $('#loginForm');
  form.querySelector('input').focus();
  form.onsubmit = async e => {
    e.preventDefault();
    const d = formData(form), err = form.querySelector('.err'), btn = form.querySelector('button');
    if (!d.username || !d.password) { err.textContent = 'Enter your username and password.'; return; }
    btn.disabled = true; err.textContent = '';
    try { ME = await api('POST', '/login', d); await startApp(); }
    catch (x) { err.textContent = x.message; btn.disabled = false; }
  };
}

const PAGES = {
  dashboard: { title: 'Dashboard', icon: 'grid', group: 'Overview' },
  invoices: { title: 'Sales invoices', icon: 'file', group: 'Sales and purchases' },
  bills: { title: 'Purchase bills', icon: 'receipt', group: 'Sales and purchases' },
  parties: { title: 'Customers and vendors', icon: 'users', group: 'Sales and purchases' },
  vouchers: { title: 'Payments and receipts', icon: 'swap', group: 'Accounting' },
  journal: { title: 'Journal entries', icon: 'edit', group: 'Accounting' },
  daybook: { title: 'Day book', icon: 'list', group: 'Accounting' },
  accounts: { title: 'Chart of accounts', icon: 'book', group: 'Accounting' },
  ledger: { title: 'Ledger', icon: 'bookopen', group: 'Reports' },
  trial: { title: 'Trial balance', icon: 'scale', group: 'Reports' },
  pnl: { title: 'Profit and loss', icon: 'trend', group: 'Reports' },
  bs: { title: 'Balance sheet', icon: 'layers', group: 'Reports' },
  gst: { title: 'GST summary', icon: 'percent', group: 'Reports' },
  users: { title: 'Users and roles', icon: 'shield', group: 'Administration', admin: true },
  settings: { title: 'Company and settings', icon: 'sliders', group: 'Administration' }
};

const isDark = () => {
  const t = document.documentElement.dataset.theme;
  return t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
};
function toggleTheme() {
  const next = isDark() ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem('sl-theme', next); } catch (e) { /* storage unavailable */ }
  $('#themeBtn').innerHTML = icon(isDark() ? 'sun' : 'moon');
}
const toggleSide = () => $('#side').classList.toggle('open');
function toggleUserMenu(e) {
  e.stopPropagation();
  $('#userMenu').classList.toggle('hide');
}
document.addEventListener('click', () => $('#userMenu')?.classList.add('hide'));

function renderShell() {
  let nav = '', group = '';
  for (const [key, p] of Object.entries(PAGES)) {
    if (p.admin && !isAdmin()) continue;
    if (p.group !== group) { group = p.group; nav += `<div class="grp">${group}</div>`; }
    nav += `<a href="#/${key}" data-p="${key}">${icon(p.icon)}<span>${p.title}</span></a>`;
  }
  const fy = fyStart(todayStr()).slice(0, 4);
  const initials = ME.fullName.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  $('#app').innerHTML = `<div class="shell">
    <aside class="side" id="side">
      <div class="brand"><div class="mark"><img src="logo.png" alt="StaplerLabs logo"></div><div><b>StaplerLabs</b><small>Accounts</small></div></div>
      <nav class="nav">${nav}</nav>
      <div class="side-foot">${esc(COMPANY.name)}<br>Financial year <b>${fy}-${String(+fy + 1).slice(2)}</b></div>
    </aside>
    <div class="main">
      <header class="top">
        <button class="icon-btn menu-btn" onclick="toggleSide()" aria-label="Menu">${icon('menu')}</button>
        <h1 id="pageTitle"></h1>
        <button class="icon-btn" id="themeBtn" onclick="toggleTheme()" title="Switch light or dark theme">${icon(isDark() ? 'sun' : 'moon')}</button>
        <div class="user">
          <button onclick="toggleUserMenu(event)"><span class="avatar">${esc(initials)}</span>
            <span class="who">${esc(ME.fullName)}<small>${ME.role.charAt(0) + ME.role.slice(1).toLowerCase()}</small></span></button>
          <div class="dropdown hide" id="userMenu">
            <button onclick="changePassword()">${icon('key')} Change password</button>
            <button onclick="logout()">${icon('logout')} Sign out</button>
          </div>
        </div>
      </header>
      <main class="content" id="view"></main>
    </div></div>`;
}

async function startApp() {
  COMPANY = await GET('/company');
  renderShell();
  await route();
}

async function logout() {
  try { await api('POST', '/logout'); } catch (e) { /* ignore */ }
  ME = null; location.hash = ''; renderLogin();
}

function changePassword() {
  modal({
    title: 'Change password', submit: 'Update password',
    body: `<div class="form-grid">
      ${field('Current password', '<input type="password" name="oldPassword" autocomplete="current-password">', 'c6')}
      ${field('New password', '<input type="password" name="newPassword" autocomplete="new-password">', 'c6', 'At least 8 characters, with letters and numbers.')}
      ${field('Confirm new password', '<input type="password" name="confirm" autocomplete="new-password">', 'c6')}</div>`,
    onSubmit: async d => {
      if (d.newPassword !== d.confirm) throw new Error('The two new passwords do not match.');
      await api('POST', '/change-password', d);
      toast('Password updated.');
    }
  });
}

/* ===================== Router ===================== */
async function route() {
  if (!ME || !$('#view')) return;
  hideTip();
  const m = location.hash.match(/^#\/([a-z]+)(?:\/(\d+))?/) || [];
  let page = m[1] || 'dashboard';
  if (!(page in VIEWS) || (PAGES[page]?.admin && !isAdmin())) page = 'dashboard';
  const view = $('#view');
  view.innerHTML = '<div class="empty">Loading...</div>';
  $('#side').classList.remove('open');
  if (page !== 'doc') {
    $('#pageTitle').textContent = PAGES[page].title;
    document.title = PAGES[page].title + ' | StaplerLabs Accounts';
  }
  try { await VIEWS[page](view, m[2]); }
  catch (e) { view.innerHTML = `<div class="card empty">${icon('alert')} ${esc(e.message)}</div>`; }
  const active = page === 'doc' ? DOC_BACK : page;
  document.querySelectorAll('.nav a').forEach(a => a.classList.toggle('active', a.dataset.p === active));
}
window.addEventListener('hashchange', route);

/* ===================== Chart ===================== */
function niceMax(v) {
  const e = 10 ** Math.floor(Math.log10(v)), f = v / e;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 4 ? 4 : f <= 6 ? 6 : f <= 8 ? 8 : 10) * e;
}
const axisFmt = v => v >= 1e7 ? (v / 1e7).toFixed(1) + ' Cr' : v >= 1e5 ? +(v / 1e5).toFixed(1) + ' L' : v >= 1e3 ? +(v / 1e3).toFixed(0) + 'k' : String(v);
function barPath(x, y, w, h) {
  if (h <= 0.5) return '';
  const r = Math.min(4, h, w / 2);
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}
function drawChart(el, data) {
  const W = 660, H = 270, P = { l: 52, r: 6, t: 10, b: 28 };
  const max = niceMax(Math.max(1, ...data.flatMap(m => [m.income, m.expense])));
  const iw = W - P.l - P.r, ih = H - P.t - P.b, gw = iw / data.length, bw = Math.max(6, Math.min(20, (gw - 16) / 2));
  const y = v => P.t + ih - (Math.max(0, v) / max) * ih;
  let s = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Bar chart of income and expenses by month">`;
  for (let i = 0; i <= 4; i++) {
    const v = max * i / 4, yy = y(v);
    s += `<line class="grid-line" x1="${P.l}" x2="${W - P.r}" y1="${yy}" y2="${yy}"/><text class="axis-label" x="${P.l - 8}" y="${yy + 4}" text-anchor="end">${axisFmt(v)}</text>`;
  }
  data.forEach((m, i) => {
    const x0 = P.l + gw * i, cx = x0 + gw / 2;
    s += `<rect class="hit" data-i="${i}" x="${x0 + 2}" y="${P.t}" width="${gw - 4}" height="${ih}" rx="6"/>`;
    s += `<path pointer-events="none" fill="var(--series-1)" d="${barPath(cx - bw - 1, y(m.income), bw, P.t + ih - y(m.income))}"/>`;
    s += `<path pointer-events="none" fill="var(--series-2)" d="${barPath(cx + 1, y(m.expense), bw, P.t + ih - y(m.expense))}"/>`;
    s += `<text class="axis-label" x="${cx}" y="${H - 8}" text-anchor="middle">${m.label}</text>`;
  });
  el.innerHTML = s + '</svg>';
  el.querySelectorAll('.hit').forEach(r => {
    const m = data[+r.dataset.i], net = m.income - m.expense;
    r.addEventListener('mousemove', e => showTip(e, `<b>${m.label}</b>
      <div class="row"><span><i style="background:var(--series-1)"></i>Income</span><span class="num">${fmt(m.income)}</span></div>
      <div class="row"><span><i style="background:var(--series-2)"></i>Expenses</span><span class="num">${fmt(m.expense)}</span></div>
      <div class="row" style="border-top:1px solid var(--line);margin-top:4px;padding-top:4px"><span>${net >= 0 ? 'Surplus' : 'Deficit'}</span><span class="num">${fmt(Math.abs(net))}</span></div>`));
    r.addEventListener('mouseleave', hideTip);
  });
}

/* ===================== Views ===================== */
const VIEWS = {};

VIEWS.dashboard = async el => {
  const d = await GET('/reports/dashboard');
  const kpi = (ic, label, val, sub, cls = '') =>
    `<div class="card kpi"><div class="k">${icon(ic)}${label}</div><div class="v num ${cls}">${val}</div><div class="s">${sub}</div></div>`;
  const maxExp = Math.max(1, ...d.topExpenses.map(x => x.amount));
  const qa = (ic, text, fn) => `<button onclick="${fn}">${icon(ic)}${text}</button>`;
  el.innerHTML = `
    <div class="grid g4">
      ${kpi('wallet', 'Cash and bank', fmt(d.cash), 'All cash and bank accounts')}
      ${kpi('down', 'To receive', fmt(d.receivable), plural(d.unpaid.SALE.count, 'unpaid invoice'))}
      ${kpi('up', 'To pay', fmt(d.payable), plural(d.unpaid.PURCHASE.count, 'unpaid bill'))}
      ${kpi('trend', d.profit >= 0 ? 'Net profit this year' : 'Net loss this year', fmt(Math.abs(d.profit)),
        `Income ${short(d.income)}, expenses ${short(d.expense)}`, d.profit >= 0 ? 'good' : 'bad')}
    </div>
    <div class="grid g-main">
      <div class="card">
        <div class="card-h"><div><h2>Income and expenses by month</h2><p>Financial year from ${fdate(d.fyStart)}</p></div>
          <div class="legend"><span><i style="background:var(--series-1)"></i>Income</span><span><i style="background:var(--series-2)"></i>Expenses</span></div></div>
        <div class="chart" id="chart"></div>
        <details class="tv"><summary>View as table</summary><div class="table-wrap"><table>
          <thead><tr><th>Month</th><th class="r">Income</th><th class="r">Expenses</th></tr></thead>
          <tbody>${d.months.map(m => `<tr><td>${m.label}</td><td class="r num">${fmt(m.income)}</td><td class="r num">${fmt(m.expense)}</td></tr>`).join('')}</tbody>
        </table></div></details>
      </div>
      <div class="card">
        <div class="card-h"><div><h2>Where the money went</h2><p>Largest expense heads this year</p></div></div>
        ${d.topExpenses.map(x => `<div class="hbar"><div class="t"><span>${esc(x.name)}</span><span class="num">${short(x.amount)}</span></div>
          <div class="track"><div class="fill" style="width:${(x.amount / maxExp * 100).toFixed(1)}%"></div></div></div>`).join('') || '<div class="empty">No expenses yet.</div>'}
      </div>
    </div>
    <div class="grid g-main">
      <div class="card flush">
        <div class="card-h"><h2>Recent activity</h2><a class="btn sm" href="#/daybook">Open day book</a></div>
        <div class="table-wrap"><table><thead><tr><th>No.</th><th>Date</th><th>Narration</th><th>Type</th><th class="r">Amount</th></tr></thead><tbody>
        ${d.recent.map(e => `<tr><td class="num">${e.entryNo}</td><td class="num">${fdate(e.date)}</td><td>${esc(e.narration)}</td>
          <td><span class="badge b-plain">${e.source.charAt(0) + e.source.slice(1).toLowerCase()}</span></td><td class="r num">${fmt(e.amount)}</td></tr>`).join('') || emptyRow(5, 'No entries yet.')}
        </tbody></table></div>
      </div>
      <div class="card">
        <div class="card-h"><h2>Quick actions</h2></div>
        <div class="quick">
          ${canWrite() ? qa('file', 'New sales invoice', "newDoc('SALE')") + qa('receipt', 'New purchase bill', "newDoc('PURCHASE')")
            + qa('up', 'Record a payment', "newVoucher('PAYMENT')") + qa('down', 'Record a receipt', "newVoucher('RECEIPT')") : ''}
          ${qa('trend', 'Profit and loss', "location.hash='#/pnl'")}${qa('layers', 'Balance sheet', "location.hash='#/bs'")}
        </div>
      </div>
    </div>`;
  drawChart($('#chart'), d.months);
};

/* ---------- Chart of accounts ---------- */
VIEWS.accounts = async el => {
  await loadAccounts();
  const types = ['ALL', 'Asset', 'Liability', 'Equity', 'Income', 'Expense'];
  const rows = ACC.filter(a => F.accType === 'ALL' || a.type === F.accType);
  el.innerHTML = `
    <div class="toolbar no-print">
      <div class="tabs">${types.map(t => `<button class="${F.accType === t ? 'on' : ''}" onclick="F.accType='${t}';route()">${t === 'ALL' ? 'All' : t}</button>`).join('')}</div>
      ${reportTools('accTable', 'chart-of-accounts')}
      ${canWrite() ? `<button class="btn primary" onclick="newAccount()">${icon('plus')} New account</button>` : ''}
    </div>
    <div class="card flush"><div class="table-wrap"><table id="accTable">
      <thead><tr><th>Code</th><th>Account</th><th>Type</th><th class="r">Balance</th><th class="no-print"></th></tr></thead><tbody>
      ${rows.map(a => `<tr><td class="num">${a.code}</td>
        <td><span class="link" onclick="openLedger(${a.id})">${esc(a.name)}</span>
          ${a.cash ? ' <span class="badge b-plain">Cash / bank</span>' : ''}${a.system ? ' <span class="badge b-plain">System</span>' : ''}</td>
        <td><span class="badge b-type">${a.type}</span></td><td class="r num">${fmt(a.balance)}</td>
        <td class="no-print"><div class="actions">${canWrite() ? `<button class="btn sm ghost" onclick="renameAccount(${a.id})">Rename</button>
          ${a.system ? '' : `<button class="btn sm ghost danger" title="Delete" aria-label="Delete" onclick="deleteAccount(${a.id})">${icon('trash')}</button>`}` : ''}</div></td></tr>`).join('')
        || emptyRow(5, 'No accounts of this type.')}
      </tbody></table></div></div>`;
};
function newAccount() {
  modal({
    title: 'New account', submit: 'Create account',
    body: `<div class="form-grid">
      ${field('Code', '<input name="code" placeholder="e.g. 5008" inputmode="numeric">', 'c2', '3 to 10 digits')}
      ${field('Account name', '<input name="name" placeholder="e.g. Professional Fees">', 'c4')}
      ${field('Type', `<select name="type">${LedgerTypes.map(t => `<option>${t}</option>`).join('')}</select>`, 'c3')}
      <div class="field c3" style="justify-content:flex-end"><label style="display:flex;gap:8px;align-items:center;font-weight:500">
        <input type="checkbox" name="cash" style="width:auto"> This is a cash or bank account</label></div></div>`,
    onSubmit: async d => { const a = await api('POST', '/accounts', d); toast(`Account ${a.code} ${a.name} created.`); route(); }
  });
}
const LedgerTypes = ['Asset', 'Liability', 'Equity', 'Income', 'Expense'];
function renameAccount(id) {
  const a = accById(id);
  modal({
    title: 'Rename account', body: field('Account name', `<input name="name" value="${esc(a.name)}">`, ''),
    onSubmit: async d => { await api('PUT', '/accounts/' + id, d); toast('Account renamed.'); route(); }
  });
}
async function deleteAccount(id) {
  const a = accById(id);
  if (!await confirmBox('Delete account', `Delete <b>${esc(a.code + ' ' + a.name)}</b>? Accounts with transactions cannot be deleted.`, 'Delete')) return;
  try { await api('DELETE', '/accounts/' + id); toast('Account deleted.'); route(); } catch (e) { toast(e.message, 'err'); }
}

/* ---------- Customers and vendors ---------- */
VIEWS.parties = async el => {
  PARTIES = await GET('/parties');
  const kind = F.partyKind, list = PARTIES.filter(p => p.kind === kind);
  const word = kind === 'CUSTOMER' ? 'customer' : 'vendor';
  el.innerHTML = `
    <div class="toolbar no-print">
      <div class="tabs">${['CUSTOMER', 'VENDOR'].map(k => `<button class="${kind === k ? 'on' : ''}" onclick="F.partyKind='${k}';route()">${k === 'CUSTOMER' ? 'Customers' : 'Vendors'} (${PARTIES.filter(p => p.kind === k).length})</button>`).join('')}</div>
      <div class="spacer"></div>
      ${canWrite() ? `<button class="btn primary" onclick="editParty(0,'${kind}')">${icon('plus')} New ${word}</button>` : ''}
    </div>
    <div class="card flush"><div class="table-wrap"><table>
      <thead><tr><th>Name</th><th>GSTIN</th><th>Contact</th><th class="r">${kind === 'CUSTOMER' ? 'To receive' : 'To pay'}</th><th class="no-print"></th></tr></thead><tbody>
      ${list.map(p => `<tr><td><b>${esc(p.name)}</b><div class="sub">${esc(p.address || '')}</div></td>
        <td class="num">${esc(p.gstin || '') || '<span class="muted">Not registered</span>'}</td>
        <td>${esc(p.phone || '')}<div class="sub">${esc(p.email || '')}</div></td>
        <td class="r num ${p.outstanding > 0 ? '' : 'muted'}">${fmt(p.outstanding)}</td>
        <td class="no-print"><div class="actions">${canWrite() ? `<button class="btn sm ghost" onclick="editParty(${p.id})">Edit</button>
          <button class="btn sm ghost danger" aria-label="Delete" onclick="deleteParty(${p.id})">${icon('trash')}</button>` : ''}</div></td></tr>`).join('')
        || emptyRow(5, `No ${word}s yet.`)}
      </tbody></table></div></div>`;
};
async function editParty(id, kind) {
  if (!PARTIES.length) PARTIES = await GET('/parties');
  const p = id ? PARTIES.find(x => x.id === id) : { kind, name: '', address: '', gstin: '', phone: '', email: '' };
  const word = p.kind === 'CUSTOMER' ? 'customer' : 'vendor';
  return modal({
    title: id ? 'Edit ' + word : 'New ' + word, submit: id ? 'Save changes' : 'Add ' + word,
    body: `<div class="form-grid">
      ${field('Name', `<input name="name" value="${esc(p.name)}">`, 'c6')}
      ${field('Address', `<input name="address" value="${esc(p.address || '')}">`, 'c6')}
      ${field('GSTIN', `<input name="gstin" value="${esc(p.gstin || '')}" maxlength="15" style="text-transform:uppercase">`, 'c2', 'Leave blank if not registered')}
      ${field('Phone', `<input name="phone" value="${esc(p.phone || '')}">`, 'c2')}
      ${field('Email', `<input name="email" type="email" value="${esc(p.email || '')}">`, 'c2')}</div>`,
    onSubmit: async d => {
      const saved = id ? await api('PUT', '/parties/' + id, d) : await api('POST', '/parties', { ...d, kind: p.kind });
      toast(`${saved.name} saved.`);
      PARTIES = [];
      if (location.hash.startsWith('#/parties')) route();
      return saved;
    }
  });
}
async function deleteParty(id) {
  const p = PARTIES.find(x => x.id === id);
  if (!await confirmBox('Delete', `Delete <b>${esc(p.name)}</b>?`, 'Delete')) return;
  try { await api('DELETE', '/parties/' + id); toast(p.name + ' deleted.'); route(); } catch (e) { toast(e.message, 'err'); }
}

/* ---------- Sales invoices and purchase bills ---------- */
const DOC_WORDS = {
  SALE: { one: 'invoice', title: 'Sales invoice', party: 'Customer', partyKind: 'CUSTOMER', acc: 'Income account', pay: 'Receive payment', paidWord: 'Received' },
  PURCHASE: { one: 'bill', title: 'Purchase bill', party: 'Vendor', partyKind: 'VENDOR', acc: 'Expense or asset account', pay: 'Make payment', paidWord: 'Paid' }
};
async function docsPage(el, kind) {
  const w = DOC_WORDS[kind];
  DOCS = await GET('/invoices?kind=' + kind);
  const q = F.q.toLowerCase();
  const list = DOCS.filter(d => (F.docStatus === 'ALL' || d.status === F.docStatus)
    && (!q || d.docNo.toLowerCase().includes(q) || d.partyName.toLowerCase().includes(q)));
  const live = DOCS.filter(d => d.status !== 'CANCELLED');
  const sumOf = arr => arr.reduce((s, d) => s + d.total, 0);
  const unpaid = live.filter(d => d.status === 'UNPAID');
  const count = s => s === 'ALL' ? DOCS.length : DOCS.filter(d => d.status === s).length;
  el.innerHTML = `
    <div class="grid g3">
      <div class="card kpi"><div class="k">${icon('alert')}Unpaid</div><div class="v num">${fmt(sumOf(unpaid))}</div><div class="s">${plural(unpaid.length, w.one)}</div></div>
      <div class="card kpi"><div class="k">${icon('check')}${w.paidWord}</div><div class="v num">${fmt(sumOf(live.filter(d => d.status === 'PAID')))}</div><div class="s">${plural(live.length - unpaid.length, w.one)}</div></div>
      <div class="card kpi"><div class="k">${icon('rupee')}Total value</div><div class="v num">${fmt(sumOf(live))}</div><div class="s">Including GST, excluding cancelled</div></div>
    </div>
    <div class="toolbar no-print">
      <div class="tabs">${['ALL', 'UNPAID', 'PAID', 'CANCELLED'].map(s => `<button class="${F.docStatus === s ? 'on' : ''}" onclick="F.docStatus='${s}';route()">${s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()} (${count(s)})</button>`).join('')}</div>
      <div class="search">${icon('search')}<input placeholder="Search number or ${w.party.toLowerCase()}" value="${esc(F.q)}" onchange="F.q=this.value;route()"></div>
      <div class="spacer"></div>
      ${canWrite() ? `<button class="btn primary" onclick="newDoc('${kind}')">${icon('plus')} New ${w.one}</button>` : ''}
    </div>
    <div class="card flush"><div class="table-wrap"><table>
      <thead><tr><th>Number</th><th>Date</th><th>${w.party}</th><th class="r">Taxable</th><th class="r">GST</th><th class="r">Total</th><th>Status</th><th class="no-print"></th></tr></thead><tbody>
      ${list.map(d => `<tr><td><a href="#/doc/${d.id}"><b>${d.docNo}</b></a></td><td class="num">${fdate(d.date)}</td>
        <td>${esc(d.partyName)}<div class="sub">${esc(d.accountName)}</div></td>
        <td class="r num">${fmt(d.subTotal)}</td><td class="r num">${fmt(d.tax)}</td><td class="r num"><b>${fmt(d.total)}</b></td>
        <td>${statusBadge(d.status)}${d.paidOn ? `<div class="sub">${fdate(d.paidOn)}</div>` : ''}</td>
        <td class="no-print"><div class="actions">
          <a class="btn sm ghost" href="#/doc/${d.id}" title="View" aria-label="View">${icon('eye')}</a>
          ${canWrite() && d.status === 'UNPAID' ? `<button class="btn sm" onclick="payDoc(${d.id})">${w.pay}</button>
            <button class="btn sm ghost danger" title="Cancel" aria-label="Cancel" onclick="cancelDoc(${d.id})">${icon('x')}</button>` : ''}
        </div></td></tr>`).join('') || emptyRow(8, `No ${w.one}s found.`)}
      </tbody></table></div></div>`;
}
VIEWS.invoices = el => docsPage(el, 'SALE');
VIEWS.bills = el => docsPage(el, 'PURCHASE');

function itemRow(it = { description: '', qty: 1, rate: '' }) {
  return `<tr><td><input name="_desc" value="${esc(it.description)}" placeholder="Description of goods or services"></td>
    <td style="width:90px"><input name="_qty" type="number" min="0" step="0.01" value="${it.qty}"></td>
    <td style="width:150px"><input name="_rate" type="number" min="0" step="0.01" value="${it.rate}" placeholder="0.00"></td>
    <td class="r num" style="width:150px;padding-top:14px" data-amt>${fmt(0)}</td>
    <td style="width:44px"><button type="button" class="icon-btn" data-del aria-label="Remove item">${icon('trash')}</button></td></tr>`;
}
async function newDoc(kind) {
  const w = DOC_WORDS[kind];
  const [accs, parties] = await Promise.all([loadAccounts(), GET('/parties?kind=' + w.partyKind)]);
  const accFilter = kind === 'SALE' ? a => a.type === 'Income' : a => (a.type === 'Expense' || a.type === 'Asset') && !a.cash && !a.system;
  if (!parties.length) {
    toast(`Add a ${w.party.toLowerCase()} first.`, 'err');
    F.partyKind = w.partyKind; location.hash = '#/parties';
    return;
  }
  const saved = await modal({
    title: 'New ' + w.title.toLowerCase(), submit: 'Save and post', wide: true,
    body: `<div class="form-grid">
      ${field(w.party, `<select name="partyId">${parties.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select>`, 'c2')}
      ${field('Date', `<input type="date" name="date" value="${todayStr()}">`, 'c2')}
      ${field('GST rate', `<select name="gstRate">${[0, 5, 12, 18, 28].map(g => `<option value="${g}" ${g === 18 ? 'selected' : ''}>${g}%</option>`).join('')}</select>`, 'c2')}
      ${field(w.acc, `<select name="accountId">${accOptions(accs, null, accFilter)}</select>`, 'c3')}
      ${field('Notes (optional)', '<input name="notes" placeholder="e.g. PO number, payment terms">', 'c3')}
      </div>
      <div class="table-wrap" style="margin-top:18px"><table class="lines">
        <thead><tr><th>Item</th><th class="r">Qty</th><th class="r">Rate (Rs.)</th><th class="r">Amount</th><th></th></tr></thead>
        <tbody id="items">${itemRow()}</tbody></table></div>
      <button type="button" class="btn sm" id="addItem" style="margin:8px 0 0 6px">${icon('plus')} Add item</button>
      <div class="totals" id="docTotals"></div>`,
    onOpen: form => {
      const tbody = form.querySelector('#items');
      const recalc = () => {
        let sub = 0;
        tbody.querySelectorAll('tr').forEach(tr => {
          const amt = (+tr.querySelector('[name=_qty]').value || 0) * (+tr.querySelector('[name=_rate]').value || 0);
          sub += Math.round(amt * 100) / 100;
          tr.querySelector('[data-amt]').textContent = fmt(amt);
        });
        const rate = +form.gstRate.value, tax = Math.round(sub * rate) / 100;
        form.querySelector('#docTotals').innerHTML = `
          <div class="ln"><span class="muted">Taxable value</span><span class="num">${fmt(sub)}</span></div>
          <div class="ln"><span class="muted">CGST ${rate / 2}%</span><span class="num">${fmt(tax / 2)}</span></div>
          <div class="ln"><span class="muted">SGST ${rate / 2}%</span><span class="num">${fmt(tax / 2)}</span></div>
          <div class="ln tot"><span>Total</span><span class="num">${fmt(sub + tax)}</span></div>`;
      };
      form.addEventListener('input', recalc);
      form.addEventListener('change', recalc);
      form.querySelector('#addItem').onclick = () => { tbody.insertAdjacentHTML('beforeend', itemRow()); tbody.lastElementChild.querySelector('input').focus(); recalc(); };
      tbody.addEventListener('click', e => {
        const b = e.target.closest('[data-del]');
        if (b && tbody.children.length > 1) { b.closest('tr').remove(); recalc(); }
      });
      recalc();
    },
    onSubmit: async (d, form) => {
      const items = [...form.querySelectorAll('#items tr')].map(tr => ({
        description: tr.querySelector('[name=_desc]').value.trim(),
        qty: tr.querySelector('[name=_qty]').value, rate: tr.querySelector('[name=_rate]').value
      })).filter(i => i.description || i.rate);
      const doc = await api('POST', '/invoices', { kind, partyId: d.partyId, date: d.date, gstRate: d.gstRate, accountId: d.accountId, notes: d.notes, items });
      toast(`${doc.docNo} saved and posted to the books.`);
      return doc;
    }
  });
  if (saved) location.hash = '#/doc/' + saved.id;
}
async function payDoc(id) {
  const doc = DOCS.find(d => d.id === id) || await GET('/invoices/' + id);
  const w = DOC_WORDS[doc.kind];
  await loadAccounts();
  await modal({
    title: `${w.pay}: ${doc.docNo}`, submit: w.pay,
    body: `<p style="margin:0 0 14px">${esc(doc.partyName)}, total <b class="num">${fmt(doc.total)}</b></p><div class="form-grid">
      ${field(doc.kind === 'SALE' ? 'Received into' : 'Paid from', `<select name="cashAccountId">${accOptions(ACC, null, a => a.cash)}</select>`, 'c3')}
      ${field('Date', `<input type="date" name="date" value="${todayStr()}">`, 'c3')}</div>`,
    onSubmit: async d => { await api('POST', `/invoices/${id}/pay`, d); toast(`Payment recorded against ${doc.docNo}.`); route(); }
  });
}
async function cancelDoc(id) {
  const doc = DOCS.find(d => d.id === id) || await GET('/invoices/' + id);
  if (!await confirmBox('Cancel ' + doc.docNo, `Cancel <b>${doc.docNo}</b> for ${esc(doc.partyName)}? Its journal entry will be removed. The number stays reserved.`, 'Cancel document')) return;
  try { await api('POST', `/invoices/${id}/cancel`); toast(doc.docNo + ' cancelled.'); route(); } catch (e) { toast(e.message, 'err'); }
}

VIEWS.doc = async (el, id) => {
  const d = await GET('/invoices/' + id);
  const sale = d.kind === 'SALE', w = DOC_WORDS[d.kind], c = COMPANY;
  DOC_BACK = sale ? 'invoices' : 'bills';
  DOCS = [d];
  $('#pageTitle').textContent = d.docNo;
  document.title = d.docNo + ' | StaplerLabs Accounts';
  const half = +d.gstRate / 2;
  el.innerHTML = `
    <div class="toolbar no-print">
      <a class="btn" href="#/${DOC_BACK}">${icon('back')} All ${w.one}s</a>
      <div class="spacer"></div>
      ${canWrite() && d.status === 'UNPAID' ? `<button class="btn" onclick="payDoc(${d.id})">${w.pay}</button>
        <button class="btn danger" onclick="cancelDoc(${d.id})">Cancel</button>` : ''}
      <button class="btn primary" onclick="print()">${icon('print')} Print or save as PDF</button>
    </div>
    <div class="card doc">
      <div class="doc-head">
        <div class="co"><img src="logo.png" alt="StaplerLabs logo"><div><h2>${esc(c.name)}</h2><div>${esc(c.address)}</div><div>${esc(c.email)} | ${esc(c.phone)}</div>${c.gstin ? `<div>GSTIN: ${esc(c.gstin)}</div>` : ''}</div></div>
        <div class="meta"><div class="title">${sale ? 'Tax invoice' : 'Purchase bill'}</div>
          <div>No. <b>${d.docNo}</b></div><div>Date: ${fdate(d.date)}</div><div style="margin-top:6px">${statusBadge(d.status)}</div></div>
      </div>
      <div class="party">
        <div><div class="lbl">${sale ? 'Bill to' : 'Vendor'}</div><b>${esc(d.partyName)}</b><div>${esc(d.partyAddress || '')}</div>
          ${d.partyGstin ? `<div>GSTIN: ${esc(d.partyGstin)}</div>` : ''}</div>
        <div><div class="lbl">Details</div><div>Account: ${esc(d.accountName)}</div><div>GST rate: ${+d.gstRate}%</div>
          ${d.paidOn ? `<div>${w.paidWord} on ${fdate(d.paidOn)}</div>` : ''}${d.notes ? `<div>Notes: ${esc(d.notes)}</div>` : ''}</div>
      </div>
      <table><thead><tr><th>#</th><th>Description</th><th class="r">Qty</th><th class="r">Rate</th><th class="r">Amount</th></tr></thead><tbody>
        ${d.items.map((it, i) => `<tr><td>${i + 1}</td><td>${esc(it.description)}</td><td class="r num">${+it.qty}</td><td class="r num">${fmt(it.rate)}</td><td class="r num">${fmt(it.amount)}</td></tr>`).join('')}
      </tbody></table>
      <div class="totals" style="margin-top:12px">
        <div class="ln"><span class="muted">Taxable value</span><span class="num">${fmt(d.subTotal)}</span></div>
        ${+d.gstRate ? `<div class="ln"><span class="muted">CGST ${half}%</span><span class="num">${fmt(d.tax / 2)}</span></div>
        <div class="ln"><span class="muted">SGST ${half}%</span><span class="num">${fmt(d.tax / 2)}</span></div>` : ''}
        <div class="ln tot"><span>Total</span><span class="num">${fmt(d.total)}</span></div>
      </div>
      <div class="words"><b>Amount in words:</b> ${inWords(+d.total)}</div>
      <div class="foot"><span>This is a computer-generated document.</span><div class="sign">For ${esc(c.name)}<br><br>Authorised signatory</div></div>
    </div>`;
};

/* ---------- Payments and receipts ---------- */
VIEWS.vouchers = async el => {
  ensureRange();
  const all = await GET('/entries?' + qs({ from: F.from, to: F.to }));
  const list = all.filter(e => e.source === 'PAYMENT' || e.source === 'RECEIPT').reverse();
  el.innerHTML = `
    <div class="toolbar no-print">${rangeFields()}<div class="spacer"></div>
      ${canWrite() ? `<button class="btn" onclick="newVoucher('RECEIPT')">${icon('down')} Record receipt</button>
      <button class="btn primary" onclick="newVoucher('PAYMENT')">${icon('up')} Record payment</button>` : ''}</div>
    <div class="card flush"><div class="table-wrap"><table>
      <thead><tr><th>No.</th><th>Date</th><th>Type</th><th>Narration</th><th>Accounts</th><th class="r">Amount</th><th class="no-print"></th></tr></thead><tbody>
      ${list.map(e => { const amt = e.lines.reduce((s, l) => s + l.debit, 0); const pay = e.source === 'PAYMENT';
        return `<tr><td class="num">${e.entryNo}</td><td class="num">${fdate(e.date)}</td>
        <td><span class="badge ${pay ? 'b-unpaid' : 'b-paid'}">${pay ? 'Payment' : 'Receipt'}</span></td>
        <td>${esc(e.narration)}${e.refNo ? `<div class="sub">Against ${esc(e.refNo)}</div>` : ''}</td>
        <td class="sub">${e.lines.map(l => esc(l.accountName)).join(' / ')}</td>
        <td class="r num ${pay ? '' : 'good'}">${pay ? '' : '+ '}${fmt(amt)}</td>
        <td class="no-print"><div class="actions">${canWrite() && !e.locked ? `<button class="btn sm ghost danger" aria-label="Delete" onclick="deleteEntry(${e.id},${e.entryNo})">${icon('trash')}</button>` : ''}</div></td></tr>`; }).join('')
        || emptyRow(7, 'No payments or receipts in this period.')}
      </tbody></table></div></div>`;
};
async function newVoucher(type) {
  await loadAccounts();
  const pay = type === 'PAYMENT';
  await modal({
    title: pay ? 'Record a payment' : 'Record a receipt', submit: pay ? 'Save payment' : 'Save receipt',
    body: `<div class="form-grid">
      ${field('Date', `<input type="date" name="date" value="${todayStr()}">`, 'c2')}
      ${field('Amount (Rs.)', '<input type="number" name="amount" min="0" step="0.01" placeholder="0.00">', 'c2')}
      ${field(pay ? 'Paid from' : 'Received into', `<select name="cashAccountId">${accOptions(ACC, null, a => a.cash)}</select>`, 'c2')}
      ${field(pay ? 'Paid for (account)' : 'Received for (account)', `<select name="accountId">${accOptions(ACC, null, a => !a.cash && (pay ? a.type !== 'Income' : a.type !== 'Expense'))}</select>`, 'c6',
        pay ? 'Usually an expense, for example Office Rent.' : 'Usually income, capital or a loan.')}
      ${field('Narration', `<input name="narration" placeholder="${pay ? 'e.g. Internet bill for November' : 'e.g. Interest credited by bank'}">`, 'c6')}</div>`,
    onSubmit: async d => {
      if (!(+d.amount > 0)) throw new Error('Enter an amount greater than zero.');
      await api('POST', '/vouchers', { ...d, type });
      toast(pay ? 'Payment saved.' : 'Receipt saved.');
      route();
    }
  });
}
async function deleteEntry(id, no) {
  if (!await confirmBox('Delete entry', `Delete journal entry <b>#${no}</b>? This cannot be undone.`, 'Delete')) return;
  try { await api('DELETE', '/entries/' + id); toast('Entry #' + no + ' deleted.'); route(); } catch (e) { toast(e.message, 'err'); }
}

/* ---------- Journal ---------- */
function jRow() {
  return `<tr><td><select name="_acc"><option value="">Select account</option>${accOptions(ACC)}</select></td>
    <td style="width:170px"><input name="_dr" type="number" min="0" step="0.01" placeholder="0.00"></td>
    <td style="width:170px"><input name="_cr" type="number" min="0" step="0.01" placeholder="0.00"></td>
    <td style="width:44px"><button type="button" class="icon-btn" data-del aria-label="Remove line">${icon('trash')}</button></td></tr>`;
}
VIEWS.journal = async el => {
  ensureRange();
  const [, all] = await Promise.all([loadAccounts(), GET('/entries?' + qs({ from: F.from, to: F.to }))]);
  const list = all.filter(e => e.source === 'JOURNAL').reverse().slice(0, 25);
  el.innerHTML = `
    ${canWrite() ? `<form class="card" id="jForm" novalidate>
      <div class="card-h"><div><h2>New journal entry</h2><p>Debits must equal credits before the entry can be posted.</p></div></div>
      <div class="form-grid">
        ${field('Date', `<input type="date" name="date" value="${todayStr()}">`, 'c2')}
        ${field('Narration', '<input name="narration" placeholder="Describe the transaction">', 'c4')}</div>
      <div class="table-wrap" style="margin-top:14px"><table class="lines">
        <thead><tr><th>Account</th><th class="r">Debit (Rs.)</th><th class="r">Credit (Rs.)</th><th></th></tr></thead>
        <tbody id="jLines">${jRow()}${jRow()}</tbody>
        <tfoot><tr><td><span class="diff" id="jDiff"></span></td><td class="r num" id="jDr"></td><td class="r num" id="jCr"></td><td></td></tr></tfoot></table></div>
      <div class="toolbar" style="margin:12px 0 0"><button type="button" class="btn" id="jAdd">${icon('plus')} Add line</button><div class="spacer"></div>
        <button type="reset" class="btn ghost">Clear</button><button class="btn primary" id="jPost">Post entry</button></div>
    </form>` : ''}
    <div class="card flush"><div class="card-h"><div><h2>Recent journal entries</h2><p>Manual entries from ${fdate(F.from)} to ${fdate(F.to)}</p></div></div>
      <div class="table-wrap"><table>
      <thead><tr><th>No.</th><th>Date</th><th>Particulars</th><th class="r">Debit</th><th class="r">Credit</th><th></th></tr></thead><tbody>
      ${list.map(e => entryRow(e)).join('') || emptyRow(6, 'No manual journal entries in this period.')}
      </tbody></table></div></div>`;
  const form = $('#jForm');
  if (!form) return;
  const tb = $('#jLines');
  const recalc = () => {
    let dr = 0, cr = 0;
    tb.querySelectorAll('tr').forEach(tr => { dr += +tr.querySelector('[name=_dr]').value || 0; cr += +tr.querySelector('[name=_cr]').value || 0; });
    dr = Math.round(dr * 100) / 100; cr = Math.round(cr * 100) / 100;
    $('#jDr').textContent = fmt(dr); $('#jCr').textContent = fmt(cr);
    const diff = $('#jDiff');
    if (!dr && !cr) { diff.className = 'diff muted'; diff.textContent = 'Totals'; }
    else if (dr === cr) { diff.className = 'diff good'; diff.innerHTML = icon('check') + ' Balanced'; }
    else { diff.className = 'diff bad'; diff.textContent = 'Difference ' + fmt(Math.abs(dr - cr)); }
  };
  form.addEventListener('input', e => {
    const tr = e.target.closest('tr');   // a line takes either a debit or a credit
    if (tr && e.target.name === '_dr' && e.target.value) tr.querySelector('[name=_cr]').value = '';
    if (tr && e.target.name === '_cr' && e.target.value) tr.querySelector('[name=_dr]').value = '';
    recalc();
  });
  form.addEventListener('reset', () => setTimeout(() => { tb.innerHTML = jRow() + jRow(); form.date.value = todayStr(); recalc(); }));
  $('#jAdd').onclick = () => { tb.insertAdjacentHTML('beforeend', jRow()); recalc(); };
  tb.addEventListener('click', e => { const b = e.target.closest('[data-del]'); if (b && tb.children.length > 2) { b.closest('tr').remove(); recalc(); } });
  form.onsubmit = async e => {
    e.preventDefault();
    const lines = [...tb.querySelectorAll('tr')].map(tr => ({ accountId: tr.querySelector('[name=_acc]').value,
      debit: tr.querySelector('[name=_dr]').value, credit: tr.querySelector('[name=_cr]').value }))
      .filter(l => l.debit || l.credit);
    if (lines.some(l => !l.accountId)) { toast('Choose an account on every line that has an amount.', 'err'); return; }
    $('#jPost').disabled = true;
    try {
      await api('POST', '/entries', { date: form.date.value, narration: form.narration.value, lines });
      toast('Journal entry posted.'); route();
    } catch (x) { toast(x.message, 'err'); $('#jPost').disabled = false; }
  };
  recalc();
};

function entryRow(e) {
  const src = e.source.charAt(0) + e.source.slice(1).toLowerCase();
  return `<tr><td class="num">${e.entryNo}</td><td class="num">${fdate(e.date)}</td>
    <td>${e.lines.map(l => `<div>${l.credit > 0 ? '<span class="muted" style="padding-left:18px">To </span>' : ''}${esc(l.accountName)}${l.debit > 0 ? ' <span class="muted">Dr</span>' : ''}</div>`).join('')}
      <div class="sub" style="margin-top:3px">${esc(e.narration)} <span class="badge b-plain">${src}</span>${e.refNo ? ` <span class="badge b-plain">${esc(e.refNo)}</span>` : ''}</div></td>
    <td class="r num">${e.lines.map(l => `<div>${l.debit > 0 ? fmt(l.debit) : '&nbsp;'}</div>`).join('')}</td>
    <td class="r num">${e.lines.map(l => `<div>${l.credit > 0 ? fmt(l.credit) : '&nbsp;'}</div>`).join('')}</td>
    <td class="no-print"><div class="actions">${canWrite() && !e.locked ? `<button class="btn sm ghost danger" aria-label="Delete" onclick="deleteEntry(${e.id},${e.entryNo})">${icon('trash')}</button>` : ''}</div></td></tr>`;
}

/* ---------- Day book ---------- */
VIEWS.daybook = async el => {
  ensureRange();
  const list = await GET('/entries?' + qs({ from: F.from, to: F.to, q: F.q }));
  const total = list.reduce((s, e) => s + e.lines.reduce((t, l) => t + l.debit, 0), 0);
  el.innerHTML = `
    <div class="toolbar no-print">${rangeFields()}
      <div class="search">${icon('search')}<input placeholder="Search narration, number or document" value="${esc(F.q)}" onchange="F.q=this.value;route()"></div>
      ${reportTools('dayTable', 'day-book')}</div>
    <div class="card flush"><div class="card-h"><div><h2>Day book</h2><p>${plural(list.length, 'entry').replace('entrys', 'entries')}, ${fdate(F.from)} to ${fdate(F.to)}</p></div></div>
      <div class="table-wrap"><table id="dayTable">
      <thead><tr><th>No.</th><th>Date</th><th>Particulars</th><th class="r">Debit</th><th class="r">Credit</th><th class="no-print"></th></tr></thead>
      <tbody>${list.map(entryRow).join('') || emptyRow(6, 'No entries match.')}</tbody>
      <tfoot><tr><td colspan="3">Total</td><td class="r num">${fmt(total)}</td><td class="r num">${fmt(total)}</td><td class="no-print"></td></tr></tfoot>
      </table></div></div>`;
};

/* ---------- Ledger ---------- */
VIEWS.ledger = async el => {
  ensureRange();
  await loadAccounts();
  if (!accById(F.ledgerAcc)) F.ledgerAcc = (ACC.find(a => a.code === '1002') || ACC[0])?.id;
  const r = await GET('/reports/ledger?' + qs({ accountId: F.ledgerAcc, from: F.from, to: F.to }));
  const a = r.account, side = (a.type === 'Asset' || a.type === 'Expense') ? 'debit' : 'credit';
  el.innerHTML = `
    <div class="toolbar no-print">
      <div class="field" style="min-width:280px"><label>Account</label><select onchange="F.ledgerAcc=+this.value;route()">${accOptions(ACC, F.ledgerAcc)}</select></div>
      ${rangeFields()}${reportTools('ledTable', 'ledger-' + a.code)}</div>
    <div class="grid g3">
      <div class="card kpi"><div class="k">Opening balance</div><div class="v num">${fmt(r.opening)}</div><div class="s">On ${fdate(r.from)}</div></div>
      <div class="card kpi"><div class="k">Movement in period</div><div class="v num" style="font-size:16px;padding-top:6px">Dr ${fmt(r.totalDebit)}<br>Cr ${fmt(r.totalCredit)}</div></div>
      <div class="card kpi"><div class="k">Closing balance</div><div class="v num">${fmt(r.closing)}</div><div class="s">${a.type} account, normal ${side} balance</div></div>
    </div>
    <div class="card flush"><div class="card-h"><div><h2>${esc(a.code + ' ' + a.name)}</h2><p>${fdate(r.from)} to ${fdate(r.to)}</p></div></div>
      <div class="table-wrap"><table id="ledTable">
      <thead><tr><th>Date</th><th>No.</th><th>Particulars</th><th class="r">Debit</th><th class="r">Credit</th><th class="r">Balance</th></tr></thead><tbody>
      <tr><td class="num">${fdate(r.from)}</td><td></td><td><b>Opening balance</b></td><td></td><td></td><td class="r num">${fmt(r.opening)}</td></tr>
      ${r.rows.map(x => `<tr><td class="num">${fdate(x.date)}</td><td class="num">${x.entryNo}</td><td>${esc(x.particulars)}<div class="sub">${esc(x.narration)}</div></td>
        <td class="r num">${x.debit ? fmt(x.debit) : ''}</td><td class="r num">${x.credit ? fmt(x.credit) : ''}</td><td class="r num">${fmt(x.balance)}</td></tr>`).join('')}
      </tbody><tfoot><tr><td colspan="3">Closing balance</td><td class="r num">${fmt(r.totalDebit)}</td><td class="r num">${fmt(r.totalCredit)}</td><td class="r num">${fmt(r.closing)}</td></tr></tfoot>
      </table></div></div>`;
};

/* ---------- Trial balance ---------- */
VIEWS.trial = async el => {
  ensureAsOn();
  const r = await GET('/reports/trial-balance?asOn=' + F.asOn);
  el.innerHTML = `
    <div class="toolbar no-print">${asOnField()}${reportTools('tbTable', 'trial-balance-' + r.asOn)}</div>
    <div class="status-line ${r.balanced ? 'ok' : 'no'}">${icon(r.balanced ? 'check' : 'alert')}
      ${r.balanced ? 'Total debits equal total credits. The books are balanced.' : 'Debits and credits do not match.'}</div>
    <div class="card flush"><div class="card-h"><div><h2>${esc(COMPANY.name)}</h2><p>Trial balance as on ${fdate(r.asOn)}</p></div></div>
      <div class="table-wrap"><table id="tbTable">
      <thead><tr><th>Code</th><th>Account</th><th>Type</th><th class="r">Debit</th><th class="r">Credit</th></tr></thead><tbody>
      ${r.rows.map(x => `<tr><td class="num">${x.code}</td><td><span class="link" onclick="openLedger(${x.id})">${esc(x.name)}</span></td><td class="sub">${x.type}</td>
        <td class="r num">${x.debit ? fmt(x.debit) : ''}</td><td class="r num">${x.credit ? fmt(x.credit) : ''}</td></tr>`).join('') || emptyRow(5, 'No balances on this date.')}
      </tbody><tfoot><tr><td colspan="3">Total</td><td class="r num">${fmt(r.totalDebit)}</td><td class="r num">${fmt(r.totalCredit)}</td></tr></tfoot>
      </table></div></div>`;
};

/* ---------- Profit and loss ---------- */
const stmtLines = rows => rows.map(x => `<div class="ln"><span>${esc(x.name)}</span><span class="num">${fmt(x.amount)}</span></div>`).join('')
  || '<div class="ln muted"><span>None</span><span></span></div>';
VIEWS.pnl = async el => {
  ensureRange();
  const r = await GET('/reports/profit-loss?' + qs({ from: F.from, to: F.to }));
  const margin = r.totalIncome ? (r.net / r.totalIncome * 100).toFixed(1) : '0.0';
  el.innerHTML = `
    <div class="toolbar no-print">${rangeFields()}${reportTools(null)}</div>
    <div class="grid g3">
      <div class="card kpi"><div class="k">Total income</div><div class="v num">${fmt(r.totalIncome)}</div></div>
      <div class="card kpi"><div class="k">Total expenses</div><div class="v num">${fmt(r.totalExpense)}</div></div>
      <div class="card kpi"><div class="k">${r.net >= 0 ? 'Net profit' : 'Net loss'}</div><div class="v num ${r.net >= 0 ? 'good' : 'bad'}">${fmt(Math.abs(r.net))}</div><div class="s">${margin}% of income</div></div>
    </div>
    <div class="card stmt" style="max-width:760px">
      <div class="card-h"><div><h2>Profit and loss statement</h2><p>${esc(COMPANY.name)}, ${fdate(r.from)} to ${fdate(r.to)}</p></div></div>
      <h3>Income</h3>${stmtLines(r.income)}<div class="ln tot"><span>Total income</span><span class="num">${fmt(r.totalIncome)}</span></div>
      <h3>Expenses</h3>${stmtLines(r.expense)}<div class="ln tot"><span>Total expenses</span><span class="num">${fmt(r.totalExpense)}</span></div>
      <h3>Result</h3><div class="ln tot big"><span>${r.net >= 0 ? 'Net profit' : 'Net loss'}</span><span class="num ${r.net >= 0 ? 'good' : 'bad'}">${fmt(Math.abs(r.net))}</span></div>
    </div>`;
};

/* ---------- Balance sheet ---------- */
VIEWS.bs = async el => {
  ensureAsOn();
  const r = await GET('/reports/balance-sheet?asOn=' + F.asOn);
  el.innerHTML = `
    <div class="toolbar no-print">${asOnField()}${reportTools(null)}</div>
    <div class="status-line ${r.balanced ? 'ok' : 'no'}">${icon(r.balanced ? 'check' : 'alert')}
      ${r.balanced ? 'Assets equal capital plus liabilities. The balance sheet tallies.' : 'The balance sheet does not tally.'}</div>
    <p class="muted">${esc(COMPANY.name)}, balance sheet as on ${fdate(r.asOn)}</p>
    <div class="grid g2">
      <div class="card stmt"><div class="card-h"><h2>Capital and liabilities</h2></div>
        <h3>Capital</h3>${stmtLines(r.equity)}
        <div class="ln"><span>Profit and loss account (${r.profit >= 0 ? 'profit' : 'loss'})</span><span class="num">${fmt(r.profit)}</span></div>
        <h3>Liabilities</h3>${stmtLines(r.liabilities)}
        <div class="ln tot big"><span>Total</span><span class="num">${fmt(r.totalLiabilities)}</span></div></div>
      <div class="card stmt"><div class="card-h"><h2>Assets</h2></div>
        <h3>Assets</h3>${stmtLines(r.assets)}
        <div class="ln tot big"><span>Total</span><span class="num">${fmt(r.totalAssets)}</span></div></div>
    </div>`;
};

/* ---------- GST summary ---------- */
VIEWS.gst = async el => {
  ensureRange();
  const r = await GET('/reports/gst?' + qs({ from: F.from, to: F.to }));
  el.innerHTML = `
    <div class="toolbar no-print">${rangeFields()}${reportTools('gstTable', 'gst-summary')}</div>
    <div class="grid g3">
      <div class="card kpi"><div class="k">${icon('up')}Output GST (on sales)</div><div class="v num">${fmt(r.output)}</div><div class="s">Collected from customers</div></div>
      <div class="card kpi"><div class="k">${icon('down')}Input GST (on purchases)</div><div class="v num">${fmt(r.input)}</div><div class="s">Paid to vendors, available as credit</div></div>
      <div class="card kpi"><div class="k">${icon('percent')}${r.net >= 0 ? 'Net GST payable' : 'Net GST credit'}</div><div class="v num">${fmt(Math.abs(r.net))}</div><div class="s">Output minus input</div></div>
    </div>
    <div class="card flush"><div class="card-h"><div><h2>Documents in period</h2><p>${fdate(r.from)} to ${fdate(r.to)}, cancelled documents excluded</p></div></div>
      <div class="table-wrap"><table id="gstTable">
      <thead><tr><th>Number</th><th>Date</th><th>Type</th><th>Party</th><th>GSTIN</th><th class="r">Rate</th><th class="r">Taxable</th><th class="r">GST</th><th class="r">Total</th></tr></thead><tbody>
      ${r.docs.map(d => `<tr><td>${d.docNo}</td><td class="num">${fdate(d.date)}</td><td><span class="badge b-plain">${d.kind === 'SALE' ? 'Sale' : 'Purchase'}</span></td>
        <td>${esc(d.party)}</td><td class="num">${esc(d.gstin || '')}</td><td class="r">${+d.rate}%</td><td class="r num">${fmt(d.taxable)}</td><td class="r num">${fmt(d.tax)}</td><td class="r num">${fmt(d.total)}</td></tr>`).join('')
        || emptyRow(9, 'No invoices or bills in this period.')}
      </tbody></table></div></div>`;
};

/* ---------- Users ---------- */
VIEWS.users = async el => {
  const users = await GET('/users');
  const roleText = { ADMIN: 'Full access, users and settings', ACCOUNTANT: 'Can record and edit transactions', VIEWER: 'Can only view and print reports' };
  el.innerHTML = `
    <div class="toolbar"><div class="spacer"></div><button class="btn primary" onclick="newUser()">${icon('plus')} New user</button></div>
    <div class="card flush"><div class="table-wrap"><table>
      <thead><tr><th>User</th><th>Role</th><th>Status</th><th></th></tr></thead><tbody>
      ${users.map(u => `<tr><td><b>${esc(u.fullName)}</b><div class="sub">${esc(u.username)}</div></td>
        <td><span class="badge b-type">${u.role.charAt(0) + u.role.slice(1).toLowerCase()}</span><div class="sub">${roleText[u.role]}</div></td>
        <td>${u.active ? '<span class="badge b-paid">Active</span>' : '<span class="badge b-cancelled">Inactive</span>'}</td>
        <td><div class="actions">${u.id !== ME.userId ? `<button class="btn sm" onclick="setActive(${u.id},${!u.active})">${u.active ? 'Deactivate' : 'Activate'}</button>` : '<span class="sub">You</span>'}</div></td></tr>`).join('')}
      </tbody></table></div></div>`;
};
function newUser() {
  modal({
    title: 'New user', submit: 'Create user',
    body: `<div class="form-grid">
      ${field('Full name', '<input name="fullName">', 'c6')}
      ${field('Username', '<input name="username" autocomplete="off">', 'c3')}
      ${field('Role', '<select name="role"><option value="ACCOUNTANT">Accountant</option><option value="VIEWER">Viewer</option><option value="ADMIN">Administrator</option></select>', 'c3')}
      ${field('Password', '<input type="password" name="password" autocomplete="new-password">', 'c6', 'At least 8 characters, with letters and numbers. Share it with the user privately.')}</div>`,
    onSubmit: async d => { await api('POST', '/users', d); toast('User ' + d.username + ' created.'); route(); }
  });
}
async function setActive(id, active) {
  try { await api('PUT', `/users/${id}/active`, { active }); toast(active ? 'User activated.' : 'User deactivated.'); route(); } catch (e) { toast(e.message, 'err'); }
}

/* ---------- Company and settings ---------- */
VIEWS.settings = async el => {
  COMPANY = await GET('/company');
  const audit = isAdmin() ? await GET('/audit') : [];
  const ro = isAdmin() ? '' : 'disabled';
  const c = COMPANY;
  el.innerHTML = `
    <form class="card" id="coForm" novalidate>
      <div class="card-h"><div><h2>Company details</h2><p>Printed on invoices, bills and reports.${isAdmin() ? '' : ' Only an administrator can change these.'}</p></div></div>
      <div class="form-grid">
        ${field('Company name', `<input name="name" value="${esc(c.name)}" ${ro}>`, 'c4')}
        ${field('GSTIN', `<input name="gstin" value="${esc(c.gstin || '')}" maxlength="15" style="text-transform:uppercase" ${ro}>`, 'c2')}
        ${field('Address', `<input name="address" value="${esc(c.address || '')}" ${ro}>`, 'c6')}
        ${field('Email', `<input name="email" value="${esc(c.email || '')}" ${ro}>`, 'c3')}
        ${field('Phone', `<input name="phone" value="${esc(c.phone || '')}" ${ro}>`, 'c3')}</div>
      ${isAdmin() ? `<div class="toolbar" style="margin:16px 0 0"><div class="spacer"></div><button class="btn primary">Save details</button></div>` : ''}
    </form>
    ${isAdmin() ? `
    <div class="card"><div class="card-h"><div><h2>Data</h2><p>Reset keeps users and company details. All accounts, parties and transactions are replaced.</p></div></div>
      <div class="toolbar" style="margin:0"><button class="btn" onclick="resetData(true)">Load demo data</button><button class="btn danger" onclick="resetData(false)">Start with empty books</button></div></div>
    <div class="card flush"><div class="card-h"><div><h2>Audit log</h2><p>Last 200 actions</p></div></div>
      <div class="table-wrap" style="max-height:420px;overflow:auto"><table>
      <thead><tr><th>When</th><th>User</th><th>Action</th><th>Details</th></tr></thead><tbody>
      ${audit.map(a => `<tr><td class="num sub">${esc(a.at)}</td><td>${esc(a.userName)}</td><td><span class="badge b-plain">${esc(a.action)}</span></td><td>${esc(a.details)}</td></tr>`).join('')}
      </tbody></table></div></div>` : ''}`;
  const form = $('#coForm');
  form.onsubmit = async e => {
    e.preventDefault();
    try { COMPANY = await api('PUT', '/company', formData(form)); toast('Company details saved.'); renderShell(); route(); }
    catch (x) { toast(x.message, 'err'); }
  };
};
async function resetData(demo) {
  const ok = await confirmBox(demo ? 'Load demo data' : 'Start with empty books',
    'This replaces <b>all</b> accounts, customers, vendors, invoices, bills and entries. It cannot be undone.', demo ? 'Load demo data' : 'Erase and start fresh');
  if (!ok) return;
  try { await api('POST', '/reset', { demo }); toast(demo ? 'Demo data loaded.' : 'Books cleared. Default accounts created.'); location.hash = '#/dashboard'; route(); }
  catch (e) { toast(e.message, 'err'); }
}

/* ===================== Start ===================== */
(async function boot() {
  try { ME = await GET('/me'); } catch (e) { ME = null; }
  if (!ME) { renderLogin(); return; }
  try { await startApp(); } catch (e) { renderLogin(e.message); }
})();
