const http = require('http');

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    let parsed = {};
    try { parsed = JSON.parse(body); } catch (e) { /* ignore */ }
    const user = (parsed.messages || []).filter(m => m.role === 'user').pop();
    const text = user ? String(user.content) : '';
    const reply = text.includes('连接成功')
      ? '连接成功'
      : '这是鲸鲸的测试分析：本月最大支出是居住，建议把外卖控制在每周两次。';
    const payload = {
      id: 'mock-' + Date.now(),
      object: 'chat.completion',
      model: parsed.model || 'mock-1',
      choices: [{ index: 0, message: { role: 'assistant', content: reply }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 10, completion_tokens: 20, total_tokens: 30 }
    };
    console.log(JSON.stringify({ path: req.url, model: parsed.model, messages: (parsed.messages || []).length }));
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
  });
});

server.listen(8787, '127.0.0.1', () => console.log('mock-ai listening on 8787'));
