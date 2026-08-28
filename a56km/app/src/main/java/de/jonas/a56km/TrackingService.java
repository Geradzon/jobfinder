package de.jonas.a56km;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.NumberFormat;
import java.util.Locale;

public class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START = "de.jonas.a56km.START";
    public static final String ACTION_STOP = "de.jonas.a56km.STOP";
    public static final String ACTION_UPDATE = "de.jonas.a56km.UPDATE";
    private static final String CHANNEL = "tracking";
    private static final int NOTIFY_ID = 56;

    private LocationManager lm;
    private SharedPreferences prefs;
    private Location last;
    private double totalMeters = 0;
    private double filteredSpeed = 0;
    private long startElapsed;

    @Override public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        prefs = getSharedPreferences("tracking", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "GPS-Tracking", NotificationManager.IMPORTANCE_LOW);
            c.setSound(null, null); getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) { stopTracking(); return START_NOT_STICKY; }
        if (ACTION_START.equals(intent.getAction())) begin(intent.getBooleanExtra("precise", false));
        return START_STICKY;
    }

    private void begin(boolean precise) {
        totalMeters = 0; filteredSpeed = 0; last = null; startElapsed = SystemClock.elapsedRealtime();
        prefs.edit().putBoolean("running", true).putFloat("distance",0).putFloat("speed",0).putLong("start",startElapsed).apply();
        startForeground(NOTIFY_ID, notification());
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopTracking(); return; }
        long minTime = precise ? 2000L : 5000L;
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, 1f, this);
    }

    @Override public void onLocationChanged(Location loc) {
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 999f;
        if (acc > 25f) { publish(acc); return; }

        double rawSpeed = loc.hasSpeed() ? Math.max(0, loc.getSpeed()*3.6) : filteredSpeed;
        if (Build.VERSION.SDK_INT >= 26 && loc.hasSpeedAccuracy() && loc.getSpeedAccuracyMetersPerSecond() > 2.5f) rawSpeed = filteredSpeed;

        if (last != null) {
            float segment = last.distanceTo(loc);
            long dtMs = Math.max(1, loc.getElapsedRealtimeNanos()/1_000_000L - last.getElapsedRealtimeNanos()/1_000_000L);
            double segmentKmh = (segment / (dtMs/1000.0))*3.6;
            float noiseFloor = Math.max(2f, Math.min(8f, (last.getAccuracy()+acc)*0.18f));
            boolean plausible = segmentKmh <= 80 && segment <= 150;
            boolean moving = rawSpeed >= 2.5 || segment >= noiseFloor;
            if (plausible && moving) { totalMeters += segment; last = loc; }
            else if (rawSpeed >= 2.5) last = loc;
        } else last = loc;

        filteredSpeed = filteredSpeed == 0 ? rawSpeed : filteredSpeed*0.65 + rawSpeed*0.35;
        if (rawSpeed < 1) { filteredSpeed *= 0.55; if (filteredSpeed < 0.4) filteredSpeed = 0; }
        publish(acc);
    }

    private void publish(float acc) {
        long sec = Math.max(0, (SystemClock.elapsedRealtime()-startElapsed)/1000);
        prefs.edit().putFloat("distance",(float)totalMeters).putFloat("speed",(float)filteredSpeed).putFloat("accuracy",acc).apply();
        Intent u = new Intent(ACTION_UPDATE); u.setPackage(getPackageName());
        u.putExtra("distance", totalMeters); u.putExtra("speed", filteredSpeed); u.putExtra("duration", sec); u.putExtra("accuracy",acc); sendBroadcast(u);
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, notification());
    }

    private Notification notification() {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.GERMANY);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(nf.format(Math.round(totalMeters)) + " m")
                .setContentText(String.format(Locale.GERMANY,"%.1f km/h · GPS läuft", filteredSpeed))
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void stopTracking() {
        try { lm.removeUpdates(this); } catch (Exception ignored) {}
        prefs.edit().putBoolean("running",false).putFloat("speed",0).apply();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public IBinder onBind(Intent intent) { return null; }
}
