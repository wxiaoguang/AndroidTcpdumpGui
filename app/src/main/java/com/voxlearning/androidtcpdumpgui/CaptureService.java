package com.voxlearning.androidtcpdumpgui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;

public class CaptureService extends Service implements LocationListener {

    static private final String TAG = "CaptureService";

    static public final String ServiceAction_CaptureStart = "CaptureService.Action.CaptureStart";
    static public final String ServiceAction_PeriodWork = "CaptureService.Action.PeriodWork";
    static public final String ServiceAction_CaptureStop = "CaptureService.Action.CaptureStop";

    static private final String IntentExtra_AlarmRestart = "CaptureService.IntentExtra.AlarmRestart";

    private CaptureManager mCaptureManager;
    private AlarmManager mAlarmManager;
    private LocationManager mLocationManager;

    static final public int AlarmInterval_RestartService = 30 * 1000;
    static final public int DelayInterval_PeriodWork = 5 * 1000;

    private String mLastForegroundAppName = "";

    @Override
    public void onCreate() {
        mCaptureManager = new CaptureManager();
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        gpsStart();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            if (ServiceAction_CaptureStart.equals(intent.getAction())) {
                Log.d(TAG, "capture start");
                periodWork();
            } else if (ServiceAction_PeriodWork.equals(intent.getAction())) {
                Log.d(TAG, "period work");
                periodWork();
            } else if (ServiceAction_CaptureStop.equals(intent.getAction())) {
                Log.d(TAG, "capture stop");
                stopSelf();
            }

            return START_STICKY;
        }

        stopSelf();
        return START_NOT_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }




    private static final long GPS_MIN_DISTANCE_UPDATE = 10; // The minimum distance to change Updates in meters
    private static final long GPS_MIN_DURATION_UPDATE = 30 * 1000; // The minimum time between updates in milliseconds

    @Override
    public void onLocationChanged(Location location) {
        mCaptureManager.recordGps(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        mCaptureManager.recordLog("gps status changed: provider=" + provider + ", status=" + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        gpsStop();
        gpsStart();
    }

    @Override
    public void onProviderDisabled(String provider) {
        gpsStop();
        gpsStart();
    }

    public void gpsStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

            mCaptureManager.recordLog("gps start with last known location:" + LocationManager.GPS_PROVIDER);
            mCaptureManager.recordGps(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_MIN_DURATION_UPDATE, GPS_MIN_DISTANCE_UPDATE, this);

        } else if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {

            mCaptureManager.recordLog("gps start with last known location:" + LocationManager.NETWORK_PROVIDER);
            mCaptureManager.recordGps(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, GPS_MIN_DURATION_UPDATE, GPS_MIN_DISTANCE_UPDATE, this);

        }
    }

    public void gpsStop() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mCaptureManager.recordLog("gps stop");
        mLocationManager.removeUpdates(this);
    }





    static public class PeriodWorkHandler extends Handler {

        private Context mContext;

        private PeriodWorkHandler(Context context) {
            mContext = context;
        }

        @Override
        public void handleMessage(Message msg) {
            //trigger period work
            Intent intentPeriodWork = new Intent(mContext, CaptureService.class);
            intentPeriodWork.setAction(ServiceAction_PeriodWork);
            mContext.startService(intentPeriodWork);
        }

        static public void postDelayed(Context context, long delayed) {
            new PeriodWorkHandler(context).sendEmptyMessageDelayed(0, delayed);
        }
    }


    private void periodWork() {
        boolean shouldStop = !mCaptureManager.isDirRunningExisting();
        if(shouldStop) {
            Log.d(TAG, "should stop, do stop");
            stopSelf();
            return;
        }

        Intent intentRestart = new Intent(this, CaptureService.class);
        intentRestart.setAction(ServiceAction_CaptureStart);
        intentRestart.putExtra(IntentExtra_AlarmRestart, true);
        PendingIntent pendingIntentRestart = PendingIntent.getService(this.getApplicationContext(), 0, intentRestart, 0);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmInterval_RestartService, pendingIntentRestart);

        String foregroundAppName = mCaptureManager.getForegroundAppName(this);
        Log.d(TAG, "found foreground app: " + foregroundAppName);

        if(foregroundAppName == null) foregroundAppName = "";
        if(!foregroundAppName.equals(mLastForegroundAppName)) {
            mCaptureManager.recordAppName(foregroundAppName);
            mLastForegroundAppName = foregroundAppName;
        }

        PeriodWorkHandler.postDelayed(getApplicationContext(), DelayInterval_PeriodWork);
    }
}
