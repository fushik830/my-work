const fs = require('fs');

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function connect() {
  const targets = await fetch('http://127.0.0.1:9222/json').then(r => r.json());
  const target = targets.find(t => t.type === 'page' && t.url.includes('index.html')) || targets.find(t => t.type === 'page');
  if (!target) throw new Error('no page target');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let seq = 0;
  const pending = new Map();
  const events = new Map();
  ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(msg.error.message));
      else resolve(msg.result);
    } else if (msg.method && events.has(msg.method)) {
      const list = events.get(msg.method);
      events.delete(msg.method);
      list.forEach(fn => fn(msg.params));
    }
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++seq;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true });
    ws.addEventListener('error', reject, { once: true });
  });
  return { ws, send };
}

async function main() {
  const { ws, send } = await connect();
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  };
  const click = async selector => {
    const ok = await evaluate(`(() => { const el = document.querySelector(${JSON.stringify(selector)}); if (!el) return false; el.click(); return true; })()`);
    if (!ok) throw new Error('missing ' + selector);
    await sleep(220);
  };

  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send('Page.navigate', { url: 'file:///C:/OhWhaleBuildSrc/app/src/main/assets/index.html?demo=1&shot=1' });
  await sleep(1400);

  const out = {};
  out.home = await evaluate(`!!document.querySelector('.page-home')`);

  await click('[data-tab="settings"]');
  await click('[data-action="clear-data"]');
  out.confirmOpened = await evaluate(`!!document.querySelector('.confirm-sheet')`);
  await click('.confirm-actions .btn-ghost');
  out.confirmCancelClosed = await evaluate(`!document.querySelector('.sheet-overlay')`);

  await click('[data-action="clear-data"]');
  await evaluate(`document.querySelector('.sheet-overlay').click()`);
  await sleep(200);
  out.overlayCloseClosed = await evaluate(`!document.querySelector('.sheet-overlay')`);

  await click('[data-tab="home"]');
  await click('[data-action="open-add"]');
  out.txOpened = await evaluate(`!!document.querySelector('.sheet')`);
  await click('.sheet-close');
  out.txCloseClosed = await evaluate(`!document.querySelector('.sheet-overlay')`);

  await click('[data-action="edit-budget"]');
  await evaluate(`(() => { const el = document.querySelector('#budgetInput'); el.value = '4000'; el.dispatchEvent(new Event('input', {bubbles:true})); })()`);
  await click('[data-action="save-budget"]');
  await sleep(700);
  out.budgetPill = await evaluate(`document.querySelector('.budget-pill') && document.querySelector('.budget-pill').textContent.trim()`);
  out.bannerBefore = await evaluate(`!!document.querySelector('[data-action="dismiss-banner"]')`);
  if (out.bannerBefore) {
    await click('[data-action="dismiss-banner"]');
    await sleep(250);
  }
  out.bannerClosed = await evaluate(`!document.querySelector('[data-action="dismiss-banner"]')`);

  await click('[data-tab="records"]');
  out.recordsOrder = await evaluate(`(() => {
    const list = document.querySelector('.day-group');
    const trend = document.querySelector('.trend-card');
    return { hasList: !!list, hasTrend: !!trend, listBeforeTrend: !!(list && trend && (list.compareDocumentPosition(trend) & Node.DOCUMENT_POSITION_FOLLOWING)) };
  })()`);
  out.pageScroll = await evaluate(`(() => { const v = document.querySelector('#view'); return { top: v.scrollTop, client: v.clientHeight, scroll: v.scrollHeight }; })()`);

  await click('[data-tab="settings"]');
  await evaluate(`(() => {
    window.__blob = null; window.__downloadName = '';
    HTMLAnchorElement.prototype.click = function () { window.__downloadName = this.download; };
    URL.createObjectURL = function (b) { window.__blob = b; return 'blob:test'; };
    URL.revokeObjectURL = function () {};
  })()`);
  await click('[data-action="export-backup"]');
  await sleep(500);
  out.backup = await evaluate(`(async () => {
    if (!window.__blob) return null;
    const data = JSON.parse(await window.__blob.text());
    return { name: window.__downloadName, count: data.transactions.length, apiKey: data.settings && data.settings.ai ? data.settings.ai.apiKey : null };
  })()`);

  console.log(JSON.stringify(out, null, 2));
  fs.writeFileSync('shots/smoke.json', JSON.stringify(out, null, 2));
  ws.close();
}

main().catch(err => { console.error(err); process.exit(1); });
