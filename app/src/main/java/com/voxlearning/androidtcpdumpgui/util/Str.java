package com.voxlearning.androidtcpdumpgui.util;

public class Str {
    static public boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    static public boolean notEmpty(CharSequence s) {
        return s != null && s.length() != 0;
    }

    static public boolean contains(String main, CharSequence sub) {
        if(main == null || sub == null)
            return false;
        return main.contains(sub);
    }

    static public CharSequence def(CharSequence s) {
        return s == null ? "" : s;
    }

    static public String def(String s) {
        return s == null ? "" : s;
    }

    static public boolean eq(CharSequence s1, CharSequence s2) {
        if(s1 == null) {
            return isEmpty(s2);
        }
        if(s2 == null) {
            return isEmpty(s1);
        }
        return s1.equals(s2);
    }

    static public boolean eqIgnoreCase(String s1, String s2) {
        if(s1 == null) {
            return isEmpty(s2);
        }
        if(s2 == null) {
            return isEmpty(s1);
        }
        return s1.equalsIgnoreCase(s2);
    }


    static public int toInt(String s) {
        return toInt(s, 0);
    }

    static public int toInt(String s, int def) {
        if(s == null || "".equals(s))
            return def;

        try {
            return Integer.parseInt(s);
        }
        catch(Exception ignored) {
            return def;
        }
    }


    static public long toLong(String s) {
        return toLong(s, 0);
    }

    static public long toLong(String s, long def) {
        if(s == null || "".equals(s))
            return def;

        try {
            return Long.parseLong(s);
        }
        catch(Exception ignored) {
            return def;
        }
    }

    static public String trim(String s) {
        return s == null ? null : s.trim();
    }
}
