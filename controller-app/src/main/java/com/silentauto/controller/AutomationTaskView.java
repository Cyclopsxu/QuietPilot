// Modified by Codex for ShadowAuto: provide full-screen handoff preview with scaled touch forwarding. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.TextureView;

import org.json.JSONObject;

final class AutomationTaskView {
    interface Listener {
        void onTaskStart(AutomationTaskView task);

        void onTaskStop(String taskId);
        void onOpenHandoff(String taskId);
        void onTaskTouch(String taskId, String action, float x, float y);
        void onHandoffAction(String taskId, String method, String outcome);
    }

    private final Activity activity;
    private final Handler main;
    private final Listener listener;
    private final String taskId;
    private final View view;
    private final EditText goal;
    private final FrameLayout screenBox;
    private final TextureView screen;
    private final TextView log;
    private final TextView displayLabel;
    private final ScrollView logScroll;
    private final Button run;
    private final Button stop;
    private final Button resultAction;
    private boolean opening, taskEnded;

    private H264VideoDecoder screenDecoder;
    private AlertDialog screenDialog;
    private H264VideoDecoder dialogDecoder;
    private VideoConfig videoConfig;
    private JSONObject handoffStatus;
    private TextView handoffHint;
    private int otherWaiting;
    private boolean finalizing;
    private Button openOnMain;

    AutomationTaskView(Activity activity, Handler main, String taskId, Listener listener) {
        this.activity = activity;
        this.main = main;
        this.taskId = taskId;
        this.listener = listener;
        view = activity.getLayoutInflater().inflate(R.layout.item_task, null, false);
        goal = view.findViewById(R.id.taskGoal);
        screenBox = view.findViewById(R.id.screenBox);
        screen = view.findViewById(R.id.screenTexture);
        screen.setOpaque(true);
        displayLabel=new TextView(activity);displayLabel.setText("虚拟屏：等待创建");
        ((android.widget.LinearLayout)view).addView(displayLabel,2);
        log = view.findViewById(R.id.logText);
        logScroll = view.findViewById(R.id.logScroll);
        run = view.findViewById(R.id.runButton);
        stop = view.findViewById(R.id.stopButton);
        screenDecoder = new H264VideoDecoder(screen, main);
        UiUtils.clearButtonTint(run, stop);
        resultAction=new Button(activity);resultAction.setText("去查看");resultAction.setVisibility(View.GONE);
        ((android.widget.LinearLayout)view).addView(resultAction,2);
        resultAction.setOnClickListener(v -> listener.onOpenHandoff(taskId));
        screenBox.setOnClickListener(v -> { if(handoffStatus!=null)listener.onOpenHandoff(taskId);else showScreenDialog(); });
        run.setOnClickListener(v -> start());
        stop.setOnClickListener(v -> {
            setRunning(false);
            listener.onTaskStop(taskId);
        });
    }

    String taskId() {
        return taskId;
    }

    void restoreGoal(String value) { goal.setText(value); showLiveUi(true); }

    String goalText() {
        return goal.getText().toString();
    }

    View view() {
        return view;
    }

    void start() {
        if (goalText().trim().isEmpty()) {
            return;
        }
        taskEnded=false;
        showLiveUi(true);
        setRunning(true);
        resetVideo();
        log.setText("");
        append(activity.getString(R.string.starting));
        listener.onTaskStart(this);
    }

    void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        activity.runOnUiThread(() -> {
            log.append(text.endsWith("\n") ? text : text + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            if (text.contains("automation finished")
                    || text.contains("automation failed")
                    || text.contains("automation cancelled")
                    || text.contains("[start error]")
                    || text.contains("[rpc error]")) {
                setRunning(false);
            }
        });
    }

    void showVideoConfig(JSONObject params) {
        activity.runOnUiThread(() -> {
            videoConfig = VideoConfig.from(params);
            if (screenDecoder != null) {
                videoConfig.apply(screenDecoder);
            }
            if (dialogDecoder != null) {
                videoConfig.apply(dialogDecoder);
            }
        });
    }

    void showVideoSample(JSONObject params) {
        String data = params.optString("data");
        long ptsUs = params.optLong("ptsUs");
        int flags = params.optInt("flags");
        activity.runOnUiThread(() -> {
            if (screenDecoder != null) {
                screenDecoder.queueSample(data, ptsUs, flags);
            }
            if (dialogDecoder != null) {
                dialogDecoder.queueSample(data, ptsUs, flags);
            }
        });
    }

    void showLiveUi(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        stop.setVisibility(visibility);
        screenBox.setVisibility(visibility);
        logScroll.setVisibility(visibility);
    }

    void setFinalizing(boolean value) { finalizing=value; }
    void setRunning(boolean running) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> setRunning(running));
            return;
        }
        if (running) {
            hideKeyboard();
        }
        goal.setVisibility(running ? View.GONE : View.VISIBLE);
        run.setEnabled(!running&&!finalizing);
        run.setText(finalizing ? "正在学习/收尾…" : running ? activity.getString(R.string.running) : activity.getString(R.string.run));
        stop.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    void setOpening(boolean value) {
        activity.runOnUiThread(() -> {opening=value;updateResultAction();});
    }
    private void updateResultAction() {
        boolean waiting=handoffStatus!=null;
        boolean moving=opening||(waiting&&java.util.Arrays.asList("moving","returning","cancelling").contains(handoffStatus.optString("transferState")));
        resultAction.setVisibility(waiting||opening?View.VISIBLE:View.GONE);
        resultAction.setText(moving?"正在交接…":waiting&&"manual_main".equals(handoffStatus.optString("transferState"))?"交回后台继续":waiting&&handoffStatus.optBoolean("resultReady")?"去查看":"到主屏处理");
        resultAction.setEnabled(!moving);screenBox.setEnabled(!moving&&!taskEnded);
    }

    void updateStatus(JSONObject status) {
        activity.runOnUiThread(() -> {
            int displayId=status.optInt("displayId",-1);
            displayLabel.setText(displayId>0?"虚拟屏 displayId="+displayId:"虚拟屏：未创建或已释放");
            taskEnded=java.util.Arrays.asList("VERIFIED","ANSWERED","NOT_FOUND","FAILED","CANCELLED","HANDOFF_TIMEOUT").contains(status.optString("status"));
            if ("WAITING_USER".equals(status.optString("status"))) {
                handoffStatus = status;
                if (handoffHint != null) handoffHint.setText(handoffMessage(status));
                if (openOnMain != null) openOnMain.setVisibility(!status.optBoolean("resultReady") ? View.VISIBLE : View.GONE);
                if("manual_main".equals(status.optString("transferState"))&&screenDialog!=null)screenDialog.dismiss();
            } else {
                handoffStatus = null;
                if (handoffHint != null && screenDialog != null) screenDialog.dismiss();
            }
            updateResultAction();
        });
    }

    void otherWaiting(int count) {
        otherWaiting = Math.max(0,count);
        if (handoffHint != null && handoffStatus != null) handoffHint.setText(handoffMessage(handoffStatus));
    }
    private String handoffMessage(JSONObject status) {
        String prefix = otherWaiting > 0 ? "还有 " + otherWaiting + " 个任务等你处理\n" : "";
        return prefix + (status.optBoolean("resultReady") ? "任务已完成。" + status.optString("message") + "\n可在预览中操作，点“重试切到主屏”重试，或结束查看。\n" + status.optString("evidence") : status.optBoolean("protectedScreen")
                ? "该页面受系统保护，请在 " + status.optString("appName", status.optString("app")) + " 中完成"
                : "建议点“到主屏处理”，在原生页面操作，完成后点“交回后台继续”。\n" + status.optString("message"));
    }

    void showHandoff(JSONObject status) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            handoffStatus = status;
            if (screenDialog != null) screenDialog.dismiss();
            android.widget.LinearLayout content = new android.widget.LinearLayout(activity);
            content.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
            content.setPadding(padding, padding + (activity.getSharedPreferences("overlay",0).getBoolean("enabled",false) ? padding * 3 : 0), padding, padding);
            handoffHint = new TextView(activity); handoffHint.setText(handoffMessage(status));
            handoffHint.setTextSize(18); content.addView(handoffHint);
            FrameLayout frame = new FrameLayout(activity);
            content.addView(frame, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
            TextureView preview = new TextureView(activity); preview.setOpaque(true);
            frame.addView(preview);
            frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                int vw = videoConfig == null ? status.optInt("width", 1080) : videoConfig.width;
                int vh = videoConfig == null ? status.optInt("height", 2400) : videoConfig.height;
                float scale = Math.min(frame.getWidth() / (float) vw, frame.getHeight() / (float) vh);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, (int)(vw * scale)), Math.max(1, (int)(vh * scale)), android.view.Gravity.CENTER);
                preview.setLayoutParams(lp);
            });
            preview.setOnTouchListener((v, event) -> {
                if (handoffStatus == null || handoffStatus.optBoolean("protectedScreen") || videoConfig == null) return true;
                String action;
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN: action = "down"; break;
                    case android.view.MotionEvent.ACTION_MOVE: action = "move"; break;
                    case android.view.MotionEvent.ACTION_UP: action = "up"; break;
                    case android.view.MotionEvent.ACTION_CANCEL: action = "cancel"; break;
                    default: return true;
                }
                listener.onTaskTouch(taskId, action, event.getX() * videoConfig.width / Math.max(1, preview.getWidth()),
                        event.getY() * videoConfig.height / Math.max(1, preview.getHeight()));
                return true;
            });
            dialogDecoder = new H264VideoDecoder(preview, main);
            if (videoConfig != null) videoConfig.apply(dialogDecoder);
            openOnMain = new Button(activity); openOnMain.setText("到主屏处理");
            openOnMain.setVisibility(!status.optBoolean("resultReady") ? View.VISIBLE : View.GONE);
            openOnMain.setOnClickListener(v -> listener.onOpenHandoff(taskId)); content.addView(openOnMain);
            if(status.optBoolean("resultReady")) {
                Button retry=new Button(activity);retry.setText("重试切到主屏");content.addView(retry);
                retry.setOnClickListener(v -> {if(screenDialog!=null)screenDialog.dismiss();listener.onOpenHandoff(taskId);});
            }
            Button done = new Button(activity); done.setText(status.optBoolean("resultReady") ? "结束查看" : "我处理好了"); content.addView(done);
            done.setOnClickListener(v -> listener.onHandoffAction(taskId, "resumeTask", "done"));
            Button cancel = new Button(activity); cancel.setText("取消任务"); cancel.setVisibility(status.optBoolean("resultReady") ? View.GONE : View.VISIBLE); content.addView(cancel);
            cancel.setOnClickListener(v -> listener.onTaskStop(taskId));
            Button back = new Button(activity); back.setText("返回任务"); content.addView(back);
            back.setOnClickListener(v -> { if (screenDialog != null) screenDialog.dismiss(); });
            AlertDialog handoffDialog = new AlertDialog.Builder(activity).setView(content).create();
            H264VideoDecoder handoffDecoder = dialogDecoder;
            screenDialog = handoffDialog;
            handoffDialog.setOnDismissListener(dialog -> {
                handoffDecoder.release();
                if (screenDialog == handoffDialog) {
                    dialogDecoder = null; screenDialog = null; handoffHint = null; openOnMain = null;
                }
            });
            handoffDialog.setOnShowListener(dialog -> {
                if (handoffDialog.isShowing() && handoffDialog.getWindow() != null) {
                    handoffDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
                    handoffDialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
                    handoffDialog.getWindow().setLayout(-1, -1);
                }
            });
            handoffDialog.show();
        });
    }

    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) {
            input.hideSoftInputFromWindow(goal.getWindowToken(), 0);
        }
        goal.clearFocus();
    }

    private void showScreenDialog() {
        if (screenDialog != null && screenDialog.isShowing()) {
            return;
        }
        View holder = activity.getLayoutInflater().inflate(R.layout.dialog_screen_preview, null, false);
        int previewHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.66f);
        holder.setMinimumHeight(previewHeight);
        TextureView preview = holder.findViewById(R.id.dialogScreenTexture);
        preview.setOpaque(true);
        H264VideoDecoder decoder = new H264VideoDecoder(preview, main);
        dialogDecoder = decoder;
        if (videoConfig != null) {
            videoConfig.apply(decoder);
        }
        AlertDialog previewDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.screen_preview_title))
                .setView(holder)
                .setPositiveButton(activity.getString(R.string.close), null)
                .create();
        screenDialog = previewDialog;
        previewDialog.setOnDismissListener(dialog -> {
            decoder.release();
            if (screenDialog == previewDialog) {
                dialogDecoder = null;
                screenDialog = null;
            }
        });
        previewDialog.setOnShowListener(dialog -> {
            android.view.Window window = previewDialog.getWindow();
            if (window != null) {
                int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f);
                int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
                window.setLayout(width, height);
            }
        });
        previewDialog.show();
    }

    private void resetVideo() {
        videoConfig = null;
        if (screenDecoder != null) {
            screenDecoder.release();
        }
        if (dialogDecoder != null) {
            dialogDecoder.release();
            dialogDecoder = null;
        }
        screenDecoder = new H264VideoDecoder(screen, main);
    }

    private static final class VideoConfig {
        final String mime;
        final int width;
        final int height;
        final String csd0;
        final String csd1;

        private VideoConfig(String mime, int width, int height, String csd0, String csd1) {
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.csd0 = csd0;
            this.csd1 = csd1;
        }

        static VideoConfig from(JSONObject params) {
            return new VideoConfig(
                    params.optString("mime", "video/avc"),
                    params.optInt("width"),
                    params.optInt("height"),
                    params.optString("csd0"),
                    params.optString("csd1")
            );
        }

        void apply(H264VideoDecoder decoder) {
            decoder.configure(mime, width, height, csd0, csd1);
        }
    }
}
