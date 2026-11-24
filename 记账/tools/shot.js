const fs = require('fs');
const path = require('path');

async function main() {
  const [url, out, heightArg] = process.argv.slice(2);
  if (!url || !out) {
    console.error('usage: node tools/shot.js <url> <out.png> [height]');
    process.exit(2);
  }
  const height = Number(heightArg || 844);
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
  const once = method => new Promise(resolve => {
    if (!events.has(method)) events.set(method, []);
    events.get(method).push(resolve);
  });
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true });
    ws.addEventListener('error', reject, { once: true });
  });
  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {
    width: 390, height, deviceScaleFactor: 2, mobile: true
  });
  const loaded = once('Page.loadEventFired');
  await send('Page.navigate', { url });
  await Promise.race([loaded, new Promise(r => setTimeout(r, 4000))]);
  await new Promise(r => setTimeout(r, 900));
  const scrollArg = process.env.SHOT_SCROLL;
  if (scrollArg) {
    await send('Runtime.evaluate', {
      expression: 'document.querySelector("#view").scrollTop=' + Number(scrollArg)
    });
    await new Promise(r => setTimeout(r, 400));
  }
  const clickArg = process.env.SHOT_CLICK;
  if (clickArg) {
    await send('Runtime.evaluate', {
      expression: 'document.querySelector(' + JSON.stringify(clickArg) + ').click()'
    });
    await new Promise(r => setTimeout(r, 350));
  }
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, Buffer.from(data, 'base64'));
  const metrics = await send('Runtime.evaluate', {
    expression: 'JSON.stringify({w:innerWidth,h:innerHeight,tab:document.querySelector(".page")?document.querySelector(".page").className:"",scrollH:document.body.scrollHeight})',
    returnByValue: true
  });
  console.log(metrics.result.value);
  ws.close();
}
main().catch(err => { console.error(err); process.exit(1); });
