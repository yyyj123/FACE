import { mkdirSync, writeFileSync } from 'node:fs'
import { spawn } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const options=Object.fromEntries(process.argv.slice(2).map((entry)=>{const [k,...v]=entry.replace(/^--/,'').split('=');return[k,v.join('=')]}))
const { url, password, output }=options
if(!url||!password||!output) throw new Error('SC8 browser check requires url, password and output.')
mkdirSync(output,{recursive:true}); const port=9338, edgeDir=options['edge-dir']||`${output}/edge`
const edge=spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',['--headless=new',`--remote-debugging-port=${port}`,'--disable-gpu','--no-first-run',`--user-data-dir=${edgeDir}`,url],{windowsHide:true,stdio:'ignore'})
const sleep=(ms)=>new Promise((resolve)=>setTimeout(resolve,ms)); let ws
try{
  let tabs
  for(let i=0;i<40;i++){try{tabs=await(await fetch(`http://127.0.0.1:${port}/json`)).json();break}catch{await sleep(250)}}
  if(!tabs)throw new Error('Edge debugging endpoint did not start.')
  const tab=tabs.find((item)=>item.type==='page'); ws=new WebSocket(tab.webSocketDebuggerUrl)
  await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject)})
  let id=0;const pending=new Map(),events=[];ws.on('message',(raw)=>{const msg=JSON.parse(raw);if(msg.id&&pending.has(msg.id)){pending.get(msg.id)(msg);pending.delete(msg.id)}else if(msg.method==='Runtime.exceptionThrown'||msg.method==='Network.loadingFailed'||(msg.method==='Network.responseReceived'&&msg.params?.response?.status>=400)){events.push(msg)}})
  const send=(method,params={})=>new Promise((resolve)=>{const requestId=++id;pending.set(requestId,resolve);ws.send(JSON.stringify({id:requestId,method,params}))})
  const evalJs=async(expression)=>{const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.result.exceptionDetails)throw new Error(result.result.exceptionDetails.text);return result.result.result.value}
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable')
  let gateReady=false
  for(let attempt=0;attempt<30&&!gateReady;attempt++){await sleep(1000);gateReady=Boolean(await evalJs(`document.querySelector('#gate-form')`));if(!gateReady)await send('Page.navigate',{url})}
  if(!gateReady)throw new Error('Public demo gate did not become ready within 30 seconds.')
  await evalJs(`document.querySelector('#password').value=${JSON.stringify(password)};document.querySelector('#gate-form').requestSubmit();true`);await sleep(2500)
  const authorized=await evalJs(`document.querySelector('.entry').getAttribute('aria-disabled')==='false'`);if(!authorized)throw new Error('Browser gate authorization failed.')
  const cookieState=await send('Network.getAllCookies');if(!cookieState.result.cookies.some((cookie)=>cookie.name==='face_demo_access'&&cookie.secure&&cookie.httpOnly))throw new Error(`Secure HttpOnly demo cookie was not retained: ${JSON.stringify(cookieState.result.cookies)}`)
  const results={}
  for(const view of [{name:'desktop',width:1440,height:900,mobile:false},{name:'mobile',width:390,height:844,mobile:true}]){
    await send('Emulation.setDeviceMetricsOverride',{width:view.width,height:view.height,deviceScaleFactor:view.mobile?2:1,mobile:view.mobile})
    results[view.name]={}
    for(const route of ['client','admin']){
      await send('Page.navigate',{url:`${url}/${route}/`})
      for(let attempt=0;attempt<15;attempt++){await sleep(1000);results[view.name][route]=await evalJs(`({title:document.title,overflow:document.documentElement.scrollWidth<=innerWidth,body:document.body.innerText.slice(0,120)})`);if(results[view.name][route].body.trim())break}
      if(!results[view.name][route].overflow||!results[view.name][route].body.trim())throw new Error(`${view.name} ${route} did not render a usable responsive page: ${JSON.stringify(events.slice(-8))}`)
      const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});writeFileSync(`${output}/${view.name}-${route}.png`,Buffer.from(shot.result.data,'base64'))
    }
  }
  writeFileSync(`${output}/browser-results.json`,JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));console.log('SC8_PUBLIC_DESKTOP_MOBILE=PASS')
}finally{try{ws?.close()}catch{};edge.kill()}
