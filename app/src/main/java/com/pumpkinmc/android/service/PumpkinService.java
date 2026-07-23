package com.pumpkinmc.android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.pumpkinmc.android.R;
import com.pumpkinmc.android.ui.MainActivity;
import com.pumpkinmc.android.util.BinaryInstaller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PumpkinService extends Service {

    private static final String TAG = "PumpkinService";
    public static final String CHANNEL_ID = "pumpkin_server_channel";
    public static final String ACTION_LOG = "com.pumpkinmc.android.LOG";
    public static final String ACTION_STATUS = "com.pumpkinmc.android.STATUS";
    public static final String EXTRA_LOG_LINE = "log_line";
    public static final String EXTRA_STATUS = "status";

    public enum Status { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

    private final IBinder binder = new LocalBinder();
    private Process serverProcess;
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    private Status currentStatus = Status.STOPPED;

    public class LocalBinder extends Binder {
        public PumpkinService getService() { return PumpkinService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newCachedThreadPool();
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopServer();
            return START_NOT_STICKY;
        }
        startForeground(1, buildNotification("Starting..."));
        startServer();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ---------------------------------------------------------------
    // Server startup
    // ---------------------------------------------------------------
    private void startServer() {
        executor.execute(() -> {
            try {
                setStatus(Status.STARTING);

                // 1. Locate the Pumpkin binary Android extracted from jniLibs/
                //    at install time (executable by design, unlike files dir)
                File serverDir = getServerDirectory();
                File binary = BinaryInstaller.locate(this);
                if (binary == null) {
                    broadcast(ACTION_LOG, "ERROR: Pumpkin binary not found in app package");
                    setStatus(Status.ERROR);
                    return;
                }

                // 2. Launch the server process
                List<String> cmd = new ArrayList<>();
                cmd.add(binary.getAbsolutePath());
                // Add extra flags here if needed: cmd.add("--config"); cmd.add("...");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(serverDir);
                pb.redirectErrorStream(true); // merge stderr into stdout

                // Set HOME so Pumpkin can locate its data directory
                pb.environment().put("HOME", serverDir.getAbsolutePath());
                pb.environment().put("PUMPKIN_DATA_DIR", serverDir.getAbsolutePath());

                serverProcess = pb.start();
                setStatus(Status.RUNNING);
                updateNotification("Running \uD83C\uDF83");

                // 4. Stream stdout/stderr to the UI in real time
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        broadcast(ACTION_LOG, line);
                    }
                }

                int exitCode = serverProcess.waitFor();
                broadcast(ACTION_LOG, "Server exited (exit code: " + exitCode + ")");
                setStatus(Status.STOPPED);

            } catch (IOException e) {
                Log.e(TAG, "Server start failed", e);
                broadcast(ACTION_LOG, "ERROR: " + e.getMessage());
                setStatus(Status.ERROR);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setStatus(Status.STOPPED);
            } finally {
                updateNotification("Stopped");
                stopForeground(false);
                stopSelf();
            }
        });
    }

    // ---------------------------------------------------------------
    // Server shutdown
    // ---------------------------------------------------------------
    public void stopServer() {
        setStatus(Status.STOPPING);
        if (serverProcess != null && serverProcess.isAlive()) {
            // Pumpkin performs a graceful shutdown on SIGTERM
            serverProcess.destroy();
            executor.execute(() -> {
                try {
                    serverProcess.waitFor();
                } catch (InterruptedException ignored) {}
                setStatus(Status.STOPPED);
                stopSelf();
            });
        } else {
            setStatus(Status.STOPPED);
            stopSelf();
        }
    }

    public Status getStatus() { return currentStatus; }

    // ---------------------------------------------------------------
    // Server data directory: /data/data/<package>/files/pumpkin/
    // ---------------------------------------------------------------
    public File getServerDirectory() {
        File dir = new File(getFilesDir(), "pumpkin");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------------------------------------------------------------
    // Notification helpers
    // ---------------------------------------------------------------
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Pumpkin Server",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows the running state of the Pumpkin Minecraft server");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, PumpkinService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("\uD83C\uDF83 Pumpkin Server")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_pumpkin)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, buildNotification(text));
    }

    // ---------------------------------------------------------------
    // Broadcast helpers
    // ---------------------------------------------------------------
    private void setStatus(Status status) {
        currentStatus = status;
        Intent intent = new Intent(ACTION_STATUS);
        intent.putExtra(EXTRA_STATUS, status.name());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcast(String action, String message) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_LOG_LINE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PumpkinAndroid::ServerWakeLock");
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serverProcess != null && serverProcess.isAlive()) serverProcess.destroy();
        if (executor != null) executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
