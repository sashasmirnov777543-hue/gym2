(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.GKLogic=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
  const STEP=2.5,TM_FACTOR=.9;
  const roundToStep=(v,step=STEP)=>{if(!Number.isFinite(v)||!Number.isFinite(step)||step<=0)throw Error('Некорректное значение');return Math.round(v/step)*step};
  const epley1RM=(w,r)=>{w=+w;r=+r;if(!(w>0)||!Number.isInteger(r)||r<1)throw Error('Некорректный AMRAP');return r===1?w:w*(1+r/30)};
  const trainingMax=(w,r)=>{const e1rm=epley1RM(w,r);return{e1rm,tm:roundToStep(e1rm*TM_FACTOR)}};
  const readiness=i=>{let s=0;s+=i.sleepMinutes<300?3:i.sleepMinutes<360?2:i.sleepMinutes<420?1:0;s+=i.sleepQuality<=1?3:i.sleepQuality===2?2:i.sleepQuality===3?1:0;s+=i.morningPulseDelta>=15?3:i.morningPulseDelta>=10?2:i.morningPulseDelta>=5?1:0;const p=Math.max(i.shoulderPain,i.backPain);s+=p>=7?4:p>=4?2:p>=2?1:0;s+=i.energy<=1?3:i.energy===2?2:i.energy===3?1:0;return p>=7||s>=10?'red':s>=7?'orange':s>=3?'yellow':'green'};
  const isMiniTaper=(block,cycle,level)=>level==='orange'||level==='red'||(block==='v9'&&[4,8].includes(+cycle));
  const isBoardPress=n=>/board\s*press|жим\s+(?:с|от)\s+бруск/i.test(n||'');
  const isMyoEligible=n=>{n=(n||'').toLocaleLowerCase('ru-RU');return /бицеп|молотк|сгибан/.test(n)&&!/запяст|обратн|трицеп|разгибан/.test(n)};
  const isMyoAllowed=(block,cycle)=>block==='v9'?![4,8,10,11,12,13].includes(+cycle):block==='h2'?![5,9].includes(+cycle):false;
  const myoStop=x=>x.pain?'Стоп: появилась боль.':!x.techniqueOk?'Стоп: техника или скорость ухудшились.':x.reps!=null&&x.reps<3?'Стоп: меньше 3 повторов.':x.miniSets>=4?'Лимит 4 мини-серии достигнут.':null;
  const fmt=v=>(Number.isInteger(v)?String(v):v.toFixed(1)).replace('.',',');
  const recalcText=(name,text,tm,e1rm=tm/TM_FACTOR)=>{if(!text)return text;const base=isBoardPress(name)?e1rm:tm;return text.replace(/(\d+(?:[.,]\d+)?)(\s*%\s*(?:(?:ТМ)|(?:e1rm)|(?:1пм))?\s*)\((\d+(?:[.,]\d+)?)\)/gi,(m,p,mid)=>`${p}${mid}(${fmt(roundToStep(+p.replace(',','.')/100*base))})`)};
  const amrapFingerprint=(sessionId,w,r)=>`${sessionId}:${(+w).toFixed(2)}:${+r}:${trainingMax(+w,+r).tm.toFixed(2)}`;
  const doubleProgression=(sets,targetText)=>{const m=String(targetText||'').match(/(\d+)\s*[–-]\s*(\d+)/);if(!m||!sets.length)return null;const top=+m[2],valid=sets.filter(s=>s.reps!=null&&s.rir!=null);if(!valid.length)return null;const ready=valid.every(s=>+s.reps>=top&&+s.rir>=1);const w=Math.max(...valid.map(s=>+s.weight||0));return ready&&w>0?{weight:roundToStep(w+STEP),reason:`Все подходы достигли ${top} повторов — добавьте ${STEP} кг`}:null};
  const dedupeOps=ops=>{const seen=new Set();return ops.filter(x=>{const k=x.operationId||JSON.stringify(x);if(seen.has(k))return false;seen.add(k);return true})};
  const pearson=(a,b)=>{if(a.length!==b.length||a.length<3)return null;const ma=a.reduce((s,x)=>s+x,0)/a.length,mb=b.reduce((s,x)=>s+x,0)/b.length;let n=0,da=0,db=0;for(let i=0;i<a.length;i++){const x=a[i]-ma,y=b[i]-mb;n+=x*y;da+=x*x;db+=y*y}return da&&db?n/Math.sqrt(da*db):null};
  return{STEP,TM_FACTOR,roundToStep,epley1RM,trainingMax,readiness,isMiniTaper,isBoardPress,isMyoEligible,isMyoAllowed,myoStop,recalcText,amrapFingerprint,doubleProgression,dedupeOps,pearson};
});
