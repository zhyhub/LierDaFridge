package com.zhy.lierdafridge;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.iflytek.cloud.SpeechUtility;
import com.zhy.lierdafridge.utils.L;

import org.litepal.LitePalApplication;

import static android.content.ContentValues.TAG;

/**
 * Created by Administrator on 2017/12/20 0020.
 */

public class MyApplication extends LitePalApplication {
    @Override
    public void onCreate() {
        // 应用程序入口处调用，避免手机内存过小，杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
        // 如在Application中调用初始化，需要在Mainifest中注册该Applicaiton
        // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
        // 参数间使用半角“,”分隔。
        // 设置你申请的应用appid,请勿在'='与appid之间添加空格及空转义符

        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
        SpeechUtility.createUtility(MyApplication.this,"appid=" + getString(R.string.app_id));
        // 以下语句用于设置日志开关（默认开启），设置成false时关闭语音云SDK日志打印
        // Setting.setShowLog(false);
        super.onCreate();

        startAccessibilityService(MyApplication.this);
    }

    private void startAccessibilityService(Context context) {
        L.e(TAG, "startAccessibilityService() called");
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        L.e(TAG, "enabledServicesSetting: " + enabledServicesSetting);
        ComponentName selfComponentName = new ComponentName(context.getPackageName(), ControlService.class.getName());
        String flattenToString = selfComponentName.flattenToString();
        L.e(TAG, "flattenToString: " + flattenToString);
        if (enabledServicesSetting == null || !enabledServicesSetting.contains(flattenToString)) {
            if (TextUtils.isEmpty(enabledServicesSetting) || TextUtils.equals(enabledServicesSetting, "null")) {
                enabledServicesSetting = flattenToString;
            } else {
                enabledServicesSetting += flattenToString;
            }
        }
        Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServicesSetting);
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }
}
