package com.kiwixsaat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * kiwix-serve programını arka planda çalıştırır ve açık tutar.
 * Program APK'nın içinde "libkiwixserve.so" adıyla duruyor; Android onu
 * kurulumda nativeLibraryDir klasörüne çıkarıyor, oradan çalıştırılabiliyor.
 */
public class ServerService extends Service {

    public static final int PORT = 8080;
    public static final String ACTION_STOP = "com.kiwixsaat.STOP";
    private static final String CHANNEL = "kiwix_server";

    private static Process process;
    private PowerManager.WakeLock wakeLock;

    /** Sunucu bu portta cevap veriyor mu? */
    public static boolean isPortOpen() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", PORT), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** ZIM dosyalarını arayacağımız klasörler. */
    public static List<File> searchDirs(Context ctx) {
        List<File> dirs = new ArrayList<>();
        File own = ctx.getExternalFilesDir(null);
        if (own != null) dirs.add(own);
        File root = Environment.getExternalStorageDirectory();
        dirs.add(new File(root, "Download"));
        dirs.add(new File(root, "Kiwix"));
        return dirs;
    }

    /** Okunabilen tüm .zim dosyalarını bulur. */
    public static List<File> findZims(Context ctx) {
        List<File> zims = new ArrayList<>();
        for (File dir : searchDirs(ctx)) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".zim") && f.canRead()) {
                    zims.add(f);
                }
            }
        }
        return zims;
    }

    public static File logFile(Context ctx) {
        return new File(ctx.getFilesDir(), "kiwix.log");
    }

    /** Log dosyasının son kısmını döndürür. */
    public static String readLogTail(Context ctx) {
        File f = logFile(ctx);
        if (!f.exists()) return "(log yok)";
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - 1500);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return "Log okunamadı: " + e.getMessage();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (process == null || !process.isAlive()) {
            startServer();
        }
        return START_STICKY;
    }

    private void startServer() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KiwixSaat:server");
        wakeLock.acquire();

        List<File> zims = findZims(this);
        if (zims.isEmpty()) {
            stopSelf();
            return;
        }

        File bin = new File(getApplicationInfo().nativeLibraryDir, "libkiwixserve.so");
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        cmd.add("--address=all");
        cmd.add("--port=" + PORT);
        for (File z : zims) cmd.add(z.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(getFilesDir());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile(this));
            process = pb.start();
        } catch (Exception e) {
            try (java.io.FileWriter w = new java.io.FileWriter(logFile(this), true)) {
                w.write("\nBaşlatılamadı: " + e + "\nProgram yolu: " + bin + "\n");
            } catch (Exception ignored) { }
            stopSelf();
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Kiwix sunucusu", NotificationManager.IMPORTANCE_LOW));

        Intent stop = new Intent(this, ServerService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_kiwix)
                .setContentTitle("Kiwix çalışıyor")
                .setContentText("Wikipedia çevrimdışı hazır")
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "Durdur", stopPi).build())
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
