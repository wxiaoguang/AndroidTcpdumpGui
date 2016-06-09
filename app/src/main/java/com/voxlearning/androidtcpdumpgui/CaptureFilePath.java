package com.voxlearning.androidtcpdumpgui;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/*
IF YOU CAN NOT FIND THE FOLDER IN ANDROID FILE TRANSFER (MTP):

This is apparently a known bug in Android which is not even acknowledged by Google since Oct 2012
— depending on the method of creating files on the Android device, these files may remain invisible
when accessing the device using MTP, until the device is rebooted.

Known workarounds include:

    Use USB storage mode instead of MTP, if it is supported by the phone. This is apparently not
    an option for LG Optimus L5 (e610), because this phone has unified internal storage
    (file storage is in /data/media on the same ext4 filesystem as /data), which cannot be
    exported as an USB storage device.

    Clear data of the “Media Storage” app, then use the SDrescan app to rebuild the media database.

    Share files over the network using third-party apps such as AirDroid or one of Samba server apps
     (in the latter case you will need to have root to make the server reachable from most clients,
     including Windows).

    (Clean Media Storage cache) Switch USB Connection from MTP to Charging Only and then switch back


 */
public class CaptureFilePath {

    static public final String BaseDirName = "AndroidTcpdump";

    static public final String FileName_Start = "start.txt";
    static public final String FileName_Stop = "stop.txt";
    static public final String FileName_Comment = "comment.txt";
    static public final String FileName_Apps = "apps.txt";
    static public final String FileName_Logs = "logs.txt";
    static public final String FileName_Gps = "gps.txt";
    static public final String FileName_TcpdumpOut = "tcpdump.out.txt";
    static public final String FileName_Packets = "packets.pcap";

    static public final String ConfigFileName_TcpdumpFilters = "tcpdump_filters.txt";

    static public String BaseDir() {
        return Environment.getExternalStorageDirectory().toString() + "/" + BaseDirName;
    }

    static public String OutputDir(String name) {
        return BaseDir() + "/Output/" + name;
    }

    static public String OutputDirRunning() {
        return OutputDir("running");
    }

    static public String OutputDirRunningFilePath(String fn) {
        return OutputDirRunning() + "/" + fn;
    }

    static public String AppFilesDir(Context context) {
        try {
            return context.getFilesDir().getCanonicalPath();
        }
        catch (Exception ignored){}
        return "";
    }

    static public String AppFilePath(Context context, String fn) {
        return AppFilesDir(context) + "/" + fn;
    }


    static public String ProgTcpdump(Context context) {
        String s = AppFilePath(context, "tcpdump");
        if(!new File(s).exists())
            s = "tcpdump";
        return s;
    }

    static public String ProgBusybox(Context context) {
        String s = AppFilePath(context, "busybox");
        if(!new File(s).exists())
            s = "busybox";
        return s;
    }

    static public String ConfigFilePath(String name) {
        return BaseDir() + "/" + ConfigFileName_TcpdumpFilters;
    }

}
