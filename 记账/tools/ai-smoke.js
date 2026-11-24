const sleep = ms => new Promise(r => setTimeout(r, ms));

async function connect() {
  const targets = await fetch('http://127.0.0.1:9222/json').then(r => r.json());
  const target = targets.find(t => t.type === 'page' && t.url.includes('index.html')) || targets.find(t => t.type === 'page');
  if (!target) throw new Error('no page target');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let seq = 0;
  const pending = new Map();
  ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(msg.error.message));
      else resolve(msg.result);
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
  const setField = async (field, value) => {
    await evaluate(`(() => {
      const el = document.querySelector('[data-ai-field="${field}"]');
      if (!el) return false;
      el.value = ${JSON.stringify(value)};
      el.dispatchEvent(new Event('input', { bubbles: true }));
      return true;
    })()`);
  };

  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send('Page.navigate', { url: 'file:///C:/OhWhaleBuildSrc/app/src/main/assets/index.html?demo=1&shot=1&tab=settings' });
  await sleep(1400);
  await click('[data-action="open-ai-config"]');
  await setField('baseUrl', 'http://127.0.0.1:8787/v1');
  await setField('apiKey', 'local');
  await setField('model', 'mock-1');
  await click('[data-action="test-ai"]');

  let tested = '';
  for (let i = 0; i < 30; i++) {
    await sleep(200);
    tested = await evaluate(`(() => { const el = document.querySelector('.test-result'); return el ? el.className + '|' + el.textContent.trim() : ''; })()`);
    if (tested.includes('ok|') || tested.includes('err|')) break;
  }
  console.log('test:', tested);
  await click('[data-action="save-ai"]');
  await click('[data-tab="ai"]');
  const configured = await evaluate(`document.querySelector('.ai-status') && document.querySelector('.ai-status').textContent.trim()`);
  console.log('status:', configured);

  await click('[data-action="ai-prompt"]');
  let reply = '';
  for (let i = 0; i < 40; i++) {
    await sleep(250);
    reply = await evaluate(`(() => { const els = [...document.querySelectorAll('.msg.ai .bubble')]; return els.length ? els[els.length - 1].textContent.trim() : ''; })()`);
    if (reply && !reply.includes('正在')) break;
  }
  console.log('reply:', reply.slice(0, 160));
  await send('Page.captureScreenshot', { format: 'png' }).then(r => require('fs').writeFileSync('shots/ai-chat-mock.png', Buffer.from(r.data, 'base64')));
  ws.close();
}

main().catch(err => { console.error(err); process.exit(1); });
