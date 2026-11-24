'use strict';
const assert = require('assert');
const C = require('../app/src/main/assets/js/core.js');

function tx(over) {
  return Object.assign({
    id: C.uid(), type: 'expense', amount: 10, category: 'food',
    date: '2026-11-17', note: '', createdAt: Date.now()
  }, over || {});
}

// 日期与月份
assert.strictEqual(C.monthOf('2026-11-17'), '2026-11');
assert.strictEqual(C.addMonths('2026-01', -1), '2025-12');
assert.strictEqual(C.addMonths('2026-12', 1), '2027-01');
assert.strictEqual(C.daysInMonth('2024-02'), 29);
assert.strictEqual(C.daysInMonth('2026-02'), 28);
assert.strictEqual(C.weekdayName('2026-11-17'), '周二');
assert.strictEqual(C.dateLabel('2026-11-17', '2026-11-17'), '今天');
assert.strictEqual(C.dateLabel('2026-11-16', '2026-11-17'), '昨天');

// 金额格式
assert.strictEqual(C.formatMoney(1234.5), '1,234.50');
assert.strictEqual(C.formatMoney(0, 0), '0');
assert.strictEqual(C.formatInputAmount('1234.'), '1,234.');
assert.strictEqual(C.formatInputAmount('0.5'), '0.5');
assert.strictEqual(C.formatInputAmount(''), '0.00');

// 月度过滤、汇总、分类
const list = [
  tx({ amount: 100, category: 'food', date: '2026-11-01' }),
  tx({ amount: 50, category: 'food', date: '2026-11-02' }),
  tx({ amount: 30, category: 'transport', date: '2026-11-02' }),
  tx({ type: 'income', amount: 500, category: 'salary', date: '2026-11-10' }),
  tx({ amount: 999, category: 'home', date: '2026-12-01' })
];
const nov = C.monthTransactions(list, '2026-11');
assert.strictEqual(nov.length, 4);
const totals = C.totals(nov);
assert.deepStrictEqual(totals, { income: 500, expense: 180, balance: 320, count: 4 });
const cats = C.categoryBreakdown(nov, 'expense');
assert.strictEqual(cats[0].name, '餐饮');
assert.strictEqual(cats[0].amount, 150);
assert.strictEqual(cats[0].count, 2);
assert.ok(Math.abs(cats[0].percent - 150 / 180) < 1e-9);

// 按日期分组
const groups = C.groupByDate(nov);
assert.strictEqual(groups.length, 3);
assert.strictEqual(groups[0].date, '2026-11-10');
assert.strictEqual(groups[1].items.length, 2);
assert.strictEqual(groups[1].totals.expense, 80);

// 预算状态
let bs = C.budgetStats(4000, 5000, '2026-11', '2026-11-17');
assert.strictEqual(bs.percent, 80);
assert.strictEqual(bs.level, 2);
assert.strictEqual(bs.daysLeft, 14);
assert.strictEqual(bs.remaining, 1000);
assert.ok(Math.abs(bs.dailyAllowance - 1000 / 14) < 0.01);
const over = C.budgetStats(5200, 5000, '2026-11', '2026-11-17');
assert.strictEqual(over.level, 3);
assert.strictEqual(over.over, 200);
const noBudget = C.budgetStats(100, 0, '2026-11', '2026-11-17');
assert.strictEqual(noBudget.level, 0);
assert.strictEqual(noBudget.percent, 0);

// 攒钱目标：已达成 / 目标落空 / 按当前节奏偏慢 / 未设置
let ss = C.savingsStats(12000, 3813.5, 3000, '2026-11', '2026-11-17');
assert.strictEqual(ss.saved, 8186.5);
assert.strictEqual(ss.shortfall, 0);
assert.strictEqual(ss.achieved, true);
assert.strictEqual(ss.level, 1);
assert.strictEqual(ss.daysLeft, 14);
assert.ok(Math.abs(ss.dailyAllowance - 5186.5 / 14) < 0.01);
const ssShort = C.savingsStats(12000, 3813.5, 12000, '2026-11', '2026-11-17');
assert.strictEqual(ssShort.level, 3);
assert.strictEqual(ssShort.shortfall, 3813.5);
assert.strictEqual(ssShort.dailyAllowance, 0);
const ssSlow = C.savingsStats(12000, 6000, 5000, '2026-11', '2026-11-17');
assert.strictEqual(ssSlow.level, 2);
assert.strictEqual(ssSlow.metNow, true);
assert.strictEqual(ssSlow.achieved, false);
assert.ok(ssSlow.projectedSaved < 5000);
assert.strictEqual(C.savingsStats(12000, 0, 0, '2026-11', '2026-11-17').level, 0);
assert.ok(C.savingsReminder(ssShort, '2026-11').text.includes('12,000'));

// 本地提醒
const reminder = C.localReminder(bs, { name: '餐饮', amount: 900 }, '2026-11');
assert.strictEqual(reminder.level, 2);
assert.ok(reminder.text.includes('80%'));
assert.ok(reminder.text.includes('餐饮'));

// 数据清洗：非法金额/日期/分类
const dirty = C.normalizeState({
  transactions: [
    { type: 'expense', amount: -3, date: '2026-11-01', category: 'food' },
    { type: 'expense', amount: '12.345', date: '2026-02-30', category: 'nope' },
    { type: 'income', amount: 100, date: '2026-11-01', category: 'salary' },
    { type: 'weird', amount: 20, date: '2026-11-01', category: 'food' }
  ],
  budget: -10,
  settings: { ai: { apiKey: ' sk-test ', baseUrl: '', model: '' } }
});
assert.strictEqual(dirty.transactions.length, 3);
assert.strictEqual(dirty.transactions[0].amount, 12.35);
assert.strictEqual(dirty.transactions[0].category, 'food');
assert.strictEqual(dirty.transactions[0].date, C.todayStr());
assert.strictEqual(dirty.transactions[2].type, 'expense');
assert.strictEqual(dirty.budget, 0);
assert.strictEqual(dirty.settings.ai.apiKey, 'sk-test');
assert.strictEqual(dirty.settings.ai.baseUrl, C.DEFAULT_BASE_URL);

const withGoals = C.normalizeState({
  savingsGoals: { '2026-11': 3000, '2026-12': -5, bad: 999, '2027-01': '1200' }
});
assert.strictEqual(withGoals.savingsGoals['2026-11'], 3000);
assert.strictEqual(withGoals.savingsGoals['2027-01'], 1200);
assert.ok(!withGoals.savingsGoals['2026-12']);
assert.ok(!withGoals.savingsGoals.bad);

// 接口地址规范化
assert.strictEqual(C.normalizeEndpoint('https://api.deepseek.com/v1'), 'https://api.deepseek.com/v1/chat/completions');
assert.strictEqual(C.normalizeEndpoint('https://api.openai.com'), 'https://api.openai.com/v1/chat/completions');
assert.strictEqual(C.normalizeEndpoint('api.deepseek.com/v1/'), 'https://api.deepseek.com/v1/chat/completions');
assert.strictEqual(C.normalizeEndpoint('https://x.com/v1/chat/completions'), 'https://x.com/v1/chat/completions');

// AI 返回解析
assert.strictEqual(C.parseAssistantResponse({
  choices: [{ message: { content: '  你好  ' } }]
}), '你好');
assert.strictEqual(C.parseAssistantResponse({
  choices: [{ message: { content: [{ type: 'text', text: '分段' }, { text: '内容' }] } }]
}), '分段内容');
assert.throws(() => C.parseAssistantResponse({ error: { message: 'bad key' } }), /bad key/);
assert.throws(() => C.parseAssistantResponse({ choices: [] }), /没有返回内容/);

// 快照不包含密钥
const state = C.normalizeState({
  transactions: list,
  budget: 5000,
  savingsGoals: { '2026-11': 100 },
  settings: { ai: { apiKey: 'super-secret', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' } }
});
const snap = C.snapshot(state, '2026-11', new Date(2026, 10, 17));
assert.strictEqual(snap.totals.expense, 180);
assert.strictEqual(snap.budgetStatus.percent, 4);
assert.strictEqual(snap.savingsGoal, 100);
assert.strictEqual(snap.savingsStatus.saved, 320);
assert.ok(snap.savingsStatus.achieved);
assert.ok(!JSON.stringify(snap).includes('super-secret'));
assert.ok(snap.recentTransactions.length >= 1);

// 构造消息时也不泄露密钥
const messages = C.buildMessages(state, '帮我分析', 'analysis', { now: new Date(2026, 10, 17), month: '2026-11' });
assert.strictEqual(messages[0].role, 'system');
assert.strictEqual(messages[1].role, 'user');
assert.ok(messages[1].content.includes('2026-11'));
assert.ok(!JSON.stringify(messages).includes('super-secret'));

// 演示数据
const demo = C.makeDemoTransactions(new Date(2026, 10, 17));
assert.ok(demo.length >= 10);
assert.ok(demo.every(t => C.isValidDate(t.date) && t.amount > 0));
assert.ok(demo.some(t => t.type === 'income'));

console.log('core.test.js: all assertions passed (' + demo.length + ' demo transactions)');
