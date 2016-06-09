package com.voxlearning.androidtcpdumpgui.util;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RootUtils {

    static final private String TAG = "RootUtils";

    static public boolean isRootAvailable() {
        String[] paths = {
                "/system/bin/", "/system/xbin/", "/data/local/xbin/", "/data/local/bin/", "/system/sd/xbin/"
        };
        for (String path : paths) {
            if (new File(path + "su").exists()) {
                return true;
            }
        }
        return false;
    }



    static public boolean chmod(String path, int mode) {
        try {
            Class<?> libcore = Class.forName("libcore.io.Libcore");
            Field field = libcore.getDeclaredField("os");
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            Object os = field.get(field);
            Method chmod = os.getClass().getMethod("chmod", String.class, int.class);
            chmod.invoke(os, path, mode);
            return true;
        }
        catch (Exception e) {
            Log.w(TAG, "call libcore.chmod failed: " + e.getMessage() + ", try to use bin/chmod");
            try {
                int ret = Runtime.getRuntime().exec("chmod " + String.format("0%o", mode) + " \"" + path.replace("\"", "\\\"") + "\"").waitFor();
                return ret == 0;
            }
            catch (Exception ignored) { }
        }
        return false;
    }
}
