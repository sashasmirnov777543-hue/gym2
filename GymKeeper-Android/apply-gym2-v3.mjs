import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root=process.cwd();
const project=path.join(root,"GymKeeper-Android");
const assets=path.join(project,"app/src/main/assets");
const here=path.dirname(fileURLToPath(import.meta.url));
const read=p=>fs.readFileSync(p,"utf8");
const write=(p,s)=>{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,s,"utf8");console.log("✓ "+path.relative(root,p))};
const must=(ok,msg)=>{if(!ok)throw new Error(msg)};

must(fs.existsSync(path.join(root,"GymKeeper-Android")),"Запустите установщик из корня репозитория gym2");
must(fs.existsSync(path.join(assets,"index.html")),"Не найден GymKeeper-Android/app/src/main/assets/index.html");
must(fs.existsSync(path.join(here,"program-v3.js")),"Рядом с установщиком отсутствует program-v3.js");

fs.copyFileSync(path.join(here,"program-v3.js"),path.join(assets,"program-v3.js"));
console.log("✓ GymKeeper-Android/app/src/main/assets/program-v3.js");

{
 const file=path.join(assets,"index.html");let s=read(file);
 if(!s.includes('src="program-v3.js"')){
  must(s.includes('<script src="boot-v2.js"></script>'),"В index.html не найден boot-v2.js");
  s=s.replace('<script src="boot-v2.js"></script>','<script src="program-v3.js"></script>\n  <script src="boot-v2.js"></script>');
 }
 write(file,s);
}

{
 const file=path.join(assets,"styles.css");let s=read(file);
 const marker='/* GymKeeper Offline 3.0 UI */';
 const css=`\n${marker}\n.space{height:12px}.next-card{display:flex;align-items:center;justify-content:space-between;text-align:left}.next-card>div{min-width:0}.next-card>div>b{font-size:28px}.next-card h3{margin:6px 0 3px}.program-grid{grid-template-columns:repeat(4,1fr)}.program-grid .tile{aspect-ratio:1.25}.plan{overflow:hidden;background:var(--card);border:1px solid var(--line);border-radius:var(--radius);margin-bottom:12px}.plan-head{padding:10px 14px;background:#f1f5f9;border-bottom:1px solid var(--line);font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#475569}.plan-row{display:flex;gap:10px;padding:11px 14px;border-bottom:1px solid var(--line);align-items:flex-start}.plan-row:last-child{border-bottom:0}.plan-row>span{width:20px;height:20px;display:grid;place-items:center;flex:none;border-radius:50%;background:#e2e8f0;color:#475569;font-weight:900;font-size:11px}.plan-row.target>span{background:#dbeafe;color:#1d4ed8}.plan-row.warn{background:#fff7ed}.plan-row.warn>span{background:#fee2e2;color:#b91c1c}.plan-row p{margin:0;color:#475569;font-size:13px;line-height:1.55}.exercise-list{display:flex;flex-direction:column;gap:10px;margin-bottom:14px}.exercise-card{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);overflow:hidden;padding:14px}.exercise-card.prep{border-style:dashed;background:#f8fafc}.exercise-title{display:flex;align-items:flex-start;gap:10px}.exercise-title>span{width:28px;height:28px;border-radius:50%;background:#e2e8f0;display:grid;place-items:center;flex:none;font-size:12px;font-weight:900;color:#475569}.exercise-title>div{min-width:0;flex:1}.exercise-title small{display:block;font-size:9px;letter-spacing:.1em;color:var(--muted);font-weight:800}.exercise-title h3{margin:2px 0 0;font-size:17px;line-height:1.3}.metrics{display:flex;flex-wrap:wrap;gap:7px;margin-top:11px}.metric{min-width:100px;max-width:100%;background:#f1f5f9;border-radius:10px;padding:8px 10px;display:flex;flex-direction:column;gap:2px}.metric.primary{background:#eff6ff;color:#1d4ed8}.metric small{font-size:8px;letter-spacing:.08em;color:#64748b;font-weight:800}.metric b{font-size:12px;line-height:1.45;overflow-wrap:anywhere}.instruction{margin:10px 0 0;border-radius:9px;background:#f1f5f9;padding:9px 11px;font-size:12px;line-height:1.5;color:#475569}.prep-session{border-style:dashed;background:#f8fafc}.prep-session h3,.logger h3{margin:4px 0 0}.history-line{display:flex;justify-content:space-between;gap:10px;padding:9px 0;border-bottom:1px solid var(--line)}.history-line:last-child{border-bottom:0}.history-line small{color:var(--muted);text-align:right}.logger .set-row{margin-top:4px}@media(max-width:380px){.program-grid{grid-template-columns:repeat(4,1fr)}.metric{min-width:calc(50% - 4px)}.exercise-card{padding:12px}.plan-row{padding:10px 12px}}\n`;
 if(!s.includes(marker))s+=css;
 write(file,s);
}

{
 const file=path.join(project,"app/build.gradle.kts");let s=read(file);
 s=s.replace(/versionCode\s*=\s*\d+/,"versionCode = 7").replace(/versionName\s*=\s*"[^"]+"/,'versionName = "3.0.0"');
 write(file,s);
}

for(const file of [path.join(root,".github/workflows/build-apk.yml"),path.join(project,".github/workflows/build-apk.yml")]){
 if(!fs.existsSync(file))continue;let s=read(file);
 s=s.replace(/Build GymKeeper Offline 2\.0/g,"Build GymKeeper Offline 3.0");
 s=s.replace(/GymKeeper-offline-2\.0-android13/g,"GymKeeper-offline-3.0-android13");
 if(!s.includes('node --check app/src/main/assets/program-v3.js')){
  s=s.replace(/node --check app\/src\/main\/assets\/analytics-v2\.js/g,'node --check app/src/main/assets/analytics-v2.js && node --check app/src/main/assets/program-v3.js');
 }
 write(file,s);
}

write(path.join(project,"tests/program-v3.test.cjs"),`const test=require('node:test');\nconst assert=require('node:assert/strict');\nconst fs=require('node:fs');\nconst source=fs.readFileSync('app/src/main/assets/program-v3.js','utf8');\nconst match=source.match(/const GK_PROGRAM=(\\{[\\s\\S]*?\\});\\nconst PREP_NAMES/);\nassert.ok(match,'payload программы не найден');\nconst data=JSON.parse(match[1]);\ntest('финальная программа H2/V9 встроена полностью',()=>{\n assert.equal(data.rmref,115);\n assert.equal(data.cycles.length,22);\n assert.equal(data.workouts.length,88);\n assert.equal(data.exercises.length,479);\n assert.equal(data.cycles.filter(x=>x.block==='h2').length,9);\n assert.equal(data.cycles.filter(x=>x.block==='v9').length,13);\n});\ntest('в активных упражнениях нет удалённых методов',()=>{\n const text=JSON.stringify(data.exercises).toLowerCase();\n for(const term of ['amrap','board press','pin press','пин-пресс'])assert.equal(text.includes(term),false,term);\n});\n`);

write(path.join(project,"CHANGELOG-3.0.md"),`# GymKeeper Offline 3.0\n\n- Встроена финальная программа H2 → V9: 9 + 13 восьмидневных циклов, 88 тренировочных дней.\n- RMref = 115 кг; ручное обновление только на контрольных точках.\n- Старая программа архивируется, история сессий и подходов сохраняется.\n- Удалены из активной программы AMRAP, board press, pin press и старый автоматический пересчёт ТМ.\n- Добавлены паузный жим, Spoto, RPE/RIR-ограничители и тестовая тройка.\n- Кардио только Z1/Z2, без HIIT и финишеров.\n- Разминочные пункты отображаются отдельно и не требуют записи подходов.\n- Исправлены длинные инструкции, карточки упражнений и группировка циклов.\n- При восстановлении старой JSON-копии программа автоматически мигрирует до v3.\n`);

{
 const file=path.join(project,"README-RU.md");let s=read(file);const marker='## Обновление 3.0';
 if(!s.includes(marker))s=s.replace(/^# .*$/m,'# GymKeeper Offline 3.0 — Realme 8 / Android 13')+`\n\n${marker}\n\nВерсия 3.0 содержит финальную программу H2 → V9 и автоматически переносит существующие локальные данные без удаления истории. Перед установкой всё равно сделайте JSON-копию. Точка входа после обновления: V9, цикл 4, B2.\n`;
 write(file,s);
}

console.log("\nГотово: файлы версии 3.0 созданы. Теперь запустите проверки из BAT-файла.");
