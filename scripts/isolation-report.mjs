#!/usr/bin/env node
// Usage: node scripts/isolation-report.mjs task1.json task2.json task3.json > report.md
import fs from 'node:fs';
export function summarize(task) {
  const samples=task.disturbance?.focusSamples??[];
  const events=[];const counts={foreground:0,focus:0,ime:0,clipboard:0};
  const unknown={foreground:0,focus:0,ime:0,clipboard:0};
  let previous={},offFocus=false,offIme=false;
  for(const row of samples){
    const values={foreground:row.foreground||null,focus:row.focusedDisplay>=0?row.focusedDisplay:null,
      ime:row.imeDisplay>=0?row.imeDisplay:null,clipboard:row.clipboardPseudonym||row.clipboardDigest||null};
    if(row.mainFocusAvailable===false)values.focus=null;
    if((row.inputMethod??[]).some(line=>/mCurClient=null/.test(line)))values.ime=null;
    for(const [key,value] of Object.entries(values)){
      if(value===null){unknown[key]++;continue;}
      let hit=false;
      if(key==='focus'){hit=value!==0&&!offFocus;offFocus=value!==0;}
      else if(key==='ime'){hit=value!==0&&!offIme;offIme=value!==0;}
      else hit=previous[key]!==undefined&&previous[key]!==value;
      if(hit){
        counts[key]++;
        const steps=task.steps??[];
        const at=step=>step?.atMs??(task.createdAtMs!=null&&step?.elapsedMs!=null?task.createdAtMs+step.elapsedMs:Infinity);
        const nearest=steps.reduce((best,s)=>Math.abs(at(s)-row.atMs)<Math.abs(at(best)-row.atMs)?s:best,null);
        events.push({metric:key,atMs:row.atMs,step:row.step,action:row.action,
          actionTimeBasis:nearest?.atMs!=null?'动作时间戳':'任务创建时间加轨迹相对时间（近似）',nearestAction:nearest?.action?.action??'未记录',target:nearest?.target??'未记录',
          injection:nearest?.injectionMethod??'未记录；需对齐动作日志，不从动作名推断'});
      }
      previous[key]=value;
    }
  }
  const durationMs=samples.length>1?samples.at(-1).atMs-samples[0].atMs:0;
  const maxGapMs=samples.slice(1).reduce((m,s,i)=>Math.max(m,s.atMs-samples[i].atMs),0);
  return {counts,unknown,events,sampleCount:samples.length,durationMs,maxGapMs,
    assessment:samples.length>1&&durationMs>=60000&&maxGapMs<=1000&&!task.disturbance?.focusSamplesTruncated&&Object.values(unknown).every(n=>n===0)&&Object.values(counts).every(n=>n===0)?'采样四项为0；人工候选词与视频仍需核验':'未通过完整并行验收'};
}
if(process.argv[1]&&import.meta.url===new URL(`file://${process.argv[1]}`).href){
  if(process.argv.length<3){console.error('Usage: isolation-report.mjs task1.json [task2.json task3.json]');process.exit(2);}
  console.log('# 并行隔离采样报告\n\n周期采样可能遗漏短事件；候选词是否消失、人工是否持续输入与滚动必须结合录像核验。IME 未激活不等于目标确定保持在主屏。\n');
  for(const file of process.argv.slice(2)){
    const task=JSON.parse(fs.readFileSync(file,'utf8'));const result=summarize(task);
    console.log(`## ${task.taskId}\n\n设备：${task.isolation?.manufacturer??'未知'} ${task.isolation?.model??'未知'} SDK ${task.isolation?.sdkInt??'未知'}；结果：${task.status}\n\n隔离配置：\`${JSON.stringify(task.isolation??{})}\`\n\n采样 ${result.sampleCount} 次，覆盖 ${result.durationMs} ms，最大间隔 ${result.maxGapMs} ms。${result.assessment}\n\n| 指标 | 次数 | 未知采样 |\n| --- | ---: | ---: |`);
    for(const key of Object.keys(result.counts))console.log(`| ${key} | ${result.counts[key]} | ${result.unknown[key]} |`);
    console.log('\n事件时间线（归因仅为时间关联）：\n');
    for(const event of result.events)console.log(`- ${new Date(event.atMs).toISOString()} ${event.metric}：${JSON.stringify(event)}`);
    if(!result.events.length)console.log(result.sampleCount?'未观察到事件；不代表未采样时段无事件。':'无原始采样，不能报告零事件。');
  }
}
