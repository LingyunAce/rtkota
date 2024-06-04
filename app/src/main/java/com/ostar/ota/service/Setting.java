package com.ostar.ota.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ostar.ota.R;

public class Setting extends Activity {
    private static final String TAG = "RKUpdateService.Setting";

    static final int BAT_LEVEL_LIMIT_IN_DISCHARGE = 15;
    static final int BAT_LEVEL_LIMIT_IN_CHARGE = 10;

    private Context mContext;
    private ImageButton mBtn_CheckNow;
    private Button back;
    private SharedPreferences mAutoCheckSet;
    private TextView mTxtProduct;
    private TextView mTxtVersion;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.setting);
        mContext = this;
        mBtn_CheckNow = (ImageButton) this.findViewById(R.id.btn_check_now);
        back = (Button) this.findViewById(R.id.back);
        mTxtProduct = (TextView) this.findViewById(R.id.txt_product);
        mTxtVersion = (TextView) this.findViewById(R.id.txt_version);
        mTxtProduct.setText(RTKUpdateService.getOtaProductName());
        mTxtVersion.setText(RTKUpdateService.getSystemVersion());

        mAutoCheckSet = getSharedPreferences("auto_check", MODE_PRIVATE);

        mBtn_CheckNow.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent serviceIntent;
                serviceIntent = new Intent(mContext, RTKUpdateService.class);
                serviceIntent.putExtra("command", RTKUpdateService.COMMAND_CHECK_REMOTE_UPDATING_BY_HAND);
                mContext.startService(serviceIntent);
            }

        });

        back.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }

        });
    }

}
