package com.silentauto.controller.ext.overlay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

public final class OverlayPermissionActivity extends Activity {
    private LinearLayout page;
    public static boolean ready(Context c){return Settings.canDrawOverlays(c)&&c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            &&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
    @Override public void onCreate(Bundle state){super.onCreate(state);}
    @Override public void onResume(){super.onResume();render();}
    private void render(){
        ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(32,80,32,32);scroll.addView(page);setContentView(scroll);
        TextView title=new TextView(this);title.setText("语音悬浮球与权限");title.setTextSize(24);page.addView(title);
        TextView preview=new TextView(this);preview.setText(ready(this)?"● 权限已就绪":"● 灰色小球：请完成下列权限");preview.setTextColor(ready(this)?0xff237b73:0xff888888);page.addView(preview);
        button("1. 悬浮窗权限"+(Settings.canDrawOverlays(this)?" ✓":""),()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        button("2. 麦克风权限",()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10));
        if(Build.VERSION.SDK_INT>=33)button("3. 通知权限",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11));
        TextView tips=new TextView(this);tips.setText("按住说话，松开发送；上滑 80dp 取消。短按打开任务中心，拖动可贴边。\n\n华为：设置 → 应用 → 应用启动管理 → ShadowAuto → 关闭自动管理，允许自启动、关联启动、后台活动。并在电池设置允许后台活动。\n\n没有悬浮窗权限时无法在其他 App 显示小球，请先授权。首次启动请保持本页在前台。");page.addView(tips);
        button("打开应用后台设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button("启用语音悬浮球",()->{if(!ready(this)){Toast.makeText(this,"请先完成所需权限",Toast.LENGTH_SHORT).show();return;}getSharedPreferences("overlay",0).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,FloatingBallService.class));Toast.makeText(this,"已启用，返回其他 App 后可按住说话",Toast.LENGTH_SHORT).show();});
        button("关闭悬浮球",()->{getSharedPreferences("overlay",0).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,FloatingBallService.class));});
        button("返回",this::finish);
    }
    private void button(String text,Runnable action){Button button=new Button(this);button.setText(text);button.setOnClickListener(v->action.run());page.addView(button);}
}
