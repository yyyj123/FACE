import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import WebSocket from '../front/node_modules/ws/index.js';

const root = 'C:/project/front/dist';
const port = 8082;
const qa = `OC_QA_${Date.now()}`;
const results = { qa };

const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.webp': 'image/webp', '.woff': 'font/woff' };
const server = http.createServer((req, res) => {
  const clean = decodeURIComponent(req.url.split('?')[0]);
  let file = path.join(root, clean === '/' ? 'index.html' : clean);
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(root, 'index.html');
  res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});

const sleep = ms => new Promise(r => setTimeout(r, ms));
async function api(url, options) {
  const response = await fetch(`http://127.0.0.1:8080/car/${url}`, options);
  return response.json();
}

let edge;
let ws;
let seq = 0;
const pending = new Map();
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++seq;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const out = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (out.result.exceptionDetails) throw new Error(out.result.exceptionDetails.text);
  return out.result.result.value;
}
async function navigate(hash, width) {
  await send('Emulation.setDeviceMetricsOverride', { width, height: 800, deviceScaleFactor: 1, mobile: width === 360 });
  await send('Page.navigate', { url: `http://127.0.0.1:${port}/#${hash}` });
  await sleep(2200);
}

try {
  await new Promise(resolve => server.listen(port, '127.0.0.1', resolve));

  const login = await api('chezhu/login?username=11&password=11');
  results.seedLogin = { code: login.code, tokenIssued: Boolean(login.token) };

  const page1 = await api('xinnengyuanqiche/list?page=1&limit=4');
  const page2 = await api('xinnengyuanqiche/list?page=2&limit=4');
  const term = page1.data.list[0].qichexinghao;
  const searched = await api(`xinnengyuanqiche/list?page=1&limit=4&qichexinghao=${encodeURIComponent(`%${term}%`)}`);
  const cleared = await api('xinnengyuanqiche/list?page=1&limit=4');
  results.searchClearPagination = {
    term,
    searchedCount: searched.data.list.length,
    clearedCount: cleared.data.list.length,
    page1FirstId: page1.data.list[0].id,
    page2FirstId: page2.data.list[0].id,
    pageChanged: page1.data.list[0].id !== page2.data.list[0].id
  };

  const appointment = {
    yuyuebianhao: qa, fuwumingcheng: '服务名称1', fuwufenlei: '服务分类1',
    fengmian: 'upload/obsidian-service-workshop.webp', jiage: 1, weixiuzhuangtai: '未维修',
    yuyueshijian: '2026-07-13 10:00:00', cheliangwenti: '<p>QA</p>', zhanghao: '11',
    xingming: '张三', shouji: '15111111111', chepaihao: qa, weixiuzhanghao: '维修账号1',
    weixiuxingming: '维修姓名1', sfsh: '待审核'
  };
  const added = await api('fuwuyuyue/add', { method: 'POST', headers: { 'Content-Type': 'application/json', token: login.token }, body: JSON.stringify(appointment) });
  results.appointmentSubmit = { code: added.code };

  const account = `${qa}_USER`;
  const registered = await api('chezhu/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ zhanghao: account, mima: 'Qa123456!', xingming: 'QA', xingbie: '男', shouji: '13900000000' }) });
  results.register = { account, code: registered.code };

  edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new', '--remote-debugging-port=9225', '--disable-gpu', '--no-first-run',
    '--user-data-dir=C:/project/.task5-remediation-runtime/edge', `http://127.0.0.1:${port}/#/login`
  ], { windowsHide: true, stdio: 'ignore' });
  let tabs;
  for (let i = 0; i < 30; i++) {
    try { tabs = await (await fetch('http://127.0.0.1:9225/json')).json(); break; } catch { await sleep(250); }
  }
  const tab = tabs.find(t => t.type === 'page');
  ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
  ws.on('message', raw => { const msg = JSON.parse(raw); if (msg.id && pending.has(msg.id)) { pending.get(msg.id).resolve(msg); pending.delete(msg.id); } });
  await send('Runtime.enable'); await send('Page.enable');

  results.browser = {};
  for (const width of [1280, 360]) {
    await navigate('/login', width);
    const key = String(width);
    results.browser[key] = await evaluate(`(() => {
      const q=s=>document.querySelector(s), box=s=>{const r=q(s)?.getBoundingClientRect(); return r?{w:r.width,h:r.height}:null};
      const contrast=(a,b)=>{const p=x=>{x/=255;return x<=.03928?x/12.92:((x+.055)/1.055)**2.4}, rgb=x=>(x.match(/\\d+/g)||[]).slice(0,3).map(Number), A=rgb(a).map(p),B=rgb(b).map(p),L=x=>.2126*x[0]+.7152*x[1]+.0722*x[2];return +((Math.max(L(A),L(B))+.05)/(Math.min(L(A),L(B))+.05)).toFixed(2)};
      const form=q('.oc-form-section'), input=q('input'), button=q('.login_btn'), eye=q('.password-box button');
      const pair=e=>{const c=getComputedStyle(e),b=getComputedStyle(form);return {fg:c.color,bg:b.backgroundColor,ratio:contrast(c.color,b.backgroundColor)}};
      input?.focus(); const focus=getComputedStyle(input);
      return {route:location.hash, rootBackground:getComputedStyle(q('.oc-auth')).backgroundColor, overflow:document.documentElement.scrollWidth<=innerWidth,
        contrast:{title:pair(q('.login-title')),label:pair(q('.lable')),placeholder:getComputedStyle(input,'::placeholder').color,errorSample:contrast('rgb(255,180,183)','rgb(17,21,26)')},
        targets:{input:box('input'),button:box('.login_btn'),passwordButton:box('.password-box button')}, focus:{outline:focus.outline,boxShadow:focus.boxShadow}, passwordAccessibleName:eye?.getAttribute('aria-label')};
    })()`);
    await navigate('/index/xinnengyuanqiche', width);
    results.browser[key].list = await evaluate(`({overflow:document.documentElement.scrollWidth<=innerWidth, cards:document.querySelectorAll('.list1>.list-item').length, pagination:Boolean(document.querySelector('#pagination')), emptyReadable:Boolean(document.querySelector('.oc-empty'))})`);
    await evaluate(`localStorage.setItem('frontToken', ${JSON.stringify(login.token)}); localStorage.setItem('frontSessionTable','chezhu'); localStorage.setItem('UserTableName','chezhu'); localStorage.setItem('username','11'); localStorage.setItem('frontRole','车主')`);
    await navigate('/index/center', width);
    results.browser[key].center = await evaluate(`(() => { const e=document.querySelector('.center-preview'); return {rendered:Boolean(e),background:e?getComputedStyle(e).backgroundColor:null, overflow:document.documentElement.scrollWidth<=innerWidth,route:location.hash}; })()`);
  }

  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await navigate('/index/xinnengyuanqiche', 1280);
  results.reducedMotion = await evaluate(`(() => { const list=document.querySelector('.list1'); const e=document.createElement('div'); e.className='list-item'; list.append(e); const c=getComputedStyle(e); const out={duration:c.transitionDuration}; e.remove(); return out; })()`);

  results.forcedStates = await evaluate(`(() => { const host=document.querySelector('.oc-content-list'); const out={}; for(const [k,html] of Object.entries({empty:'<div class="oc-empty">暂无匹配结果，请清除筛选后重试</div>',loading:'<div class="oc-empty" aria-busy="true">正在加载车辆信息…</div>',error:'<div class="oc-state-error" role="alert">加载失败，请稍后重试</div>'})){const n=document.createElement('div');n.innerHTML=html;host.prepend(n.firstChild);const e=host.firstChild,r=e.getBoundingClientRect();out[k]={text:e.textContent.trim(),overflow:r.right<=innerWidth,role:e.getAttribute('role'),busy:e.getAttribute('aria-busy')};e.remove()}return out})()`);
} finally {
  try { ws?.close(); } catch {}
  try { edge?.kill(); } catch {}
  await new Promise(resolve => server.close(resolve));
  fs.writeFileSync('C:/project/.task5-remediation-runtime/evidence.json', JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
}
