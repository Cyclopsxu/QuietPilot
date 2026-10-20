// Modified by Codex for ShadowAuto: log results and notify users about verified handoff boundaries. Original: ShadowAuto (Apache-2.0).
package com.silentauto.controller;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity implements AutomationTaskView.Listener, RpcClient.Listener {
    private static final int PORT = 43110;
    private static final int REQUEST_CONFIG = 1001;
    private static final int MAIN_PADDING_DP = 24;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final AtomicInteger nextTask = new AtomicInteger(1);
    private final List<AutomationTaskView> tasks = Collections.synchronizedList(new ArrayList<>());

    private AiConfigStore configStore;
    private AiConfig config = new AiConfig("", "", "");
    private RpcClient rpcClient;
    private ViewPager2 pager;
    private TaskPagerAdapter pagerAdapter;
    private TextView indicator;
    private boolean configOpen;
    private final java.util.Map<String, JSONObject> pendingHandoffs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String HANDOFF_CHANNEL = "task-handoff";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        UiUtils.setupSystemBars(this);
        startKeepAliveService();
        configStore = new AiConfigStore(this);
        config = configStore.load();
        rpcClient = new RpcClient("127.0.0.1", PORT, this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 23);
        }
        if (config.isConfigured()) {
            showMain();
        } else {
            openConfig(false);
        }
        handleHandoffIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleHandoffIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (configStore == null) {
            return;
        }
        config = configStore.load();
        if (!config.isConfigured() && !configOpen) {
            openConfig(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CONFIG) {
            return;
        }
        configOpen = false;
        config = configStore.load();
        if (config.isConfigured()) {
            showMain();
        } else {
            openConfig(false);
        }
    }

    @Override
    public void onTaskStart(AutomationTaskView task) {
        io.execute(() -> sendStart(task));
    }

    @Override
    public void onTaskStop(String taskId) {
        io.execute(() -> sendStop(taskId));
    }

    @Override
    public void onLine(String line) {
        handleRpcLine(line);
    }

    @Override
    public void onClosed(Exception error) {
        appendGlobal("[closed] " + error.getMessage());
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void startKeepAliveService() {
        Intent intent = new Intent(this, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void openConfig(boolean fromSettings) {
        configOpen = true;
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.putExtra(ConfigActivity.EXTRA_FROM_SETTINGS, fromSettings);
        startActivityForResult(intent, REQUEST_CONFIG);
    }

    private void showMain() {
        setContentView(R.layout.activity_main);
        FrameLayout root = findViewById(R.id.mainRoot);
        LinearLayout page = findViewById(R.id.mainPage);
        ImageButton stopAll = findViewById(R.id.stopAllButton);
        ImageButton settings = findViewById(R.id.settingsButton);
        ImageButton help = findViewById(R.id.helpButton);
        Button add = findViewById(R.id.addTaskButton);
        pager = findViewById(R.id.taskPager);
        indicator = findViewById(R.id.pageIndicator);

        UiUtils.clearButtonTint(add);
        FrameLayout.LayoutParams addLp = (FrameLayout.LayoutParams) add.getLayoutParams();
        UiUtils.applyMainInsets(this, root, page, add, addLp, MAIN_PADDING_DP);

        ensureTaskPage();
        pagerAdapter = new TaskPagerAdapter(tasks);
        pager.setAdapter(pagerAdapter);
        pager.setOffscreenPageLimit(2);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator();
            }
        });

        stopAll.setOnClickListener(v -> confirmStopAll());
        settings.setOnClickListener(v -> openConfig(true));
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        add.setOnClickListener(v -> addEmptyTask());
        updateIndicator();
    }

    private void ensureTaskPage() {
        synchronized (tasks) {
            if (!tasks.isEmpty()) {
                return;
            }
            tasks.add(new AutomationTaskView(this, main, newTaskId(), this));
        }
    }

    private void addEmptyTask() {
        Toast.makeText(this, getString(R.string.parallel_task_warning), Toast.LENGTH_SHORT).show();
        AutomationTaskView task = new AutomationTaskView(this, main, newTaskId(), this);
        int index;
        synchronized (tasks) {
            tasks.add(task);
            index = tasks.size() - 1;
        }
        pagerAdapter.notifyItemInserted(index);
        pager.setCurrentItem(index, true);
        updateIndicator();
    }

    private void confirmStopAll() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.stop_all_title))
                .setMessage(getString(R.string.stop_all_message))
                .setNegativeButton(getString(R.string.no), null)
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> stopAllTasks())
                .show();
    }

    private void stopAllTasks() {
        synchronized (tasks) {
            for (AutomationTaskView task : tasks) {
                task.setRunning(false);
            }
        }
        io.execute(() -> {
            if (sendStopAll()) {
                main.post(() -> Toast.makeText(this, getString(R.string.stop_all_sent), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String newTaskId() {
        return "app-" + nextTask.getAndIncrement();
    }

    private void sendStart(AutomationTaskView task) {
        try {
            JSONObject params = new JSONObject();
            params.put("taskId", task.taskId());
            params.put("goal", task.goalText());
            params.put("apiKey", config.apiKey);
            params.put("apiBase", config.apiBase);
            params.put("model", config.model);
            rpcClient.send("startTask", params);
        } catch (Exception e) {
            reportRpcError(task, e);
        }
    }

    private void sendStop(String taskId) {
        try {
            rpcClient.send("stopTask", new JSONObject().put("taskId", taskId));
        } catch (Exception e) {
            reportRpcError(taskById(taskId), e);
        }
    }

    private boolean sendStopAll() {
        try {
            rpcClient.send("stopTask", new JSONObject());
            return true;
        } catch (Exception e) {
            reportRpcError(null, e);
            return false;
        }
    }

    private void handleRpcLine(String line) {
        try {
            JSONObject object = new JSONObject(line);
            if (!"event".equals(object.optString("method"))) {
                return;
            }
            JSONObject params = object.getJSONObject("params");
            AutomationTaskView task = taskById(params.optString("taskId"));
            if (task == null) {
                return;
            }
            String type = params.optString("type");
            if ("handoff".equals(type)) {
                pendingHandoffs.put(task.taskId(), params);
                main.post(() -> notifyHandoff(params));
                task.append("等待用户: " + params.optString("message"));
            } else if ("result".equals(type)) {
                pendingHandoffs.remove(task.taskId());
                main.post(() -> getSystemService(NotificationManager.class).cancel(task.taskId(), 23));
                task.append("RESULT " + params.optString("status") + ": " + params.optString("evidence"));
                task.setRunning(false);
            } else if ("video.config".equals(type)) {
                task.showVideoConfig(params);
            } else if ("video.sample".equals(type)) {
                task.showVideoSample(params);
            } else if ("frame".equals(type)) {
                return;
            } else {
                task.append(params.optString("message"));
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyHandoff(JSONObject params) {
        String taskId = params.optString("taskId");
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(HANDOFF_CHANNEL, "任务交接", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        Intent open = new Intent(this, MainActivity.class)
                .setAction("handoff:" + taskId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("handoffTaskId", taskId)
                .putExtra("handoffMessage", params.optString("message"))
                .putExtra("handoffBoundary", params.optString("boundary"));
        PendingIntent show = PendingIntent.getActivity(this, taskId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, HandoffCancelReceiver.class)
                .setAction("cancel:" + taskId).putExtra("taskId", taskId);
        PendingIntent dismiss = PendingIntent.getBroadcast(this, taskId.hashCode(), cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, HANDOFF_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("任务等待处理")
                .setContentText(params.optString("message"))
                .setStyle(new Notification.BigTextStyle().bigText(params.optString("message")))
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(show)
                .addAction(new Notification.Action.Builder(null, "去处理", show).build())
                .addAction(new Notification.Action.Builder(null, "取消任务", dismiss).build())
                .setAutoCancel(true).build();
        try {
            manager.notify(taskId, 23, notification);
        } catch (SecurityException e) {
            AutomationTaskView task = taskById(taskId);
            if (task != null) task.append("通知权限未开启，请在任务页处理: " + params.optString("message"));
        }
    }

    private void handleHandoffIntent(Intent intent) {
        String taskId = intent.getStringExtra("handoffTaskId");
        if (taskId == null) return;
        String message = intent.getStringExtra("handoffMessage");
        String boundary = intent.getStringExtra("handoffBoundary");
        intent.removeExtra("handoffTaskId");
        main.post(() -> {
            if (pager != null) {
                synchronized (tasks) {
                    for (int i = 0; i < tasks.size(); i++) {
                        if (tasks.get(i).taskId().equals(taskId)) pager.setCurrentItem(i, false);
                    }
                }
            }
            new AlertDialog.Builder(this).setTitle("任务等待处理")
                    .setMessage(message + "\n\n" + boundary + "\n\n当前预览尚不支持触控转发。")
                    .setPositiveButton("已处理，继续", (dialog, which) -> resumeHandoff(taskId, "done"))
                    .setNegativeButton("取消任务", (dialog, which) -> resumeHandoff(taskId, "cancel"))
                    .setNeutralButton("稍后", null).show();
        });
    }

    private void resumeHandoff(String taskId, String outcome) {
        io.execute(() -> {
            try {
                rpcClient.send("resumeTask", new JSONObject().put("taskId", taskId).put("outcome", outcome));
                pendingHandoffs.remove(taskId);
                getSystemService(NotificationManager.class).cancel(taskId, 23);
            } catch (Exception e) {
                reportRpcError(taskById(taskId), e);
            }
        });
    }

    public static final class HandoffCancelReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String taskId = intent.getStringExtra("taskId");
            if (taskId == null) return;
            PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    RpcClient client = new RpcClient("127.0.0.1", PORT, new RpcClient.Listener() {
                        public void onLine(String line) {}
                        public void onClosed(Exception error) {}
                    });
                    try {
                        client.send("resumeTask", new JSONObject().put("taskId", taskId).put("outcome", "cancel"));
                    } finally {
                        client.close();
                    }
                    context.getSystemService(NotificationManager.class).cancel(taskId, 23);
                } catch (Exception e) {
                    android.util.Log.e("ShadowAuto", "handoff cancellation failed", e);
                } finally {
                    pending.finish();
                }
            }, "handoff-cancel").start();
        }
    }

    private AutomationTaskView taskById(String taskId) {
        synchronized (tasks) {
            for (AutomationTaskView task : tasks) {
                if (task.taskId().equals(taskId)) {
                    return task;
                }
            }
        }
        return null;
    }

    private void appendGlobal(String text) {
        runOnUiThread(() -> {
            synchronized (tasks) {
                if (!tasks.isEmpty()) {
                    tasks.get(0).append(text);
                }
            }
        });
    }

    private void reportRpcError(AutomationTaskView task, Exception error) {
        String detail = rpcErrorMessage(error);
        String message = getString(R.string.rpc_error, detail);
        if (task != null) {
            task.append(message);
            task.setRunning(false);
        } else {
            appendGlobal(message);
        }
        main.post(() -> Toast.makeText(this, detail, Toast.LENGTH_LONG).show());
    }

    private String rpcErrorMessage(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                return getString(R.string.rpc_shell_unavailable);
            }
            current = current.getCause();
        }
        String message = error.getMessage();
        if (message != null && message.toLowerCase(Locale.US).contains("refused")) {
            return getString(R.string.rpc_shell_unavailable);
        }
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void updateIndicator() {
        if (indicator == null || pagerAdapter == null || pager == null) {
            return;
        }
        int count = pagerAdapter.getItemCount();
        int current = pager.getCurrentItem();
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                dots.append("  ");
            }
            dots.append(i == current ? "\u25CF" : "\u25CB");
        }
        indicator.setText(dots.toString());
        indicator.setOnClickListener(v -> {
            String taskId = tasks.get(pager.getCurrentItem()).taskId();
            JSONObject handoff = pendingHandoffs.get(taskId);
            if (handoff != null) {
                handleHandoffIntent(new Intent().putExtra("handoffTaskId", taskId)
                        .putExtra("handoffMessage", handoff.optString("message"))
                        .putExtra("handoffBoundary", handoff.optString("boundary")));
            }
        });
    }
}
