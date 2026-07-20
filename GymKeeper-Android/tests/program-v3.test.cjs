const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const source=fs.readFileSync('app/src/main/assets/program-v3.js','utf8');
const match=source.match(/const GK_PROGRAM=(\{[\s\S]*?\});\nconst PREP_NAMES/);
assert.ok(match,'payload программы не найден');
const data=JSON.parse(match[1]);
test('финальная программа H2/V9 встроена полностью',()=>{
 assert.equal(data.rmref,115);
 assert.equal(data.cycles.length,22);
 assert.equal(data.workouts.length,88);
 assert.equal(data.exercises.length,479);
 assert.equal(data.cycles.filter(x=>x.block==='h2').length,9);
 assert.equal(data.cycles.filter(x=>x.block==='v9').length,13);
});
test('в активных упражнениях нет удалённых методов',()=>{
 const text=JSON.stringify(data.exercises).toLowerCase();
 for(const term of ['amrap','board press','pin press','пин-пресс'])assert.equal(text.includes(term),false,term);
});
