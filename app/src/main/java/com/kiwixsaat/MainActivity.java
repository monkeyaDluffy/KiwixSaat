package com.kiwixsaat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Simgeye dokununca: sunucuyu başlatır, hazır olunca tarayıcıda açar.
 */
public class MainActivity extends Activity {

    private static final String URL = "http://127.0.0.1:" + ServerService.PORT;

    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Yuvarlak ekranda kenarlara taşmasın diye bol iç boşluk
        int pad = (int) (getResources().getDisplayMetrics().widthPixels * 0.14);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setText("Başlatılıyor…");
        box.addView(status);

        Button open = new Button(this);
        open.setText("Wikipedia'yı aç");
        open.setOnClickListener(v -> startAndOpen());
        box.addView(open);

        Button stop = new Button(this);
        stop.setText("Sunucuyu durdur");
        stop.setOnClickListener(v -> {
            startService(new Intent(this, ServerService.class).setAction(ServerService.ACTION_STOP));
            status.setText("Sunucu durduruldu.");
        });
        box.addView(stop);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(box);
        setContentView(scroll);

        startAndOpen();
    }

    private void startAndOpen() {
        status.setText("Başlatılıyor…");
        new Thread(() -> {
            if (ServerService.isPortOpen()) {
                openBrowser();
                return;
            }
            if (ServerService.findZims(this).isEmpty()) {
                show(noZimMessage());
                return;
            }
            startForegroundService(new Intent(this, ServerService.class));
            for (int i = 0; i < 60; i++) {
                if (ServerService.isPortOpen()) {
                    openBrowser();
                    return;
                }
                sleep(500);
            }
            show("Sunucu açılmadı.\n\nSon log:\n" + ServerService.readLogTail(this));
        }).start();
    }

    private String noZimMessage() {
        String s = "ZIM dosyası bulunamadı ya da okuma izni yok.\n\n";
        if (!Environment.isExternalStorageManager()) {
            s += "Bugjaeger shell'de şunu çalıştır:\n\n"
                    + "appops set " + getPackageName() + " MANAGE_EXTERNAL_STORAGE allow\n\n";
        }
        s += "Aranan klasörler:\n";
        for (java.io.File d : ServerService.searchDirs(this)) s += d + "\n";
        return s;
    }

    private void openBrowser() {
        ui.post(() -> {
            status.setText("Hazır. Tarayıcı açılıyor…");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(URL));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
            } catch (Exception e) {
                status.setText("Tarayıcı açılamadı: " + e.getMessage());
            }
        });
    }

    private void show(String text) {
        ui.post(() -> status.setText(text));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
