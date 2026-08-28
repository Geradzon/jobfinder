package de.jonas.a56km;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Space;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ = 42;
    private TextView status, distance, speed, duration, accuracy;
    private Button start, stop;
    private RadioButton battery, precise;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!TrackingService.ACTION_UPDATE.equals(intent.getAction())) return;
            render(intent.getDoubleExtra("distance", 0), intent.getDoubleExtra("speed", 0),
                    intent.getLongExtra("duration", 0), intent.getFloatExtra("accuracy", 999));
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        restore();
    }

    private View buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(32), pad, dp(20));

        status = text("Bereit", 15, false); root.addView(status);
        distance = text("0 m", 56, true); distance.setPadding(0, dp(45), 0, 0); root.addView(distance);
        speed = text("0,0 km/h", 30, false); speed.setPadding(0, dp(10), 0, 0); root.addView(speed);
        duration = text("00:00:00", 24, false); duration.setPadding(0, dp(8), 0, 0); root.addView(duration);
        accuracy = text("GPS: —", 14, false); accuracy.setPadding(0, dp(28), 0, 0); root.addView(accuracy);

        TextView modeLabel = text("Tracking-Modus", 16, true); modeLabel.setPadding(0, dp(32), 0, dp(6)); root.addView(modeLabel);
        RadioGroup group = new RadioGroup(this);
        battery = new RadioButton(this); battery.setText("Akku · GPS etwa alle 5 Sekunden"); battery.setTextSize(16); battery.setChecked(true);
        precise = new RadioButton(this); precise.setText("Genau · GPS etwa alle 2 Sekunden"); precise.setTextSize(16);
        group.addView(battery); group.addView(precise); root.addView(group);

        Space spacer = new Space(this); root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        start = new Button(this); start.setText("TRACKING STARTEN"); start.setOnClickListener(v -> ensurePermissions()); root.addView(start, new LinearLayout.LayoutParams(-1, dp(58)));
        stop = new Button(this); stop.setText("BEENDEN"); stop.setEnabled(false); stop.setOnClickListener(v -> stopTracking());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(58)); stopLp.topMargin = dp(10); root.addView(stop, stopLp);
        return root;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(0xFF111111);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }

    private void ensurePermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}, REQ);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
        } else startTracking();
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r == REQ && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startTracking();
        else status.setText("Präziser Standort wird benötigt");
    }

    private void startTracking() {
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(TrackingService.ACTION_START); i.putExtra("precise", precise.isChecked());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText(precise.isChecked() ? "GPS läuft · Genau" : "GPS läuft · Akku");
        start.setEnabled(false); stop.setEnabled(true); battery.setEnabled(false); precise.setEnabled(false);
    }

    private void stopTracking() {
        Intent i = new Intent(this, TrackingService.class); i.setAction(TrackingService.ACTION_STOP); startService(i);
        status.setText("Beendet"); speed.setText("0,0 km/h"); start.setEnabled(true); stop.setEnabled(false); battery.setEnabled(true); precise.setEnabled(true);
    }

    private void restore() {
        android.content.SharedPreferences p = getSharedPreferences("tracking", MODE_PRIVATE);
        boolean running = p.getBoolean("running", false);
        double d = p.getFloat("distance", 0); double s = p.getFloat("speed", 0); float a = p.getFloat("accuracy", 999);
        long dur = 0; if (running) { long st = p.getLong("start", 0); if (st > 0) dur = Math.max(0, (SystemClock.elapsedRealtime()-st)/1000); }
        render(d,s,dur,a); status.setText(running ? "GPS läuft" : "Bereit"); start.setEnabled(!running); stop.setEnabled(running); battery.setEnabled(!running); precise.setEnabled(!running);
    }

    private void render(double meters, double kmh, long sec, float acc) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.GERMANY);
        distance.setText(nf.format(Math.round(meters)) + " m");
        speed.setText(String.format(Locale.GERMANY, "%.1f km/h", kmh));
        long h=sec/3600, m=(sec%3600)/60, s=sec%60; duration.setText(String.format(Locale.GERMANY, "%02d:%02d:%02d", h,m,s));
        accuracy.setText(acc < 100 ? String.format(Locale.GERMANY, "GPS: ±%.0f m", acc) : "GPS: suche Signal …");
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(TrackingService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, f);
        receiverRegistered = true; restore();
    }

    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered=false; }
        super.onStop();
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
