/* 哦鲸鲸记账 — 界面与交互 */
(function () {
  'use strict';

  var C = window.WhaleCore;
  var native = window.AndroidNative || null;
  var LS_KEY = C.LS_KEY;
  var appVersion = '1.1.0';

  var state = loadState();
  var ui = {
    tab: 'home',
    month: C.currentMonth(),
    filter: 'all',
    catType: 'expense',
    scroll: {},
    tx: null,
    sheet: null,
    budgetDraft: '',
    savingsDraft: '',
    savingsMonth: '',
    aiForm: null,
    aiTest: null,
    aiInput: '',
    aiBusy: false,
    aiError: '',
    lastQuestion: '',
    lastMode: 'chat',
    aiReminderBusy: false,
    confirm: null,
    bannerDismissedLevel: 0,
    bannerDismissedKey: '',
    showKey: false,
    lastDay: C.todayStr(),
    pending: {},
    pendingSeq: 0
  };

  var PRESETS = {
    deepseek: { baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
    openai: { baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini' },
    custom: { baseUrl: '', model: '' }
  };

  var PROVIDERS = [
    { id: 'deepseek', name: 'DeepSeek' },
    { id: 'openai', name: 'OpenAI' },
    { id: 'custom', name: '自定义' }
  ];

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function escapeHtml(s) { return C.escapeHtml(s); }
  function money(n, decimals) { return C.formatMoney(n, decimals === undefined ? 2 : decimals); }
  function money0(n) { return C.formatMoney(n, 0); }
  function signedMoney(n) {
    var num = Number(n) || 0;
    return (num < 0 ? '-¥' : '¥') + money(Math.abs(num));
  }
  function icon(name, cls) {
    var id = String(name || '');
    if (id.indexOf('i-') !== 0) id = 'i-' + id;
    return '<svg class="ico ' + (cls || '') + '" aria-hidden="true"><use href="#' + id + '"></use></svg>';
  }
  function whale(cls) {
    return '<svg class="' + (cls || '') + '" viewBox="0 0 96 96" aria-hidden="true"><use href="#whale"></use></svg>';
  }
  var MASCOT_FILES = {
    rice: 'img/mascot-rice.jpg',
    think: 'img/mascot-think.jpg',
    fish: 'img/mascot-fish.jpg',
    justRice: 'img/mascot-just-rice.jpg'
  };
  function mascot(name, cls) {
    var file = MASCOT_FILES[name] || MASCOT_FILES.rice;
    return '<img class="mascot ' + (cls || '') + '" src="' + file + '" alt="" draggable="false">';
  }
  function timeText(ts) {
    var d = new Date(Number(ts) || Date.now());
    return C.pad2(d.getHours()) + ':' + C.pad2(d.getMinutes());
  }
  function todayText() {
    var d = new Date();
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + C.weekdayName(C.todayStr());
  }
  function greetingText() {
    var h = new Date().getHours();
    if (h < 5) return '夜深了';
    if (h < 11) return '早上好';
    if (h < 14) return '中午好';
    if (h < 18) return '下午好';
    return '晚上好';
  }
  function shiftDate(dateStr, delta) {
    var d = C.parseDate(dateStr) || new Date();
    d.setDate(d.getDate() + delta);
    return C.formatDate(d);
  }
  function catOf(tx) { return C.CATEGORY_MAP[tx.category] || C.CATEGORY_MAP.other; }
  function catColor(tx) { return (catOf(tx) || {}).color || '#9FB0C0'; }

  function loadState() {
    var raw = '';
    if (native) {
      try { raw = native.loadState ? native.loadState() : ''; } catch (e) { raw = ''; }
    }
    if (!raw) {
      try { raw = localStorage.getItem(LS_KEY) || ''; } catch (e) { raw = ''; }
    }
    var parsed = null;
    if (raw) {
      try { parsed = JSON.parse(raw); } catch (e) { parsed = null; }
    }
    return C.normalizeState(parsed);
  }

  function save() {
    state.updatedAt = Date.now();
    var json = JSON.stringify(state);
    try { localStorage.setItem(LS_KEY, json); } catch (e) { /* ignore */ }
    if (native && native.saveState) {
      try { native.saveState(json); } catch (e) { /* ignore */ }
    }
  }

  function savingsGoalFor(month) {
    var goals = state.savingsGoals || {};
    return Number(goals[month]) || 0;
  }

  function statsFor(month) {
    var mtx = C.monthTransactions(state.transactions, month);
    var t = C.totals(mtx);
    var bs = C.budgetStats(t.expense, state.budget, month);
    var ss = C.savingsStats(t.income, t.expense, savingsGoalFor(month), month);
    return { mtx: mtx, totals: t, bs: bs, ss: ss };
  }

  function currentStats() {
    return statsFor(C.currentMonth());
  }

  function isAiConfigured() {
    var ai = state.settings.ai || {};
    return !!(ai.baseUrl && ai.model && ai.apiKey && String(ai.apiKey).trim());
  }

  function cleanAiText(text) {
    return String(text || '')
      .replace(/^["“「『]+|["”」』]+$/g, '')
      .replace(/^(鲸鲸提醒|提醒|预算提醒)[:：]\s*/, '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 140);
  }

  function formatHost(url) {
    try { return new URL(C.normalizeEndpoint(url)).host; } catch (e) { return url || '未设置'; }
  }

  /* ---------------- 页面渲染 ---------------- */

  function render(options) {
    options = options || {};
    var view = $('#view');
    if (!view) return;
    var prevScroll = view.scrollTop;
    view.classList.toggle('no-scroll', ui.tab === 'ai');

    var html = '';
    if (ui.tab === 'home') html = pageHome();
    else if (ui.tab === 'records') html = pageRecords();
    else if (ui.tab === 'ai') html = pageAi();
    else html = pageSettings();

    view.innerHTML = html;
    setActiveTab();
    if (options.reset) view.scrollTop = 0;
    else if (options.restore !== undefined) view.scrollTop = options.restore;
    else view.scrollTop = prevScroll;

    if (ui.tab === 'ai') {
      requestAnimationFrame(function () { scrollChatBottom(false); });
    }
  }

  function setActiveTab() {
    $$('.tab').forEach(function (b) {
      b.classList.toggle('active', b.dataset.tab === ui.tab);
    });
  }

  function pageHomeLegacy() {
    var st = currentStats();
    var t = st.totals;
    var bs = st.bs;
    var month = C.currentMonth();
    var today = C.todayStr();
    var todayTx = state.transactions.filter(function (x) { return x.date === today; });
    var todayTotals = C.totals(todayTx);
    var todayGroups = C.groupByDate(todayTx);
    var top = C.categoryBreakdown(st.mtx, 'expense')[0] || null;
    var rem = C.localReminder(bs, top, month);
    var alert = getAlert(st, top);
    var recent = st.mtx.slice().sort(function (a, b) {
      if (a.date !== b.date) return a.date < b.date ? 1 : -1;
      return b.createdAt - a.createdAt;
    }).slice(0, 3);

    var balancePrefix = t.balance < 0 ? '-¥' : '¥';
    var barWidth = bs.hasBudget ? Math.max(0, Math.min(100, bs.ratio * 100)) : 0;
    var budgetCaption = bs.hasBudget
      ? (bs.level >= 3
        ? '已超支 ¥' + money(bs.over) + ' · 慢一点也没关系'
        : '已用 ' + bs.percent + '% · 还可花 ¥' + money(Math.max(bs.remaining, 0)))
      : '还没有设置每月限额';

    return '<div class="page page-home">' +
      '<header class="topbar">' +
        '<div class="brand">' +
          '<span class="logo-mark">' + mascot('rice') + '</span>' +
          '<div><p class="brand-name">哦鲸鲸记账</p><p class="brand-date">' + todayText() + '</p></div>' +
        '</div>' +
        '<button class="icon-btn" data-tab="settings" aria-label="设置">' + icon('settings') + '</button>' +
      '</header>' +
      '<div class="greeting">' +
        '<h1>' + greetingText() + '，<br>今天也好好记账呀</h1>' +
        '<p>每一笔小记录，都是给未来的自己攒底气。</p>' +
      '</div>' +
      '<section class="balance-card">' +
        '<svg class="balance-wave" viewBox="0 0 200 120" fill="none" aria-hidden="true">' +
          '<path d="M0 72c28-30 50 18 82-6s48 18 118-12v78H0Z" fill="currentColor"/>' +
          '<path d="M0 88c35-20 54 14 92-4s63 10 108-8v56H0Z" fill="currentColor" opacity=".5"/>' +
        '</svg>' +
        '<div class="balance-top">' +
          '<span>' + C.monthLabel(month) + '结余</span>' +
          (bs.hasBudget
            ? '<button class="budget-pill" data-action="edit-budget">预算 ¥' + money0(state.budget) + '</button>'
            : '<button class="budget-pill" data-action="edit-budget">设置预算</button>') +
        '</div>' +
        '<div class="balance-amount"><small>' + balancePrefix + '</small>' + money(Math.abs(t.balance)) + '</div>' +
        '<div class="balance-row">' +
          '<span>收入<b>+¥' + money(t.income) + '</b></span>' +
          '<span>支出<b>-¥' + money(t.expense) + '</b></span>' +
        '</div>' +
        '<div class="budget-line">' +
          '<div class="budget-track ' + (bs.level >= 3 ? 'over' : '') + '"><i style="width:' + barWidth.toFixed(1) + '%"></i></div>' +
          '<div class="budget-caption"><span>' + budgetCaption + '</span>' +
            (bs.hasBudget ? '<b>' + (bs.level >= 3 ? '超支' : bs.percent + '%') + '</b>' : '') +
          '</div>' +
        '</div>' +
      '</section>' +
      '<div class="quick-actions">' +
        '<button class="quick-btn expense" data-action="open-add" data-type="expense">' +
          '<span class="quick-ico">−</span><span><b>记支出</b><small>花出去的都算数</small></span>' +
        '</button>' +
        '<button class="quick-btn income" data-action="open-add" data-type="income">' +
          '<span class="quick-ico">+</span><span><b>记收入</b><small>每一份收获都值得</small></span>' +
        '</button>' +
      '</div>' +
      (alert ? bannerHtml(alert) : '') +
      '<section class="card today-card">' +
        '<div class="section-head">' +
          '<div><h2>今日账单</h2><p>' + C.dateLabel(today, today) + ' · ' + C.weekdayName(today) + '</p></div>' +
          '<button class="text-link" data-tab="records">全部 ' + icon('right') + '</button>' +
        '</div>' +
        '<div class="today-sum">' +
          '<div><span>今日支出</span><b class="neg">¥' + money(todayTotals.expense) + '</b></div>' +
          '<div class="income"><span>今日收入</span><b>+¥' + money(todayTotals.income) + '</b></div>' +
        '</div>' +
        (todayGroups.length
          ? todayGroups[0].items.slice(0, 3).map(function (x) { return txRow(x); }).join('') +
            (todayGroups[0].items.length > 3
              ? '<button class="btn-link" data-tab="records">还有 ' + (todayGroups[0].items.length - 3) + ' 笔，去明细看看 ›</button>'
              : '')
          : '<div class="empty">' +
              '<div class="empty-whale">' + mascot('justRice') + '</div>' +
              '<b>今天还没有记账</b><p>一杯咖啡、一趟地铁，都值得被记住。</p>' +
              '<button class="btn-ghost" data-action="open-add" data-type="expense">记下第一笔</button>' +
            '</div>') +
      '</section>' +
      (todayTx.length === 0 && recent.length
        ? '<section class="card">' +
            '<div class="section-head"><div><h2>最近记录</h2><p>这个月的小脚印</p></div>' +
            '<button class="text-link" data-tab="records">查看全部 ' + icon('right') + '</button></div>' +
            recent.map(function (x) { return txRow(x); }).join('') +
          '</section>'
        : '') +
      aiMiniHtml(rem, bs) +
    '</div>';
  }

  function pageHome() {
    var st = currentStats();
    var t = st.totals;
    var bs = st.bs;
    var ss = st.ss;
    var month = C.currentMonth();
    var today = C.todayStr();
    var todayTx = state.transactions.filter(function (x) { return x.date === today; });
    var todayTotals = C.totals(todayTx);
    var todayGroups = C.groupByDate(todayTx);
    var top = C.categoryBreakdown(st.mtx, 'expense')[0] || null;
    var alert = getAlert(st, top);
    var goal = ss.goal;
    var goalPercent = Math.min(100, Math.max(0, ss.percent));
    var todayLeft = Math.max(0, ss.dailyAllowance - todayTotals.expense);
    var balancePrefix = t.balance < 0 ? '-¥' : '¥';
    var barWidth = bs.hasBudget ? Math.max(0, Math.min(100, bs.ratio * 100)) : 0;
    var budgetCaption = bs.hasBudget
      ? (bs.level >= 3
        ? '已超支 ¥' + money(bs.over)
        : '已用 ' + bs.percent + '% · 还能花 ¥' + money(Math.max(bs.remaining, 0)))
      : '还没设支出上限';

    var goalCard;
    if (!goal) {
      goalCard = '<section class="goal-card goal-empty">' +
        '<div class="goal-empty-main"><span class="goal-kicker">本月攒钱目标</span>' +
          '<b>这个月想攒多少？</b><p>设个目标，鲸鲸按天帮你算还能花多少。</p></div>' +
        '<button class="goal-set" data-action="edit-savings">设目标</button>' +
      '</section>';
    } else {
      var goalTone = ss.achieved ? ' achieved' : (ss.level >= 2 ? ' risk' : '');
      var goalLine = ss.achieved
        ? '目标达成'
        : (ss.metNow ? '现在够了，月底会差 ¥' + money(ss.projectedShortfall) : '还差 ¥' + money(ss.shortfall));
      var dayLine = ss.achieved
        ? '可以松口气'
        : (todayLeft > 0 ? '今天还能花 ¥' + money(todayLeft) : '今天先别花了');
      goalCard = '<section class="goal-card' + goalTone + '">' +
        '<div class="goal-head">' +
          '<span class="goal-kicker">本月攒钱目标</span>' +
          '<button class="goal-edit" data-action="edit-savings">调整 ' + icon('right') + '</button>' +
        '</div>' +
        '<div class="goal-main"><b>¥' + money0(goal) + '</b><span>已攒 ¥' + money(ss.saved) + '</span></div>' +
        '<div class="goal-track"><i style="width:' + goalPercent.toFixed(1) + '%"></i></div>' +
        '<div class="goal-foot"><span>' + goalLine + '</span><b>' + dayLine + '</b></div>' +
      '</section>';
    }

    return '<div class="page page-home">' +
      '<header class="topbar">' +
        '<div class="brand">' +
          '<span class="logo-mark">' + mascot('rice') + '</span>' +
          '<div><p class="brand-name">哦鲸鲸记账</p><p class="brand-date">' + todayText() + '</p></div>' +
        '</div>' +
        '<button class="icon-btn" data-tab="settings" aria-label="设置">' + icon('settings') + '</button>' +
      '</header>' +
      '<div class="greeting">' +
        '<h1>' + greetingText() + '，今天也好好记账</h1>' +
        '<p>记清楚每一笔，剩下的交给时间。</p>' +
      '</div>' +
      '<section class="balance-card">' +
        '<svg class="balance-wave" viewBox="0 0 200 120" fill="none" aria-hidden="true">' +
          '<path d="M0 72c28-30 50 18 82-6s48 18 118-12v78H0Z" fill="currentColor"/>' +
          '<path d="M0 88c35-20 54 14 92-4s63 10 108-8v56H0Z" fill="currentColor" opacity=".5"/>' +
        '</svg>' +
        '<div class="balance-top">' +
          '<span>' + C.monthLabel(month) + '结余</span>' +
          (bs.hasBudget
            ? '<button class="budget-pill" data-action="edit-budget">上限 ¥' + money0(state.budget) + '</button>'
            : '<button class="budget-pill" data-action="edit-budget">设支出上限</button>') +
        '</div>' +
        '<div class="balance-amount"><small>' + balancePrefix + '</small>' + money(Math.abs(t.balance)) + '</div>' +
        '<div class="balance-row">' +
          '<span>收入<b>+¥' + money(t.income) + '</b></span>' +
          '<span>支出<b>-¥' + money(t.expense) + '</b></span>' +
        '</div>' +
        '<div class="budget-line">' +
          '<div class="budget-track ' + (bs.level >= 3 ? 'over' : '') + '"><i style="width:' + barWidth.toFixed(1) + '%"></i></div>' +
          '<div class="budget-caption"><span>' + budgetCaption + '</span>' +
            (bs.hasBudget ? '<b>' + (bs.level >= 3 ? '超了' : bs.percent + '%') + '</b>' : '') +
          '</div>' +
        '</div>' +
      '</section>' +
      goalCard +
      '<div class="quick-actions">' +
        '<button class="quick-btn expense" data-action="open-add" data-type="expense">' +
          '<span class="quick-ico">−</span><b>记支出</b>' +
        '</button>' +
        '<button class="quick-btn income" data-action="open-add" data-type="income">' +
          '<span class="quick-ico">+</span><b>记收入</b>' +
        '</button>' +
      '</div>' +
      (alert ? bannerHtml(alert) : '') +
      '<section class="card today-card">' +
        '<div class="section-head">' +
          '<div><h2>今天</h2><p>' + C.dateLabel(today, today) + ' · ' + C.weekdayName(today) + '</p></div>' +
          '<button class="text-link" data-tab="records">明细 ' + icon('right') + '</button>' +
        '</div>' +
        '<div class="today-sum">' +
          '<div><span>支出</span><b class="neg">¥' + money(todayTotals.expense) + '</b></div>' +
          '<div class="income"><span>收入</span><b>+¥' + money(todayTotals.income) + '</b></div>' +
        '</div>' +
        (todayGroups.length
          ? todayGroups[0].items.slice(0, 3).map(function (x) { return txRow(x); }).join('') +
            (todayGroups[0].items.length > 3
              ? '<button class="btn-link" data-tab="records">还有 ' + (todayGroups[0].items.length - 3) + ' 笔，去明细看看 ›</button>'
              : '')
          : '<div class="empty">' +
              '<div class="empty-whale">' + mascot('justRice') + '</div>' +
              '<b>今天还没记账</b><p>一杯咖啡、一趟地铁，都值得记一笔。</p>' +
              '<button class="btn-ghost" data-action="open-add" data-type="expense">记下第一笔</button>' +
            '</div>') +
      '</section>' +
    '</div>';
  }

  function computeAlert(st, top) {
    if (!state.settings.reminderEnabled) return null;
    var candidates = [];
    var month = C.currentMonth();
    if (st.ss.hasGoal && st.ss.level >= 2) {
      var sr = C.savingsReminder(st.ss, month);
      if (sr) candidates.push({
        key: sr.key, level: sr.level, danger: sr.level >= 3,
        title: sr.title, text: sr.text, kind: 'savings', priority: 2
      });
    }
    if (st.bs.hasBudget && st.bs.level >= 2) {
      var br = C.localReminder(st.bs, top, month);
      candidates.push({
        key: 'budget-' + br.level, level: br.level, danger: br.level >= 3,
        title: br.title, text: br.text, kind: 'budget', priority: 1
      });
    }
    if (!candidates.length) return null;
    candidates.sort(function (a, b) {
      if (b.level !== a.level) return b.level - a.level;
      return b.priority - a.priority;
    });
    return candidates[0];
  }

  function getAlert(st, top) {
    var alert = computeAlert(st, top);
    if (!alert || ui.bannerDismissedKey === alert.key) return null;
    var month = C.currentMonth();
    var r = state.reminders[month] || {};
    var useAi = !!r.aiText && r.key === alert.key;
    return {
      key: alert.key,
      level: alert.level,
      danger: alert.danger,
      title: useAi ? '鲸鲸提醒' : alert.title,
      text: useAi ? cleanAiText(r.aiText) : alert.text,
      kind: alert.kind,
      ai: useAi
    };
  }

  function bannerHtml(alert) {
    return '<div class="alert-banner ' + (alert.danger ? 'danger' : '') + '">' +
      '<div class="alert-ico">' + icon(alert.danger ? 'bell' : 'spark') + '</div>' +
      '<div class="alert-main">' +
        '<b>' + escapeHtml(alert.title) + '</b>' +
        '<p>' + escapeHtml(alert.text) + '</p>' +
        '<div class="alert-actions">' +
          '<button data-tab="ai">问鲸鲸</button>' +
          '<button data-action="ask-ai-reminder"' + (ui.aiReminderBusy ? ' disabled' : '') + '>' +
            (ui.aiReminderBusy ? '鲸鲸正在想…' : '让 AI 说说') + '</button>' +
        '</div>' +
      '</div>' +
      '<button class="alert-close" data-action="dismiss-banner" aria-label="关闭提醒">' + icon('close') + '</button>' +
    '</div>';
  }

  function aiMiniHtml(rem, bs) {
    var title = '鲸鲸的小提醒';
    var text = rem.text;
    var button = '<button data-tab="ai">问问鲸鲸</button>';
    if (!bs.hasBudget) {
      title = '先定一个小目标';
      text = '设置每月支出限额，鲸鲸会在你快花超时提醒你，还会帮你看清钱都花在哪儿。';
      button = '<button data-action="edit-budget">设预算</button>';
    }
    return '<section class="ai-mini">' +
      '<div class="ai-orb">' + mascot('justRice') + '</div>' +
      '<div class="ai-mini-main"><b>' + title + '</b><p>' + escapeHtml(text) + '</p></div>' +
      button +
    '</section>';
  }

  function txRow(tx) {
    var c = catOf(tx);
    var note = tx.note ? tx.note : c.name;
    return '<button class="tx-row" data-action="edit-tx" data-id="' + escapeHtml(tx.id) + '">' +
      '<span class="cat-icon" style="background:' + c.color + '20">' + c.emoji + '</span>' +
      '<span class="tx-main"><b>' + escapeHtml(c.name) + '</b><small>' + escapeHtml(note) + ' · ' + timeText(tx.createdAt) + '</small></span>' +
      '<span class="tx-amount ' + tx.type + '">' + (tx.type === 'income' ? '+' : '-') + '¥' + money(tx.amount) + '</span>' +
    '</button>';
  }

  function pageRecords() {
    var st = statsFor(ui.month);
    var mtx = st.mtx;
    var t = st.totals;
    var bs = st.bs;
    var current = C.currentMonth();
    var isCurrent = ui.month === current;
    var nextDisabled = ui.month >= current;
    var filtered = mtx.filter(function (x) { return ui.filter === 'all' || x.type === ui.filter; });
    var groups = C.groupByDate(filtered);

    return '<div class="page page-records">' +
      '<header class="page-head">' +
        '<div><h1>明细</h1><p>钱花在哪，一眼看到</p></div>' +
        '<button class="round-add" data-action="open-add" data-type="expense" aria-label="记一笔">' + icon('plus') + '</button>' +
      '</header>' +
      '<div class="month-nav">' +
        '<button data-action="prev-month" aria-label="上个月">' + icon('left') + '</button>' +
        '<div class="month-center"><b>' + C.monthLabel(ui.month) + '</b><span>' + C.daysInMonth(ui.month) + ' 天 · ' + mtx.length + ' 笔</span></div>' +
        '<div class="month-right">' +
          '<button data-action="next-month" aria-label="下个月"' + (nextDisabled ? ' disabled' : '') + '>' + icon('right') + '</button>' +
          (isCurrent ? '' : '<button class="this-month-btn" data-action="this-month">今</button>') +
        '</div>' +
      '</div>' +
      '<div class="summary-grid">' +
        '<div class="summary-card income"><span>收入</span><b>+' + money0(t.income) + '</b></div>' +
        '<div class="summary-card expense"><span>支出</span><b>-' + money0(t.expense) + '</b></div>' +
        '<div class="summary-card balance"><span>结余</span><b>' + (t.balance < 0 ? '-' : '') + money0(Math.abs(t.balance)) + '</b></div>' +
      '</div>' +
      '<button class="goal-strip" data-action="edit-savings" data-month="' + ui.month + '">' +
        (st.ss.hasGoal
          ? '<span>' + (isCurrent ? '本月' : C.shortMonth(ui.month)) + '攒钱 <b>¥' + money0(st.ss.goal) + '</b></span><em>已攒 ¥' + money(st.ss.saved) + ' · ' + (st.ss.achieved ? '目标达成' : (st.ss.metNow ? '月底会差 ¥' + money(st.ss.projectedShortfall) : '还差 ¥' + money(st.ss.shortfall))) + '</em>'
          : '<span>' + (isCurrent ? '还没设攒钱目标' : '这个月没设目标') + '</span><em>设一个，鲸鲸帮你算每天能花多少</em>') +
        icon('right') +
      '</button>' +
      '<section class="card budget-card">' +
        '<div class="section-head">' +
          '<div><h2>支出上限</h2><p>' + (bs.hasBudget ? '本月已用 ' + bs.percent + '%' : '还没设置') + '</p></div>' +
          '<button class="text-link" data-action="edit-budget">' + (bs.hasBudget ? '调整' : '设置') + ' ' + icon('right') + '</button>' +
        '</div>' +
        (bs.hasBudget
          ? '<div class="budget-track ' + (bs.level >= 3 ? 'over' : '') + '"><i style="width:' + Math.min(100, bs.ratio * 100).toFixed(1) + '%"></i></div>' +
            '<div class="budget-caption card-caption"><span>' +
              (bs.level >= 3 ? '已超支 ¥' + money(bs.over) : '还剩 ¥' + money(Math.max(bs.remaining, 0)) + ' · 每天约可花 ¥' + money(bs.dailyAllowance)) +
            '</span><b>' + bs.percent + '%</b></div>'
          : '<button class="btn-ghost mt8" data-action="edit-budget">设置支出上限</button>') +
      '</section>' +
      '<div class="chips">' +
        '<button class="chip ' + (ui.filter === 'all' ? 'active' : '') + '" data-action="filter" data-filter="all">全部</button>' +
        '<button class="chip ' + (ui.filter === 'expense' ? 'active' : '') + '" data-action="filter" data-filter="expense">支出</button>' +
        '<button class="chip ' + (ui.filter === 'income' ? 'active' : '') + '" data-action="filter" data-filter="income">收入</button>' +
      '</div>' +
      (groups.length
        ? groups.map(dayGroupHtml).join('')
        : '<div class="card empty"><div class="empty-whale">' + mascot('fish') + '</div><b>' + (mtx.length ? '这个筛选下没有记录' : '这个月还没有账单') + '</b><p>记下第一笔，就知道钱去哪了。</p>' +
          '<button class="btn-ghost" data-action="open-add" data-type="expense">去记一笔</button></div>') +
      trendCardHtml(mtx, t.expense, isCurrent) +
      categoryCardHtml(mtx, ui.catType) +
    '</div>';
  }

  function trendCardHtml(mtx, expenseTotal, isCurrent) {
    var series = C.dailySeries(mtx, ui.month);
    var maxDay = 0;
    series.forEach(function (d) { if (d.expense > maxDay) maxDay = d.expense; });
    return '<section class="card trend-card">' +
      '<div class="trend-head"><b>每天花了多少</b><span>' + (isCurrent ? '按天看' : C.monthLabel(ui.month)) + '</span></div>' +
      (expenseTotal > 0
        ? '<div class="trend-chart">' + series.map(function (d) {
            var px = maxDay > 0 ? Math.max(3, Math.round(d.expense / maxDay * 78)) : 3;
            var show = d.day === 1 || d.day % 5 === 0 || d.day === series.length;
            return '<div class="trend-col ' + (isCurrent && d.day === new Date().getDate() ? 'today' : '') + '" title="' + d.date + ' 支出 ¥' + money(d.expense) + '">' +
              '<i style="height:' + px + 'px"></i><b>' + (show ? d.day : '') + '</b></div>';
          }).join('') + '</div>'
        : '<div class="empty" style="padding:16px 0 8px"><p style="margin:0">这个月还没有支出记录</p></div>') +
    '</section>';
  }

  function categoryCardHtml(mtx, catType) {
    var cats = C.categoryBreakdown(mtx, catType);
    var topName = cats[0] ? cats[0].name : '';
    return '<section class="card">' +
      '<div class="section-head">' +
        '<div><h2>钱花在哪</h2><p>' + (cats.length ? '最多的是' + topName : '记几笔就能看见') + '</p></div>' +
        '<div class="segmented">' +
          '<button class="' + (catType === 'expense' ? 'active' : '') + '" data-action="cat-type" data-type="expense">支出</button>' +
          '<button class="' + (catType === 'income' ? 'active' : '') + '" data-action="cat-type" data-type="income">收入</button>' +
        '</div>' +
      '</div>' +
      (cats.length
        ? '<div class="mt8">' + cats.slice(0, 6).map(function (c) {
            return '<div class="cat-row">' +
              '<span class="cat-icon sm" style="background:' + c.color + '20">' + c.emoji + '</span>' +
              '<div class="cat-info"><div class="cat-top"><b>' + escapeHtml(c.name) + '</b><span>¥' + money(c.amount) + ' · ' + Math.round(c.percent * 100) + '%</span></div>' +
                '<div class="cat-track"><i style="width:' + Math.max(2, c.percent * 100).toFixed(1) + '%;background:' + c.color + '"></i></div></div>' +
            '</div>';
          }).join('') + '</div>'
        : '<div class="empty" style="padding:14px 0 4px"><p style="margin:0">还没有' + (catType === 'expense' ? '支出' : '收入') + '记录</p></div>') +
    '</section>';
  }

  function dayGroupHtml(group) {
    var t = group.totals;
    var isToday = group.date === C.todayStr();
    var label = isToday ? '今天' : C.dateLabel(group.date, C.todayStr());
    var sub = C.weekdayName(group.date);
    if (label === '昨天' || label === '前天') {
      sub = Number(group.date.slice(5, 7)) + '月' + Number(group.date.slice(8, 10)) + '日 ' + sub;
    }
    return '<div class="day-group">' +
      '<div class="day-head">' +
        '<div><b>' + label + '</b><span>' + sub + '</span></div>' +
        '<em>支出 ¥' + money(t.expense) + (t.income > 0 ? ' · 收入 ¥' + money(t.income) : '') + '</em>' +
      '</div>' +
      '<div class="card list-card">' + group.items.map(txRow).join('') + '</div>' +
    '</div>';
  }

  function pageAi() {
    var ready = isAiConfigured();
    var ai = state.settings.ai;
    var st = currentStats();
    var top = C.categoryBreakdown(st.mtx, 'expense')[0] || null;
    var messages = state.aiMessages || [];
    var hasMessages = messages.length > 0;

    var header = '<header class="page-head ai-page-head">' +
      '<div class="ai-title">' +
        '<div class="ai-orb">' + mascot('rice') + '</div>' +
        '<div><h1>鲸鲸 AI</h1><p>账单分析 · 攒钱计划</p>' +
          '<span class="ai-status ' + (ready ? '' : 'off') + '"><i></i>' + (ready ? 'AI 已就绪' : 'AI 未配置') + '</span>' +
        '</div>' +
      '</div>' +
      (hasMessages ? '<button class="ai-head-action" data-action="ai-clear">清空</button>' : '') +
    '</header>';

    if (!ready) {
      return '<div class="page page-ai">' + header +
        '<div class="ai-setup">' +
          '<h2>让鲸鲸看看你的账</h2>' +
          '<p>接一个 OpenAI 兼容接口。它会结合你的真实账单，帮你看消费、定预算，也会在快超支时提醒你。</p>' +
          '<div class="ai-features">' +
            '<div class="ai-feature"><span>01</span><div><b>账单分析</b><small>找出这个月最值得注意的变化</small></div></div>' +
            '<div class="ai-feature"><span>02</span><div><b>攒钱计划</b><small>按收入和目标，算下月怎么安排</small></div></div>' +
            '<div class="ai-feature"><span>03</span><div><b>日常提醒</b><small>预算或目标吃紧时，说一句</small></div></div>' +
          '</div>' +
          '<button class="btn-primary" data-action="open-ai-config">配置 AI 接口</button>' +
          '<p class="field-hint">支持 DeepSeek、OpenAI、通义千问，也能接本地 Ollama。密钥只存在本机。</p>' +
        '</div>' +
        '<div class="card empty" style="margin-top:4px"><div class="empty-whale">' + mascot('justRice') + '</div><b>配置后可以这样问</b>' +
          '<p>“这个月花在哪了” · “哪里能省” · “下月怎么攒”</p></div>' +
      '</div>';
    }

    var summary = '<div class="ai-summary">' +
      '<span>本月支出 <b>¥' + money0(st.totals.expense) + '</b></span>' +
      '<span>' + (st.ss.hasGoal ? '攒钱 <b>' + st.ss.percent + '%</b>' : '攒钱 <b>未设目标</b>') + '</span>' +
      '<span>最大分类 <b>' + (top ? escapeHtml(top.name) : '暂无') + '</b></span>' +
      '<span>模型 <b>' + escapeHtml(ai.model) + '</b></span>' +
    '</div>';

    var chat = '<div class="chat" id="chat">' +
      (hasMessages ? messages.map(renderMessage).join('') : welcomeMessage()) +
      (ui.aiBusy ? '<div class="msg ai"><div class="ai-avatar">' + mascot('rice') + '</div><div class="bubble"><div class="typing"><i></i><i></i><i></i></div></div></div>' : '') +
    '</div>';

    var error = ui.aiError
      ? '<div class="ai-error">' + escapeHtml(ui.aiError) +
          (ui.lastQuestion ? ' <button class="text-link" data-action="ai-retry">重试</button>' : '') +
        '</div>'
      : '';

    var prompts = '<div class="prompt-chips">' +
      promptChip('看看这个月', '帮我看看这个月的消费结构，指出最值得注意的地方。', 'analysis') +
      promptChip('哪里能省', '本月哪些支出最值得优化？给我 3 条具体、能马上做的建议。', 'analysis') +
      promptChip('下月怎么攒', '根据我的账单和攒钱目标，帮我安排下个月的预算和储蓄计划。', 'plan') +
      promptChip('花得健康吗', '结合我的收入和支出，评价一下这个月的消费是否健康？', 'analysis') +
    '</div>';

    var composer = '<div class="composer">' +
      '<textarea id="aiInput" rows="1" placeholder="' + (ui.aiBusy ? '鲸鲸正在想…' : '问鲸鲸：这个月哪里花多了？') + '"' + (ui.aiBusy ? ' disabled' : '') + '>' + escapeHtml(ui.aiInput) + '</textarea>' +
      '<button class="send-btn" data-action="ai-send" aria-label="发送"' + (ui.aiBusy ? ' disabled' : '') + '>' + icon('send') + '</button>' +
    '</div>' +
    '<div class="field-hint" style="text-align:center;margin:1px 0 0">AI 回答仅供记账参考，不构成投资建议。</div>';

    return '<div class="page page-ai">' + header + summary + chat + error + prompts + composer + '</div>';
  }

  function promptChip(label, prompt, mode) {
    return '<button class="prompt-chip" data-action="ai-prompt" data-prompt="' + escapeHtml(prompt) + '" data-mode="' + mode + '">' + label + '</button>';
  }

  function welcomeMessage() {
    return '<div class="msg ai"><div class="ai-avatar">' + mascot('rice') + '</div>' +
      '<div class="bubble"><p>嗨，我是鲸鲸 ヾ(•ω•`)o</p><p>我会看你的真实账单，帮你找出花得不对的地方，也一起把下个月安排明白。</p><p>想问什么，直接说。</p>' +
      '<div class="msg-time">现在</div></div></div>';
  }

  function renderMessage(m) {
    var isUser = m.role === 'user';
    return '<div class="msg ' + (isUser ? 'user' : 'ai') + '">' +
      (isUser ? '' : '<div class="ai-avatar">' + mascot('rice') + '</div>') +
      '<div class="bubble">' + renderMarkdown(m.content) + '<div class="msg-time">' + timeText(m.ts) + '</div></div>' +
    '</div>';
  }

  function renderMarkdown(text) {
    var lines = escapeHtml(text || '').split(/\r?\n/);
    var out = '';
    var list = null;
    var para = [];

    function inline(s) {
      return s
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/`([^`]+)`/g, '<code>$1</code>');
    }
    function flushPara() {
      if (para.length) {
        out += '<p>' + para.join('<br>') + '</p>';
        para = [];
      }
    }
    function closeList() {
      if (list) {
        out += '</' + list + '>';
        list = null;
      }
    }

    lines.forEach(function (line) {
      var t = line.trim();
      if (!t) {
        flushPara();
        closeList();
        return;
      }
      var m;
      if ((m = /^#{1,4}\s+(.+)$/.exec(t))) {
        flushPara();
        closeList();
        out += '<h4>' + inline(m[1]) + '</h4>';
        return;
      }
      if ((m = /^[-*•]\s+(.+)$/.exec(t))) {
        flushPara();
        if (list !== 'ul') {
          closeList();
          out += '<ul>';
          list = 'ul';
        }
        out += '<li>' + inline(m[1]) + '</li>';
        return;
      }
      if ((m = /^\d+[.、)]\s*(.+)$/.exec(t))) {
        flushPara();
        if (list !== 'ol') {
          closeList();
          out += '<ol>';
          list = 'ol';
        }
        out += '<li>' + inline(m[1]) + '</li>';
        return;
      }
      closeList();
      para.push(inline(t));
    });
    flushPara();
    closeList();
    return out || '<p></p>';
  }

  function pageSettings() {
    var st = currentStats();
    var ai = state.settings.ai;
    var ready = isAiConfigured();
    var notifyPerm = true;
    if (native && native.hasNotificationPermission) {
      try { notifyPerm = native.hasNotificationPermission(); } catch (e) { notifyPerm = true; }
    }
    var budgetText = state.budget > 0 ? '¥' + money0(state.budget) : '未设置';
    var goalText = st.ss.hasGoal ? '¥' + money0(st.ss.goal) : '未设置';
    var goalHint = st.ss.hasGoal
      ? (st.ss.achieved ? '已攒 ¥' + money(st.ss.saved) + '，目标达成'
        : (st.ss.metNow ? '现在够了，按节奏月底会差 ¥' + money(st.ss.projectedShortfall)
          : '已攒 ¥' + money(st.ss.saved) + ' · 还差 ¥' + money(st.ss.shortfall)))
      : '设一个数，鲸鲸按天帮你盯着';

    return '<div class="page page-settings">' +
      '<header class="page-head"><div><h1>我的</h1><p>好吧，你爱怎么样怎么样吧，本鲸才不会顺你的意(╯▔皿▔)╯</p></div></header>' +
      '<section class="profile-card">' +
        '<div class="profile-logo">' + mascot('rice') + '</div>' +
        '<div class="profile-main"><b>哦鲸鲸记账</b><p>v' + appVersion + ' · 只存在本机</p></div>' +
        '<span class="profile-badge">本机账本</span>' +
      '</section>' +

      '<div class="section-title">钱与提醒</div>' +
      '<div class="settings-card">' +
        '<button class="set-row" data-action="edit-savings">' +
          '<span class="set-ico gold">' + icon('chart') + '</span>' +
          '<span class="set-main"><b>本月攒钱目标</b><small>' + goalHint + '</small></span>' +
          '<span class="set-value">' + goalText + ' ' + icon('right') + '</span>' +
        '</button>' +
        '<button class="set-row" data-action="edit-budget">' +
          '<span class="set-ico">' + icon('wallet') + '</span>' +
          '<span class="set-main"><b>每月支出上限</b><small>' + (state.budget > 0 ? '本月已用 ' + st.bs.percent + '%' : '设一个月度上限') + '</small></span>' +
          '<span class="set-value">' + budgetText + ' ' + icon('right') + '</span>' +
        '</button>' +
        '<div class="set-row">' +
          '<span class="set-ico">' + icon('bell') + '</span>' +
          '<span class="set-main"><b>预算提醒</b><small>到 80% 或超支时提醒</small></span>' +
          '<button class="switch ' + (state.settings.reminderEnabled ? 'on' : '') + '" data-action="toggle" data-key="reminderEnabled" role="switch" aria-checked="' + (!!state.settings.reminderEnabled) + '"><span></span></button>' +
        '</div>' +
        '<div class="set-row">' +
          '<span class="set-ico">' + icon('spark') + '</span>' +
          '<span class="set-main"><b>系统通知</b><small>' + (notifyPerm ? '同步到通知栏' : '通知权限未开启') + '</small></span>' +
          '<button class="switch ' + (state.settings.notifyEnabled ? 'on' : '') + '" data-action="toggle" data-key="notifyEnabled" role="switch" aria-checked="' + (!!state.settings.notifyEnabled) + '"><span></span></button>' +
        '</div>' +
        '<div class="set-row">' +
          '<span class="set-ico amber">' + icon('ai') + '</span>' +
          '<span class="set-main"><b>AI 智能提醒</b><small>到 80% 时让 AI 写一句</small></span>' +
          '<button class="switch ' + (state.settings.aiReminder ? 'on' : '') + '" data-action="toggle" data-key="aiReminder" role="switch" aria-checked="' + (!!state.settings.aiReminder) + '"><span></span></button>' +
        '</div>' +
      '</div>' +

      '<div class="section-title">AI 接口</div>' +
      '<div class="settings-card">' +
        '<button class="set-row" data-action="open-ai-config">' +
          '<span class="set-ico">' + icon('ai') + '</span>' +
          '<span class="set-main"><b>大模型接口</b><small>' + (ready ? escapeHtml(formatHost(ai.baseUrl)) + ' · ' + escapeHtml(ai.model) : '接入后可以分析账单、做规划') + '</small></span>' +
          '<span class="set-value">' + (ready ? '已配置' : '去配置') + ' ' + icon('right') + '</span>' +
        '</button>' +
        '<div class="set-row">' +
          '<span class="set-ico">' + icon('lock') + '</span>' +
          '<span class="set-main"><b>隐私说明</b><small>API Key 只保存在本机；只有你发起 AI 请求时，账单摘要才会发送到你填写的接口。</small></span>' +
        '</div>' +
      '</div>' +

      '<div class="section-title">数据</div>' +
      '<div class="settings-card">' +
        '<button class="set-row" data-action="export-backup">' +
          '<span class="set-ico">' + icon('download') + '</span>' +
          '<span class="set-main"><b>导出备份</b><small>保存为 JSON，换手机能带走</small></span>' +
          '<span class="set-value">' + icon('right') + '</span>' +
        '</button>' +
        '<button class="set-row" data-action="import-backup">' +
          '<span class="set-ico">' + icon('upload') + '</span>' +
          '<span class="set-main"><b>导入备份</b><small>从备份文件恢复</small></span>' +
          '<span class="set-value">' + icon('right') + '</span>' +
        '</button>' +
        '<button class="set-row" data-action="copy-backup">' +
          '<span class="set-ico">' + icon('copy') + '</span>' +
          '<span class="set-main"><b>复制备份</b><small>复制到剪贴板</small></span>' +
          '<span class="set-value">' + icon('right') + '</span>' +
        '</button>' +
        '<button class="set-row" data-action="demo-data">' +
          '<span class="set-ico amber">' + icon('spark') + '</span>' +
          '<span class="set-main"><b>载入示例账单</b><small>会替换当前账单</small></span>' +
          '<span class="set-value">' + icon('right') + '</span>' +
        '</button>' +
        '<button class="set-row" data-action="clear-data">' +
          '<span class="set-ico coral">' + icon('trash') + '</span>' +
          '<span class="set-main"><b>清空账单</b><small>删除所有记录和预算</small></span>' +
          '<span class="set-value" style="color:var(--coral)">清空</span>' +
        '</button>' +
      '</div>' +

      '<div class="section-title">关于</div>' +
      '<div class="settings-card about-card">' +
        '<div class="about-whale">' + mascot('think') + '</div>' +
        '<b>哦鲸鲸记账</b>' +
        '<p>一个安静的小账本。记下收支，看看每天和每月，设个目标，慢慢花。</p>' +
        '<span class="privacy-line">' + icon('lock') + ' 无广告 · 无账号 · 数据留在本机</span>' +
        '<p style="margin-top:9px;color:#9AA8BC">v' + appVersion + ' · 让每一笔都更从容</p>' +
      '</div>' +
    '</div>';
  }

  /* ---------------- 弹层 ---------------- */

  function openTxSheet(tx, type) {
    var t = type || 'expense';
    if (tx) {
      t = tx.type;
      ui.tx = {
        id: tx.id,
        type: tx.type,
        amount: String(tx.amount),
        category: tx.category,
        date: tx.date,
        note: tx.note || '',
        createdAt: tx.createdAt
      };
    } else {
      ui.tx = {
        id: null,
        type: t,
        amount: '',
        category: C.CATEGORIES[t][0].id,
        date: C.todayStr(),
        note: '',
        createdAt: null
      };
    }
    ui.sheet = { type: 'tx' };
    renderSheet();
  }

  function openBudgetSheet() {
    ui.budgetDraft = state.budget > 0 ? String(state.budget) : '';
    ui.sheet = { type: 'budget' };
    renderSheet();
  }

  function openSavingsSheet(month) {
    ui.savingsMonth = month || C.currentMonth();
    var goal = savingsGoalFor(ui.savingsMonth);
    ui.savingsDraft = goal > 0 ? String(goal) : '';
    ui.sheet = { type: 'savings' };
    renderSheet();
  }

  function openAiSheet() {
    ui.aiForm = {
      provider: state.settings.ai.provider || 'deepseek',
      baseUrl: state.settings.ai.baseUrl || '',
      apiKey: state.settings.ai.apiKey || '',
      model: state.settings.ai.model || ''
    };
    ui.aiTest = null;
    ui.showKey = false;
    ui.sheet = { type: 'ai' };
    renderSheet();
  }

  function openConfirm(opts) {
    ui.confirm = opts || {};
    ui.sheet = { type: 'confirm' };
    renderSheet();
  }

  function closeSheet() {
    ui.sheet = null;
    ui.confirm = null;
    var root = $('#sheetRoot');
    if (root) root.innerHTML = '';
  }

  function renderSheet() {
    var root = $('#sheetRoot');
    if (!root) return;
    if (!ui.sheet) {
      root.innerHTML = '';
      return;
    }
    if (ui.sheet.type === 'tx') root.innerHTML = renderTxSheet();
    else if (ui.sheet.type === 'budget') root.innerHTML = renderBudgetSheet();
    else if (ui.sheet.type === 'savings') root.innerHTML = renderSavingsSheet();
    else if (ui.sheet.type === 'ai') root.innerHTML = renderAiSheet();
    else if (ui.sheet.type === 'confirm') root.innerHTML = renderConfirmSheet();
  }

  function sheetHead(title, subtitle) {
    return '<div class="sheet-handle"></div>' +
      '<div class="sheet-head"><div><h2>' + title + '</h2>' + (subtitle ? '<p>' + subtitle + '</p>' : '') + '</div>' +
      '<button class="sheet-close" data-action="close-sheet" aria-label="关闭">' + icon('close') + '</button></div>';
  }

  function txPlanHint(d) {
    if (!d || d.type !== 'expense') return '';
    var month = C.monthOf(d.date || C.todayStr());
    if (month !== C.currentMonth()) return '';
    var st = statsFor(month);
    if (!st.ss.hasGoal) return '';
    var today = C.todayStr();
    var todaySpent = state.transactions.reduce(function (n, x) {
      return n + (x.date === today && x.type === 'expense' ? Number(x.amount) || 0 : 0);
    }, 0);
    var left = Math.max(0, st.ss.dailyAllowance - todaySpent);
    return '<div class="tx-plan-hint ' + (left <= 0 ? 'over' : '') + '">' +
      (left > 0 ? '按攒钱目标，今天还能花 ¥' + money(left) : '今天已经到计划线了，花之前想一下') +
    '</div>';
  }

  function renderTxSheet() {
    var d = ui.tx;
    if (!d) return '';
    var isEdit = !!d.id;
    var cats = C.CATEGORIES[d.type];
    var amountText = d.amount ? C.formatInputAmount(d.amount) : '0.00';
    var hasAmount = parseFloat(d.amount) > 0;
    var today = C.todayStr();
    var yesterday = shiftDate(today, -1);
    var before = shiftDate(today, -2);

    return '<div class="sheet-overlay" data-action="close-sheet">' +
      '<div class="sheet">' +
        sheetHead(isEdit ? '编辑账单' : '记一笔', isEdit ? '改一改也没关系' : '慢慢记，不着急') +
        '<div class="type-switch">' +
          '<button class="expense ' + (d.type === 'expense' ? 'active' : '') + '" data-action="tx-type" data-type="expense">支出</button>' +
          '<button class="income ' + (d.type === 'income' ? 'active' : '') + '" data-action="tx-type" data-type="income">收入</button>' +
        '</div>' +
        '<div class="amount-panel">' +
          '<div class="amount-label">' + (d.type === 'expense' ? '这次花了多少' : '这次收入多少') + '</div>' +
          '<div class="amount-display ' + d.type + ' ' + (hasAmount ? '' : 'placeholder') + '">' +
            '<span>¥</span><b id="txAmount">' + amountText + '</b>' +
          '</div>' +
          txPlanHint(d) +
        '</div>' +
        '<div class="cat-grid">' + cats.map(function (c) {
          return '<button class="cat-item ' + (d.category === c.id ? 'active' : '') + '" data-action="select-cat" data-cat="' + c.id + '">' +
            '<span class="cat-icon" style="background:' + c.color + '22">' + c.emoji + '</span><span>' + c.name + '</span></button>';
        }).join('') + '</div>' +
        '<div class="tx-meta">' +
          '<div class="form-row">' + icon('calendar', 'row-ico') +
            '<input id="txDate" type="date" value="' + d.date + '" max="' + today + '" aria-label="日期">' +
            '<div class="quick-dates">' +
              '<button class="' + (d.date === today ? 'active' : '') + '" data-action="quick-date" data-date="' + today + '">今天</button>' +
              '<button class="' + (d.date === yesterday ? 'active' : '') + '" data-action="quick-date" data-date="' + yesterday + '">昨天</button>' +
              '<button class="' + (d.date === before ? 'active' : '') + '" data-action="quick-date" data-date="' + before + '">前天</button>' +
            '</div>' +
          '</div>' +
          '<div class="form-row">' + icon('list', 'row-ico') +
            '<input id="txNote" type="text" maxlength="40" placeholder="备注（选填）" value="' + escapeHtml(d.note) + '" aria-label="备注">' +
          '</div>' +
        '</div>' +
        '<div class="keypad">' +
          ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'back'].map(function (k) {
            return '<button class="' + (k === 'back' ? 'key-back' : '') + (k === '.' ? ' key-dot' : '') + '" data-action="keypad" data-key="' + k + '" aria-label="' + (k === 'back' ? '删除' : k) + '">' + (k === 'back' ? '⌫' : k) + '</button>';
          }).join('') +
        '</div>' +
        '<div class="tx-save-wrap">' +
          '<div class="tx-save-row">' +
            (isEdit ? '<button class="tx-delete" data-action="delete-tx" aria-label="删除这笔">' + icon('trash') + '</button>' : '') +
            '<button class="btn-primary" id="txSave" data-action="save-tx"' + (hasAmount ? '' : ' disabled') + '>' + (isEdit ? '保存修改' : '记下这一笔') + '</button>' +
          '</div>' +
        '</div>' +
      '</div>' +
    '</div>';
  }

  function renderBudgetSheet() {
    var val = ui.budgetDraft;
    return '<div class="sheet-overlay" data-action="close-sheet">' +
      '<div class="sheet">' +
        sheetHead('每月支出上限', '只算支出，不含收入') +
        '<div class="field"><label>这个月最多花多少</label>' +
          '<input id="budgetInput" type="number" inputmode="decimal" min="0" step="100" placeholder="例如 3000" value="' + escapeHtml(val) + '">' +
        '</div>' +
        '<div class="chips" style="padding-bottom:2px">' +
          [1500, 2000, 3000, 5000, 8000].map(function (n) {
            return '<button class="chip ' + (Number(val) === n ? 'active' : '') + '" data-action="preset-budget" data-amount="' + n + '">¥' + money0(n) + '</button>';
          }).join('') +
        '</div>' +
        '<p class="sheet-note">上限按自然月计算。到 80% 或超支时，首页会提醒；打开系统通知，也会同步到通知栏。</p>' +
        '<div style="height:10px"></div>' +
        '<button class="btn-primary" data-action="save-budget">保存上限</button>' +
        '<p class="sheet-note" style="text-align:center">不设上限也能正常记账。</p>' +
      '</div>' +
    '</div>';
  }

  function renderSavingsSheet() {
    var val = ui.savingsDraft;
    return '<div class="sheet-overlay" data-action="close-sheet">' +
      '<div class="sheet">' +
        sheetHead('本月攒钱目标', '收入减去支出，至少留下这些') +
        '<div class="field"><label>这个月想攒多少</label>' +
          '<input id="savingsInput" type="number" inputmode="decimal" min="0" step="100" placeholder="例如 3000" value="' + escapeHtml(val) + '">' +
        '</div>' +
        '<div class="chips" style="padding-bottom:2px">' +
          [500, 1000, 2000, 3000, 5000].map(function (n) {
            return '<button class="chip ' + (Number(val) === n ? 'active' : '') + '" data-action="preset-savings" data-amount="' + n + '">¥' + money0(n) + '</button>';
          }).join('') +
        '</div>' +
        '<p class="sheet-note">目标按自然月计算。设好后，首页会按剩下的天数算出每天大概还能花多少；花超了会提醒你。</p>' +
        '<div style="height:10px"></div>' +
        '<button class="btn-primary" data-action="save-savings">保存目标</button>' +
        '<p class="sheet-note" style="text-align:center">不想设目标也可以，记账本身也有用。</p>' +
      '</div>' +
    '</div>';
  }

  function renderAiSheet() {
    var f = ui.aiForm || {};
    var test = ui.aiTest;
    var testClass = test ? (test.state === 'ok' ? 'ok' : (test.state === 'err' ? 'err' : '')) : '';
    return '<div class="sheet-overlay" data-action="close-sheet">' +
      '<div class="sheet sheet--tall">' +
        sheetHead('配置 AI 接口', 'OpenAI 兼容接口') +
        '<div class="provider-row">' + PROVIDERS.map(function (p) {
          return '<button class="' + (f.provider === p.id ? 'active' : '') + '" data-action="ai-provider" data-provider="' + p.id + '">' + p.name + '</button>';
        }).join('') + '</div>' +
        '<div class="field"><label>Base URL</label>' +
          '<input type="url" data-ai-field="baseUrl" value="' + escapeHtml(f.baseUrl || '') + '" placeholder="https://api.deepseek.com/v1" autocapitalize="off" autocorrect="off">' +
          '<p class="field-hint">填到 /v1 即可，鲸鲸会自动补上 /chat/completions。也可以直接填完整接口地址。</p>' +
        '</div>' +
        '<div class="field"><label>API Key</label><div class="key-wrap">' +
          '<input type="' + (ui.showKey ? 'text' : 'password') + '" data-ai-field="apiKey" value="' + escapeHtml(f.apiKey || '') + '" placeholder="sk-..." autocapitalize="off" autocorrect="off" spellcheck="false">' +
          '<button class="key-toggle" data-action="toggle-key">' + (ui.showKey ? '隐藏' : '显示') + '</button>' +
        '</div><p class="field-hint">只保存在本机。使用本地 Ollama 等无需鉴权的接口时，可以填 local。</p></div>' +
        '<div class="field"><label>模型</label>' +
          '<input type="text" data-ai-field="model" value="' + escapeHtml(f.model || '') + '" placeholder="deepseek-chat" autocapitalize="off" autocorrect="off">' +
        '</div>' +
        '<div class="test-row">' +
          '<button class="btn-ghost" data-action="test-ai"' + (test && test.state === 'loading' ? ' disabled' : '') + '>' +
            (test && test.state === 'loading' ? '连接中…' : '测试连接') + '</button>' +
          '<div class="test-result ' + testClass + '">' + escapeHtml(test ? test.msg : '保存前可以先测一下') + '</div>' +
        '</div>' +
        '<button class="btn-primary" data-action="save-ai">保存接口设置</button>' +
        '<p class="sheet-note">发起分析时，账单摘要会发送到这个接口。请只填写你信任的服务商；应用不会上传通讯录、位置等无关信息。</p>' +
      '</div>' +
    '</div>';
  }

  function renderConfirmSheet() {
    var c = ui.confirm || {};
    return '<div class="sheet-overlay center" data-action="close-sheet">' +
      '<div class="sheet confirm-sheet">' +
        '<div class="confirm-icon">' + icon(c.icon || 'trash') + '</div>' +
        '<h3>' + escapeHtml(c.title || '确认操作') + '</h3>' +
        '<p>' + escapeHtml(c.message || '') + '</p>' +
        '<div class="confirm-actions">' +
          '<button class="btn-ghost" data-action="close-sheet">取消</button>' +
          '<button class="btn-primary ' + (c.danger ? 'danger' : '') + '" data-action="confirm-yes">' + escapeHtml(c.confirmText || '确定') + '</button>' +
        '</div>' +
      '</div>' +
    '</div>';
  }

  /* ---------------- 交互 ---------------- */

  function switchTab(tab) {
    if (['home', 'records', 'ai', 'settings'].indexOf(tab) < 0) return;
    if (ui.tab === tab) {
      var v = $('#view');
      if (v) v.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    var view = $('#view');
    if (view) ui.scroll[ui.tab] = view.scrollTop;
    ui.tab = tab;
    render({ restore: ui.scroll[tab] || 0 });
  }

  function handleClick(e) {
    var tabEl = e.target.closest('[data-tab]');
    if (tabEl && !e.target.closest('.sheet')) {
      e.preventDefault();
      switchTab(tabEl.dataset.tab);
      return;
    }

    var el = e.target.closest('[data-action]');
    if (!el) return;
    var action = el.dataset.action;

    if (action === 'close-sheet' && el.classList.contains('sheet-overlay') && e.target !== el) return;

    switch (action) {
      case 'close-sheet':
        closeSheet();
        break;
      case 'open-add':
        openTxSheet(null, el.dataset.type || 'expense');
        break;
      case 'edit-tx': {
        var tx = state.transactions.filter(function (x) { return x.id === el.dataset.id; })[0];
        if (tx) openTxSheet(tx);
        break;
      }
      case 'tx-type':
        if (ui.tx) {
          ui.tx.type = el.dataset.type;
          ui.tx.category = C.CATEGORIES[ui.tx.type][0].id;
          renderSheet();
        }
        break;
      case 'select-cat':
        if (ui.tx) {
          ui.tx.category = el.dataset.cat;
          $$('.cat-item').forEach(function (n) {
            n.classList.toggle('active', n.dataset.cat === ui.tx.category);
          });
          vibrate(6);
        }
        break;
      case 'keypad':
        txKeypad(el.dataset.key);
        break;
      case 'quick-date':
        if (ui.tx) {
          ui.tx.date = el.dataset.date;
          var dateInput = $('#txDate');
          if (dateInput) dateInput.value = ui.tx.date;
          $$('.quick-dates button').forEach(function (n) {
            n.classList.toggle('active', n.dataset.date === ui.tx.date);
          });
        }
        break;
      case 'save-tx':
        saveTx();
        break;
      case 'delete-tx':
        confirmDeleteTx();
        break;
      case 'edit-budget':
      case 'open-budget':
        openBudgetSheet();
        break;
      case 'edit-savings':
      case 'open-savings':
        openSavingsSheet(el.dataset.month || C.currentMonth());
        break;
      case 'preset-budget': {
        var amount = el.dataset.amount;
        ui.budgetDraft = amount;
        var budgetInput = $('#budgetInput');
        if (budgetInput) budgetInput.value = amount;
        $$('.chips .chip').forEach(function (n) {
          n.classList.toggle('active', n.dataset.amount === amount);
        });
        break;
      }
      case 'preset-savings': {
        var sAmount = el.dataset.amount;
        ui.savingsDraft = sAmount;
        var savingsInput = $('#savingsInput');
        if (savingsInput) savingsInput.value = sAmount;
        $$('.chips .chip').forEach(function (n) {
          n.classList.toggle('active', n.dataset.amount === sAmount);
        });
        break;
      }
      case 'save-budget':
        saveBudget();
        break;
      case 'save-savings':
        saveSavings();
        break;
      case 'open-ai-config':
        openAiSheet();
        break;
      case 'ai-provider': {
        var p = el.dataset.provider;
        if (ui.aiForm) {
          ui.aiForm.provider = p;
          if (p !== 'custom' && PRESETS[p]) {
            ui.aiForm.baseUrl = PRESETS[p].baseUrl;
            ui.aiForm.model = PRESETS[p].model;
          }
          renderSheet();
        }
        break;
      }
      case 'toggle-key':
        ui.showKey = !ui.showKey;
        renderSheet();
        break;
      case 'test-ai':
        testAiForm();
        break;
      case 'save-ai':
        saveAiForm();
        break;
      case 'confirm-yes': {
        var cb = ui.confirm && ui.confirm.onConfirm;
        closeSheet();
        if (cb) setTimeout(cb, 60);
        break;
      }
      case 'toggle':
        toggleSetting(el.dataset.key);
        break;
      case 'prev-month':
        ui.month = C.addMonths(ui.month, -1);
        render({ reset: true });
        break;
      case 'next-month':
        if (ui.month < C.currentMonth()) {
          ui.month = C.addMonths(ui.month, 1);
          render({ reset: true });
        }
        break;
      case 'this-month':
        ui.month = C.currentMonth();
        render({ reset: true });
        break;
      case 'filter':
        ui.filter = el.dataset.filter;
        render();
        break;
      case 'cat-type':
        ui.catType = el.dataset.type;
        render();
        break;
      case 'dismiss-banner': {
        var ast = statsFor(C.currentMonth());
        var atop = C.categoryBreakdown(ast.mtx, 'expense')[0] || null;
        var al = getAlert(ast, atop);
        ui.bannerDismissedKey = al ? al.key : '';
        render();
        break;
      }
      case 'ask-ai-reminder':
        manualAiReminder();
        break;
      case 'export-backup':
        exportBackup();
        break;
      case 'import-backup':
        importBackup();
        break;
      case 'copy-backup':
        copyBackup();
        break;
      case 'demo-data':
        confirmDemoData();
        break;
      case 'clear-data':
        confirmClearData();
        break;
      case 'ai-prompt':
        sendAi(el.dataset.prompt, el.dataset.mode);
        break;
      case 'ai-send':
        sendAi(ui.aiInput || ($('#aiInput') ? $('#aiInput').value : ''), 'chat');
        break;
      case 'ai-retry':
        if (ui.lastQuestion) sendAi(ui.lastQuestion, ui.lastMode, true);
        break;
      case 'ai-clear':
        clearAiChat();
        break;
      default:
        break;
    }
  }

  function handleInput(e) {
    var el = e.target;
    if (el.id === 'txNote' && ui.tx) ui.tx.note = el.value;
    else if (el.id === 'txDate' && ui.tx) ui.tx.date = el.value;
    else if (el.id === 'budgetInput') ui.budgetDraft = el.value;
    else if (el.id === 'savingsInput') ui.savingsDraft = el.value;
    else if (el.id === 'aiInput') {
      ui.aiInput = el.value;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 92) + 'px';
    } else if (el.dataset && el.dataset.aiField && ui.aiForm) {
      ui.aiForm[el.dataset.aiField] = el.value;
    }
  }

  function handleKeydown(e) {
    if (e.target && e.target.id === 'aiInput' && e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      sendAi(ui.aiInput, 'chat');
    }
  }

  function toggleSetting(key) {
    if (!key) return;
    if (key === 'reminderEnabled') {
      state.settings.reminderEnabled = !state.settings.reminderEnabled;
    } else if (key === 'notifyEnabled') {
      state.settings.notifyEnabled = !state.settings.notifyEnabled;
      if (state.settings.notifyEnabled && native && native.requestNotificationPermission) {
        try { native.requestNotificationPermission(); } catch (e) { /* ignore */ }
      }
    } else if (key === 'aiReminder') {
      var next = !state.settings.aiReminder;
      if (next && !isAiConfigured()) {
        toast('先配置 AI 接口');
        openAiSheet();
        return;
      }
      state.settings.aiReminder = next;
    }
    save();
    render();
  }

  function vibrate(ms) {
    if (native && native.vibrate) {
      try { native.vibrate(ms); } catch (e) { /* ignore */ }
    }
  }

  function txKeypad(key) {
    var a = ui.tx ? ui.tx.amount : '';
    if (key === 'back') {
      a = a.slice(0, -1);
    } else if (key === '.') {
      if (!a) a = '0.';
      else if (a.indexOf('.') < 0) a += '.';
    } else {
      var parts = a.split('.');
      if (parts.length > 1 && parts[1].length >= 2) return;
      if (a.replace('.', '').length >= 9) return;
      if (a === '0') a = key;
      else a += key;
    }
    ui.tx.amount = a;
    updateTxAmount();
    vibrate(4);
  }

  function updateTxAmount() {
    var d = ui.tx;
    if (!d) return;
    var display = $('.amount-display');
    var amount = $('#txAmount');
    var saveBtn = $('#txSave');
    var has = parseFloat(d.amount) > 0;
    if (amount) amount.textContent = d.amount ? C.formatInputAmount(d.amount) : '0.00';
    if (display) {
      display.classList.toggle('placeholder', !has);
      display.classList.remove('expense', 'income');
      display.classList.add(d.type);
    }
    if (saveBtn) saveBtn.disabled = !has;
  }

  function saveTx() {
    var d = ui.tx;
    if (!d) return;
    var amount = C.round2(parseFloat(d.amount));
    if (!isFinite(amount) || amount <= 0) {
      toast('请输入金额');
      return;
    }
    if (!d.date || !C.isValidDate(d.date)) {
      toast('日期不正确');
      return;
    }
    if (d.date > C.todayStr()) {
      toast('不能记未来的账');
      return;
    }
    var isEdit = !!d.id;
    var tx = {
      id: isEdit ? d.id : C.uid(),
      type: d.type,
      amount: amount,
      category: d.category,
      date: d.date,
      note: (d.note || '').trim().slice(0, 40),
      createdAt: isEdit ? (d.createdAt || Date.now()) : Date.now()
    };
    if (isEdit) {
      var idx = state.transactions.findIndex(function (x) { return x.id === tx.id; });
      if (idx >= 0) state.transactions[idx] = tx;
      else state.transactions.push(tx);
    } else {
      state.transactions.push(tx);
    }
    save();
    ui.month = C.monthOf(tx.date);
    ui.aiError = '';
    closeSheet();
    render({ reset: true });
    var doneMsg = isEdit ? '已保存修改' : '记好了，鲸鲸收到';
    if (tx.type === 'expense') {
      var nowStats = currentStats();
      if (nowStats.ss.hasGoal) {
        var tSpent = state.transactions.reduce(function (n, x) {
          return n + (x.date === C.todayStr() && x.type === 'expense' ? Number(x.amount) || 0 : 0);
        }, 0);
        if (tSpent > nowStats.ss.dailyAllowance) {
          doneMsg = '记好了，今天比计划多花 ¥' + money(tSpent - nowStats.ss.dailyAllowance);
        }
      }
    }
    toast(doneMsg);
    vibrate(12);
    setTimeout(function () { checkBudget({ render: true }); }, 80);
  }

  function confirmDeleteTx() {
    var d = ui.tx;
    if (!d || !d.id) return;
    var c = C.CATEGORY_MAP[d.category] || { name: '这笔' };
    closeSheet();
    openConfirm({
      title: '删除这笔账单？',
      message: '「' + c.name + ' ¥' + money(parseFloat(d.amount) || 0) + '」删除后就找不回来了。',
      confirmText: '删除',
      danger: true,
      onConfirm: function () {
        state.transactions = state.transactions.filter(function (x) { return x.id !== d.id; });
        save();
        render({ reset: true });
        toast('已删除');
        setTimeout(function () { checkBudget({ render: true }); }, 80);
      }
    });
  }

  function saveBudget() {
    var input = $('#budgetInput');
    var raw = input ? input.value : ui.budgetDraft;
    var val = Number(raw);
    if (!isFinite(val) || val < 0) {
      toast('请输入一个有效的金额');
      return;
    }
    state.budget = C.round2(Math.min(val, 99999999));
    var month = C.currentMonth();
    state.reminders[month] = { level: 0, notified: 0, key: '', aiText: '', aiTriedAt: 0, ts: 0 };
    ui.bannerDismissedLevel = 0;
    ui.bannerDismissedKey = '';
    if (state.budget > 0 && state.settings.notifyEnabled && native && native.requestNotificationPermission) {
      try { native.requestNotificationPermission(); } catch (e) { /* ignore */ }
    }
    save();
    closeSheet();
    render();
    toast(state.budget > 0 ? '支出上限已设置' : '已取消每月支出上限');
    setTimeout(function () { checkBudget({ render: true }); }, 100);
  }

  function saveSavings() {
    var input = $('#savingsInput');
    var raw = input ? input.value : ui.savingsDraft;
    var val = Number(raw);
    if (!isFinite(val) || val < 0) {
      toast('请输入一个有效的金额');
      return;
    }
    var month = ui.savingsMonth || C.currentMonth();
    state.savingsGoals = state.savingsGoals || {};
    if (val > 0) state.savingsGoals[month] = C.round2(Math.min(val, 99999999));
    else delete state.savingsGoals[month];
    state.reminders[month] = { level: 0, notified: 0, key: '', aiText: '', aiTriedAt: 0, ts: 0 };
    ui.bannerDismissedKey = '';
    save();
    closeSheet();
    render();
    toast(val > 0 ? '攒钱目标定好了' : '已取消本月目标');
    setTimeout(function () { checkBudget({ render: true }); }, 100);
  }

  function testAiForm() {
    if (!ui.aiForm) return;
    var f = ui.aiForm;
    if (!f.baseUrl || !f.model) {
      ui.aiTest = { state: 'err', msg: '先填好 Base URL 和模型' };
      renderSheet();
      return;
    }
    ui.aiTest = { state: 'loading', msg: '正在连接模型…' };
    renderSheet();
    var messages = [{ role: 'user', content: '请只回复四个字：连接成功' }];
    callAI(messages, 24, f, 30000).then(function (content) {
      ui.aiTest = { state: 'ok', msg: '连接成功 · ' + String(content).slice(0, 40) };
      renderSheet();
    }).catch(function (err) {
      ui.aiTest = { state: 'err', msg: friendlyAiError(err) };
      renderSheet();
    });
  }

  function saveAiForm() {
    if (!ui.aiForm) return;
    var f = ui.aiForm;
    var base = String(f.baseUrl || '').trim();
    var model = String(f.model || '').trim();
    if (!base) {
      toast('请先填写 Base URL');
      return;
    }
    try { new URL(C.normalizeEndpoint(base)); } catch (e) {
      toast('Base URL 格式不正确');
      return;
    }
    if (!model) {
      toast('请先填写模型名称');
      return;
    }
    state.settings.ai = {
      provider: f.provider || 'custom',
      baseUrl: base,
      apiKey: String(f.apiKey || '').trim(),
      model: model
    };
    save();
    closeSheet();
    render();
    toast('AI 接口已保存');
  }

  function sendAi(question, mode, isRetry) {
    var q = String(question || '').trim();
    if (!q) return;
    if (q.length > 600) q = q.slice(0, 600);
    if (!isAiConfigured()) {
      toast('请先配置 AI 接口');
      openAiSheet();
      return;
    }
    if (ui.aiBusy) return;

    var history = state.aiMessages.slice();
    if (isRetry && history.length && history[history.length - 1].role === 'user') {
      history = history.slice(0, -1);
    }
    var m = mode || detectMode(q);
    var messages = C.buildMessages(state, q, m, { history: history, month: C.currentMonth(), now: new Date() });

    if (!isRetry) {
      state.aiMessages.push({ role: 'user', content: q, ts: Date.now() });
      state.aiMessages = state.aiMessages.slice(-40);
      ui.aiInput = '';
      save();
    }
    ui.lastQuestion = q;
    ui.lastMode = m;
    ui.aiBusy = true;
    ui.aiError = '';
    render();
    scrollChatBottom(true);

    callAI(messages, 1200).then(function (content) {
      state.aiMessages.push({ role: 'assistant', content: content, ts: Date.now() });
      state.aiMessages = state.aiMessages.slice(-40);
      ui.aiBusy = false;
      save();
      render();
      scrollChatBottom(true);
    }).catch(function (err) {
      ui.aiBusy = false;
      ui.aiError = friendlyAiError(err);
      render();
      scrollChatBottom();
    });
  }

  function detectMode(q) {
    if (/预算|计划|规划|存钱|储蓄|下个月|下月/.test(q)) return 'plan';
    if (/分析|哪里|为什么|花多|省|健康|结构|建议/.test(q)) return 'analysis';
    return 'chat';
  }

  function callAI(messages, maxTokens, configOverride, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var cfg = configOverride || state.settings.ai;
      var endpoint = C.normalizeEndpoint(cfg.baseUrl);
      if (!endpoint) {
        reject(new Error('还没有配置接口地址'));
        return;
      }
      var id = 'ai_' + (++ui.pendingSeq) + '_' + Date.now();
      var settled = false;
      var timer = setTimeout(function () {
        finish(new Error('请求超时，鲸鲸可能正在忙'));
      }, timeoutMs || 95000);

      function finish(err, content) {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        delete ui.pending[id];
        if (err) reject(err);
        else resolve(content);
      }

      ui.pending[id] = { finish: finish };

      try {
        if (native && native.aiChat) {
          native.aiChat(id, endpoint, cfg.apiKey || '', cfg.model, JSON.stringify(messages), maxTokens, 0.7);
        } else {
          fetch(endpoint, {
            method: 'POST',
            headers: Object.assign({
              'Content-Type': 'application/json',
              'Accept': 'application/json'
            }, cfg.apiKey ? { 'Authorization': 'Bearer ' + cfg.apiKey } : {}),
            body: JSON.stringify({
              model: cfg.model,
              messages: messages,
              max_tokens: maxTokens,
              temperature: 0.7,
              stream: false
            })
          }).then(function (res) {
            return res.text().then(function (text) {
              if (!res.ok) {
                var detail = '';
                try {
                  var j = JSON.parse(text);
                  detail = (j.error && (j.error.message || j.error.type)) || j.message || '';
                } catch (e) { detail = text.slice(0, 200); }
                throw new Error('HTTP ' + res.status + (detail ? '：' + detail : ''));
              }
              return C.parseAssistantResponse(text);
            });
          }).then(function (content) {
            finish(null, content);
          }).catch(function (err) {
            finish(err);
          });
        }
      } catch (e) {
        finish(e);
      }
    });
  }

  function friendlyAiError(err) {
    var msg = String((err && err.message) || err || '未知错误');
    if (/401/.test(msg)) return 'API Key 无效或已过期，去接口设置里检查一下。';
    if (/403/.test(msg)) return '接口拒绝了这次请求，请检查 Key 的权限或余额。';
    if (/404/.test(msg)) return '接口地址不对，请检查 Base URL。';
    if (/429/.test(msg)) return '请求太频繁或额度用完了，歇一会儿再试。';
    if (/HTTP 5\d\d/.test(msg)) return 'AI 服务暂时不可用，稍后再试。';
    if (/Failed to fetch|NetworkError|Load failed/i.test(msg)) return '网络连接失败，请检查网络和接口地址。';
    if (/timeout|超时/i.test(msg)) return '等待超时，请稍后再试。';
    if (/JSON|格式/.test(msg)) return '接口返回的格式不太对，请确认是 OpenAI 兼容接口。';
    return 'AI 连接失败：' + msg.replace(/^Error:\s*/, '').slice(0, 160);
  }

  function manualAiReminder() {
    if (!isAiConfigured()) {
      toast('请先配置 AI 接口');
      openAiSheet();
      return;
    }
    if (ui.aiReminderBusy) return;
    var month = C.currentMonth();
    var st = statsFor(month);
    var top = C.categoryBreakdown(st.mtx, 'expense')[0] || null;
    var alert = computeAlert(st, top);
    var rem = C.savingsReminder(st.ss, month) || C.localReminder(st.bs, top, month);
    var r = state.reminders[month] || { level: 0, notified: 0, key: '', aiText: '', aiTriedAt: 0, ts: 0 };
    r.key = alert ? alert.key : (rem && rem.key) || '';
    r.aiTriedAt = Date.now();
    state.reminders[month] = r;
    save();
    ui.aiReminderBusy = true;
    render();
    var messages = C.buildMessages(state, '', 'reminder', { month: month, now: new Date() });
    callAI(messages, 150).then(function (text) {
      var rr = state.reminders[month] || r;
      rr.aiText = cleanAiText(text);
      rr.ts = Date.now();
      state.reminders[month] = rr;
      save();
      ui.aiReminderBusy = false;
      render();
      toast('提醒已更新');
    }).catch(function (err) {
      ui.aiReminderBusy = false;
      render();
      toast('AI 提醒没成功：' + friendlyAiError(err));
    });
  }

  function clearAiChat() {
    if (!state.aiMessages.length) return;
    openConfirm({
      title: '清空对话？',
      message: '只会清空和鲸鲸的聊天记录，账单不会受影响。',
      confirmText: '清空',
      danger: false,
      onConfirm: function () {
        state.aiMessages = [];
        ui.aiError = '';
        save();
        render();
        toast('对话已清空');
      }
    });
  }

  function checkBudget(opts) {
    opts = opts || {};
    if (!state.settings.reminderEnabled) return;
    var month = C.currentMonth();
    var st = statsFor(month);
    var top = C.categoryBreakdown(st.mtx, 'expense')[0] || null;
    var alert = computeAlert(st, top);
    if (!alert || alert.level < 2) return;
    var r = state.reminders[month] || { level: 0, notified: 0, key: '', aiText: '', aiTriedAt: 0, ts: 0 };
    var changed = false;
    if (r.key !== alert.key) {
      r.key = alert.key;
      r.level = alert.level;
      r.aiText = '';
      r.aiTriedAt = 0;
      changed = true;
      if (state.settings.notifyEnabled) {
        if (native && native.notifyBudget) {
          try { native.notifyBudget(alert.title, alert.text); } catch (e) { /* ignore */ }
        }
        r.notified = alert.level;
      }
    }
    if (changed) {
      r.ts = Date.now();
      state.reminders[month] = r;
      save();
    }
    if (state.settings.aiReminder && isAiConfigured() && !r.aiText && (!r.aiTriedAt || Date.now() - r.aiTriedAt > 12 * 3600 * 1000)) {
      autoAiReminder(month, r);
    }
    if (changed && opts.render && ui.tab === 'home') render();
  }

  function autoAiReminder(month, r) {
    if (ui.aiReminderBusy) return;
    ui.aiReminderBusy = true;
    r.aiTriedAt = Date.now();
    state.reminders[month] = r;
    save();
    var messages = C.buildMessages(state, '', 'reminder', { month: month, now: new Date() });
    callAI(messages, 150).then(function (text) {
      var rr = state.reminders[month] || r;
      rr.aiText = cleanAiText(text);
      rr.ts = Date.now();
      state.reminders[month] = rr;
      save();
      ui.aiReminderBusy = false;
      if (ui.tab === 'home') render();
    }).catch(function () {
      ui.aiReminderBusy = false;
    });
  }

  /* ---------------- 数据导入导出 ---------------- */

  function backupName() {
    var d = new Date();
    return '哦鲸鲸记账-备份-' + d.getFullYear() + C.pad2(d.getMonth() + 1) + C.pad2(d.getDate()) +
      '-' + C.pad2(d.getHours()) + C.pad2(d.getMinutes()) + '.json';
  }

  function backupJson() {
    var copy = JSON.parse(JSON.stringify(state));
    // 备份可能被分享或上传，绝不把 API Key 一起导出。
    if (copy.settings && copy.settings.ai) copy.settings.ai.apiKey = '';
    return JSON.stringify(copy);
  }

  function exportBackup() {
    var json = backupJson();
    var name = backupName();
    if (native && native.exportBackup) {
      toast('正在导出备份（不含 API Key）…');
      try { native.exportBackup(json, name); } catch (e) { toast('导出失败，试试复制备份'); }
    } else {
      try {
        var blob = new Blob([json], { type: 'application/json;charset=utf-8' });
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name;
        document.body.appendChild(a);
        a.click();
        setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 800);
        toast('备份已下载');
      } catch (e) {
        toast('浏览器不支持直接下载，试试复制备份');
      }
    }
  }

  function importBackup() {
    if (native && native.importBackup) {
      try { native.importBackup(); } catch (e) { toast('打不开文件选择器'); }
      return;
    }
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json,text/plain';
    input.addEventListener('change', function () {
      var file = input.files && input.files[0];
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () { applyImport(String(reader.result || '')); };
      reader.onerror = function () { toast('读取文件失败'); };
      reader.readAsText(file, 'utf-8');
    });
    input.click();
  }

  function applyImport(raw) {
    var parsed;
    try { parsed = JSON.parse(raw); } catch (e) { toast('这不是有效的备份文件'); return; }
    if (!parsed || !Array.isArray(parsed.transactions)) {
      toast('备份里没有找到账单数据');
      return;
    }
    var next = C.normalizeState(parsed);
    openConfirm({
      title: '导入这份备份？',
      message: '备份里有 ' + next.transactions.length + ' 笔账单，导入后会覆盖当前数据。',
      confirmText: '导入',
      danger: false,
      onConfirm: function () {
        state = next;
        ui.month = C.currentMonth();
        ui.bannerDismissedLevel = 0;
        ui.bannerDismissedKey = '';
        save();
        render({ reset: true });
        toast('备份导入成功');
        setTimeout(function () { checkBudget({ render: true }); }, 100);
      }
    });
  }

  function copyBackup() {
    var json = backupJson();
    if (native && native.copyText) {
      try { native.copyText('哦鲸鲸记账备份', json); } catch (e) { toast('复制失败'); }
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(json).then(function () { toast('备份已复制到剪贴板'); }, function () {
        fallbackCopy(json);
      });
    } else {
      fallbackCopy(json);
    }
  }

  function fallbackCopy(text) {
    try {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      var ok = document.execCommand('copy');
      ta.remove();
      toast(ok ? '备份已复制到剪贴板' : '复制失败，试试导出备份');
    } catch (e) {
      toast('复制失败，试试导出备份');
    }
  }

  function confirmDemoData() {
    openConfirm({
      title: '载入示例账单？',
      message: '会替换当前账单，并设一个支出上限和攒钱目标，方便先体验。',
      confirmText: '载入示例',
      danger: false,
      onConfirm: function () {
        state.transactions = C.makeDemoTransactions(new Date());
        if (!state.budget) state.budget = 4500;
        state.savingsGoals = state.savingsGoals || {};
        if (!state.savingsGoals[C.currentMonth()]) state.savingsGoals[C.currentMonth()] = 3000;
        state.reminders[C.currentMonth()] = { level: 0, notified: 0, key: '', aiText: '', aiTriedAt: 0, ts: 0 };
        save();
        ui.month = C.currentMonth();
        ui.bannerDismissedLevel = 0;
        ui.bannerDismissedKey = '';
        render({ reset: true });
        toast('示例账单已就位');
        setTimeout(function () { checkBudget({ render: true }); }, 100);
      }
    });
  }

  function confirmClearData() {
    openConfirm({
      title: '清空所有账单？',
      message: '所有收支记录、支出上限和攒钱目标都会删除，AI 接口配置会保留。这个操作无法撤销。',
      confirmText: '全部清空',
      danger: true,
      onConfirm: function () {
        state.transactions = [];
        state.budget = 0;
        state.savingsGoals = {};
        state.reminders = {};
        save();
        ui.month = C.currentMonth();
        ui.bannerDismissedKey = '';
        render({ reset: true });
        toast('账单已清空');
      }
    });
  }

  function toast(message) {
    var el = $('#toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(function () { el.classList.remove('show'); }, 2400);
  }

  function scrollChatBottom(smooth) {
    var chat = $('#chat');
    if (!chat) return;
    chat.scrollTo({ top: chat.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
  }

  /* ---------------- 原生回调 ---------------- */

  window.__nativeAIResult = function (id, result) {
    var p = ui.pending[id];
    if (!p) return;
    if (result && result.ok) p.finish(null, result.content);
    else p.finish(new Error((result && result.error) || 'AI 请求失败'));
  };

  window.__nativeExportResult = function (ok, message) {
    toast(message || (ok ? '备份导出成功' : '导出失败'));
  };

  window.__nativeImportResult = function (result) {
    if (!result || !result.ok) {
      toast((result && result.payload) || '导入失败');
      return;
    }
    applyImport(result.payload);
  };

  window.__nativeNotifyPermission = function (granted) {
    if (granted) toast('通知已开启');
    else toast('通知权限未开启，仍会在应用内提醒');
  };

  window.appHandleBack = function () {
    if (ui.sheet) {
      closeSheet();
      return true;
    }
    if (ui.tab !== 'home') {
      switchTab('home');
      return true;
    }
    var view = $('#view');
    if (view && view.scrollTop > 40) {
      view.scrollTo({ top: 0, behavior: 'smooth' });
      return true;
    }
    return false;
  };

  window.appOnResume = function () {
    var now = C.todayStr();
    if (now !== ui.lastDay) {
      ui.lastDay = now;
      if (ui.tab === 'home' || ui.tab === 'records') render();
    }
    setTimeout(function () { checkBudget({ render: true }); }, 500);
  };

  /* ---------------- 初始化 ---------------- */

  function init() {
    if (native && native.getVersionName) {
      try {
        var v = native.getVersionName();
        if (v) appVersion = v;
      } catch (e) { /* ignore */ }
    }
    var params = new URLSearchParams(window.location.search || '');
    if (params.get('month') && /^\d{4}-\d{2}$/.test(params.get('month'))) {
      ui.month = params.get('month');
    }
    if (params.get('tab') && ['home', 'records', 'ai', 'settings'].indexOf(params.get('tab')) >= 0) {
      ui.tab = params.get('tab');
    }
    if (params.get('demo') === '1' && state.transactions.length === 0) {
      state.transactions = C.makeDemoTransactions(new Date());
      if (!state.budget) state.budget = 4500;
      state.savingsGoals = state.savingsGoals || {};
      if (!state.savingsGoals[C.currentMonth()]) state.savingsGoals[C.currentMonth()] = 3000;
      save();
    }
    if (ui.month > C.currentMonth()) ui.month = C.currentMonth();
    render({ reset: true });

    var boot = $('#boot');
    if (boot) {
      if (params.get('shot') === '1') boot.classList.add('hide');
      else setTimeout(function () { boot.classList.add('hide'); }, 320);
    }

    setTimeout(function () {
      checkBudget({ render: true });
    }, 850);
  }

  document.addEventListener('click', handleClick);
  document.addEventListener('input', handleInput);
  document.addEventListener('keydown', handleKeydown);
  setInterval(function () {
    if (C.todayStr() !== ui.lastDay) window.appOnResume();
  }, 30000);

  init();
})();
