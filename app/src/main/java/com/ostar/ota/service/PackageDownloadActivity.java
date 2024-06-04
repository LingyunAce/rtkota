package com.ostar.ota.service;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.HttpClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
//import android.support.v4.app.NotificationCompat;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;

import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.system.Os;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.ostar.ota.R;
import com.ostar.ota.util.FTPRequestInfo;

public class PackageDownloadActivity extends Activity {
	private String TAG = "ABotaTest";
	private Context mContext;
	private static PowerManager.WakeLock mWakeLock;
	private String WAKELOCK_KEY = "myDownload";
	private HttpClient mHttpClient;
	private ProgressBar mProgressBar;
	private HTTPdownloadHandler mHttpDownloadHandler;
	private Button mBtnControl;
	private Button mBtnCancel;
	private TextView mRemainTimeTV;
	private TextView mDownloadRateTV;
	private TextView mCompletedTV;
	private int mState = STATE_IDLE;
	//private TextView mTxtState;
	private ResolveInfo homeInfo;
	private NotificationManager mNotifyManager;
	private Notification mNotify;
	private NotificationChannel mChannel = null;
	private String ChannelId="rk_update";
	private String ChannelName="rk_update_channel";
	private String ChannelDescription = "rk update channel";
	private int notification_id = 20140825;
	private HTTPFileDownloadTask mHttpTask;
	private FTPFileDownloadTask mFtpTask;
	private URI mHttpUri;
	private FTPRequestInfo mFTPRequest;
	private int mDownloadProtocol = 0;
	private String mFileName;
	private RTKUpdateService.LocalBinder mBinder;
	private volatile boolean mIsCancelDownload = false;
	
	private static final int DOWNLOAD_THREAD_COUNT = 1;
	public static final int STATE_IDLE = 0;
	public static final int STATE_STARTING = 1; //not used
	public static final int STATE_STARTED = 2;
	public static final int STATE_STOPING = 3; //not used
	public static final int STATE_STOPED = 4;
	public static final int STATE_ERROR = 5; //not used
	
	public static final int DOWNLOAD_PROTOCOL_HTTP = 0;
	public static final int DOWNLOAD_PROTOCOL_FTP = 1;

	private static final int MSG_SHOW_APP_PROGRESS = 1001;
	private static final int MSG_DISMISS_APP_PROGRESS = 1002;
	private static final int MSG_UPDATE_APP_PROGRESS = 1003;

	private static final int MSG_SHOW_SYS_PROGRESS = 2001;
	private static final int MSG_DISMISS_SYS_PROGRESS = 2002;
	private static final int MSG_UPDATE_SYS_PROGRESS = 2003;

	private static final int MSG_APP_UPDATE_SUCCESS = 3001;
	private static final int MSG_APP_UPDATE_FAIL = 3002;
	private static final int MSG_SYS_UPDATE_SUCCESS = 3003;
	private static final int MSG_SYS_UPDATE_FAIL = 3004;

	private ProgressDialog mUpdateAppProgressDialog;
	private ProgressDialog mUpdateSysProgressDialog;

	private AlertDialog mDialogSysUpdateSuccess, mDialogSysUpdateFail;

	private UpdateEngineCallback mUpdateEngineCallback;
	private Handler mMainHandler;
	private PowerManager mPowerManager = null;
	private PowerManager.WakeLock otaUpdateWakeLock = null;

	private static volatile boolean otaUpdating = false;

	private ServiceConnection mConnection = new ServiceConnection() { 
        public void onServiceConnected(ComponentName className, IBinder service) { 
        	mBinder = (RTKUpdateService.LocalBinder)service;
        	mBinder.LockWorkHandler();
        } 

        public void onServiceDisconnected(ComponentName className) { 
        	mBinder = null;
        }     
    }; 
	
	@SuppressLint("InvalidWakeLockTag")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mContext = this;
        mHttpUri = null;
        Intent intent = getIntent();
        String uriStr = intent.getStringExtra("uri");
        if(uriStr == null) {
        	return;
        }
        
        setContentView(R.layout.package_download);
        setFinishOnTouchOutside(false);

		mPowerManager = mContext.getSystemService(PowerManager.class);
		otaUpdateWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "otaUpdateWakeLock");
        	        
        if(uriStr.startsWith("ftp://")) {
        	mDownloadProtocol = DOWNLOAD_PROTOCOL_FTP;
        	mFTPRequest = parseFtpUri(uriStr);
        }else {
        	mDownloadProtocol = DOWNLOAD_PROTOCOL_HTTP;
        	try {
				mHttpUri = new URI(uriStr);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
        }
        
        mContext.bindService(new Intent(mContext, RTKUpdateService.class), mConnection, Context.BIND_AUTO_CREATE);
		
        mFileName = intent.getStringExtra("OtaPackageName");
        //SystemProperties.set("persist.sf.ota.packagename",mFileName);
        //not finish activity
        PackageManager pm = getPackageManager();
        homeInfo = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        
        mNotifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.download_notify);
        views.setProgressBar(R.id.pb_download, 100, 0, false);
        Intent notificationIntent = new Intent(this, PackageDownloadActivity.class); 
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContent(views)
                .setContentIntent(pIntent)
                .setSmallIcon(android.R.drawable.ic_menu_more)
                .setChannelId(ChannelId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              int importance = NotificationManager.IMPORTANCE_LOW;
              if (mChannel == null) {
                  mChannel = new NotificationChannel(ChannelId, ChannelName, importance);
                  mChannel.setDescription(ChannelDescription);
                  mNotifyManager.createNotificationChannel(mChannel);
              }
          }
        mNotify=mBuilder.build();


    	
    	PowerManager powerManager = (PowerManager) this.getSystemService(this.POWER_SERVICE);
    	mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);
        mProgressBar = (ProgressBar)findViewById(R.id.progress_horizontal);
        mBtnControl = (Button)findViewById(R.id.btn_control);
        mBtnCancel = (Button)findViewById(R.id.button_cancel);
        mRemainTimeTV = (TextView)findViewById(R.id.download_info_remaining);
        mDownloadRateTV = (TextView)findViewById(R.id.download_info_rate);
        mCompletedTV = (TextView)findViewById(R.id.progress_completed);
        //mTxtState = (TextView)findViewById(R.id.txt_state);
        
        //mTxtState.setText("");       
        mBtnControl.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(mState == STATE_IDLE || mState == STATE_STOPED) {
					//try to start
					if(mDownloadProtocol == DOWNLOAD_PROTOCOL_HTTP){
						mHttpTask = new HTTPFileDownloadTask(mHttpClient, mHttpUri, RTKUpdateService.FLASH_ROOT, mFileName, DOWNLOAD_THREAD_COUNT);
						mHttpTask.setProgressHandler(mHttpDownloadHandler);
						mHttpTask.start();
					}else {
						mFtpTask = new FTPFileDownloadTask(mFTPRequest, RTKUpdateService.FLASH_ROOT, mFileName);
						mFtpTask.setProgressHandler(mHttpDownloadHandler);
						mFtpTask.start();
					}
					
					mBtnControl.setText(getString(R.string.starting));
					mBtnControl.setClickable(false);
					mBtnControl.setFocusable(false);
					mBtnCancel.setClickable(false);
					mBtnCancel.setFocusable(false);
				}else if(mState == STATE_STARTED) {
					//try to stop
					if(mDownloadProtocol == DOWNLOAD_PROTOCOL_HTTP){
						mHttpTask.stopDownload();
					}else {
						mFtpTask.stopDownload();
					}
					
					mBtnControl.setText(getString(R.string.stoping));
					mBtnControl.setClickable(false);
					mBtnControl.setFocusable(false);
					mBtnCancel.setClickable(false);
					mBtnCancel.setFocusable(false);
				}
			}
		});
        
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(mState == STATE_IDLE || mState == STATE_STOPED) {
					finish();
				}else {
					if(mDownloadProtocol == DOWNLOAD_PROTOCOL_HTTP){
						if(mHttpTask != null) {
							mHttpTask.stopDownload();
							mIsCancelDownload = true;
						}else {
							finish();
						}
					}else {
						if(mFtpTask != null) {
							mFtpTask.stopDownload();
							mIsCancelDownload = true;
						}else {
							finish();
						}
					}
				}
			}
		});
        
        mProgressBar.setIndeterminate(false);
        mProgressBar.setProgress(0);
        mHttpDownloadHandler = new HTTPdownloadHandler();
        mHttpClient = CustomerHttpClient.getHttpClient();
		
        //try to start
        if(mDownloadProtocol == DOWNLOAD_PROTOCOL_HTTP){
			mHttpTask = new HTTPFileDownloadTask(mHttpClient, mHttpUri, RTKUpdateService.FLASH_ROOT, mFileName, DOWNLOAD_THREAD_COUNT);
			mHttpTask.setProgressHandler(mHttpDownloadHandler);
			mHttpTask.start();
		}else {
			mFtpTask = new FTPFileDownloadTask(mFTPRequest, RTKUpdateService.FLASH_ROOT, mFileName);
			mFtpTask.setProgressHandler(mHttpDownloadHandler);
			mFtpTask.start();
		}
		mBtnControl.setText(getString(R.string.starting));
		mBtnControl.setClickable(false);
		mBtnControl.setFocusable(false);

		mUpdateAppProgressDialog = new ProgressDialog(getApplicationContext());
		mUpdateAppProgressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		mUpdateAppProgressDialog.setIndeterminate(false);
		mUpdateAppProgressDialog.setCanceledOnTouchOutside(true);
		mUpdateAppProgressDialog.setCancelable(true);
		mUpdateAppProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mUpdateAppProgressDialog.setMax(100);
		//mUpdateAppProgressDialog.setMessage(getResources().getString(R.string.upgrade_title));

		mUpdateSysProgressDialog = new ProgressDialog(getApplicationContext());
		mUpdateSysProgressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		mUpdateSysProgressDialog.setIndeterminate(false);
		mUpdateAppProgressDialog.setCanceledOnTouchOutside(true);
		mUpdateSysProgressDialog.setCancelable(true);
		mUpdateSysProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mUpdateSysProgressDialog.setMessage(getResources().getString(R.string.upgrade_sys_title));
		mUpdateSysProgressDialog.setMax(100);

		mDialogSysUpdateSuccess = new AlertDialog.Builder(getApplicationContext())
				.setTitle(R.string.upgrade_sys_title)
				.setMessage(R.string.upgrade_sys_success_and_need_reboot)
				.setPositiveButton(R.string.confirm,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int i) {
								dialog.dismiss();
							}
						}).create();
		mDialogSysUpdateSuccess.setCanceledOnTouchOutside(false);
		mDialogSysUpdateSuccess.setCancelable(false);
		mDialogSysUpdateSuccess.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);


		mDialogSysUpdateFail = new AlertDialog.Builder(getApplicationContext())
				.setTitle(R.string.upgrade_sys_title)
				.setPositiveButton(R.string.confirm,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int i) {
								dialog.dismiss();
							}
						}).create();
		mDialogSysUpdateFail.setCanceledOnTouchOutside(false);
		mDialogSysUpdateFail.setCancelable(false);
		mDialogSysUpdateFail.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

		mMainHandler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
					case MSG_SHOW_APP_PROGRESS:
						if (mUpdateAppProgressDialog != null && mUpdateAppProgressDialog.isShowing()) {
							mUpdateAppProgressDialog.dismiss();
						}

						mUpdateAppProgressDialog.setMessage((String) msg.obj);
						mUpdateAppProgressDialog.show();
						mUpdateAppProgressDialog.setProgress(0);
						break;
					case MSG_DISMISS_APP_PROGRESS:
						if (mUpdateAppProgressDialog != null && mUpdateAppProgressDialog.isShowing()) {
							mUpdateAppProgressDialog.dismiss();
						}
						break;
					case MSG_UPDATE_APP_PROGRESS:
						if (mUpdateAppProgressDialog != null) {
                            /*
                            if (!mUpdateAppProgressDialog.isShowing()) {
                                mUpdateAppProgressDialog.show();
                            }
                            */
							if (mUpdateAppProgressDialog.isShowing()) {
								mUpdateAppProgressDialog.setProgress(msg.arg1);
							}
						}
						break;
					case MSG_SHOW_SYS_PROGRESS:
						if (mUpdateSysProgressDialog != null && mUpdateSysProgressDialog.isShowing()) {
							mUpdateSysProgressDialog.dismiss();
						}
						mUpdateSysProgressDialog.setMessage(getResources().getString(R.string.upgrade_sys_title));
						mUpdateSysProgressDialog.show();
						mUpdateSysProgressDialog.setProgress(0);
						break;
					case MSG_DISMISS_SYS_PROGRESS:
						if (mUpdateSysProgressDialog != null && mUpdateSysProgressDialog.isShowing()) {
							mUpdateSysProgressDialog.dismiss();
						}
						break;
					case MSG_UPDATE_SYS_PROGRESS:
						if (mUpdateSysProgressDialog != null) {
                            /*
                            if (!mUpdateSysProgressDialog.isShowing()) {
                                mUpdateSysProgressDialog.show();
                            }
                            */
							if (mUpdateSysProgressDialog.isShowing()) {
								mUpdateSysProgressDialog.setProgress(msg.arg1);
							}
						}
						break;
					case MSG_APP_UPDATE_SUCCESS:
						showUpgradeSuccess();
						break;
					case MSG_APP_UPDATE_FAIL:
						showUpgradeFailed();
						break;
					case MSG_SYS_UPDATE_SUCCESS:
						showInstallSystemSuccess();
						break;
					case MSG_SYS_UPDATE_FAIL:
						showInstallSystemFail((String) msg.obj);
						break;
				}
				super.handleMessage(msg);
			}
		};

		// callback of ota update_engine
		mUpdateEngineCallback = new UpdateEngineCallback() {
			@Override
			public void onStatusUpdate(int status, float percent) {
				Log.d(TAG, "UpdateEngineCallback onStatusUpdate  status=" + status);

				switch (status) {
					case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT:
						Log.d(TAG, "UpdateStatusConstants UPDATED_NEED_REBOOT");
						break;
					case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
						//升级进度回调
						if (mUpdateSysProgressDialog != null) {
							if (mUpdateSysProgressDialog.getProgress() >= 0
									&& mUpdateSysProgressDialog.getProgress() != ((int) (percent * 100))) {
								Message msgUpdate = new Message();
								msgUpdate.what = MSG_UPDATE_SYS_PROGRESS;
								msgUpdate.arg1 = ((int) (percent * 100));
								mMainHandler.sendMessage(msgUpdate);
							}
						}
						break;
					default:
						break;
				}

			}

			@Override
			public void onPayloadApplicationComplete(int errorCode) {
				//升级结果回调
				Log.d(TAG, "UpdateEngineCallback onPayloadApplicationComplete errorCode=" + errorCode);

				Message msgDismiss = new Message();
				msgDismiss.what = MSG_DISMISS_SYS_PROGRESS;
				mMainHandler.sendMessage(msgDismiss);

				otaUpdating = false;

				if (otaUpdateWakeLock.isHeld()) {
					otaUpdateWakeLock.release();
				}

				if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
					Log.d(TAG, "UpdateEngineCallback UPDATE SUCCESS!");

					Message msg = new Message();
					msg.what = MSG_SYS_UPDATE_SUCCESS;
					mMainHandler.sendMessage(msg);
				} else {
					String reason = "";
					if (errorCode == UpdateEngine.ErrorCodeConstants.NOT_ENOUGH_SPACE) {
						reason = getResources().getString(R.string.upgrade_sys_error_reason_not_enough_space);
					} else if (errorCode == UpdateEngine.ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR) {
						reason = getResources().getString(R.string.upgrade_sys_error_reason_timestamp_error);
					} else if (errorCode == UpdateEngine.ErrorCodeConstants.PAYLOAD_SIZE_MISMATCH_ERROR) {
						reason = getResources().getString(R.string.upgrade_sys_error_reason_size_mismatch_error);
					} else if (errorCode == UpdateEngine.ErrorCodeConstants.DOWNLOAD_PAYLOAD_VERIFICATION_ERROR) {
						reason = getResources().getString(R.string.upgrade_sys_error_reason_verification_error);
					} else {
						reason = getResources().getString(R.string.upgrade_sys_error_reason_other);
					}
					reason += (getResources().getString(R.string.upgrade_sys_error_reason_suffix) + errorCode);

					Log.d(TAG, "UpdateEngineCallback UPDATE FAIL! reason=" + reason);

					Message msg = new Message();
					msg.what = MSG_SYS_UPDATE_FAIL;
					msg.obj = reason;
					mMainHandler.sendMessage(msg);
				}
			}
		};
		
    }
    
    private class HTTPdownloadHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			int whatMassage = msg.what;
			switch(whatMassage) {
			case HTTPFileDownloadTask.PROGRESS_UPDATE : {
					Bundle b = msg.getData();
					long receivedCount = b.getLong("ReceivedCount", 0);
					long contentLength = b.getLong("ContentLength", 0);
					long receivedPerSecond = b.getLong("ReceivedPerSecond", 0);
					int percent = (int)(receivedCount * 100 / contentLength);
					Log.d(TAG, "percent = " + percent);
					
					setDownloadInfoViews(contentLength, receivedCount, receivedPerSecond);
					mProgressBar.setProgress(percent);
					setNotificationProgress(percent);
					showNotification();
				}
				break;
			case HTTPFileDownloadTask.PROGRESS_DOWNLOAD_COMPLETE : {
					//mTxtState.setText("State: download complete");
					mState = STATE_IDLE;
					mBtnControl.setText(getString(R.string.start));
					mBtnControl.setClickable(true);
					mBtnControl.setFocusable(true);
					mBtnCancel.setClickable(true);
					mBtnCancel.setFocusable(true);
					Log.d(TAG, "-----------------download complete---------------");
					/*Intent intent = new Intent();
		            intent.setClass(mContext, UpdateAndRebootActivity.class);
		            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		            intent.putExtra(RKUpdateService.EXTRA_IMAGE_PATH, RKUpdateService.FLASH_ROOT + "/" + mFileName);
		            startActivity(intent);*/
					installSystem(new File(RTKUpdateService.FLASH_ROOT + "/" + mFileName));

		            finish();
				}
				break;
			case HTTPFileDownloadTask.PROGRESS_START_COMPLETE : {
					//mTxtState.setText("");
					mState = STATE_STARTED;
					mBtnControl.setText(getString(R.string.pause));
					mBtnControl.setClickable(true);
					mBtnControl.setFocusable(true);
					mBtnCancel.setClickable(true);
					mBtnCancel.setFocusable(true);
					setNotificationStrat();
					showNotification();
					mWakeLock.acquire();
				}
				break;
			case HTTPFileDownloadTask.PROGRESS_STOP_COMPLETE : {
					Bundle b  = msg.getData();
					int errCode = b.getInt("err", HTTPFileDownloadTask.ERR_NOERR);
					if(errCode == HTTPFileDownloadTask.ERR_CONNECT_TIMEOUT) {
						//mTxtState.setText("State: ERR_CONNECT_TIMEOUT");
						Toast.makeText(getApplicationContext(), getString(R.string.error_display), Toast.LENGTH_LONG).show();
					}else if(errCode == HTTPFileDownloadTask.ERR_FILELENGTH_NOMATCH) {
						//mTxtState.setText("State: ERR_FILELENGTH_NOMATCH");
					}else if(errCode == HTTPFileDownloadTask.ERR_NOT_EXISTS) {
						//mTxtState.setText("State: ERR_NOT_EXISTS");
						Toast.makeText(getApplicationContext(), getString(R.string.error_display), Toast.LENGTH_LONG).show();
					}else if(errCode == HTTPFileDownloadTask.ERR_REQUEST_STOP) {
						//mTxtState.setText("State: ERR_REQUEST_STOP");
					}else if(errCode == HTTPFileDownloadTask.ERR_UNKNOWN) {
						Toast.makeText(getApplicationContext(), getString(R.string.error_display), Toast.LENGTH_LONG).show();
					}
					
					mState = STATE_STOPED;
					mRemainTimeTV.setText("");
					mDownloadRateTV.setText("");
					mBtnControl.setText(getString(R.string.retry));
					mBtnControl.setClickable(true);
					mBtnControl.setFocusable(true);
					mBtnCancel.setClickable(true);
					mBtnCancel.setFocusable(true);
					setNotificationPause();
					showNotification();
					if(mWakeLock.isHeld()){
						mWakeLock.release();
					}
					
					if(mIsCancelDownload) {
						finish();
					}
				}
				break;
			default:
				break;
			}
		}  	
    }
    
    private void showNotification() {
    	if(mNotifyManager != null) {
    		mNotifyManager.notify(notification_id, mNotify);
    		Log.d(TAG, "show notification " + notification_id);
    	}
    }
    
    private void clearNotification() {
    	if(mNotifyManager != null) {
    		mNotifyManager.cancel(notification_id);
    		Log.d(TAG, "clearNotification " + notification_id);
    	}
    }
    
    private void setNotificationProgress(int percent) {
    	if(mNotify != null) {
    		mNotify.contentView.setProgressBar(R.id.pb_download, 100, percent, false);
    		mNotify.contentView.setTextViewText(R.id.pb_percent, String.valueOf(percent) + "%");
    	}
    }
    
    private void setNotificationPause() {
    	if(mNotify != null) {
    		mNotify.contentView.setTextViewText(R.id.pb_title, mContext.getString(R.string.pb_title_pause));
    		mNotify.contentView.setViewVisibility(R.id.image_pause, View.VISIBLE);
    	}
    }
    
    private void setNotificationStrat() {
    	if(mNotify != null) {
    		mNotify.contentView.setTextViewText(R.id.pb_title, mContext.getString(R.string.pb_title_downloading));
    		mNotify.contentView.setViewVisibility(R.id.image_pause, View.GONE);
    	}
    }
    
    private void setDownloadInfoViews(long contentLength, long receivedCount, long receivedPerSecond) {
    	int percent = (int)(receivedCount * 100 / contentLength);
    	mCompletedTV.setText(String.valueOf(percent) + "%");
    	
    	String rate = "";
    	if(receivedPerSecond < 1024) {
    		rate = String.valueOf(receivedPerSecond) + "B/S";
        }else if(receivedPerSecond/1024 > 0 && receivedPerSecond/1024/1024 == 0) {
        	rate = String.valueOf(receivedPerSecond/1024) + "KB/S";
        }else if(receivedPerSecond/1024/1024 > 0) {
        	rate = String.valueOf(receivedPerSecond/1024/1024) + "MB/S";
        }
    	
		mDownloadRateTV.setText(rate);
		
		int remainSecond = (receivedPerSecond == 0) ? 0 : (int)((contentLength - receivedCount) / receivedPerSecond);
		String remainSecondString = "";
		if(remainSecond < 60) {
			remainSecondString = String.valueOf(remainSecond) + "s";
        }else if(remainSecond/60 > 0 && remainSecond/60/60 == 0) {
        	remainSecondString = String.valueOf(remainSecond/60) + "min";
        }else if(remainSecond/60/60 > 0) {
        	remainSecondString = String.valueOf(remainSecond/60/60) + "h";
        }
		
		remainSecondString = mContext.getString(R.string.remain_time) + " " + remainSecondString;
		mRemainTimeTV.setText(remainSecondString);
    }
    
    @Override
	protected void onDestroy() {
		Log.d(TAG, "ondestroy");
		
		if(mWakeLock != null) {
			if(mWakeLock.isHeld()){
				mWakeLock.release();
			}
		}
		
		clearNotification();
		mNotifyManager = null;
		
		if(mBinder != null) {
			mBinder.unLockWorkHandler();
		}
		mContext.unbindService(mConnection);
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
	}

	@Override
	protected void onRestart() {
		Log.d(TAG, "onRestart");
		super.onRestart();
	}

	@Override
	protected void onStart() {
		Log.d(TAG, "onStart");
		super.onStart();
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "onStop");	
		super.onStop();
	}
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {  
    	if (keyCode == KeyEvent.KEYCODE_BACK) {  
	    	ActivityInfo ai = homeInfo.activityInfo;  
	    	Intent startIntent = new Intent(Intent.ACTION_MAIN);  
	    	startIntent.addCategory(Intent.CATEGORY_LAUNCHER);  
	    	startIntent.setComponent(new ComponentName(ai.packageName, ai.name));  
	    	startActivitySafely(startIntent);  
	    	return true;  
    	} else { 
    		return super.onKeyDown(keyCode, event);  
    	}  
    }
    
    void startActivitySafely(Intent intent) {  
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  
        try {  
            startActivity(intent);  
        } catch (ActivityNotFoundException e) {    
        	
        } catch (SecurityException e) {  
        
        }  
    } 
    
    private FTPRequestInfo parseFtpUri(String uri) {
    	FTPRequestInfo info = new FTPRequestInfo();
    	try {	    	
	    	String[] s = uri.split("//");
	    	if(s[1].contains("@")) {
	    		String[] s2 = s[1].split("@", 2);
	    		String[] s3 = s2[0].split(":", 2);
	    		info.setUsername(s3[0]);
	    		info.setPassword(s3[1]);
	    		
	    		String[] s4 = s2[1].split(":", 2);
	    		if(s4.length > 1) {
	    			info.setHost(s4[0]);
	    			info.setPort(Integer.valueOf(s4[1].substring(0, s4[1].indexOf("/"))));
	    			info.setRequestPath(s4[1].substring(s4[1].indexOf("/")));
	    		}else {
	    			info.setHost(s4[0].substring(0, s4[0].indexOf("/")));
	    			info.setRequestPath(s4[0].substring(s4[0].indexOf("/")));
	    		}
	    	}else {
	    		String[] str = s[1].split(":", 2);
	    		if(str.length > 1) {
	    			info.setHost(str[0]);
	    			info.setPort(Integer.valueOf(str[1].substring(0, str[1].indexOf("/"))));
	    			info.setRequestPath(str[1].substring(str[1].indexOf("/")));
	    		}else {
	    			info.setHost(str[0].substring(0, str[0].indexOf("/")));
	    			info.setRequestPath(str[0].substring(str[0].indexOf("/")));
	    		}
	    	}
    	}catch (Exception e) {
    		Log.e(TAG, "parseFtpUri error....!");
    	}
    	
    	info.dump();
    	return info;
    }

	public void installSystem(final File file) {
		installSystem("", file);
	}

	private void installSystem(final String pkgName, final File file) {
		Log.i(TAG, "installSystem, pkgName=" + pkgName + " file=" + file.getPath());
		chmodFile(file);
		startOTA(file);
	}

	private void chmodFile(File file) {
		Log.e(TAG, "chmodFile file: " + file.getAbsolutePath() + " to 0666");
		try {
			Os.chmod(file.getAbsolutePath(), 0666);
		} catch (Exception e) {
			Log.i(TAG, "chmod fail!!!! e=" + e.toString());
			e.printStackTrace();
		}
	}

	private void startOTA(File file) {
		otaUpdateWakeLock.acquire();

		try {
			UpdateParser.ParsedUpdate mParsedUpdate = UpdateParser.parse(file);
			Log.e(TAG, mParsedUpdate.toString());

			otaUpdating = true;

			Message msgShow = new Message();
			msgShow.what = MSG_SHOW_SYS_PROGRESS;
			mMainHandler.sendMessage(msgShow);

			SystemUpdateManager mSystemUpdateManager = new SystemUpdateManager(mContext);
			mSystemUpdateManager.startUpdateSystem(mParsedUpdate, mUpdateEngineCallback);
		} catch (Exception e) {
			otaUpdating = false;
			Log.e(TAG, "e=" + e.toString());
			if (otaUpdateWakeLock.isHeld()) {
				otaUpdateWakeLock.release();
			}

			Message msgDismiss = new Message();
			msgDismiss.what = MSG_DISMISS_SYS_PROGRESS;
			mMainHandler.sendMessage(msgDismiss);

			Message msg = new Message();
			msg.what = MSG_SYS_UPDATE_FAIL;
			msg.obj = e.toString();
			mMainHandler.sendMessage(msg);
		}
	}

	private void showUpgradeSuccess() {
		Dialog dialog = new AlertDialog.Builder(getApplicationContext())
				.setTitle(R.string.upgrade_title)
				.setMessage(R.string.upgrade_success)
				.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		dialog.show();
	}

	private void showUpgradeFailed() {
		Dialog dialog = new AlertDialog.Builder(getApplicationContext())
				.setTitle(R.string.upgrade_title)
				.setMessage(R.string.upgrade_failed)
				.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		dialog.show();
	}

	private void showInstallSystemSuccess() {
		Log.d(TAG, "showInstallSystemSuccess");

		if (mDialogSysUpdateSuccess != null) {
			if (mDialogSysUpdateSuccess.isShowing()) {
				mDialogSysUpdateSuccess.dismiss();
			}

			mDialogSysUpdateSuccess.setTitle(getResources().getString(R.string.upgrade_sys_title));
			mDialogSysUpdateSuccess.setMessage(getResources().getString(R.string.upgrade_sys_success_and_need_reboot));
			mDialogSysUpdateSuccess.show();

			if (mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
				mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_POSITIVE).setText(getResources().getString(R.string.confirm));
			}
		}
	}

	private void showInstallSystemFail(String reason) {
		Log.d(TAG, "showInstallSystemFail reason=" + reason);

		if (mDialogSysUpdateFail != null) {
			if (mDialogSysUpdateFail.isShowing()) {
				mDialogSysUpdateFail.dismiss();
			}

			mDialogSysUpdateFail.setTitle(getResources().getString(R.string.upgrade_sys_title));
			mDialogSysUpdateFail.setMessage(reason);
			mDialogSysUpdateFail.show();

			if (mDialogSysUpdateFail.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
				mDialogSysUpdateFail.getButton(DialogInterface.BUTTON_POSITIVE).setText(getResources().getString(R.string.confirm));
			}
		}

	}
}
