import assert from 'node:assert/strict';
import { mkdir, readFile, rename, readdir } from 'node:fs/promises';
import { join } from 'node:path';

const dir = process.argv[2];
const base = 'http://127.0.0.1:15173';
const admin = 'Basic ' + Buffer.from('admin:sprint5-test-admin').toString('base64');
const operator = 'Basic ' + Buffer.from('operador:sprint5-test-operator').toString('base64');
const run = String(Date.now()).slice(-8);
const sleeps = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function api(path, method='GET', data, auth=admin, expected) {
  const response = await fetch(base + '/api' + path, {method, headers:{Authorization:auth,'Content-Type':'application/json'}, body:data && JSON.stringify(data)});
  const raw = await response.text(); const result = raw ? JSON.parse(raw) : null;
  if (expected) assert.equal(response.status,expected,JSON.stringify(result));
  else assert(response.ok,JSON.stringify(result));
  return result;
}
const profile = await api('/production-costs/profiles','POST',{code:'BROWSER-'+run,name:'Perfil navegador '+run,electricityRatePerKwh:'1',laborRatePerHour:'2',indirectRatePerHour:'3',active:true});
const machine = await api('/production-costs/machines','POST',{code:'FDM-'+run,name:'Máquina navegador '+run,technique:'FDM',nominalPowerWatts:'100',machineRatePerHour:'5',active:true});
const category = (await api('/categories'))[0];
const material = await api('/materials','POST',{sku:'BROWSER-'+run,name:'PLA navegador '+run,categoryId:category.id,unit:'g',unitCost:'0',active:true});
const targets = await (await fetch('http://127.0.0.1:19091/json/list')).json();
const page = targets.find((target) => target.type === 'page');
const debuggerSocket = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve,reject) => {debuggerSocket.addEventListener('open',resolve,{once:true});debuggerSocket.addEventListener('error',reject,{once:true});});
let id = 0;
const commands = new Map();
debuggerSocket.addEventListener('message',({data}) => {
  const response = JSON.parse(data);
  if (response.id && commands.has(response.id)) {
    const {resolve,reject} = commands.get(response.id);
    commands.delete(response.id);
    if (response.error) reject(Error(response.error.message));
    else resolve(response.result);
  }
});
function cdp(method,params={}) {
  return new Promise((resolve,reject) => {
    const number = ++id;
    commands.set(number,{resolve,reject});
    debuggerSocket.send(JSON.stringify({id:number,method,params}));
  });
}
async function evaluate(fn,...args) {
  const result = await cdp('Runtime.evaluate', {expression:'('+fn.toString()+')(...'+JSON.stringify(args)+')',returnByValue:true,awaitPromise:true});
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description);
  return result.result.value;
}
async function until(fn, description, ...args) {
  for(let i=0;i<120;i++) {
    if(await evaluate(fn,...args)) return;
    await sleeps(150);
  }
  throw Error('Timeout: '+description+'\n'+ await evaluate(()=>document.body.innerText));
}
const hasText = (text) => until((value)=> document.body.innerText.includes(value),text,text);
async function click(label) {
  await until((text)=>[...document.querySelectorAll('button')].some((b)=>!b.disabled && b.getClientRects().length && b.textContent.trim()===text),label,label);
  await evaluate((text)=>[...document.querySelectorAll('button')].find((b)=>!b.disabled && b.getClientRects().length && b.textContent.trim()===text).click(),label);
}
async function enter(selector,value) {
  await until((query)=>Boolean(document.querySelector(query)),selector,selector);
  await evaluate((query,text)=>{
    const el=document.querySelector(query);
    const prototype=el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype,'value').set.call(el,String(text));
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input',{bubbles:true}));
  },selector,value);
}
async function label(name,value,index=0) {
  await until((text,i)=>[...document.querySelectorAll('label')].filter((el)=>el.firstChild?.textContent?.trim()===text && el.querySelector('input,select,textarea')).length>i,name,name,index);
  await evaluate((text,i,selected)=>{
    const el=[...document.querySelectorAll('label')].filter((item)=>item.firstChild?.textContent?.trim()===text&& item.querySelector('input,select,textarea'))[i].querySelector('input,select,textarea');
    const prototype=el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype,'value').set.call(el,String(selected));
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input',{bubbles:true}));
  },name,index,value);
}
const saveDir = join(dir,'downloads');
await mkdir(saveDir,{recursive:true});
await cdp('Page.enable');
await cdp('Browser.setDownloadBehavior',{behavior:'allow',downloadPath:saveDir});
await cdp('Page.navigate',{url:base});
try {
  await until(()=>document.readyState==='complete','carga');
  if (await evaluate(()=>sessionStorage.getItem('cadprintAuth') !== null)) {
    await evaluate(()=>{sessionStorage.removeItem('cadprintAuth');location.reload();});
  }
  await label('Usuario','admin');
  await label('Contraseña','sprint5-test-admin');
  await click('Ingresar');
  await click('Costos de producción');
  await click('Perfiles y máquinas');
  await label('Configuración','machine');
  await enter('[name=code]','PRESS-'+run);
  await enter('[name=name]','Prensa interfaz '+run);
  await label('Técnica','SUBLIMATION');
  await enter('[name=nominalPowerWatts]',1000);
  await enter('[name=machineRatePerHour]','8.25');
  await click('Guardar configuración');
  await hasText('Configuración guardada.');
  const press=(await api('/production-costs/machines?technique=SUBLIMATION')).find(item=>item.code==='PRESS-'+run);
  assert(press);
  await until((value)=>[...document.querySelectorAll('select option')].some(e=>e.textContent.includes(value)),press.name,press.name);
  await label('Registro',press.id);
  assert.equal(await evaluate(()=>document.querySelector('[name=technique]').disabled),true);
  await click('Cerrar sesión');
  await label('Usuario','operador');
  await label('Contraseña','sprint5-test-operator');
  await click('Ingresar');
  await click('Costos de producción');
  await click('Sublimación');
  await enter('[name=jobDescription]','Sublimación navegador '+run);
  await enter('[name=costProfileId]',profile.id);
  await enter('[name=machineId]',press.id);
  await enter('[name=cycleCount]',2);
  await enter('[name=pressSecondsPerCycle]',45);
  await enter('[name=preparationSeconds]',30);
  await enter('[name=laborSeconds]',90);
  await enter('[name=productiveSeconds]',240);
  await enter('[name=material0]',material.id);
  await enter('[name=quantity0]',1);
  await until(()=>!!document.querySelector('[name=zero0]'),'confirmación de costo cero');
  await evaluate(()=>document.querySelector('[name=zero0]').click());
  await enter('[name=reason0]','Muestra de prueba sin costo');
  await click('Calcular vista previa');
  await hasText('Precio sugerido');
  await click('Guardar cálculo');
  await hasText('Sublimación navegador '+run);
  const saved=(await api('/production-costs/calculations?technique=SUBLIMATION')).content.find(item=>item.jobDescription==='Sublimación navegador '+run);
  assert(saved);
  console.log('PASS navegador ADMIN crea máquina, técnica inmutable y OPERADOR guarda Sublimación con costo cero autorizado');
} finally {debuggerSocket.close();}
