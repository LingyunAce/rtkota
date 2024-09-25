package com.ostar.ota.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

//import android.os.storage.VolumeInfo;
//import android.os.storage.DiskInfo;

import android.os.Build;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.documentfile.provider.DocumentFile;

import com.ostar.ota.R;
import com.ostar.ota.util.AppUtils;
import com.ostar.ota.util.LocalPackage;
import com.ostar.ota.util.SystemPropertiesProxy;

public class RTKUpdateService extends Service {
    public static final String VERSION = "1.0";
    private static final String TAG = "ABotaTest";
    private static final boolean DEBUG = true;
    private static final boolean mIsNotifyDialog = true;
    private static final boolean mIsSupportUsbUpdate = true;

    private static Context mContext;
    private volatile boolean mIsFirstStartUp = true;

    private static void LOG(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }


    public static String OTA_PACKAGE_FILE = "update_signed.zip";
    public static final int RKUPDATE_MODE = 1;
    public static final int OTAUPDATE_MODE = 2;
    private static volatile boolean mWorkHandleLocked = false;
    //private static volatile boolean sWorkHandleLocked = false;
    private static volatile boolean mIsNeedDeletePackage = false;

    public static String DATA_ROOT = "/data/media/0";
    public static String FLASH_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    // public static String SDCARD_ROOT = "/mnt/external_sd";
    // public static String USB_ROOT = "/mnt/usb_storage";
    public static String SDCARD_ROOT = "/mnt/media_rw";
    public static String USB_ROOT = "storge/*/";
    public static String CACHE_ROOT = Environment.getDownloadCacheDirectory().getAbsolutePath();

    public static final int COMMAND_NULL = 0;
    public static final int COMMAND_CHECK_LOCAL_UPDATING = 1;
    public static final int COMMAND_CHECK_REMOTE_UPDATING = 2;
    public static final int COMMAND_CHECK_REMOTE_UPDATING_BY_HAND = 3;
    public static final int COMMAND_DELETE_UPDATEPACKAGE = 4;

    private static final String COMMAND_FLAG_SUCCESS = "success";
    private static final String COMMAND_FLAG_UPDATING = "updating";

    public static final int UPDATE_SUCCESS = 1;
    public static final int UPDATE_FAILED = 2;

    private boolean isFirmwareImageFile = false;

    private static final String[] IMAGE_FILE_DIRS = {DATA_ROOT + "/", FLASH_ROOT + "/",
            // SDCARD_ROOT + "/",
            // USB_ROOT + "/",
    };

    private String mLastUpdatePath;
    private WorkHandler mWorkHandler;
    private Handler mMainHandler;
    private SharedPreferences mAutoCheckSet;

    /*----------------------------------------------------------------------------------------------------*/
    public static URI mRemoteURI = null;
    public static URI mRemoteURIBackup = null;
    private String mTargetURI = null;
    private boolean mUseBackupHost = false;
    private String mOtaPackageVersion = null;
    private String mSystemVersion = null;
    private String mOtaPackageName = null;
    private String mOtaPackageLength = null;
    private String mDescription = null;
    private volatile boolean mIsOtaCheckByHand = false;

    private int mLocalPackageIndex = 0;
    private List<LocalPackage> mLocalPackages = new ArrayList<>();
    private List<String> mInternalDirs = new ArrayList<>();
    private List<String> mExternalDirs = new ArrayList<>();
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
    private BatteryManager mBatteryManager;
    private StorageManager mStorageManager;
    private PowerManager mPowerManager = null;
    private PowerManager.WakeLock copyWakeLock = null;
    private PowerManager.WakeLock appUpdateWakeLock = null;
    private PowerManager.WakeLock otaUpdateWakeLock = null;
    private static volatile boolean otaUpdating = false;
    private UpdateEngineCallback mUpdateEngineCallback;
    private ProgressDialog mUpdateAppProgressDialog;
    private ProgressDialog mUpdateSysProgressDialog;
    private AlertDialog mDialogSysUpdateSuccess, mDialogSysUpdateFail;

    private static String OTAFILE = "";
    @Override
    public IBinder onBind(Intent arg0) {
        LOG("mBinder is start");
        // TODO Auto-generated method stub
        return mBinder;
    }

    private final LocalBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {

        public void deletePackage(String path) {
            LOG("try to deletePackage... path=" + path);
            String fileName = path;// = "/data/media/0/update.zip";
            if (path.startsWith("@")) {
                if (Build.VERSION.SDK_INT >= 30) {
                    fileName = "/storage/emulated/0/update_signed.zip";

                } else {
                    fileName = "/data/media/0/update_signed.zip";
                }
                LOG("ota was maped, path = " + path + ",delete " + fileName);

                File f_ota = new File(fileName);
                if (f_ota.exists()) {
                    f_ota.delete();
                    LOG("delete complete! path=" + fileName);
                }

                String netota = SystemPropertiesProxy.get(mContext, "persist.sf.ota.packagename");
                LOG("ota  delete name  = " + netota);
                if (!netota.equals("")) {
                    String ota_path = "/data/media/0/" + netota;
                    File ota_net = new File(ota_path);
                    if (ota_net.exists()) {
                        ota_net.delete();
                        LOG("delete ota! path=" + ota_path);
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= 30) {
                if (path.startsWith("/data/media")) {
                    fileName = path.replace("/data/media", "/storage/emulated");
                } else if (path.startsWith("/mnt/media_rw")) {
                    fileName = path.replace("/mnt/media_rw", "/storage");
                }

            } else {
                fileName = path;
            }
            Log.d(TAG, " deletePackage fileName =" + fileName);

            File f = new File(fileName);
            if (f.exists()) {
                f.delete();
                LOG("delete complete! path=" + fileName);
            } else {
                LOG("fileName=" + fileName + ",file not exists!");
            }
        }

        public void unLockWorkHandler() {
            LOG("unLockWorkHandler...");
            mWorkHandleLocked = false;
        }

        public void LockWorkHandler() {
            mWorkHandleLocked = true;
            LOG("LockWorkHandler...!");
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        /*-----------------------------------*/
        LOG("starting RKUpdateService, version is " + getAppVersionName(mContext));

        // whether is UMS or m-user
        Log.e(TAG, "FLASH_ROOT=" + FLASH_ROOT);
        if (getMultiUserState()) {
            Log.e(TAG, "--------------218-----------"); //dont go this
            FLASH_ROOT = DATA_ROOT;
        }

        mBatteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        copyWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "copyWakeLock");
        appUpdateWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "appUpdateWakeLock");
        otaUpdateWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "otaUpdateWakeLock");
        mStorageManager = mContext.getSystemService(StorageManager.class);

        initScanDir();

        File externalStorageDir = Environment.getExternalStorageDirectory();
        String packageName = mContext.getPackageName();
        File appDirectory = new File(externalStorageDir.getPath() + "/Android/data/" + packageName + "/");
        String appPath = appDirectory.getPath();
        LOG("appPath = " + appPath);

        String ota_packagename = getOtaPackageFileName();
        if (ota_packagename != null) {
            OTA_PACKAGE_FILE = ota_packagename;
            LOG("get ota package name private is " + OTA_PACKAGE_FILE);
        }

        try {
            mRemoteURI = new URI(getRemoteUri());
            mRemoteURIBackup = new URI(getRemoteUriBackup());
            //mRemoteURIBackup = new URI("http://192.168.1.219:2306/OtaUpdater/android?product=jade&version=1.0.3&sn=unknown&country=CN&language=zh");//new URI(getRemoteUriBackup());
            LOG("remote uri is " + mRemoteURI.toString());
            LOG("remote_backup uri is " + mRemoteURIBackup.toString());
            //LOG("remote uri backup is " + mRemoteURIBackup.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mAutoCheckSet = getSharedPreferences("auto_check", MODE_PRIVATE);

        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread workThread = new HandlerThread("UpdateService : work thread");
        workThread.start();
        mWorkHandler = new WorkHandler(workThread.getLooper());


        // handle dialog in main thread
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
                        //showInstallSystemSuccess();
                        deleteSdcardOtaPackage();
                        break;
                    case MSG_SYS_UPDATE_FAIL:
                        showInstallSystemFail((String) msg.obj);
                        break;
                }
                super.handleMessage(msg);
            }
        };

        if (mIsFirstStartUp) {
            LOG("first startup!!!");
            mIsFirstStartUp = false;
            String command = "";
            String path;
            if (command != null) {
                LOG("command = " + command);
                if (command.contains("$path")) {
                    path = command.substring(command.indexOf('=') + 1);
                    LOG("last_flag: path = " + path);

                    if (command.startsWith(COMMAND_FLAG_SUCCESS)) {
                        if (!mIsNotifyDialog) {
                            mIsNeedDeletePackage = true;
                            mLastUpdatePath = path;
                            return;
                        }

                        LOG("now try to start notifydialog activity!");
                        Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("flag", UPDATE_SUCCESS);
                        intent.putExtra("path", path);
                        startActivity(intent);
                        mWorkHandleLocked = true;
                        return;
                    }
                    if (command.startsWith(COMMAND_FLAG_UPDATING)) {
                        Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("flag", UPDATE_FAILED);
                        intent.putExtra("path", path);
                        startActivity(intent);
                        mWorkHandleLocked = true;
                        return;
                    }
                }
            }
        }

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
                        /*
                        DecimalFormat df = new DecimalFormat("#");
    					String progress = df.format(percent * 100);
    					Log.d(TAG, "update progress: " + progress);
                        */

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

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            long otaTimestamp = getOtaTimestamp(OTAFILE);
                            SystemPropertiesProxy.set(mContext, "persist.sys.otatimestamp", String.valueOf(otaTimestamp));
                        }
                    }).start();

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
    }

    private void initScanDir() {
        mInternalDirs.clear();
        mInternalDirs.add(DATA_ROOT);
        mInternalDirs.add(FLASH_ROOT);

        mExternalDirs.clear();
        mExternalDirs.add("/mnt/media_rw");
        mExternalDirs.add("/mnt/sdcard");
        mExternalDirs.add(USB_ROOT);
    }

    @Override
    public void onDestroy() {
        LOG("onDestroy.......");
        super.onDestroy();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        LOG("onStart.......");

        super.onStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG("onStartCommand.......");

        //Toast.makeText(mContext, "RKUpdateService is start", Toast.LENGTH_SHORT).show();
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        int command = intent.getIntExtra("command", COMMAND_NULL);
        int delayTime = intent.getIntExtra("delay", 1000);

        LOG("command = " + command + " delaytime = " + delayTime);
        if (command == COMMAND_NULL) {
            return Service.START_NOT_STICKY;
        }

        if (command == COMMAND_CHECK_REMOTE_UPDATING) {
            mIsOtaCheckByHand = false;
            if (!mAutoCheckSet.getBoolean("auto_check", true)) {
                LOG("user set not auto check!");
                return Service.START_NOT_STICKY;
            }
        }

        if (command == COMMAND_CHECK_REMOTE_UPDATING_BY_HAND) {
            mIsOtaCheckByHand = true;
            mWorkHandleLocked = false;
            command = COMMAND_CHECK_LOCAL_UPDATING;
        }

        if (mIsNeedDeletePackage) {
            command = COMMAND_DELETE_UPDATEPACKAGE;
            delayTime = 20000;
            mWorkHandleLocked = true;
        }

        Message msg = new Message();
        msg.what = command;
        msg.arg1 = WorkHandler.NOT_NOTIFY_IF_NO_IMG;
        mWorkHandler.sendMessageDelayed(msg, delayTime);
        return Service.START_REDELIVER_INTENT;
    }


    /**
     * @see .
     */
    private class WorkHandler extends Handler {
        private static final int NOTIFY_IF_NO_IMG = 1;
        private static final int NOT_NOTIFY_IF_NO_IMG = 0;

        /*-----------------------------------*/

        public WorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {

            String[] searchResult = null;

            switch (msg.what) {

                case COMMAND_CHECK_LOCAL_UPDATING:
                    LOG("WorkHandler::handleMessage() : To perform 'COMMAND_CHECK_LOCAL_UPDATING'.");
                    if (mWorkHandleLocked) {
                        LOG("WorkHandler::handleMessage() : locked !!!");
                        return;
                    }
                    resetExternalDir();
                    checkLocalUpdate();
                    startCheckRemoteUpdate();
                    break;
                case COMMAND_CHECK_REMOTE_UPDATING:
                    if (mWorkHandleLocked) {
                        LOG("WorkHandler::handleMessage() : locked !!!");
                        return;
                    }

                    try {
                        boolean result;
                        mUseBackupHost = false;
                        result = requestRemoteServerForUpdate(mRemoteURI);
                        LOG("----------471 result= " + result);

                        if (result) {
                            LOG("find a remote update package, now start PackageDownloadActivity...");
                            //提示有可用的更新包
                            startNotifyActivity();
                        } else {
                            LOG("no find remote update package...");
                            myMakeToast(mContext.getString(R.string.current_new));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOG("request remote server error...");
                            myMakeToast(mContext.getString(R.string.current_new));
                        }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    break;
                case COMMAND_DELETE_UPDATEPACKAGE:
                    // if mIsNeedDeletePackage == true delete the package
                    if (mIsNeedDeletePackage) {
                        LOG("execute COMMAND_DELETE_UPDATEPACKAGE...");
                        File f = new File(mLastUpdatePath);
                        if (f.exists()) {
                            f.delete();
                            LOG("delete complete! path=" + mLastUpdatePath);
                        } else {
                            LOG("path=" + mLastUpdatePath + " ,file not exists!");
                        }

                        mIsNeedDeletePackage = false;
                        mWorkHandleLocked = false;
                    }

                    break;
                default:
                    break;
            }
        }

    }


    private void checkLocalUpdate() {
        mLocalPackageIndex = 0;
        mLocalPackages.clear();

        // del last apps and ota package
        /*File targetDir = new File(AppUtils.getExternalDir(mContext, "packages"));
        if (targetDir != null) {
            if (targetDir.isDirectory()) {
                if (null != targetDir.listFiles()) {
                    for (File file : targetDir.listFiles()) {
                        deletePackage_(file);
                    }
                }
            } else {
                deletePackage_(targetDir);
            }
        }*/

        // scan external dirs(root dirs and 2 depth child dirs)
        for (String root : mExternalDirs/*EXTERNAL_DIRS*/) {
            Log.i(TAG, "checkLocalUpdate, search external: " + root);


            File rootDir = new File(root);
            if (null != rootDir.listFiles()) {
                // check files in root dir of external
                //loadAnimUpdate(new File(rootDir, BOOT_ANIMATION_PATH));
                //loadAppUpdate(new File(rootDir, LOCAL_UPDATE_PATH));
                loadOtaPackage(rootDir);
                if (!mLocalPackages.isEmpty()) {
                    break;
                }

                for (File file : rootDir.listFiles()) {
                    Log.i(TAG, "checkLocalUpdate, external rootDir: " + file.getAbsolutePath());
                    //if (file.isDirectory()) {
                    // check 2 depth child dir in external root
                    //loadAnimUpdate(new File(file, BOOT_ANIMATION_PATH));
                    //loadAppUpdate(new File(file, LOCAL_UPDATE_PATH));
                    loadOtaPackage(file);

                    if (!mLocalPackages.isEmpty()) {
                        break;
                    }
                    //}
                }
            }
        }

        Log.i(TAG, "checkLocalUpdate external dirs end");

        // scan internal dirs if external dirs can not find any target
        if (mLocalPackages.isEmpty()) {
            for (String root : mInternalDirs/*INTERNAL_DIRS*/) {
                Log.i(TAG, "checkLocalUpdate, search internal: " + root);

                File rootDir = new File(root);
                //loadAnimUpdate(new File(rootDir, BOOT_ANIMATION_PATH));
                //loadAppUpdate(new File(rootDir, LOCAL_UPDATE_PATH));
                loadOtaPackage(rootDir);

                if (!mLocalPackages.isEmpty()) {
                    break;
                }
            }
        }

        if (!mLocalPackages.isEmpty()) {
            for (LocalPackage localPackage : mLocalPackages) {
                Log.i(TAG, "checkLocalUpdate finally found: " + localPackage.toString());
            }
            // start handle
            mWorkHandleLocked = true;
            showNewVersion();

        }
    }

    public void resetExternalDir() {
        mExternalDirs.clear();
        mExternalDirs.add("/mnt/media_rw");
        mExternalDirs.add("/mnt/sdcard");

        //
        try {
            Class storeManagerClazz = Class.forName("android.os.storage.StorageManager");
            Method getVolumesMethod = storeManagerClazz.getMethod("getVolumes");

            List<VolumeInfo> list = (List<VolumeInfo>) getVolumesMethod.invoke(mStorageManager);/*mStorageManager.getVolumes()*/
            ;
            for (VolumeInfo item : list) {
                if (item.getType() == VolumeInfo.TYPE_PUBLIC) {
                    Log.i(TAG, "TYPE_PUBLIC volumeInfo item=" + item.getPath() + ", internalPath=" + item.getInternalPath());

                    DiskInfo diskInfo = item.getDisk();
                    if (diskInfo != null && diskInfo.isUsb()) {
                        File path = item.getPath();
                        if (path != null) {
                            Log.i(TAG, "pulic volume: path=" + path.getAbsolutePath());
                            mExternalDirs.add(path.getAbsolutePath());
                        }

                        File internalPath = item.getInternalPath();
                        if (internalPath != null) {
                            Log.i(TAG, "pulic volume: internalPath=" + internalPath.getAbsolutePath());
                            mExternalDirs.add(internalPath.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        //

    }

    private void deletePackage_(File file) {
        if (file.exists()) {
            boolean isDelete = file.delete();
            if (isDelete) {
                Log.e(TAG, "--------------DEL PAC IS SUCCESS-----------------");
            } else {
                Log.e(TAG, "--------------DEL PAC IS FAILED-----------------");
            }
        }
    }

    /**
     * add ota package info
     */
    private boolean loadOtaPackage(File file) {
        Log.i(TAG, "loadOtaPackage, file: " + file.getAbsolutePath());
        if (null != file && file.exists() && file.isDirectory()) {
            File otaFile = new File(file, OTA_PACKAGE_FILE);
            Log.i(TAG, "loadOtaPackage, otaFile: " + otaFile.getAbsolutePath());

            if (otaFile.exists()) {
                mLocalPackages.add(new LocalPackage(LocalPackage.TYPE_ROM, otaFile));
                OTAFILE = otaFile.getAbsolutePath();
            } else {
                Log.i(TAG, "loadOtaPackage, otaFile: " + otaFile.getAbsolutePath() + " do not exist!");
            }
        }

        return !mLocalPackages.isEmpty();
    }

    /**
     * show update dialog
     */
    private void showNewVersion() {
        StringBuilder sb = new StringBuilder();
        for (LocalPackage localPackage : mLocalPackages) {
            sb.append(localPackage.getFile().getName()).append("\n");
        }

        Dialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(getResources().getString(R.string.upgrade_message) + sb.toString())
                .setPositiveButton(R.string.upgrade_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                long otaTimestampNew = getOtaTimestamp(OTAFILE);
                                long otaTimestampOld = 0L;
                                String otaTimestampOldStr = SystemPropertiesProxy.get(mContext, "persist.sys.otatimestamp");
                                if (otaTimestampOldStr != null && !otaTimestampOldStr.equals("")) {
                                    otaTimestampOld = Long.parseLong(otaTimestampOldStr);
                                }
                                if (otaTimestampNew > otaTimestampOld) {
                                    installLocalNext();
                                } else {
                                    Looper.prepare();
                                    //Toast.makeText(mContext,R.string.pac_is_old,Toast.LENGTH_LONG).show();
                                    showIsOldPac();
                                    Looper.loop();
                                }
                            }
                        }).start();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.upgrade_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                        //mWorkHandleLocked = true;
                    }
                }).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    /**
     * install local apks and ota package
     */
    private void installLocalNext() {
        if (mLocalPackageIndex < mLocalPackages.size()) {
            LocalPackage localPackage = mLocalPackages.get(mLocalPackageIndex);
            mLocalPackageIndex++;

            if (localPackage.getFile() != null
                    && localPackage.getFile().exists()) {
                /*Toast.makeText(this, getResources().getString(R.string.upgrade_prepare_install)
                        + localPackage.getFile().getName(), Toast.LENGTH_LONG).show();*/
                installWithCopy(localPackage);
            } else {
                installLocalNext();
            }
        } else {
            mWorkHandleLocked = false;
        }
    }

    /**
     * copy apks and ota package from u-disk to externalStorage
     * then install
     */
    private void installWithCopy(final LocalPackage localPackage) {
        File to = new File(AppUtils.getExternalDir(mContext, "packages")
                + File.separator + localPackage.getFile().getName());
        Log.w(TAG, "installWithCopy localPackage=" + localPackage.toString());

        Message msgShow = new Message();
        msgShow.what = MSG_SHOW_APP_PROGRESS;
        msgShow.obj = getResources().getString(R.string.upgrade_copy_file_title) + ": " + localPackage.getFile().getName();
        mMainHandler.sendMessage(msgShow);

        try {
            copyWakeLock.acquire();
            copyFile(DocumentFile.fromFile(localPackage.getFile()), DocumentFile.fromFile(to));
            if (copyWakeLock.isHeld()) {
                copyWakeLock.release();
            }

            Log.w(TAG, "installWithCopy copyFile finished");

            if (localPackage.getType() == LocalPackage.TYPE_ROM) {
                installSystem(to);
            }
            installLocalNext();
        } catch (Exception e) {
            Log.w(TAG, "copyFile Exception=" + e);

            mWorkHandleLocked = false;

            if (copyWakeLock.isHeld()) {
                copyWakeLock.release();
            }
            if (appUpdateWakeLock.isHeld()) {
                appUpdateWakeLock.release();
            }
            if (otaUpdateWakeLock.isHeld()) {
                otaUpdateWakeLock.release();
            }

            Message msgDismiss = new Message();
            msgDismiss.what = MSG_DISMISS_APP_PROGRESS;
            mMainHandler.sendMessage(msgDismiss);

            Message msgFail = new Message();
            msgFail.what = MSG_APP_UPDATE_FAIL;
            mMainHandler.sendMessage(msgFail);
        }
    }

    /**
     * install ota package
     */
    private void installSystem(final File file) {
        installSystem("", file);
    }

    /**
     * install ota package
     */
    @SuppressLint("CheckResult")
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
        AlertDialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_title)
                .setMessage(R.string.upgrade_success)
                .setPositiveButton(R.string.confirm_boot, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //dialog.dismiss();
                        try {
                            SystemUpdateManager mSystemUpdateManager = new SystemUpdateManager(mContext);
                            mSystemUpdateManager.rebootNow(mContext);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .setNegativeButton(R.string.confirm_notboot, new DialogInterface.OnClickListener() {
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

    private void deleteSdcardOtaPackage() {
        Log.d(TAG, "-----------deleteSdcardOtaPackage");
        AlertDialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.delete_pac)
                .setMessage("")
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deletePackage_(new File(OTAFILE));
                        deletePackage_(new File("/storage/emulated/0/com.ostar.ota/packages/update_signed.zip"));
                        showInstallSystemSuccess();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showInstallSystemSuccess();
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
        /*if (mDialogSysUpdateSuccess != null) {
            if (mDialogSysUpdateSuccess.isShowing()) {
                mDialogSysUpdateSuccess.dismiss();
            }

            mDialogSysUpdateSuccess.setTitle(getResources().getString(R.string.upgrade_sys_title));
            mDialogSysUpdateSuccess.setMessage(getResources().getString(R.string.upgrade_sys_success_and_need_reboot));
            mDialogSysUpdateSuccess.show();

            if (mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
                mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_POSITIVE).setText(getResources().getString(R.string.confirm_boot));
                PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                //pManager.reboot(null);
                pManager.reboot("reboot-ab-update");
            }
            if(mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_NEGATIVE) != null){
                mDialogSysUpdateSuccess.getButton(DialogInterface.BUTTON_POSITIVE).setText(getResources().getString(R.string.confirm_notboot));
            }
        }*/

        AlertDialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.upgrade_sys_title)
                .setMessage(R.string.upgrade_sys_success_and_need_reboot)
                .setPositiveButton(R.string.confirm_boot, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //dialog.dismiss();
                        SystemUpdateManager mSystemUpdateManager = null;
                        try {
                            mSystemUpdateManager = new SystemUpdateManager(mContext);
                            mSystemUpdateManager.rebootNow(mContext);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .setNegativeButton(R.string.confirm_notboot, new DialogInterface.OnClickListener() {
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

    /**
     * copy files from u-dsk to externalStorage
     */
    private void copyFile(DocumentFile from, DocumentFile to) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(from.getUri());
        OutputStream outputStream = getContentResolver().openOutputStream(to.getUri(), "rwt");

        if (null != inputStream && null != outputStream) {
            int count;
            byte[] bytes = new byte[1024];

            int copyed = 0;
            int maxSize = inputStream.available();
            Log.w(TAG, "copyFile maxSize=" + maxSize);

            while ((count = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, count);
                copyed += count;
                //Log.w(TAG, "copyFile have copied="+copyed);

                if (maxSize > 0) {
                    float pressent = (float) copyed / (float) maxSize * 100;

                    //Log.w(TAG, "copyFile pressent="+((int)pressent));

                    if (mUpdateAppProgressDialog != null) {
                        if (mUpdateAppProgressDialog.getProgress() >= 0
                                && mUpdateAppProgressDialog.getProgress() != ((int) pressent)) {
                            Message msgUpdate = new Message();
                            msgUpdate.what = MSG_UPDATE_APP_PROGRESS;
                            msgUpdate.arg1 = ((int) pressent);
                            mMainHandler.sendMessage(msgUpdate);
                        }
                    }
                }
            }

            outputStream.close();
            inputStream.close();

            Message msgDismiss = new Message();
            msgDismiss.what = MSG_DISMISS_APP_PROGRESS;
            mMainHandler.sendMessage(msgDismiss);
        } else {
            Message msgDismiss = new Message();
            msgDismiss.what = MSG_DISMISS_APP_PROGRESS;
            mMainHandler.sendMessage(msgDismiss);

            throw new IOException("InputStream or OutputStream is null");
        }
    }

    private void startCheckRemoteUpdate() {
        Intent serviceIntent = new Intent(this, RTKUpdateService.class);
        serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_REMOTE_UPDATING);
        serviceIntent.putExtra("delay", 15000);
        startService(serviceIntent);

        mContext.startService(serviceIntent);
    }


    private String getOtaPackageFileName() {
        String str = SystemPropertiesProxy.get(mContext, "ro.ota.packagename");
        if (str == null || str.length() == 0) {
            return null;
        }
        if (!str.endsWith(".zip")) {
            return str + ".zip";
        }

        return str;
    }

    private String getRKimageFileName() {
        String str = SystemPropertiesProxy.get(mContext, "ro.rkimage.name");
        if (str == null || str.length() == 0) {
            return null;
        }
        if (!str.endsWith(".img")) {
            return str + ".img";
        }

        return str;
    }

    private String getCurrentFirmwareVersion() {
        return SystemPropertiesProxy.get(mContext, "ro.firmware.version");
    }

    private static String getProductName() {
        return SystemPropertiesProxy.get(mContext, "ro.product.model");
    }


    private void makeToast(final CharSequence msg) {
        mMainHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**********************************************************************************************************************
     * ota update
     ***********************************************************************************************************************/
    public static String getRemoteUri() {
        return "http://" + getRemoteHost() + "/OtaUpdater/android?product=" + getOtaProductName() + "&version="
                + getSystemVersion() + "&sn=" + getProductSN() + "&country=" + getCountry() + "&language="
                + getLanguage();
    }

    public static String getRemoteUriBackup() {
        return "http://" + getRemoteHostBackup() + "/OtaUpdater/android?product=" + getOtaProductName() + "&version="
                + getSystemVersion() + "&sn=" + getProductSN() + "&country=" + getCountry() + "&language="
                + getLanguage();
    }

    public static String getRemoteHost() {
        String remoteHost = SystemPropertiesProxy.get(mContext, "ro.product.ota.host");
        if (remoteHost == null || remoteHost.length() == 0) {
            //remoteHost = "172.16.14.202:2300";
            remoteHost = "192.168.1.219:2306";
        }
        return remoteHost;
    }

    public static String getRemoteHostBackup() {
        String remoteHost = SystemPropertiesProxy.get(mContext, "ro.product.ota.host2");
        if (remoteHost == null || remoteHost.length() == 0) {
            //remoteHost = "172.16.14.202:2300";
            remoteHost = "192.168.1.219:2306";
        }
        return remoteHost;
    }

    public static String getOtaProductName() {
        String productName = SystemPropertiesProxy.get(mContext, "ro.product.model");
        if (productName.contains(" ")) {
            productName = productName.replaceAll(" ", "");
        }

        return productName;
    }

    public static boolean getMultiUserState() {
        String multiUser = SystemPropertiesProxy.get(mContext, "ro.factory.hasUMS");
        if (multiUser != null && multiUser.length() > 0) {
            return !multiUser.equals("true");
        }

        multiUser = SystemPropertiesProxy.get(mContext, "ro.factory.storage_policy");
        if (multiUser != null && multiUser.length() > 0) {
            return multiUser.equals("1");
        }

        return false;
    }

    private void startNotifyActivity() {
        Intent intent = new Intent(mContext, OtaUpdateNotifyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("uri", mTargetURI);
        intent.putExtra("OtaPackageLength", mOtaPackageLength);
        intent.putExtra("OtaPackageName", mOtaPackageName);
        intent.putExtra("OtaPackageVersion", mOtaPackageVersion);
        intent.putExtra("SystemVersion", mSystemVersion);
        intent.putExtra("description", mDescription);
        mContext.startActivity(intent);
        mWorkHandleLocked = true;
    }

    private void myMakeToast(CharSequence msg) {
        Log.e(TAG, "------------mIsOtaCheckByHand= " + mIsOtaCheckByHand);
        if (mIsOtaCheckByHand) {
            makeToast(msg);
        }
    }

    private boolean requestRemoteServerForUpdate(URI remote) throws IOException/*, ClientProtocolException*/ {
        LOG("---------remote = " + remote.toString());
        if (remote == null) {
            return false;
        }

        HttpClient httpClient = CustomerHttpClient.getHttpClient();
        HttpHead httpHead = new HttpHead(remote);

        HttpResponse response = httpClient.execute(httpHead);
        int statusCode = response.getStatusLine().getStatusCode();
        LOG("---------statusCode = " + statusCode);

        if (statusCode != 200) {
            return false;
        }
        if (DEBUG) {
            for (Header header : response.getAllHeaders()) {
                LOG(header.getName() + ":" + header.getValue());
            }
        }

        Header[] headLength = response.getHeaders("OtaPackageLength");
        if (headLength != null && headLength.length > 0) {
            mOtaPackageLength = headLength[0].getValue();
            LOG("---------- mOtaPackageLength" + mOtaPackageLength);

        }

        Header[] headName = response.getHeaders("OtaPackageName");
        LOG("---------- headName" + headName);

        if (headName == null) {
            return false;
        }
        if (headName.length > 0) {
            mOtaPackageName = headName[0].getValue();
        }

        Header[] headVersion = response.getHeaders("OtaPackageVersion");
        if (headVersion != null && headVersion.length > 0) {
            mOtaPackageVersion = headVersion[0].getValue();
            LOG("---------- mOtaPackageVersion" + mOtaPackageVersion);
        }
        Header[] headTargetURI = response.getHeaders("OtaPackageUri");
        if (headTargetURI == null) {
            return false;
        }
        LOG("---------- headTargetURI" + headTargetURI);
        if (headTargetURI.length > 0) {
            mTargetURI = headTargetURI[0].getValue();
        }

        if (mOtaPackageName == null || mTargetURI == null) {
            LOG("server response format error!");
            return false;
        }

        // get description from server response.
        Header[] headDescription = response.getHeaders("description");
        if (headDescription != null && headDescription.length > 0) {
            mDescription = new String(headDescription[0].getValue().getBytes("ISO8859_1"), "UTF-8");
        }

        if (!mTargetURI.startsWith("http://") && !mTargetURI.startsWith("https://")
                && !mTargetURI.startsWith("ftp://")) {
            mTargetURI = "http://" + (mUseBackupHost ? getRemoteHostBackup() : getRemoteHost())
                    + (mTargetURI.startsWith("/") ? mTargetURI : ("/" + mTargetURI));
        }

        mSystemVersion = getSystemVersion();

        LOG("OtaPackageName = " + mOtaPackageName + " OtaPackageVersion = " + mOtaPackageVersion
                + " OtaPackageLength = " + mOtaPackageLength + " SystemVersion = " + mSystemVersion + "OtaPackageUri = "
                + mTargetURI);
        return true;
    }

    public static String getSystemVersion() {
        String version = SystemPropertiesProxy.get(mContext, "ro.product.version");
        if (version == null || version.length() == 0) {
            version = "1.0.0";
        }

        return version;
    }

    public static String getProductSN() {
        String sn = SystemPropertiesProxy.get(mContext, "ro.serialno");
        if (sn == null || sn.length() == 0) {
            sn = "unknown";
        }

        return sn;
    }

    public static String getCountry() {
        return Locale.getDefault().getCountry();
    }

    public static String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    public static String getAppVersionName(Context context) {
        String versionName = "";
        try {
            // ---get the package info---
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            versionName = pi.versionName;
            if (versionName == null || versionName.length() <= 0) {
                return "";
            }
        } catch (Exception e) {
            Log.e("VersionInfo", "Exception", e);
        }
        return versionName;
    }

    public static long getOtaTimestamp(String otaFilePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(otaFilePath))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("post-timestamp=")) {
                    String timestampStr = line.split("=")[1];
                    Log.e(TAG, "----------timestampStr= " + timestampStr);
                    return Long.parseLong(timestampStr);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1; // 未找到时间戳
    }

    private void showIsOldPac() {
        Log.d(TAG, "-----------pac is old");
        AlertDialog dialog = new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.pac_is_old)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();

        //dialog.setCanceledOnTouchOutside(false);
        //dialog.setCancelable(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }
}
