package com.silentauto.controller.ext.history;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import com.silentauto.controller.MainActivity;
import com.silentauto.controller.ext.overlay.OverlayRpc;
import java.util.*;
import java.util.concurrent.*;

public final class TaskCenterActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    private OverlayRpc rpc;private LinearLayout content;private String tab="history";
    @Override public void onCreate(Bundle state){super.onCreate(state);rpc=new OverlayRpc(value->{});tab=getIntent().getStringExtra("tab");if(tab==null)tab="history";render();String id=getIntent().getStringExtra("taskId");if(id!=null)detail(id);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private void text(LinearLayout parent,String value,int size,int color){TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);label.setPadding(0,dp(6),0,dp(6));parent.addView(label);}
    private void button(LinearLayout parent,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setOnClickListener(v->action.run());parent.addView(button);}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void render(){
        LinearLayout root=column();root.setPadding(dp(16),dp(40),dp(16),dp(16));root.setBackgroundColor(0xfff7f8fc);root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(16),insets.getSystemWindowInsetTop()+dp(12),dp(16),insets.getSystemWindowInsetBottom()+dp(12));return insets;});
        text(root,"任务中心",26,0xff182639);LinearLayout tabs=new LinearLayout(this);root.addView(tabs);
        String[] labels={"执行","历史","技能"};for(int i=0;i<labels.length;i++){final int index=i;Button choice=new Button(this);choice.setText(labels[i]);choice.setEnabled(i==0||!tab.equals(i==1?"history":"skills"));choice.setOnClickListener(v->{if(index==0){finish();return;}tab=index==1?"history":"skills";render();});tabs.addView(choice,new LinearLayout.LayoutParams(0,-2,1));}
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);load();
    }
    private void load(){String selected=tab;LinearLayout target=content;text(target,"正在读取…",15,0xff576879);io.execute(()->{try{
        JSONArray data=rpc.request(selected.equals("history")?"listHistory":"listSkills",new JSONObject()).getJSONArray("result");main.post(()->{if(!alive()||!tab.equals(selected)||content!=target)return;target.removeAllViews();if(data.length()==0)text(target,selected.equals("history")?"暂无历史任务":"暂无技能；任务验收后会自动学习",16,0xff576879);
            if(selected.equals("history"))history(data,target);else skills(data,target);button(target,"刷新",()->{target.removeAllViews();load();});});
    }catch(Exception error){main.post(()->{if(alive()&&content==target){target.removeAllViews();text(target,error.getMessage(),16,0xffa33a44);button(target,"重试",()->{target.removeAllViews();load();});}});}});}
    private static String calls(JSONObject item){JSONObject counts=item.optJSONObject("llmCalls");if(counts==null)return "未记录";return String.valueOf(counts.optInt("executor")+counts.optInt("verifier")+counts.optInt("intake"));}
    private static String elapsed(JSONObject item){return item.optBoolean("statsUnavailable")?"未记录":String.format(Locale.ROOT,"%.1f秒",item.optLong("elapsedMs")/1000.0);}
    private void history(JSONArray items,LinearLayout parent){for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("taskId");button(parent,item.optString("goal")+"\n"+item.optString("status")+" · "+elapsed(item)+" · 模型 "+calls(item)+" 次\n"+item.optString("message"),()->detail(id));}}
    private void detail(String id){io.execute(()->{try{JSONObject item=rpc.request("getTask",new JSONObject().put("taskId",id)).getJSONObject("result");main.post(()->{if(!alive())return;LinearLayout body=column();body.setPadding(dp(16),dp(8),dp(16),dp(8));
        if(!item.optString("transcript").isEmpty())text(body,"原始语音："+item.optString("transcript"),16,0xff375c7b);
        text(body,item.optString("goal"),20,0xff182639);text(body,item.optString("status")+" · "+elapsed(item)+" · 模型 "+calls(item)+" 次",15,0xff576879);
        text(body,"结果："+item.optString("message"),16,0xff182639);JSONObject counts=item.optJSONObject("llmCalls");if(counts!=null)text(body,"模型调用："+counts+"\n技能："+item.optString("skillId")+"\n来源步数："+item.optJSONObject("sourceSteps"),14,0xff576879);
        if(!item.optString("comparison").isEmpty())text(body,item.optString("comparison"),16,0xff217c69);
        text(body,"步骤时间线",18,0xff182639);JSONArray steps=item.optJSONArray("steps");if(steps!=null)for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i);String source=step.optString("source","model"),label=step.optString("stepText",step.optString("reason"));TextView row=new TextView(this);row.setText(step.optInt("step")+" · "+source+" · "+label);row.setTextSize(15);row.setTextColor(source.equals("skill")?0xff218461:source.equals("rescue")?0xffb46a18:0xff365e9d);row.setPadding(0,dp(6),0,dp(6));body.addView(row);row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("步骤详情").setMessage(step.toString()).setPositiveButton("关闭",null).show());}
        text(body,"验收证据："+item.optString("evidence","暂无"),16,0xff182639);String image=item.optString("evidenceBase64");if(!image.isEmpty()){byte[] bytes=android.util.Base64.decode(image,android.util.Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null){ImageView proof=new ImageView(this);proof.setImageBitmap(bitmap);proof.setAdjustViewBounds(true);body.addView(proof,new LinearLayout.LayoutParams(-1,-2));}}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle("任务详情").setView(scroll).setPositiveButton("关闭",null).show();
    });}catch(Exception error){main.post(()->{if(alive())Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();});}});}
    private String statistics(JSONObject skill) {
        JSONObject stats=skill.optJSONObject("stats");if(stats==null)return "暂无统计";
        return String.format(Locale.ROOT,"使用 %d 次 · 成功 %d 次\n平均 %.1f 秒 / %.1f 次模型调用\n首次 %.1f 秒 / %d 次模型调用 · 连续放弃 %d 次",
                stats.optInt("uses"),stats.optInt("successes"),stats.optDouble("avgElapsedMs")/1000,stats.optDouble("avgLlmCalls"),stats.optDouble("firstElapsedMs")/1000,stats.optInt("firstLlmCalls"),stats.optInt("consecutiveAbandons"));
    }
    private void skills(JSONArray items,LinearLayout parent){TreeMap<String,List<JSONObject>> groups=new TreeMap<>();for(int i=0;i<items.length();i++){JSONObject skill=items.optJSONObject(i);groups.computeIfAbsent(skill.optString("appName",skill.optString("packageName"))+"\n"+skill.optString("packageName"),k->new ArrayList<>()).add(skill);}
        for(String app:groups.keySet()){text(parent,app,18,0xff182639);for(JSONObject skill:groups.get(app)){JSONArray params=skill.optJSONArray("params"),steps=skill.optJSONArray("steps");String names="";if(params!=null)for(int i=0;i<params.length();i++)names+=(i==0?"":", ")+params.optJSONObject(i).optString("desc",params.optJSONObject(i).optString("name"));
            button(parent,skill.optString("name")+"\n参数："+names+" · "+(steps==null?0:steps.length())+" 步 · "+skill.optString("status")+"\n"+statistics(skill),()->skillDetail(skill));}}
    }
    private void skillDetail(JSONObject skill){LinearLayout body=column();body.setPadding(dp(12),dp(4),dp(12),dp(4));AlertDialog[] dialog={null};
        button(body,"disabled".equals(skill.optString("status"))?"启用技能":"停用技能",()->mutate("setSkillStatus",skill,()->dialog[0].dismiss()));
        button(body,"删除技能",()->new AlertDialog.Builder(this).setTitle("删除技能？").setMessage(skill.optString("name")).setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->mutate("deleteSkill",skill,()->dialog[0].dismiss())).show());
        text(body,skill.optString("description"),16,0xff182639);text(body,statistics(skill),14,0xff576879);
        JSONArray params=skill.optJSONArray("params");if(params!=null)for(int i=0;i<params.length();i++){JSONObject param=params.optJSONObject(i);text(body,param.optString("name")+" · "+param.optString("desc")+"\n示例："+param.optString("example"),14,0xff576879);}
        text(body,"步骤（点击查看明细）",18,0xff182639);JSONArray steps=skill.optJSONArray("steps");if(steps!=null)for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i),target=step.optJSONObject("target");String locator=target==null?"":target.optString("text");if(locator.isEmpty()&&target!=null)locator=target.optString("contentDesc",target.optString("hint"));
            button(body,(i+1)+". "+step.optString("action")+" · "+locator+(step.isNull("text")?"":"\n"+step.optString("text")),()->new AlertDialog.Builder(this).setTitle("步骤明细").setMessage(step.toString()).setPositiveButton("关闭",null).show());}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);dialog[0]=new AlertDialog.Builder(this).setTitle(skill.optString("name")).setView(scroll).setPositiveButton("关闭",null).create();dialog[0].show();
    }
    private void mutate(String method,JSONObject skill,Runnable done){io.execute(()->{try{JSONObject params=new JSONObject().put("packageName",skill.optString("packageName")).put("skillId",skill.optString("skillId"));if(method.equals("setSkillStatus"))params.put("status","disabled".equals(skill.optString("status"))?"active":"disabled");rpc.request(method,params);main.post(()->{if(alive()){done.run();render();}});}catch(Exception error){main.post(()->{if(alive())Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();});}});}
    @Override public void onDestroy(){rpc.close();io.shutdown();super.onDestroy();}
}
