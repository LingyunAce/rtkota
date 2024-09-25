package com.ostar.ota.service;

import android.content.Context;
import android.os.PowerManager;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import java.net.MalformedURLException;
import java.text.DecimalFormat;

public class SystemUpdateManager {

	private static final String TAG = "ota";

	UpdateEngine mUpdateEngine;

	public SystemUpdateManager(Context context) throws MalformedURLException {
		mUpdateEngine = new UpdateEngine();
	}

	UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {
		@Override
		public void onStatusUpdate(int status, float percent) {

			Log.d(TAG, "onStatusUpdate  status: " + status);

			switch (status) {
				case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT:
					//rebootNow();
					break;
				case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
					//mProgressBar.setProgress((int) (percent * 100));
					DecimalFormat df = new DecimalFormat("#");
					String progress = df.format(percent * 100);

					Log.d(TAG, "update progress: " + progress);

					break;
				default:
					// noop
			}

		}

		@Override
		public void onPayloadApplicationComplete(int errorCode) {
			Log.d(TAG, "onPayloadApplicationComplete errorCode=" + errorCode);

			if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
				Log.d(TAG, "UPDATE SUCCESS!");
			}
		}
	};


	public void startUpdateSystem(UpdateParser.ParsedUpdate parsedUpdate, UpdateEngineCallback callback) {
        if (callback != null) {
            mUpdateEngine.bind(callback);
        } else {
            mUpdateEngine.bind(mUpdateEngineCallback);
        }		

		mUpdateEngine.applyPayload(
				parsedUpdate.mUrl, parsedUpdate.mOffset, parsedUpdate.mSize, parsedUpdate.mProps);
	}

	/**
	 * Reboot the system.
	 */
	public void rebootNow(Context context) {
		Log.e(TAG, "rebootNow");

		PowerManager pManager=(PowerManager) context.getSystemService(Context.POWER_SERVICE);
		pManager.reboot("reboot-ab-update");
	}
}