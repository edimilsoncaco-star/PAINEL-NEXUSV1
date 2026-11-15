package com.painelnexus.monitor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.painelnexus.monitor.R;
import com.painelnexus.monitor.config.AppConfig;
import com.painelnexus.monitor.databinding.LayoutMonitorHudBinding;
import com.painelnexus.monitor.ui.MainActivity;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mostra um painel pequeno e arrastavel sobre qualquer app com:
 *  - FPS da tela (quantos quadros por segundo o Android esta entregando)
 *  - Ping (tempo de resposta da sua internet ate 1.1.1.1)
 */
public class FloatingMonitorService extends Service {

    private static volatile boolean sRunning = false;
    public static boolean isRunning() { return sRunning; }

    private WindowManager wm;
    private View hudView;
    private LayoutMonitorHudBinding hud;
    private WindowManager.LayoutParams params;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService pingExecutor;

    // ── FPS (Choreographer) ────────────────────────────────────────────────
    private boolean monitoring = false;
    private long fpsWindowStart = 0L;
    private int frames = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!monitoring) return;
            if (fpsWindowStart == 0L) fpsWindowStart = frameTimeNanos;
            frames++;
            long elapsed = frameTimeNanos - fpsWindowStart;
            if (elapsed >= 1_000_000_000L) {
                float fps = frames * 1_000_000_000f / elapsed;
                updateFps(Math.round(fps));
                frames = 0;
                fpsWindowStart = frameTimeNanos;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();

        // Precisa chamar startForeground logo, senao o Android derruba o app.
        startForegroundNotif();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            addHud();
        } catch (RuntimeException e) {
            stopSelf();
            return;
        }

        sRunning = true;
        monitoring = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
        startPing();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        monitoring = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        mainHandler.removeCallbacksAndMessages(null);
        if (pingExecutor != null) pingExecutor.shutdownNow();
        removeHud();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Notificacao do servico ─────────────────────────────────────────────

    private void startForegroundNotif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                AppConfig.NOTIF_CHANNEL_ID,
                AppConfig.NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);

        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, AppConfig.NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nexus_notif)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Monitor de FPS e ping ativo")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(AppConfig.NOTIF_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(AppConfig.NOTIF_ID, notif);
        }
    }

    // ── HUD flutuante ──────────────────────────────────────────────────────

    private void addHud() {
        hud = LayoutMonitorHudBinding.inflate(LayoutInflater.from(this));
        hudView = hud.getRoot();

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 120;

        hud.btnClose.setOnClickListener(v -> stopSelf());

        hudView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int) (e.getRawX() - touchX);
                        params.y = initY + (int) (e.getRawY() - touchY);
                        wm.updateViewLayout(hudView, params);
                        return true;
                }
                return false;
            }
        });

        wm.addView(hudView, params);
    }

    private void removeHud() {
        if (hudView != null && wm != null) {
            try {
                wm.removeView(hudView);
            } catch (IllegalArgumentException ignored) {
                // ja foi removido
            }
        }
        hudView = null;
        hud = null;
    }

    // ── Atualizacao dos valores ────────────────────────────────────────────

    private void updateFps(int fps) {
        if (hud == null) return;
        hud.tvFps.setText("FPS " + fps);
        int color = fps >= 55 ? R.color.nexus_green
                : fps >= 30 ? R.color.nexus_yellow
                : R.color.nexus_red;
        hud.tvFps.setTextColor(getColor(color));
    }

    private void updatePing(long ms) {
        if (hud == null) return;
        if (ms < 0) {
            hud.tvPing.setText("PING --");
            hud.tvPing.setTextColor(getColor(R.color.nexus_red));
            return;
        }
        hud.tvPing.setText("PING " + ms + "ms");
        int color = ms < 60 ? R.color.nexus_green
                : ms < 120 ? R.color.nexus_yellow
                : R.color.nexus_red;
        hud.tvPing.setTextColor(getColor(color));
    }

    // ── Ping (tempo de conexao TCP) ────────────────────────────────────────

    private void startPing() {
        pingExecutor = Executors.newSingleThreadScheduledExecutor();
        pingExecutor.scheduleWithFixedDelay(() -> {
            try {
                final long ms = measurePing();
                mainHandler.post(() -> updatePing(ms));
            } catch (Exception ignored) {
                // nunca deixa a tarefa agendada morrer
            }
        }, 0L, AppConfig.PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Retorna o tempo em ms para abrir uma conexao TCP, ou -1 se falhar. */
    private long measurePing() {
        try (Socket socket = new Socket()) {
            long t0 = System.nanoTime();
            socket.connect(
                    new InetSocketAddress(AppConfig.PING_HOST, AppConfig.PING_PORT),
                    AppConfig.PING_TIMEOUT_MS);
            return (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception e) {
            return -1L;
        }
    }
}
