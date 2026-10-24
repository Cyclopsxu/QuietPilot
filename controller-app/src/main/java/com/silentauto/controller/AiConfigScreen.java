// Modified by Codex for ShadowAuto: save and transmit three domestic model profiles. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import android.app.Activity;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;

final class AiConfigScreen {
    interface Listener {
        void onConfigSaved(AiConfig config);
        void onConfigCancelled();
    }
    private final Activity activity;
    private final ExecutorService io;
    private final AiConfigStore store;
    private final Listener listener;
    AiConfigScreen(Activity activity, Handler main, ExecutorService io, AiConfigStore store, Listener listener) {
        this.io=io; this.activity = activity; this.store = store; this.listener = listener;
    }
    void show(AiConfig config, boolean fromSettings) {
        activity.setContentView(R.layout.activity_config);
        UiUtils.applyPageInsets(activity, activity.findViewById(R.id.configPage), 28);
        View close = activity.findViewById(R.id.configCloseButton);
        close.setVisibility(fromSettings ? View.VISIBLE : View.GONE);
        close.setOnClickListener(v -> listener.onConfigCancelled());
        LinearLayout card = activity.findViewById(R.id.configCard);
        card.removeAllViews();
        Button voice = new Button(activity); voice.setText("语音悬浮球与权限");
        voice.setOnClickListener(v -> activity.startActivity(new android.content.Intent(activity,com.silentauto.controller.ext.overlay.OverlayPermissionActivity.class)));
        card.addView(voice);
        String[] names = {"执行模型 executor（DeepSeek 官方）", "验收模型 verifier（百炼视觉）", "语音理解 intake（百炼 Omni）"};
        AiConfig.Model[] values = {config.executor, config.verifier, config.intake};
        EditText[][] inputs = new EditText[3][3];
        for (int i = 0; i < 3; i++) {
            TextView label = new TextView(activity); label.setText(names[i]); label.setTextSize(18);
            card.addView(label);
            inputs[i][0] = field(card, "API Base", values[i].apiBase, false);
            inputs[i][1] = field(card, "API Key", values[i].apiKey, true);
            inputs[i][2] = field(card, "Model ID", values[i].model, false);
            final int role=i;
            Button probe=new Button(activity);probe.setText("测试连接与服务端模型名");card.addView(probe);
            TextView probeStatus=new TextView(activity);card.addView(probeStatus);
            probe.setOnClickListener(v -> {
                try {
                    AiConfig.Model selected=new AiConfig.Model(inputs[role][1].getText().toString(),inputs[role][0].getText().toString(),inputs[role][2].getText().toString());
                    selected.validate();probe.setEnabled(false);probeStatus.setText("测试中…");
                    io.execute(() -> {
                        String message;
                        try {message=com.silentauto.controller.ext.config.ModelConnectionProbe.test(selected.apiBase,selected.apiKey,selected.model);}
                        catch(Exception failed){message="连接测试失败："+failed.getClass().getSimpleName();}
                        final String result=message;
                        activity.runOnUiThread(() -> {probe.setEnabled(true);probeStatus.setText(result);});
                    });
                } catch(Exception invalid){probeStatus.setText(invalid.getMessage());}
            });
        }
        android.widget.CheckBox privacy = new android.widget.CheckBox(activity);
        privacy.setText("不读取屏幕");
        privacy.setChecked(com.silentauto.controller.ext.context.ScreenPrivacy.disabled(activity));
        privacy.setOnCheckedChangeListener((button, checked) -> com.silentauto.controller.ext.context.ScreenPrivacy.save(activity,checked));
        card.addView(privacy);
        TextView status = new TextView(activity); card.addView(status);
        Button save = new Button(activity); save.setText("保存三组配置"); card.addView(save);
        save.setOnClickListener(v -> {
            try {
                AiConfig.Model[] models = new AiConfig.Model[3];
                for (int i = 0; i < 3; i++) models[i] = new AiConfig.Model(inputs[i][1].getText().toString(), inputs[i][0].getText().toString(), inputs[i][2].getText().toString());
                models[0].validate(); models[1].validate();
                if (!models[2].apiKey.isEmpty()) models[2].validate();
                listener.onConfigSaved(store.save(new AiConfig(models[0], models[1], models[2])));
            } catch (Exception e) { status.setText(e.getMessage()); }
        });
    }
    private EditText field(LinearLayout parent, String hint, String value, boolean secret) {
        EditText input = new EditText(activity);
        input.setHint(hint); input.setSingleLine(true);
        if (secret) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(value); parent.addView(input);
        return input;
    }
}
