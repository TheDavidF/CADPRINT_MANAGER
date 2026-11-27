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
const material = await api('/materials','POST',{sku:'BROWSER-'+run,name:'PLA navegador '+run,categoryId:category.id,unit:'g',unitCost:'0.20',active:true});
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
const categoryCatalog=await api('/product-categories','POST',{name:'Catálogo interfaz '+run});
const product=await api('/products','POST',{sku:'PC-'+run,name:'Producto interfaz '+run,categoryId:categoryCatalog.id,productionMode:'BAJO_PEDIDO',pricingMode:'PRECIO_BASE',basePrice:'10.25'});
const variant=await api('/products/'+product.id+'/variants','POST',{sku:'VC-'+run,name:'Variante interfaz '+run,ownPrice:'12.50'});
try {
  await until(()=>document.readyState==='complete','carga');
  if (!(await evaluate(()=>sessionStorage.getItem('cadprintAuth') !== null))) {
    await label('Usuario','operador');
    await label('Contraseña','sprint5-test-operator');
    await click('Ingresar');
  }
  await click('Cotizaciones');
  await click('Nueva cotización');
  const recipient='Precisión navegador '+run;
  await label('Nombre o razón social',recipient);
  await label('Buscar en catálogo',product.sku);
  await until((key)=>[...document.querySelectorAll('select option')].some(e=>e.textContent.includes(key)),product.sku,product.sku);
  await label('Producto',product.id);
  await until((key)=>[...document.querySelectorAll('select option')].some(e=>e.textContent.includes(key)),variant.sku,variant.sku);
  await label('Variante',variant.id);
  await click('Agregar artículo');
  await label('Tipo de artículo','CUSTOM',1);
  await label('Descripción del artículo','Decimal exacto');
  await label('Precio unitario en GTQ','1234567890123456789012345.67',1);
  await click('Guardar borrador');
  await hasText('Q 1,234,567,890,123,456,789,012,345.67');
  await hasText('Q 12.50');
  await click('Editar borrador');
  assert.equal(await evaluate(()=>[...document.querySelectorAll('label')].filter(e=>e.firstChild?.textContent.trim()==='Precio unitario en GTQ')[1].querySelector('input').value),'1234567890123456789012345.67');
  console.log('PASS navegador catálogo con variante e importe DECIMAL(27,2) exacto al guardar y reabrir');
} finally {debuggerSocket.close();}
