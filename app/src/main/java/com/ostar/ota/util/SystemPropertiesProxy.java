package com.ostar.ota.util;

import android.content.Context;

import java.lang.reflect.Method;

public final class SystemPropertiesProxy {
    public static String get(Context context, String key) throws IllegalArgumentException {
        String ret = "";
        try {
            ClassLoader cl = context.getClassLoader();
            @SuppressWarnings("rawtypes")
            Class SystemProperties = cl.loadClass("android.os.SystemProperties");
            //参数类型
            @SuppressWarnings("rawtypes")
            Class[] paramTypes = new Class[1];
            paramTypes[0] = String.class;
            Method get = SystemProperties.getMethod("get", paramTypes);
            //参数
            Object[] params = new Object[1];
            params[0] = new String(key);
            ret = (String) get.invoke(SystemProperties, params);
        } catch (IllegalArgumentException iAE) {
            throw iAE;
        } catch (Exception e) {
            ret = "";
            //TODO
        }
        return ret;
    }

    public static void set(Context context, String key, String val) throws IllegalArgumentException {
        try {
            ClassLoader cl = context.getClassLoader();
            Class SystemProperties = Class.forName("android.os.SystemProperties");
            //参数类型
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = String.class;
            Method set = SystemProperties.getMethod("set", paramTypes);
            //参数
            Object[] params = new Object[2];
            params[0] = new String(key);
            params[1] = new String(val);
            set.invoke(SystemProperties, params);
        } catch (IllegalArgumentException iAE) {
            throw iAE;
        } catch (Exception e) {

        }
    }
}
