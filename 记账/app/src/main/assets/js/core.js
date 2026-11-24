/* 哦鲸鲸记账 — 纯逻辑核心（浏览器与 Node 测试共用） */
(function (global) {
  'use strict';

  var VERSION = 1;
  var LS_KEY = 'ohwhale_state_v1';
  var DEFAULT_BASE_URL = 'https://api.deepseek.com/v1';
  var DEFAULT_MODEL = 'deepseek-chat';

  var CATEGORIES = {
    expense: [
      { id: 'food', name: '餐饮', emoji: '🍜', color: '#F6A96B' },
      { id: 'transport', name: '交通', emoji: '🚌', color: '#6FA8DC' },
      { id: 'shopping', name: '购物', emoji: '🛍️', color: '#E88AB4' },
      { id: 'home', name: '居住', emoji: '🏠', color: '#7CC4B4' },
      { id: 'fun', name: '娱乐', emoji: '🎮', color: '#B49BDF' },
      { id: 'health', name: '医疗', emoji: '💊', color: '#EE8C8C' },
      { id: 'study', name: '学习', emoji: '📚', color: '#8CBF8F' },
      { id: 'social', name: '人情', emoji: '🎁', color: '#E9BE63' },
      { id: 'other', name: '其他', emoji: '🫧', color: '#9FB0C0' }
    ],
    income: [
      { id: 'salary', name: '工资', emoji: '💼', color: '#45B99D' },
      { id: 'bonus', name: '奖金', emoji: '🌟', color: '#E9BE63' },
      { id: 'invest', name: '理财', emoji: '📈', color: '#6FA8DC' },
      { id: 'parttime', name: '兼职', emoji: '🧩', color: '#B49BDF' },
      { id: 'gift', name: '红包', emoji: '🧧', color: '#E88AB4' },
      { id: 'other_income', name: '其他', emoji: '🫧', color: '#9FB0C0' }
    ]
  };

  var CATEGORY_MAP = {};
  CATEGORIES.expense.forEach(function (c) { CATEGORY_MAP[c.id] = c; });
  CATEGORIES.income.forEach(function (c) { CATEGORY_MAP[c.id] = c; });

  function pad2(n) { return n < 10 ? '0' + n : String(n); }

  function formatDate(d) {
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
  }

  function todayStr(d) { return formatDate(d || new Date()); }

  function parseDate(str) {
    if (typeof str !== 'string') return null;
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str);
    if (!m) return null;
    var y = Number(m[1]), mo = Number(m[2]), da = Number(m[3]);
    if (mo < 1 || mo > 12 || da < 1 || da > 31) return null;
    var d = new Date(y, mo - 1, da);
    if (d.getFullYear() !== y || d.getMonth() !== mo - 1 || d.getDate() !== da) return null;
    return d;
  }

  function isValidDate(str) { return !!parseDate(str); }

  function currentMonth(now) { return todayStr(now).slice(0, 7); }

  function monthOf(dateStr) { return String(dateStr || '').slice(0, 7); }

  function monthLabel(key) {
    var p = String(key || '').split('-');
    return Number(p[0]) + '年' + Number(p[1]) + '月';
  }

  function shortMonth(key) {
    var p = String(key || '').split('-');
    return Number(p[1]) + '月';
  }

  function addMonths(key, delta) {
    var p = String(key).split('-');
    var d = new Date(Number(p[0]), Number(p[1]) - 1 + Number(delta), 1);
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1);
  }

  function daysInMonth(key) {
    var p = String(key).split('-');
    return new Date(Number(p[0]), Number(p[1]), 0).getDate();
  }

  function weekdayName(dateStr) {
    var d = parseDate(dateStr);
    if (!d) return '';
    return '周' + ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  }

  function dateLabel(dateStr, today) {
    var t = today || todayStr();
    if (dateStr === t) return '今天';
    var td = parseDate(t);
    if (!td) return dateStr;
    var d = parseDate(dateStr);
    if (!d) return dateStr;
    var diff = Math.round((td.getTime() - d.getTime()) / 86400000);
    if (diff === 1) return '昨天';
    if (diff === 2) return '前天';
    return (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }

  function uid() {
    return 't' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }

  function round2(n) { return Math.round((Number(n) + Number.EPSILON) * 100) / 100; }

  function formatMoney(n, decimals) {
    if (decimals === undefined) decimals = 2;
    var num = Number(n);
    if (!isFinite(num)) num = 0;
    try {
      return num.toLocaleString('zh-CN', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
      });
    } catch (e) {
      return num.toFixed(decimals);
    }
  }

  function formatInputAmount(raw) {
    var s = String(raw == null ? '' : raw);
    if (!s) return '0.00';
    var parts = s.split('.');
    var intPart = parts[0] || '0';
    intPart = intPart.replace(/^0+(?=\d)/, '');
    var grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (parts.length > 1) return grouped + '.' + parts.slice(1).join('');
    return grouped;
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function safeText(value, max) {
    if (typeof value !== 'string') return '';
    return value.slice(0, max || 200);
  }

  function defaultState() {
    return {
      version: VERSION,
      transactions: [],
      budget: 0,
      savingsGoals: {},
      settings: {
        reminderEnabled: true,
        notifyEnabled: true,
        aiReminder: false,
        ai: {
          provider: 'deepseek',
          baseUrl: DEFAULT_BASE_URL,
          apiKey: '',
          model: DEFAULT_MODEL
        }
      },
      aiMessages: [],
      reminders: {},
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
  }

  function normalizeTx(raw) {
    if (!raw || typeof raw !== 'object') return null;
    var type = raw.type === 'income' ? 'income' : 'expense';
    var amount = round2(Number(raw.amount));
    if (!isFinite(amount) || amount <= 0 || amount > 99999999) return null;
    var date = isValidDate(raw.date) ? raw.date : todayStr();
    var category = typeof raw.category === 'string' ? raw.category : '';
    var validCats = CATEGORIES[type].map(function (c) { return c.id; });
    if (validCats.indexOf(category) < 0) category = validCats[0];
    var createdAt = Number(raw.createdAt);
    if (!isFinite(createdAt) || createdAt <= 0) createdAt = Date.now();
    return {
      id: typeof raw.id === 'string' && raw.id ? raw.id.slice(0, 48) : uid(),
      type: type,
      amount: amount,
      category: category,
      date: date,
      note: safeText(raw.note, 60),
      createdAt: createdAt
    };
  }

  function normalizeState(raw) {
    var base = defaultState();
    if (!raw || typeof raw !== 'object') return base;

    var seen = {};
    if (Array.isArray(raw.transactions)) {
      raw.transactions.forEach(function (t) {
        var tx = normalizeTx(t);
        if (!tx) return;
        if (seen[tx.id]) tx.id = uid();
        seen[tx.id] = true;
        base.transactions.push(tx);
      });
    }

    var budget = Number(raw.budget);
    if (!isFinite(budget) || budget < 0) budget = 0;
    base.budget = round2(Math.min(budget, 99999999));

    base.savingsGoals = {};
    if (raw.savingsGoals && typeof raw.savingsGoals === 'object') {
      Object.keys(raw.savingsGoals).slice(-24).forEach(function (key) {
        var parts = String(key).split('-');
        if (parts.length !== 2 || parts[0].length !== 4 || parts[1].length !== 2) return;
        if (!/^[0-9]+$/.test(parts[0]) || !/^[0-9]+$/.test(parts[1])) return;
        var goal = Number(raw.savingsGoals[key]);
        if (isFinite(goal) && goal > 0) base.savingsGoals[key] = round2(Math.min(goal, 99999999));
      });
    }
    // 兼容早期没有按月份存储的版本。
    if (!Object.keys(base.savingsGoals).length) {
      var legacyGoal = Number(raw.savingsGoal);
      if (isFinite(legacyGoal) && legacyGoal > 0) {
        base.savingsGoals[currentMonth()] = round2(Math.min(legacyGoal, 99999999));
      }
    }

    var s = raw.settings || {};
    base.settings.reminderEnabled = s.reminderEnabled !== false;
    base.settings.notifyEnabled = s.notifyEnabled !== false;
    base.settings.aiReminder = s.aiReminder === true;
    var ai = s.ai || {};
    base.settings.ai.provider = typeof ai.provider === 'string' ? ai.provider.slice(0, 20) : 'deepseek';
    base.settings.ai.baseUrl = safeText(ai.baseUrl, 300).trim() || DEFAULT_BASE_URL;
    base.settings.ai.apiKey = safeText(ai.apiKey, 300).trim();
    base.settings.ai.model = safeText(ai.model, 80).trim() || DEFAULT_MODEL;

    if (Array.isArray(raw.aiMessages)) {
      base.aiMessages = raw.aiMessages.filter(function (m) {
        return m && (m.role === 'user' || m.role === 'assistant') &&
          typeof m.content === 'string' && m.content.trim();
      }).slice(-40).map(function (m) {
        return {
          role: m.role,
          content: m.content.slice(0, 4000),
          ts: Number(m.ts) || Date.now()
        };
      });
    }

    if (raw.reminders && typeof raw.reminders === 'object') {
      Object.keys(raw.reminders).slice(-12).forEach(function (k) {
        if (!/^\d{4}-\d{2}$/.test(k)) return;
        var r = raw.reminders[k] || {};
        base.reminders[k] = {
          level: Math.max(0, Math.min(3, Number(r.level) || 0)),
          notified: Math.max(0, Math.min(3, Number(r.notified) || 0)),
          key: safeText(r.key, 32),
          aiText: safeText(r.aiText, 300),
          aiTriedAt: Number(r.aiTriedAt) || 0,
          ts: Number(r.ts) || 0
        };
      });
    }

    base.createdAt = Number(raw.createdAt) || Date.now();
    base.updatedAt = Number(raw.updatedAt) || Date.now();
    return base;
  }

  function sum(list, key) {
    return (list || []).reduce(function (acc, item) {
      var v = Number(item && item[key]);
      return acc + (isFinite(v) ? v : 0);
    }, 0);
  }

  function monthTransactions(txs, month) {
    return (txs || []).filter(function (t) { return monthOf(t.date) === month; });
  }

  function totals(list) {
    var income = 0, expense = 0;
    (list || []).forEach(function (t) {
      if (t.type === 'income') income += Number(t.amount) || 0;
      else expense += Number(t.amount) || 0;
    });
    return {
      income: round2(income),
      expense: round2(expense),
      balance: round2(income - expense),
      count: (list || []).length
    };
  }

  function categoryBreakdown(list, type) {
    var map = {};
    (list || []).forEach(function (t) {
      if (t.type !== type) return;
      var id = t.category;
      if (!map[id]) map[id] = { id: id, amount: 0, count: 0 };
      map[id].amount += Number(t.amount) || 0;
      map[id].count += 1;
    });
    var total = 0;
    Object.keys(map).forEach(function (k) { total += map[k].amount; });
    return Object.keys(map).map(function (k) {
      var c = CATEGORY_MAP[k] || CATEGORY_MAP.other;
      return {
        id: k,
        name: c.name,
        emoji: c.emoji,
        color: c.color,
        amount: round2(map[k].amount),
        count: map[k].count,
        percent: total > 0 ? map[k].amount / total : 0
      };
    }).sort(function (a, b) { return b.amount - a.amount; });
  }

  function groupByDate(list) {
    var map = {};
    (list || []).forEach(function (t) {
      if (!map[t.date]) map[t.date] = [];
      map[t.date].push(t);
    });
    return Object.keys(map).sort(function (a, b) { return a < b ? 1 : -1; }).map(function (date) {
      var items = map[date].slice().sort(function (a, b) {
        if (b.createdAt !== a.createdAt) return b.createdAt - a.createdAt;
        return a.id < b.id ? 1 : -1;
      });
      var t = totals(items);
      return { date: date, items: items, totals: t };
    });
  }

  function budgetStats(expense, budget, month, today) {
    var dim = daysInMonth(month);
    var todayKey = today || todayStr();
    var current = currentMonth();
    var daysPassed;
    if (month < current) daysPassed = dim;
    else if (month > current) daysPassed = 0;
    else daysPassed = Number(todayKey.slice(8, 10)) || 1;
    var daysLeft = month === current ? Math.max(0, dim - daysPassed + 1) : (month > current ? dim : 0);
    var b = Number(budget) || 0;
    var e = Number(expense) || 0;
    var ratio = b > 0 ? e / b : 0;
    var remaining = b - e;
    var avgDaily = daysPassed > 0 ? e / daysPassed : 0;
    var projected = daysPassed > 0 ? avgDaily * dim : 0;
    var level = 0;
    if (b > 0) {
      if (ratio >= 1) level = 3;
      else if (ratio >= 0.8) level = 2;
      else if (ratio >= 0.6) level = 1;
    }
    return {
      budget: round2(b),
      expense: round2(e),
      ratio: ratio,
      percent: Math.round(ratio * 100),
      remaining: round2(remaining),
      daysInMonth: dim,
      daysPassed: daysPassed,
      daysLeft: daysLeft,
      avgDaily: round2(avgDaily),
      projected: round2(projected),
      dailyAllowance: round2(daysLeft > 0 ? Math.max(remaining, 0) / daysLeft : 0),
      level: level,
      over: round2(Math.max(0, e - b)),
      hasBudget: b > 0
    };
  }

  function savingsStats(income, expense, goal, month, today, todayExpense) {
    var dim = daysInMonth(month);
    var todayKey = today || todayStr();
    var current = currentMonth();
    var daysPassed;
    if (month < current) daysPassed = dim;
    else if (month > current) daysPassed = 0;
    else daysPassed = Number(todayKey.slice(8, 10)) || 1;
    var daysLeft = month === current ? Math.max(0, dim - daysPassed + 1) : (month > current ? dim : 0);
    var g = Math.max(0, Number(goal) || 0);
    var inc = Number(income) || 0;
    var exp = Number(expense) || 0;
    var saved = round2(inc - exp);
    var ratio = g > 0 ? Math.max(0, Math.min(1, saved / g)) : 0;
    var shortfall = round2(Math.max(0, g - saved));
    var free = round2(inc - g - exp);
    var dailyAllowance = daysLeft > 0 ? round2(Math.max(0, free) / daysLeft) : 0;
    var avgDaily = daysPassed > 0 ? exp / daysPassed : 0;
    var projectedExpense = round2(avgDaily * dim);
    var projectedSaved = round2(inc - projectedExpense);
    var projectedShortfall = round2(Math.max(0, g - projectedSaved));
    var metNow = g > 0 && saved >= g;
    var achieved = metNow && projectedSaved >= g;
    var level = 0;
    if (g > 0) {
      if (metNow) level = achieved ? 1 : 2;
      else level = shortfall > g * 0.2 ? 3 : 2;
    }
    var todayLeft = round2(Math.max(0, dailyAllowance - (Number(todayExpense) || 0)));
    return {
      goal: round2(g),
      income: round2(inc),
      expense: round2(exp),
      saved: saved,
      ratio: ratio,
      percent: Math.round(ratio * 100),
      shortfall: shortfall,
      projectedShortfall: projectedShortfall,
      free: free,
      daysInMonth: dim,
      daysPassed: daysPassed,
      daysLeft: daysLeft,
      dailyAllowance: dailyAllowance,
      projectedExpense: projectedExpense,
      projectedSaved: projectedSaved,
      todayLeft: todayLeft,
      metNow: metNow,
      achieved: achieved,
      level: level,
      hasGoal: g > 0
    };
  }

  function savingsReminder(stats, month) {
    if (!stats || !stats.hasGoal) return null;
    var tail = stats.dailyAllowance > 0
      ? '按剩下的 ' + stats.daysLeft + ' 天算，每天最多花 ¥' + formatMoney(stats.dailyAllowance) + '。'
      : '这个月已经没有能自由花的额度了。';
    if (stats.achieved) {
      return {
        key: 'savings-1',
        level: 1,
        tone: 'calm',
        title: '这个月的攒钱目标已经够了',
        text: '已经攒下 ¥' + formatMoney(stats.saved) + '，比目标多 ¥' + formatMoney(Math.max(0, stats.saved - stats.goal)) + '。剩下的日子，花得舒服一点。'
      };
    }
    if (stats.metNow) {
      return {
        key: 'savings-2',
        level: 2,
        tone: 'warn',
        title: '现在够，月底未必够',
        text: '已经攒下 ¥' + formatMoney(stats.saved) + '，但按现在的花法，月底会比目标少 ¥' + formatMoney(stats.projectedShortfall) + '。' + tail + '后面收着点。'
      };
    }
    if (stats.level >= 3) {
      return {
        key: 'savings-3',
        level: 3,
        tone: 'danger',
        title: '攒钱目标有点悬',
        text: '这个月想攒 ¥' + formatMoney(stats.goal) + '，现在离目标还差 ¥' + formatMoney(stats.shortfall) + '。' + tail + '先把非必要消费停一停。'
      };
    }
    return {
      key: 'savings-2',
      level: 2,
      tone: 'warn',
      title: '花得比计划快一点',
      text: '离攒钱目标还差 ¥' + formatMoney(stats.shortfall) + '，' + tail + '花之前先看一眼。'
    };
  }

  function localReminder(stats, topCategory, month) {
    var p = stats.percent;
    var top = topCategory && topCategory.amount > 0
      ? topCategory.name + '已经花了 ¥' + formatMoney(topCategory.amount)
      : '';
    var text, title, tone;
    if (stats.level >= 3) {
      title = '这个月超支了';
      tone = 'danger';
      text = '已经超了 ¥' + formatMoney(stats.over) + '。剩下的 ' + stats.daysLeft + ' 天，每天最多花 ¥' +
        formatMoney(stats.dailyAllowance) + '，先停一停非必要消费。' + (top ? top + '。' : '');
    } else if (stats.level === 2) {
      title = '支出上限快到了';
      tone = 'warn';
      text = '已经用了 ' + p + '%，还剩 ¥' + formatMoney(Math.max(stats.remaining, 0)) + '。接下来每天约 ¥' +
        formatMoney(stats.dailyAllowance) + '。' + (top ? top + '。' : '');
    } else if (stats.level === 1) {
      title = '支出进度过半';
      tone = 'notice';
      text = '已经用了 ' + p + '%。照这个速度，月底大约 ¥' + formatMoney(stats.projected) + '，' +
        (stats.projected > stats.budget ? '比上限多 ¥' + formatMoney(stats.projected - stats.budget) + '。' : '还在上限内。');
    } else {
      title = '支出很稳';
      tone = 'calm';
      text = stats.hasBudget
        ? '已经用了 ' + p + '%，还剩 ¥' + formatMoney(Math.max(stats.remaining, 0)) + '。照这个速度，月底大约 ¥' + formatMoney(stats.projected) + '。'
        : '还没设支出上限。设一个，快超的时候我会提醒你。';
    }
    return { level: stats.level, title: title, text: text, tone: tone, month: month };
  }

  function dailySeries(list, month) {
    var dim = daysInMonth(month);
    var arr = [];
    for (var i = 1; i <= dim; i++) {
      arr.push({ day: i, date: month + '-' + pad2(i), expense: 0, income: 0 });
    }
    (list || []).forEach(function (t) {
      var d = Number(String(t.date).slice(8, 10));
      if (d >= 1 && d <= dim) {
        if (t.type === 'income') arr[d - 1].income += Number(t.amount) || 0;
        else arr[d - 1].expense += Number(t.amount) || 0;
      }
    });
    arr.forEach(function (x) { x.expense = round2(x.expense); x.income = round2(x.income); });
    return arr;
  }

  function snapshot(state, month, now) {
    var today = todayStr(now);
    var mtx = monthTransactions(state.transactions, month);
    var t = totals(mtx);
    var bs = budgetStats(t.expense, state.budget, month, today);
    var savingsGoal = Number((state.savingsGoals || {})[month]) || 0;
    var ss = savingsStats(t.income, t.expense, savingsGoal, month, today);
    var cats = categoryBreakdown(mtx, 'expense');
    var prev = addMonths(month, -1);
    var prevT = totals(monthTransactions(state.transactions, prev));
    var series = dailySeries(mtx, month);
    var recent = mtx.slice().sort(function (a, b) {
      if (a.date !== b.date) return a.date < b.date ? 1 : -1;
      return b.createdAt - a.createdAt;
    }).slice(0, 15).map(function (x) {
      return {
        date: x.date,
        type: x.type,
        category: (CATEGORY_MAP[x.category] || {}).name || x.category,
        amount: x.amount,
        note: x.note
      };
    });
    return {
      app: '哦鲸鲸记账',
      month: month,
      today: today,
      totals: t,
      budget: state.budget,
      budgetStatus: {
        percent: bs.percent,
        remaining: bs.remaining,
        over: bs.over,
        daysLeft: bs.daysLeft,
        avgDaily: bs.avgDaily,
        dailyAllowance: bs.dailyAllowance,
        projected: bs.projected,
        level: bs.level
      },
      savingsGoal: round2(savingsGoal),
      savingsStatus: {
        saved: ss.saved,
        percent: ss.percent,
        shortfall: ss.shortfall,
        dailyAllowance: ss.dailyAllowance,
        projectedSaved: ss.projectedSaved,
        daysLeft: ss.daysLeft,
        achieved: ss.achieved,
        level: ss.level
      },
      previousMonth: { month: prev, income: prevT.income, expense: prevT.expense, balance: prevT.balance },
      categories: cats.slice(0, 8).map(function (c) {
        return { name: c.name, amount: c.amount, percent: Math.round(c.percent * 100), count: c.count };
      }),
      daily: series.filter(function (d) { return d.expense > 0 || d.income > 0; }),
      recentTransactions: recent
    };
  }

  function systemPrompt(mode) {
    var base = '你是「哦鲸鲸记账」里的理财助手鲸鲸。你说话自然、务实、不评判，像一个会记账的朋友。' +
      '你只能依据用户提供的真实账单数据回答，金额一律使用人民币（¥），不要编造数据；数据不足时直接说明。' +
      '建议要具体、可执行，避免空泛的大道理。使用简体中文，回答用简洁的 Markdown；' +
      '普通回答控制在 350 字以内，分点不超过 5 条。';
    if (mode === 'plan') {
      return base + '这次请重点给出下个月的预算分配和储蓄计划：先给一句结论，再给出分类预算表（最多 6 类），' +
        '最后给 3 条最容易执行的动作。不要推荐具体金融产品，不构成投资建议。';
    }
    if (mode === 'analysis') {
      return base + '这次请像一个账单分析师：先指出本月最值得注意的 1 个事实，再分析 2-3 个消费结构问题，' +
        '最后给 3 条具体可执行的调整建议。不要只复述数字。';
    }
    if (mode === 'reminder') {
      return '你是「哦鲸鲸记账」的预算和攒钱提醒助手鲸鲸。根据用户本月的预算、攒钱目标和消费数据，写一句 40-70 字的中文提醒。' +
        '要求：自然、具体，指出预算或攒钱目标的风险，并给 1 条可执行的小建议。只输出提醒正文，不要标题、引号、Markdown 或解释。';
    }
    return base;
  }

  function buildMessages(state, question, mode, options) {
    options = options || {};
    var now = options.now || new Date();
    var month = options.month || currentMonth(now);
    var data = snapshot(state, month, now);
    var q = safeText(question, 1500).trim();
    var messages = [{ role: 'system', content: systemPrompt(mode) }];
    if (mode !== 'reminder' && Array.isArray(options.history)) {
      options.history.slice(-6).forEach(function (m) {
        if (m && (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string') {
          messages.push({ role: m.role, content: m.content.slice(0, 2500) });
        }
      });
    }
    var intro = mode === 'reminder'
      ? '这是我的本月账单摘要（JSON）：\n'
      : '这是我的账单数据（JSON，金额单位：元）：\n';
    var ask = mode === 'reminder'
      ? '\n\n请根据这些数据，写一句 40-70 字的预算或攒钱提醒。'
      : '\n\n我的问题：' + (q || '请帮我看看这个月的账单，有什么值得注意的地方？');
    messages.push({ role: 'user', content: intro + JSON.stringify(data) + ask });
    return messages;
  }

  function normalizeEndpoint(baseUrl) {
    var raw = String(baseUrl || '').trim();
    if (!raw) return '';
    if (!/^https?:\/\//i.test(raw)) raw = 'https://' + raw;
    var hash = '';
    var hashIndex = raw.indexOf('#');
    if (hashIndex >= 0) { hash = raw.slice(hashIndex); raw = raw.slice(0, hashIndex); }
    var q = '';
    var qIndex = raw.indexOf('?');
    if (qIndex >= 0) { q = raw.slice(qIndex); raw = raw.slice(0, qIndex); }
    raw = raw.replace(/\/+$/, '');
    if (/\/chat\/completions$/i.test(raw)) return raw + q + hash;
    if (/\/v1$/i.test(raw)) return raw + '/chat/completions' + q + hash;
    return raw + '/v1/chat/completions' + q + hash;
  }

  function parseAssistantResponse(payload) {
    var data = payload;
    if (typeof payload === 'string') {
      try { data = JSON.parse(payload); }
      catch (e) { throw new Error('接口返回的不是有效 JSON'); }
    }
    if (!data || typeof data !== 'object') throw new Error('接口返回为空');
    if (data.error) {
      var msg = (data.error && (data.error.message || data.error.type)) || String(data.error);
      throw new Error(msg);
    }
    var choice = data.choices && data.choices[0];
    var content = choice && choice.message && choice.message.content;
    if (Array.isArray(content)) {
      content = content.map(function (part) {
        return part && typeof part.text === 'string' ? part.text : '';
      }).join('');
    }
    if (!content && choice && typeof choice.text === 'string') content = choice.text;
    if (!content && typeof data.output_text === 'string') content = data.output_text;
    if (!content && data.choices && data.choices[0] && data.choices[0].message &&
      typeof data.choices[0].message.reasoning_content === 'string') {
      content = data.choices[0].message.reasoning_content;
    }
    if (typeof content !== 'string' || !content.trim()) throw new Error('AI 没有返回内容，请稍后再试');
    return content.trim();
  }

  function makeDemoTransactions(now) {
    var t = now || new Date();
    var y = t.getFullYear(), m = t.getMonth() + 1;
    var d = t.getDate();
    function day(offset) {
      var dd = Math.max(1, d - offset);
      return y + '-' + pad2(m) + '-' + pad2(dd);
    }
    function at(dateStr, hour) {
      var parts = String(dateStr).split('-');
      return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]), hour || 12, 0, 0).getTime();
    }
    var rows = [
      ['expense', 2600, 'home', day(12), '房租', 9],
      ['expense', 268.5, 'shopping', day(3), '超市囤货', 19],
      ['expense', 399, 'shopping', day(6), '换季外套', 15],
      ['expense', 32, 'food', day(0), '早餐 + 咖啡', 8],
      ['expense', 48, 'food', day(0), '午饭', 12],
      ['expense', 26, 'food', day(1), '晚饭', 18],
      ['expense', 18, 'transport', day(0), '地铁', 8],
      ['expense', 76, 'fun', day(4), '电影', 20],
      ['expense', 199, 'health', day(8), '健身房月卡', 10],
      ['expense', 59, 'home', day(9), '话费', 11],
      ['expense', 88, 'social', day(5), '朋友生日礼物', 16],
      ['income', 12000, 'salary', day(10), '工资', 9],
      ['income', 800, 'parttime', day(2), '周末兼职', 21]
    ];
    return rows.map(function (r) {
      return {
        id: uid(),
        type: r[0],
        amount: r[1],
        category: r[2],
        date: r[3],
        note: r[4],
        createdAt: at(r[3], r[5])
      };
    });
  }

  var WhaleCore = {
    VERSION: VERSION,
    LS_KEY: LS_KEY,
    DEFAULT_BASE_URL: DEFAULT_BASE_URL,
    DEFAULT_MODEL: DEFAULT_MODEL,
    CATEGORIES: CATEGORIES,
    CATEGORY_MAP: CATEGORY_MAP,
    pad2: pad2,
    formatDate: formatDate,
    todayStr: todayStr,
    parseDate: parseDate,
    isValidDate: isValidDate,
    currentMonth: currentMonth,
    monthOf: monthOf,
    monthLabel: monthLabel,
    shortMonth: shortMonth,
    addMonths: addMonths,
    daysInMonth: daysInMonth,
    weekdayName: weekdayName,
    dateLabel: dateLabel,
    uid: uid,
    round2: round2,
    formatMoney: formatMoney,
    formatInputAmount: formatInputAmount,
    escapeHtml: escapeHtml,
    defaultState: defaultState,
    normalizeState: normalizeState,
    sum: sum,
    monthTransactions: monthTransactions,
    totals: totals,
    categoryBreakdown: categoryBreakdown,
    groupByDate: groupByDate,
    budgetStats: budgetStats,
    savingsStats: savingsStats,
    savingsReminder: savingsReminder,
    localReminder: localReminder,
    dailySeries: dailySeries,
    snapshot: snapshot,
    systemPrompt: systemPrompt,
    buildMessages: buildMessages,
    normalizeEndpoint: normalizeEndpoint,
    parseAssistantResponse: parseAssistantResponse,
    makeDemoTransactions: makeDemoTransactions
  };

  global.WhaleCore = WhaleCore;
  if (typeof module !== 'undefined' && module.exports) module.exports = WhaleCore;
})(typeof window !== 'undefined' ? window : globalThis);
