/*
 **
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.ostar.ota.service;

import android.content.Context; 
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import android.util.Log;

import java.io.File;

public class RTKUpdateReceiver extends BroadcastReceiver {
    private final static String TAG = "RKUpdateReceiver";
    private static boolean isBootCompleted = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action = " + action);
        Intent serviceIntent;
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "RKUpdateReceiver recv ACTION_BOOT_COMPLETED.");
            serviceIntent = new Intent(context, RTKUpdateService.class);
            serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_REMOTE_UPDATING);
            serviceIntent.putExtra("delay", 20000);
            context.startService(serviceIntent);

            isBootCompleted = true;
        } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED) && isBootCompleted) {
            String[] path = {intent.getData().getPath()};
            serviceIntent = new Intent(context, RTKUpdateService.class);
            serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 5000);
            context.startService(serviceIntent);
            Log.d(TAG, "media is mounted to '" + path[0] + "'. To check local update.");

        } else if (action.equals("android.hardware.usb.action.USB_STATE")&& isBootCompleted) {
            Bundle extras = intent.getExtras();
            boolean connected = extras.getBoolean("connected");
            boolean configured = extras.getBoolean("configured");
            boolean mtpEnabled = extras.getBoolean("mtp");
            boolean ptpEnabled = extras.getBoolean("ptp");
            // Start MTP service if USB is connected and either the MTP or PTP function is enabled
            if ((!connected) && mtpEnabled && (!configured)) {
                serviceIntent = new Intent(context, RTKUpdateService.class);
                serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
                serviceIntent.putExtra("delay", 5000);
                context.startService(serviceIntent);
            }
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)&& isBootCompleted) {
            ConnectivityManager cmanger = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cmanger.getActiveNetworkInfo();
            if (netInfo != null) {
                Log.d(TAG, " Connection is change.");
                if ((netInfo.getType() == ConnectivityManager.TYPE_WIFI || netInfo.getType() == ConnectivityManager.TYPE_ETHERNET)
                        && netInfo.isConnected()) {
                    serviceIntent = new Intent(context, RTKUpdateService.class);
                    serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_REMOTE_UPDATING);
                    serviceIntent.putExtra("delay", 5000);
                    context.startService(serviceIntent);
                }
            }
        }else if (action.equals("android.del.otapac")) {
            Log.e(TAG,"--------------DEL PAC-----------------");
        }
    }

    private void deletePackage(File file) {
        if (file.exists()) {
            boolean isDelete = file.delete();
            if (isDelete) {
                Log.e(TAG,"--------------DEL PAC IS SUCCESS-----------------");
            } else {
                Log.e(TAG,"--------------DEL PAC IS FAILED-----------------");
            }
        }
    }
}


