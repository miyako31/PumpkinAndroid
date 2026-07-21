package com.pumpkinmc.android.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.pumpkinmc.android.R;
import com.pumpkinmc.android.service.PumpkinService;
import com.pumpkinmc.android.util.BinaryInstaller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_LOG_LINES = 500;
    private static final int REQ_NOTIFICATION = 1001;

    private Button btnStartStop;
    private TextView tvLog;
    private TextView tvStatus;
    private TextView tvInfo;
    private ScrollView scrollLog;

    private PumpkinService pumpkinService;
    private boolean serviceBound = false;
    private final StringBuilder logBuffer = new StringBuilder();
    private int logLineCount = 0;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartStop = findViewById(R.id.btn_start_stop);
        tvLog        = findViewById(R.id.tv_log);
        tvStatus     = findViewById(R.id.tv_status);
        tvInfo       = findViewById(R.id.tv_info);
        scrollLog    = findViewById(R.id.scroll_log);

        tvLog.setMovementMethod(new ScrollingMovementMethod());

        // Show device ABI and Android version
        String abi = BinaryInstaller.getPrimaryAbi();
        tvInfo.setText("ABI: " + abi + "  |  Android " + Build.VERSION.RELEASE);

        btnStartStop.setOnClickListener(v -> {
            if (pumpkinService != null &&
                pumpkinService.getStatus() == PumpkinService.Status.RUNNING) {
                stopServer();
            } else {
                startServer();
            }
        });

        requestNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service if it is already running
        Intent intent = new Intent(this, PumpkinService.class);
        bindService(intent, serviceConnection, 0);

        // Register for log and status broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(PumpkinService.ACTION_LOG);
        filter.addAction(PumpkinService.ACTION_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    // ---------------------------------------------------------------
    // Server control
    // ---------------------------------------------------------------
    private void startServer() {
        appendLog("Starting server...");
        Intent intent = new Intent(this, PumpkinService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, serviceConnection, 0);
    }

    private void stopServer() {
        appendLog("Stopping server...");
        if (pumpkinService != null) {
            pumpkinService.stopServer();
        }
    }

    // ---------------------------------------------------------------
    // Service connection
    // ---------------------------------------------------------------
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PumpkinService.LocalBinder binder = (PumpkinService.LocalBinder) service;
            pumpkinService = binder.getService();
            serviceBound = true;
            updateUI(pumpkinService.getStatus());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pumpkinService = null;
            serviceBound = false;
            updateUI(PumpkinService.Status.STOPPED);
        }
    };

    // ---------------------------------------------------------------
    // Broadcast receiver
    // ---------------------------------------------------------------
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PumpkinService.ACTION_LOG.equals(intent.getAction())) {
                String line = intent.getStringExtra(PumpkinService.EXTRA_LOG_LINE);
                if (line != null) appendLog(line);
            } else if (PumpkinService.ACTION_STATUS.equals(intent.getAction())) {
                String statusStr = intent.getStringExtra(PumpkinService.EXTRA_STATUS);
                if (statusStr != null) {
                    try {
                        updateUI(PumpkinService.Status.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    };

    // ---------------------------------------------------------------
    // UI updates
    // ---------------------------------------------------------------
    private void updateUI(PumpkinService.Status status) {
        runOnUiThread(() -> {
            switch (status) {
                case STOPPED:
                    tvStatus.setText("Stopped");
                    tvStatus.setTextColor(getColor(R.color.status_stopped));
                    btnStartStop.setText("Start");
                    btnStartStop.setEnabled(true);
                    break;
                case STARTING:
                    tvStatus.setText("Starting...");
                    tvStatus.setTextColor(getColor(R.color.status_starting));
                    btnStartStop.setText("Starting...");
                    btnStartStop.setEnabled(false);
                    break;
                case RUNNING:
                    tvStatus.setText("Running");
                    tvStatus.setTextColor(getColor(R.color.status_running));
                    btnStartStop.setText("Stop");
                    btnStartStop.setEnabled(true);
                    break;
                case STOPPING:
                    tvStatus.setText("Stopping...");
                    tvStatus.setTextColor(getColor(R.color.status_starting));
                    btnStartStop.setText("Stopping...");
                    btnStartStop.setEnabled(false);
                    break;
                case ERROR:
                    tvStatus.setText("Error");
                    tvStatus.setTextColor(getColor(R.color.status_error));
                    btnStartStop.setText("Retry");
                    btnStartStop.setEnabled(true);
                    break;
            }
        });
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            logBuffer.append("[").append(timestamp).append("] ").append(line).append("\n");
            logLineCount++;

            // Drop the oldest line when the buffer is full
            if (logLineCount > MAX_LOG_LINES) {
                int firstNewline = logBuffer.indexOf("\n");
                if (firstNewline >= 0) {
                    logBuffer.delete(0, firstNewline + 1);
                    logLineCount--;
                }
            }

            tvLog.setText(logBuffer.toString());
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    // ---------------------------------------------------------------
    // Notification permission (Android 13+)
    // ---------------------------------------------------------------
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Notification permission denied. Background operation may be limited.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
