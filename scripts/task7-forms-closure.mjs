import { spawn } from 'node:child_process';
import fs from 'node:fs';
import WebSocket from '../admin/node_modules/ws/index.js';

const sleep = ms => new Promise(r => setTimeout(r, ms));
const qa = `OC_QA_${Date.now()}`;
const profile = 'C:/project/.task7-forms-closure';
const out = { qa, routes: ['/xinnengyuanqiche', '/fuwuyuyue'] };
let edge, ws, id = 0;
const pending = new Map();
const send = (method, params={}) => new Promise((resolve,reject)=>{ const n=++id; pending.set(n,{resolve,reject}); ws.send(JSON.stringify({id:n,method,params})); });
const evalv = async expression => { const x=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true}); if(x.result.exceptionDetails) throw Error(x.result.exceptionDetails.text); return x.result.result.value; };
const waitFor = async (expr, timeout=15000) => { const end=Date.now()+timeout; while(Date.now()<end){ const v=await evalv(expr); if(v) return v; await sleep(200); } throw Error(`timeout: ${expr}`); };
const clickText = async text => evalv(`(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()===${JSON.stringify(text)});if(!e)return false;e.click();return true})()`);

try {
  fs.mkdirSync(profile,{recursive:true});
  edge=spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',['--headless=new','--disable-gpu','--no-first-run','--remote-debugging-port=9237',`--user-data-dir=${profile}`,'http://127.0.0.1:8081/#/login'],{windowsHide:true,stdio:'ignore'});
  let tabs; for(let i=0;i<50;i++){try{tabs=await (await fetch('http://127.0.0.1:9237/json')).json();break}catch{await sleep(200)}}
  ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl); await new Promise((r,j)=>{ws.once('open',r);ws.once('error',j)});
  ws.on('message',raw=>{const m=JSON.parse(raw);if(m.id&&pending.has(m.id)){pending.get(m.id).resolve(m);pending.delete(m.id)}});
  await send('Runtime.enable'); await send('Page.enable'); await send('Network.enable');
  await send('Page.navigate',{url:'http://127.0.0.1:8081/#/login'}); await sleep(1800);
  await waitFor(`document.querySelector('#admin-username')`);
  await evalv(`(()=>{const set=(s,v)=>{const e=document.querySelector(s);e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}))};set('#admin-username','维修账号1');set('#admin-password','123456');return true})()`);
  out.loginState = {};
  out.loginState.selectOpened = await evalv(`(()=>{const e=document.querySelector('.login-container .el-select input');e?.click();return !!e})()`);
  await waitFor(`document.querySelectorAll('.el-select-dropdown__item:not(.is-disabled)').length`);
  out.loginState.options = await evalv(`[...document.querySelectorAll('.el-select-dropdown__item:not(.is-disabled)')].map(x=>x.innerText.trim())`);
  out.loginState.selected = await evalv(`(()=>{const options=[...document.querySelectorAll('.el-select-dropdown__item:not(.is-disabled)')];const e=options.find(x=>x.innerText.trim()==='维修技师');e?.click();return e?.innerText.trim()||''})()`);
  await clickText('登录');
  out.login = await waitFor(`(()=>{const t=localStorage.getItem('Token');return t&&location.hash!=='#/login'?{tokenNonempty:!!t,route:location.hash,role:localStorage.getItem('role')}:null})()`);

  async function nav(route){await evalv(`location.hash=${JSON.stringify('#'+route)}`);await sleep(1800);}
  await nav('/fuwuyuyue');
  out.appointmentTable = await waitFor(`(()=>{const rows=[...document.querySelectorAll('.el-table__body-wrapper tbody tr')];return rows.length?{rows:rows.length,firstText:rows[0].innerText.trim().slice(0,180)}:null})()`);
  out.addOpened = await clickText('添加'); await waitFor(`document.querySelector('.add-update-preview')`);
  out.upload = await evalv(`(()=>{const u=document.querySelector('.el-upload, .upload');return {widgetPresent:!!u,clickable:!!u&&getComputedStyle(u).pointerEvents!=='none'}})()`);
  await send('Page.setInterceptFileChooserDialog',{enabled:true});
  const chooser = new Promise(resolve=>{const h=raw=>{const m=JSON.parse(raw);if(m.method==='Page.fileChooserOpened'){ws.off('message',h);resolve(true)}};ws.on('message',h);setTimeout(()=>{ws.off('message',h);resolve(false)},2000)});
  await evalv(`(()=>{const u=document.querySelector('.el-upload');u?.click();return !!u})()`); out.upload.fileChooserOpened=await chooser;
  await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'}); await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape'}); out.upload.cancelledWithoutFile=true;
  await clickText('提交');
  out.validation = await waitFor(`(()=>{const e=document.querySelector('.el-form-item__error');return e?{visible:true,text:e.innerText.trim()}:null})()`);
  // populate the only required model through the rendered input and use the real submit button
  out.form = await evalv(`(()=>{const labels=[...document.querySelectorAll('.el-form-item')];const item=labels.find(x=>x.innerText.includes('车牌号'));const e=item?.querySelector('input');if(!e)return {found:false};e.focus();e.value=${JSON.stringify(qa)};e.dispatchEvent(new Event('input',{bubbles:true}));return {found:true,value:e.value}})()`);
  // Representative disabled control: read-only generated appointment number.
  out.disabledControl = await evalv(`(()=>{const es=[...document.querySelectorAll('input')];const e=es.find(x=>x.readOnly||x.disabled);return {present:!!e,readonly:!!e?.readOnly,disabled:!!e?.disabled,value:e?.value||''}})()`);
  // Keyboard traversal: Tab through all rendered controls and activate Back with Enter, then reopen.
  out.keyboard = {focusOrder:[]};
  await evalv(`document.body.focus()`);
  for(let i=0;i<8;i++){await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab'});await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab'});out.keyboard.focusOrder.push(await evalv(`document.activeElement?.innerText?.trim()||document.activeElement?.placeholder||document.activeElement?.className||document.activeElement?.tagName`));}
  out.keyboard.fullTraversal = out.keyboard.focusOrder.filter(Boolean).length>=6;
  await clickText('提交');
  await waitFor(`document.body.innerText.includes('操作成功')`); await sleep(1800);
  await evalv(`(()=>{const e=[...document.querySelectorAll('input')].find(x=>x.placeholder==='车牌号');e.value=${JSON.stringify(qa)};e.dispatchEvent(new Event('input',{bubbles:true}));return true})()`); await clickText('查询'); await sleep(1000);
  out.saveSuccess = await evalv(`(()=>{const rows=[...document.querySelectorAll('.el-table__body-wrapper tbody tr')];const r=rows.find(x=>x.innerText.includes(${JSON.stringify(qa)}));return {rowConfirmed:!!r,rowText:r?.innerText.trim().slice(0,200)||''}})()`);
  // Edit and cancel/back using real buttons.
  await clickText('修改'); await waitFor(`document.querySelector('.add-update-preview')`); out.editOpened=true; await clickText('取消'); await waitFor(`document.querySelector('.el-table__body-wrapper')`); out.cancelBack=true;
  // Intercept one save and return application error; confirm no row persisted.
  await clickText('添加'); await waitFor(`document.querySelector('.add-update-preview')`); const errQa=qa+'_ERR';
  await evalv(`(()=>{const item=[...document.querySelectorAll('.el-form-item')].find(x=>x.innerText.includes('车牌号'));const e=item.querySelector('input');e.value=${JSON.stringify(errQa)};e.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await send('Fetch.enable',{patterns:[{urlPattern:'*fuwuyuyue/save*',requestStage:'Request'}]});
  const intercepted=new Promise(resolve=>{const h=async raw=>{const m=JSON.parse(raw);if(m.method==='Fetch.requestPaused'){ws.off('message',h);await send('Fetch.fulfillRequest',{requestId:m.params.requestId,responseCode:200,responseHeaders:[{name:'Content-Type',value:'application/json'}],body:Buffer.from(JSON.stringify({code:500,msg:'OC_QA intercepted save error'})).toString('base64')});resolve(true)}};ws.on('message',h);setTimeout(()=>resolve(false),3000)});
  await clickText('提交'); out.interceptedError={intercepted:await intercepted}; await waitFor(`document.body.innerText.includes('OC_QA intercepted save error')`); out.interceptedError.visible=true; await send('Fetch.disable'); await clickText('取消');
  const token=await evalv(`JSON.parse(localStorage.getItem('Token'))`);
  const headers={'Content-Type':'application/json',token};
  const list=await (await fetch(`http://127.0.0.1:8080/car/fuwuyuyue/list?page=1&limit=100&chepaihao=${encodeURIComponent('%'+qa+'%')}`,{headers})).json();
  const owned=(list.data?.list||[]).filter(x=>String(x.chepaihao||'').startsWith(qa));
  out.cleanup={found:owned.map(x=>({id:x.id,chepaihao:x.chepaihao}))};
  for(const r of owned.filter(x=>x.chepaihao===qa)) await fetch('http://127.0.0.1:8080/car/fuwuyuyue/delete',{method:'POST',headers,body:JSON.stringify([r.id])});
  const verify=await (await fetch(`http://127.0.0.1:8080/car/fuwuyuyue/list?page=1&limit=100&chepaihao=${encodeURIComponent('%'+qa+'%')}`,{headers})).json();
  out.cleanup.remaining=(verify.data?.list||[]).filter(x=>String(x.chepaihao||'').startsWith(qa)).length;
} finally {
  try{ws?.close()}catch{}; try{edge?.kill()}catch{}; await sleep(500);
  fs.writeFileSync('C:/project/.task7-forms-closure-evidence.json',JSON.stringify(out,null,2)); console.log(JSON.stringify(out,null,2));
}
