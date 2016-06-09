package com.voxlearning.androidtcpdumpgui;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.voxlearning.androidtcpdumpgui.util.Io;
import com.voxlearning.androidtcpdumpgui.util.Str;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation"})
public class CaptureManager {

    static private final String TAG = "CaptureManager";

    private int execSu(String cmd) {
        return execSu(cmd, null);
    }

    private int execSu(String cmd, List<String> result) {
        return exec(new String[]{"su", "-c", cmd}, result);
    }

    private int exec(String []cmd, List<String> result) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            InputStreamReader osRes = new InputStreamReader(process.getInputStream());
            BufferedReader reader = new BufferedReader(osRes);

            String line = reader.readLine();
            while(line != null) {
                if(result != null) {
                    result.add(line);
                }
                line = reader.readLine();
            }

            return process.waitFor();
        }
        catch (Exception ignored) {

        }
        return -1;
    }

    private int exec(String []cmd) {
        return exec(cmd, null);
    }

    private String convertPathForShell(String s) {
        //in shell, we won't see "/storage/emulated/0/" which is mounted by fuse in app
        String src = System.getenv("EMULATED_STORAGE_SOURCE");
        String tgt = System.getenv("EMULATED_STORAGE_TARGET");

        if(s != null && !Str.isEmpty(src) && !Str.isEmpty(tgt)) {
            if(s.startsWith(tgt)) {
                s = src + s.substring(tgt.length());
            }
        }
        return s;
    }


    private String singleQuoteForShell(String s) {
        s = s.replace("'", "'\"'\"'");
        return "'" + s + "'";
    }


    public boolean prepare(Context context) {

        new File(CaptureFilePath.BaseDir()).mkdirs();
        new File(CaptureFilePath.OutputDir("")).mkdirs();

        if(Build.CPU_ABI.startsWith("arm")) {
            if(!new File(CaptureFilePath.AppFilePath(context, "tcpdump")).exists())
                Io.extractAssetExecutable(context, "bin/arm/tcpdump", "tcpdump");

            if(!new File(CaptureFilePath.AppFilePath(context, "busybox")).exists())
                Io.extractAssetExecutable(context, "bin/arm/busybox", "busybox");
        }
        else {
            Log.i(TAG, "under non-arm CPU, please prepare tcpdump & busybox by yourself");
        }

        String fn = CaptureFilePath.ConfigFilePath(CaptureFilePath.ConfigFileName_TcpdumpFilters);
        if(!new File(fn).exists()) {
            Io.extractAssetFile(context, CaptureFilePath.ConfigFileName_TcpdumpFilters, fn);
        }

        return true;
    }


    public boolean isDirRunningExisting() {
        return new File(CaptureFilePath.OutputDirRunning()).exists();
    }

    public void start(Context context, String intf, String expr) {

        String busybox = CaptureFilePath.ProgBusybox(context);
        String tcpdump = CaptureFilePath.ProgTcpdump(context);

        if(isDirRunningExisting()) {
            stop(context);
            return;
        }


        killTcpdump(context);

        File dirRunning = new File(CaptureFilePath.OutputDirRunning());
        dirRunning.mkdirs();

        if(!dirRunning.exists()) {
            Log.e(TAG, "can not mkdir " + dirRunning.getPath());
            Toast.makeText(context, "Can not write to external storage. Broken sdcard or memory?", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            recordStart(context);

            String fnPackets = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Packets);
            String fnTcpdumpOut = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_TcpdumpOut);

            fnPackets = convertPathForShell(fnPackets);
            fnTcpdumpOut = convertPathForShell(fnTcpdumpOut);

            String quotedExpr = singleQuoteForShell(expr);

            String cmdTcpdump = String.format("%s -i %s -s 0 -w %s %s", tcpdump, intf, fnPackets, quotedExpr);
            String cmd = String.format("%s nohup %s >%s 2>&1 &", busybox, cmdTcpdump, fnTcpdumpOut);
            recordLog(cmd);

            List<String> execOutput = new ArrayList<>();
            int ret = execSu(cmd, execOutput);
            if (ret != 0) {
                throw new RuntimeException("can not exec su");
            }

            Log.i(TAG, "exec output count = " + execOutput.size());
            for (String s:execOutput) {
                Log.i(TAG, "exec output: " + s);
            }
        }
        catch(Exception e) {
            Log.e(TAG, "can not start tcpdump", e);
            Io.deleteFollowSymlink(dirRunning);
            stop(context);
        }
    }

    public void killTcpdump(Context context) {
        String busybox = CaptureFilePath.ProgBusybox(context);
        int ret = execSu(busybox + " killall tcpdump", null);

        if (ret < 0) {
            Log.e(TAG, "can not exec killall");
        }
    }


    public String stop(Context context) {

        killTcpdump(context);

        if(isDirRunningExisting()) {
            recordStop();

            String now = android.text.format.DateFormat.format("yyyy-MM-dd_HH-mm-ssz", new java.util.Date()).toString();
            File dirRunning = new File(CaptureFilePath.OutputDirRunning());
            if(dirRunning.renameTo(new File(CaptureFilePath.OutputDir(now)))) {
                return now;
            } else {
                Io.deleteFollowSymlink(dirRunning);
            }
        }

        return null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean needPermissionForBlocking(Context context){
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow("android:get_usage_stats", applicationInfo.uid, applicationInfo.packageName);
            return  (mode != AppOpsManager.MODE_ALLOWED);
        } catch (Throwable e) {
            return true;
        }
    }

    public String getForegroundAppName(Context context) {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

            if(needPermissionForBlocking(context)) {
                Toast.makeText(context, "Turn on Settings->Security->[Apps with usage access]", Toast.LENGTH_SHORT).show();
            }

            // intentionally using string value as Context.USAGE_STATS_SERVICE was
            // strangely only added in API 22 (LOLLIPOP_MR1)
            @SuppressWarnings("WrongConstant")
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService("usagestats");
            long now = System.currentTimeMillis();
            List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 1000, now);
            if (usageStatsList != null && usageStatsList.size() > 0) {
                long time = 0;
                String name = "";
                for(UsageStats usageStats : usageStatsList) {
                    if(usageStats.getLastTimeUsed() > time) {
                        time = usageStats.getLastTimeUsed();
                        name = usageStats.getPackageName();
                    }
                }
                return name;
            }
        } else {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            try {
                ActivityManager.RunningTaskInfo foregroundTaskInfo = am.getRunningTasks(1).get(0);
                return foregroundTaskInfo.topActivity.getPackageName();
            } catch (Exception e) {
                Log.e(TAG, "can not get foreground app: " + e.getMessage(), e);
            }
        }
        return "";
    }

    static public String nowLogString() {
        return android.text.format.DateFormat.format("[yyyy-MM-dd HH:mm:ssz]", new java.util.Date()).toString();
    }

    public void recordAppName(String appName) {
        String fn = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Apps);
        Io.writeFileAppend(fn, nowLogString() + " " + appName + "\n");
        recordLog("app: " + appName);
    }

    private void recordStart(Context context) {
        String fn = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Start);
        Io.writeFile(fn, "" + System.currentTimeMillis());

        StringBuilder sb = new StringBuilder();
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("; ")
                .append("MODEL=").append(Build.MODEL).append("; ")
                .append("SERIAL=").append(Build.SERIAL).append("\n")
        ;
        sb.append("FINGERPRINT=").append(Build.FINGERPRINT).append("\n");
        sb.append("RadioVersion=").append(Build.getRadioVersion()).append("\n");

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String tmDevice = "", tmSerial = "", androidId = "";
        try {
            tmDevice = tm.getDeviceId();
        }catch (Exception ignored){}
        try {
            tmSerial = tm.getSimSerialNumber();
        }catch (Exception ignored){}
        try{
            androidId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        }catch (Exception ignored){}

        sb.append("DeviceId=").append(tmDevice).append("; ")
                .append("AndroidId=").append(androidId).append("; ")
                .append("SimSerialNumber=").append(tmSerial).append("\n");


        NetworkInfo ni = null;
        try {
            ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            ni = cm.getActiveNetworkInfo();
        }
        catch (Exception ignored) { }
        sb.append("NetworkInfo=").append(ni == null ? "null" : ni.toString()).append("\n");

        recordLog("start\n" + sb.toString());
    }

    private void recordStop() {
        String fn = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Stop);
        Io.writeFile(fn, "" + System.currentTimeMillis());
        recordLog("stop");
    }

    public void recordLog(String s) {
        String fn = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Logs);
        Io.writeFileAppend(fn, nowLogString() + " " + s + "\n");
        Log.d(TAG, "recordLog: " + s);
    }

    public void recordGps(Location location) {
        String fn = CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Gps);
        String s = location == null ? "(unknown-location)" : location.toString();

        Io.writeFileAppend(fn, nowLogString() + " " + s + "\n");
        Log.d(TAG, "recordGps: " + s);
    }

    static public class Stats {
        public long mStartTime;
        public long mPacketsSize;
    }

    public Stats getStats() {
        Stats stats = new Stats();
        String s = Io.readAll(CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Start));
        stats.mStartTime = Str.toLong(Str.trim(s));

        File f = new File(CaptureFilePath.OutputDirRunningFilePath(CaptureFilePath.FileName_Packets));
        if (f.exists() && f.isFile()) {
            stats.mPacketsSize = f.length();
        }
        return stats;
    }



    static public class Interface {
        public int mIndex;
        public String mName = "";
        public boolean mStateUp;
        public String mIpv4Net = "";
    }

    public List<Interface> getInterfaces(Context context) {
        /*
        2: ifb0: <BROADCAST,NOARP> mtu 1500 qdisc noop state DOWN qlen 32
            link/ether 02:7a:a0:1b:1c:a8 brd ff:ff:ff:ff:ff:ff
        5: eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast qlen 1000
            link/ether 08:00:27:22:8a:9c brd ff:ff:ff:ff:ff:ff
            inet 192.168.1.138/22 brd 192.168.3.255 scope global eth1
            inet6 fe80::a00:27ff:fe22:8a9c/64 scope link
               valid_lft forever preferred_lft forever
         */
        String busybox = CaptureFilePath.ProgBusybox(context);

        List<Interface> interfaces = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        exec(new String[]{"su", "-c", busybox + " ip address"}, lines);
        Interface intf = null;
        for(String line : lines) {
            line = line.trim();
            if(line.length() == 0)
                continue;

            int p = line.indexOf(": ");
            if(Character.isDigit(line.charAt(0)) && p != -1) {
                String []a = line.split(": ", 3);
                if(a.length == 3) {
                    intf = new Interface();
                    intf.mIndex = Str.toInt(a[0]);
                    intf.mName = a[1];

                    String []fields = a[2].split(" ");
                    if(fields[0].startsWith("<") && fields[0].endsWith(">")) {
                        String statesStr = fields[0];
                        String []states = statesStr.substring(1, statesStr.length() - 1).split(",");
                        for(String state : states) {
                            if(state.equals("UP")) {
                                intf.mStateUp = true;
                            }
                        }
                    }

                    interfaces.add(intf);
                    continue;
                }
            }

            if(intf == null)
                continue;

            if(line.startsWith("inet ")) {
                String []a = line.split(" ");
                if(a.length >= 2) {
                    intf.mIpv4Net = a[1];
                }
            }
        }

        return interfaces;
    }


    public LinkedHashMap<String, String> loadTcpdumpFilters() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            fis = new FileInputStream(CaptureFilePath.ConfigFilePath(CaptureFilePath.ConfigFileName_TcpdumpFilters));
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);
            String line;
            while((line = br.readLine()) != null) {
                line = line.trim();
                if(line.startsWith("#")) {
                    continue;
                }

                String []a = line.split("=", 2);
                if(a.length == 2) {
                    m.put(a[0].trim(), a[1].trim());
                }
                else {
                    m.put(line, line);
                }

            }
        }
        catch (Exception ignored) {

        }
        finally {
            Io.close(br);
            Io.close(isr);
            Io.close(fis);

        }
        return m;
    }
}

