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
try {
  if (await evaluate(()=>sessionStorage.getItem('cadprintAuth') !== null)) {
    await evaluate(()=>{sessionStorage.removeItem('cadprintAuth');location.reload();});
  }
  await label('Usuario','operador');
  await label('Contraseña','sprint5-test-operator');
  await click('Ingresar');
  await click('Costos de producción');
  await hasText('Materiales consumidos');
  assert.equal(await evaluate(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='Perfiles y máquinas')),false);
  const name = 'Cálculo FDM navegador '+run;
  await enter('[name=jobDescription]',name);
  await enter('[name=costProfileId]',profile.id);
  await enter('[name=machineId]',machine.id);
  await enter('[name=printSeconds]',3600);
  await enter('[name=partMassGrams]',30);
  await enter('[name=laborSeconds]',180);
  await enter('[name=productiveSeconds]',1800);
  await enter('[name=material0]',material.id);
  await enter('[name=quantity0]',30);
  await label('Precio elegido','custom');
  await enter('[name=chosenUnitPrice]',0);
  await click('Calcular vista previa');
  await hasText('No aplica');
  await hasText('inferior al costo unitario');
  await enter('[name=chosenUnitPrice]',25);
  await click('Calcular vista previa');
  await hasText('Q 25.00');
  await api('/production-costs/profiles/'+profile.id,'PUT',{...profile, laborRatePerHour:'3'});
  await click('Guardar cálculo');
  await hasText('La vista previa quedó obsoleta');
  assert.equal(await evaluate(()=>document.querySelector('[name=jobDescription]').value),name);
  assert.equal(await evaluate(()=>document.querySelector('[name=chosenUnitPrice]').value),'25');
  await click('Calcular vista previa');
  await hasText('Q 25.00');
  await click('Guardar cálculo');
  await hasText('Historial de cálculos');
  await hasText(name);
  const calculate = (await api('/production-costs/calculations')).content.find((item)=>item.jobDescription===name);
  assert(calculate);
  console.log('PASS navegador FDM, precio cero, precio elegido, 409 y persistencia; operador sin administración');

  await click('Cotizaciones');
  await click('Nueva cotización');
  const recipient = 'Cotización navegador '+run;
  await label('Nombre o razón social',recipient);
  await label('Tipo de artículo','CUSTOM');
  await label('Descripción del artículo','Prototipo FDM');
  await click('Buscar cálculo histórico');
  await click(name+' · Impresión 3D FDM');
  await click('Usar precio elegido del cálculo (Q 25.00)');
  await label('Cantidad cotizada',2);
  await click('Agregar artículo');
  await label('Tipo de artículo','CUSTOM',1);
  await label('Descripción del artículo','Empaque',1);
  await label('Precio unitario en GTQ','5.00',1);
  await click('Guardar borrador');
  await hasText('Emisión vigente: ninguna');
  const match = (await api('/quotations?recipient='+encodeURIComponent(recipient))).items[0];
  const quote = match.quotationId;
  const detail = await api('/quotations/'+quote);
  assert.equal(detail.lines.length,2);
  assert.equal(detail.lines[0].productionCalculationId,calculate.id);
  assert.equal(detail.totals.totalAmount,55);

  await click('Editar borrador');
  await label('Condiciones comerciales','Texto local preservado');
  const externalDraft = {
    recipient:detail.recipient, commercialTerms:'Cambio remoto',notes:'',validUntil:null,generalDiscount:detail.totals.generalDiscount,shippingAmount:'0.00',
    lines:detail.lines.map((line)=>({type:line.type,description:line.commercial.description,name:line.commercial.name,quantity:line.commercial.quantity,unitPrice:line.commercial.unitPrice,discount:line.commercial.discount,productionCalculationId:line.productionCalculationId})),
  };
  await api('/quotations/'+quote+'/revisions/1/draft','PUT',{version:detail.revision.version,draft:externalDraft},operator);
  await click('Guardar borrador');
  await hasText('Se conserva el formulario');
  await click('Consultar versión actual');
  await hasText('Cambio remoto');
  assert.equal(await evaluate(()=>document.querySelector('[name=commercialTerms]').value),'Texto local preservado');
  await click('Cerrar edición');
  await click('Editar borrador');
  await click('Cerrar edición');
  // Selecting the listed quotation reloads the authoritative revision and its version.
  await evaluate((customer)=>[...document.querySelectorAll('tr')].find(row=>row.textContent.includes(customer)).querySelector('button').click(),recipient);
  await click('Emitir revisión');
  await click('Confirmar acción');
  await hasText('Emisión vigente: revisión 1');
  assert.equal(await evaluate(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='Cancelar emisión')),false);
  await api('/quotations/'+quote+'/revisions/1/cancel','POST',{reason:'Caso de permisos'},operator,403);
  await click('Ver vista comercial');
  await hasText('Cambio remoto');
  assert.equal(await evaluate(()=>document.body.innerText.includes('Historial de eventos')),false);
  await click('Descargar PDF original');
  const issued = await api('/quotations/'+quote+'/revisions/1');
  const filename = 'CADPRINT-'+issued.revision.quotationNumber+'-R01.pdf';
  async function file(name) {
    for(let i=0;i<60;i++) {
      try {return await readFile(join(saveDir,name));} catch {await sleeps(150);}
    }
    throw Error('No se guardó '+name);
  }
  const bytes = await file(filename);
  assert.equal(bytes.subarray(0,5).toString(),'%PDF-');
  console.log('PASS navegador cotización con dos líneas, 409 preservado, emisión, vista comercial y descarga autenticada');

  await click('Crear revisión');
  await click('Confirmar acción');
  await hasText('Más reciente: revisión 2 · BORRADOR');
  await hasText('Emisión vigente: revisión 1');
  assert.equal(await evaluate(()=>document.body.innerText.includes('PDF histórico')),false);
  await click('Emitir revisión');
  await click('Confirmar acción');
  await hasText('Emisión vigente: revisión 2');
  await label('Revisión seleccionada',1);
  await hasText('Sustituida');
  await rename(join(saveDir,filename), join(saveDir,'first-original.pdf'));
  await click('Descargar PDF original');
  assert.deepEqual(await file(filename),bytes);
  const storage = join(dir,'documents');
  const files = (await readdir(storage)).filter(file=>file.endsWith('.pdf'));
  const contents = await Promise.all(files.map(file=>readFile(join(storage,file))));
  const original = files[contents.findIndex(buffer=>buffer.equals(bytes))];
  assert(original);
  await rename(join(storage,original),join(storage,original+'.unavailable'));
  await click('Descargar PDF original');
  await hasText('El PDF original no está disponible');
  assert.equal(await evaluate(()=>[...document.querySelectorAll('button')].some(b=>['Solicitar PDF','Reintentar PDF'].includes(b.textContent.trim()))),false);
  console.log('PASS navegador revisión sustituida con PDF idéntico, borrador sin PDF y 503 sin regeneración');
} finally {debuggerSocket.close();}
