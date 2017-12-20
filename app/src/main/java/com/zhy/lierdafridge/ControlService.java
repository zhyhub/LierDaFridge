package com.zhy.lierdafridge;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.google.gson.Gson;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.signway.SignwayManager;
import com.zhy.lierdafridge.bean.BaseEntity;
import com.zhy.lierdafridge.bean.ZigbeeBean;
import com.zhy.lierdafridge.utils.BaseCallBack;
import com.zhy.lierdafridge.utils.BaseOkHttpClient;
import com.zhy.lierdafridge.utils.JsonParser;
import com.zhy.lierdafridge.utils.L;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;

/**
 * Created by Administrator on 2017/12/20 0020.
 */

public class ControlService extends AccessibilityService {

    private static String TAG = ControlService.class.getSimpleName();

    // 语音听写对象
    private SpeechRecognizer mIat;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;

    private SpeechSynthesizer mTts;

    private static int CurrentTemp = 0;

    private SignwayManager mSignwayManager;
    private int readLength;
    private int fid = -1;

    private NetWorkStateReceiver receiver;

    int ret = 0; // 函数调用返回值
    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;

    /**
     * 音量控制
     */
    private AudioManager audioManager;

    private ZigbeeBean zigbeeBean;
    private ZigbeeBean.AttributesBean attributesBean = new ZigbeeBean.AttributesBean();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createSocket();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(ControlService.this, mInitListener);
        mTts = SpeechSynthesizer.createSynthesizer(ControlService.this, mTtsInitListener);
        // 设置参数
        setTtsParam();
        initUart();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        L.e(TAG, "当前音量    " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "  最大音量  " + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

        if (receiver == null) {
            receiver = new NetWorkStateReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(receiver, filter);

        L.e(TAG, "ControlService ControlService ControlService ControlService");
    }

    @Override
    public void onDestroy() {
        Intent service = new Intent(this, ControlService.class);
        this.startService(service);
        closeSocket();
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    /**
     * 讯飞唤醒监听
     *
     * @param event 监听事件
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.v(TAG, "onKeyEvent");
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_F1:
                //接受到f1信号，设备已经被唤醒，调用讯飞语音识别
                L.e(TAG, "接受到f1信号，设备已经被唤醒，调用讯飞语音识别");
                if (mTts.isSpeaking()) {
                    mTts.stopSpeaking();
                }
                if (mIat.isListening()) {
                    mIat.stopListening();
                }
                int code = mTts.startSpeaking("你要说什么", mTtsListener);
                /*
                 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
		         * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
		        */
                if (code != ErrorCode.SUCCESS) {
                    showTip("语音合成失败,错误码: " + code);
                }
                break;
        }
        return super.onKeyEvent(event);
    }

    private void showTip(final String str) {
        L.e(TAG, str);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {
        L.d(TAG, "Interrupt");
    }


//=============================================================  下面是讯飞所需  ======================================================================================================

    private void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        // 设置语言
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, "10000");
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, "1000");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");
        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/iat.wav");
    }

    private void setTtsParam() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        // 根据合成引擎设置相应参数
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            // 设置在线合成发音人
            String voicer = "nannan";
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
            //设置合成语速
            mTts.setParameter(SpeechConstant.SPEED, "55");
            //设置合成音调
            mTts.setParameter(SpeechConstant.PITCH, "60");
            //设置合成音量
            mTts.setParameter(SpeechConstant.VOLUME, "100");
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            // 设置本地合成发音人 voicer为空，默认通过语记界面指定发音人。
            mTts.setParameter(SpeechConstant.VOICE_NAME, "");
            /*
             * TODO 本地合成不设置语速、音调、音量，默认使用语记设置
             */
        }
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");
    }

    /**
     * 合成回调监听。
     */
    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            showTip("开始播放");
        }

        @Override
        public void onSpeakPaused() {
            showTip("暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            showTip("继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
            // 合成进度
            mPercentForBuffering = percent;
//            showTip(String.format(getString(R.string.tts_toast_format),
//                    mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
//            showTip(String.format(getString(R.string.tts_toast_format),
//                    mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                showTip("播放完成");
                mIatResults.clear();
                // 设置参数
                setParam();
                ret = mIat.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("听写失败,错误码：" + ret);
                } else {
                    showTip(getString(R.string.text_begin));
                }
            } else {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            L.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

    /**
     * 初始化监听。
     */
    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            L.d(TAG, "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code);
            }
        }
    };

    /**
     * 讯飞听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            showTip(error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            L.d(TAG, results.getResultString());

            String msg = printResult(results);

            if (isLast) {
                // TODO 最后的结果
                L.e(TAG, "msg    " + msg);
                if (!msg.equals("")) {
                    sendMsg(msg, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                }
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
//            showTip("当前正在说话，音量大小：" + volume);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    /**
     * 拼接语音
     *
     * @param results 讯飞识别返回的结果
     * @return 返回拼接的结果
     */
    private String printResult(RecognizerResult results) {
        L.e(TAG, "printResult");
        String text = JsonParser.parseIatResult(results.getResultString());
        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mIatResults.put(sn, text);
        StringBuilder resultBuffer = new StringBuilder();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }
        L.e(TAG, "TAG   printResult " + resultBuffer.toString());

        return resultBuffer.toString();
    }

//=============================================================  下面是监听wifi连接情况  ======================================================================================================

    private static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        assert connectivityManager != null;
        NetworkInfo info = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return info.isConnected();
    }

    private class NetWorkStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            L.e("网络状态发生变化");
            if (isWifiConnected(ControlService.this)) {
                mTts.startSpeaking("网络连接成功了了了了了", mTtsListener);
            } else {
                mTts.startSpeaking("网络连接已断开", mTtsListener);
            }
        }
    }


//=============================================================  下面是请求海知语音获取意图 并做相应的指令操作 ======================================================================================================

    private void sendMsg(String txt, int currentVolume, final int maxVolume) {
        L.e(TAG, "  sendMsg   " + "   currentVolume   " + currentVolume + "   maxVolume   " + maxVolume);
        BaseOkHttpClient.newBuilder()
                .addParam("q", txt)
                .addParam("currentVolume", currentVolume)
                .addParam("maxVolume", maxVolume)
                .addParam("data", Arrays.toString(new byte[48]))
                .addParam("user_id", "123456")
                .addParam("refrigeratorId", "1")
                .get()
                .url(ConstantPool.AI)
                .build().enqueue(new BaseCallBack() {
            @Override
            public void onSuccess(Object o) {
                L.e(TAG, "onSuccess" + o.toString());
                if (mIat.isListening()) {
                    mIat.stopListening();
                }
                Gson gson = new Gson();
                BaseEntity entity = gson.fromJson(o.toString(), BaseEntity.class);
                if (entity.getCode() == 1) {
                    switch (entity.getType()) {
                        case 0://不操作指令
                            TTS(entity);
                            break;
                        case 2://音量操作
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, entity.getVolume(), 0);
                            TTS(entity);
                            break;
                        case 101://启动-手机无线充电设备
                            mSignwayManager.openGpioDevice();
                            mSignwayManager.setHighGpio(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN23);
                            TTS(entity);
                            break;
                        case 102://关闭-手机无线充电设备
                            mSignwayManager.openGpioDevice();
                            mSignwayManager.setLowGpio(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN23);
                            TTS(entity);
                            break;
                        case 103://启动-台灯无线充电设备
                            mSignwayManager.openGpioDevice();
                            mSignwayManager.setLowGpio(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN24);
                            TTS(entity);
                            break;
                        case 104://关闭-台灯无线充电设备
                            mSignwayManager.openGpioDevice();
                            mSignwayManager.setLowGpio(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN24);
                            TTS(entity);
                            break;
                        case 107://启动-电灯
                            CurrentTemp = 60;
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(-1);
                            zigbeeBean.setId("00124B000B277AD8");

                            attributesBean = new ZigbeeBean.AttributesBean();
                            attributesBean.setTYP("LT-CTM");
                            attributesBean.setLEV(String.valueOf(CurrentTemp));
                            attributesBean.setSWI("ON");

                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  Open " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 108://关闭-电灯
                            CurrentTemp = 0;
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(-1);
                            zigbeeBean.setId("00124B000B277AD8");

                            attributesBean = new ZigbeeBean.AttributesBean();

                            attributesBean.setLEV(String.valueOf(CurrentTemp));
                            attributesBean.setSWI("OFF");
                            attributesBean.setTYP("LT-CTM");

                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  Close " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 110://点灯亮度调高
                            CurrentTemp = CurrentTemp + 20;
                            if (CurrentTemp <= 100) {
                                zigbeeBean = new ZigbeeBean();
                                zigbeeBean.setSourceId("009569B4662A");
                                zigbeeBean.setRequestType("cmd");
                                zigbeeBean.setSerialNum(-1);
                                zigbeeBean.setId("00124B000B277AD8");

                                attributesBean = new ZigbeeBean.AttributesBean();

                                attributesBean.setTYP("LT-CTM");
                                attributesBean.setLEV(String.valueOf(CurrentTemp));
                                attributesBean.setSWI("ON");
                                zigbeeBean.setAttributes(attributesBean);

                                L.e(TAG, "ZigbeeBean  Up " + new Gson().toJson(zigbeeBean));
                                sendData(new Gson().toJson(zigbeeBean));
                                TTS(entity);
                            } else {
                                mTts.startSpeaking("已经是最大亮度了", mTtsListener);
                                CurrentTemp = 100;
                            }
                            break;
                        case 111://点灯亮度调底
                            CurrentTemp = CurrentTemp - 20;
                            if (CurrentTemp >= 20) {
                                zigbeeBean = new ZigbeeBean();
                                zigbeeBean.setSourceId("009569B4662A");
                                zigbeeBean.setRequestType("cmd");
                                zigbeeBean.setSerialNum(-1);
                                zigbeeBean.setId("00124B000B277AD8");

                                attributesBean = new ZigbeeBean.AttributesBean();

                                attributesBean.setTYP("LT-CTM");
                                attributesBean.setLEV(String.valueOf(CurrentTemp));
                                attributesBean.setSWI("ON");

                                zigbeeBean.setAttributes(attributesBean);

                                L.e(TAG, "ZigbeeBean  attributesBean " + new Gson().toJson(zigbeeBean));
                                sendData(new Gson().toJson(zigbeeBean));
                                TTS(entity);
                            } else {
                                mTts.startSpeaking("已经是最低亮度了", mTtsListener);
                                CurrentTemp = 20;
                            }
                            break;
                        case 112://点灯亮度调到最高
                            CurrentTemp = 100;
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(-1);
                            zigbeeBean.setId("00124B000B277AD8");

                            attributesBean = new ZigbeeBean.AttributesBean();

                            attributesBean.setTYP("LT-CTM");
                            attributesBean.setLEV(String.valueOf(CurrentTemp));
                            attributesBean.setSWI("ON");
                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  attributesBean " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 113://点灯亮度调到最底
                            CurrentTemp = 20;
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(-1);
                            zigbeeBean.setId("00124B000B277AD8");

                            attributesBean = new ZigbeeBean.AttributesBean();

                            attributesBean.setTYP("LT-CTM");
                            attributesBean.setLEV(String.valueOf(CurrentTemp));
                            attributesBean.setSWI("ON");
                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  Min " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 105://启动-窗帘
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(-1);
                            zigbeeBean.setId("00124B0009E8D140");

                            attributesBean = new ZigbeeBean.AttributesBean();
                            attributesBean.setTYP("WD-RXJ");
                            attributesBean.setWIN("OPEN");

                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  CurtainsOpen " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 106://关闭-窗帘
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(1);
                            zigbeeBean.setId("00124B0009E8D140");

                            attributesBean = new ZigbeeBean.AttributesBean();
                            attributesBean.setTYP("WD-RXJ");
                            attributesBean.setWIN("CLOSE");

                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  CurtainsClose " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                        case 109://停止-窗帘
                            zigbeeBean = new ZigbeeBean();
                            zigbeeBean.setSourceId("009569B4662A");
                            zigbeeBean.setRequestType("cmd");
                            zigbeeBean.setSerialNum(1);
                            zigbeeBean.setId("00124B0009E8D140");

                            attributesBean = new ZigbeeBean.AttributesBean();
                            attributesBean.setTYP("WD-RXJ");
                            attributesBean.setWIN("STOP");

                            zigbeeBean.setAttributes(attributesBean);

                            L.e(TAG, "ZigbeeBean  CurtainsStop " + new Gson().toJson(zigbeeBean));
                            sendData(new Gson().toJson(zigbeeBean));
                            TTS(entity);
                            break;
                    }
                }
            }

            @Override
            public void onError(int code) {
                L.e(TAG, "sendMsg onError");
                mTts.startSpeaking("哎呀，好像出问题了", mTtsListener);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                L.e(TAG, "onFailure" + e.getMessage());
            }
        });
    }

    private void TTS(BaseEntity entity) {
        if (!entity.getText().equals("")) {
            mTts.startSpeaking(entity.getText(), mTtsListener);
        }
    }

//=============================================================  下面是控制利尔达设备逻辑 ======================================================================================================

    private Handler mMainHandler;
    private Socket socket;
    private ExecutorService mThreadPool;
    private InputStream is;
    private InputStreamReader isR;
    private BufferedReader br;
    private String response;
    private OutputStream outputStream;
    private static final int MSG_SOCKET = 1234;

    private Handler nfcHandler;
    private Runnable nfcUpdate;

    private void initUart() {
        mSignwayManager = SignwayManager.getInstatnce();
        if (fid < 0) {
            fid = mSignwayManager.openUart("dev/ttyS2", 9600);

        }

//        nfcHandler = new Handler();
//        nfcUpdate = new Runnable() {
//            @Override
//            public void run() {
//                mSignwayManager.openGpioDevice();
//                //配置SWH5528_J9_PIN1,对应GPIO2_A6
//                mSignwayManager.setGpioNum(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN24,
//                        SignwayManager.GPIOGroup.GPIO0, SignwayManager.GPIONum.PD2);
//                int state = mSignwayManager.getGpioStatus(SignwayManager.ExterGPIOPIN.SWH5528_J9_PIN24);
//                L.e(TAG, "  state  : " + state);
//                nfcHandler.postDelayed(nfcUpdate, 500);
//            }
//        };
//        nfcHandler.post(nfcUpdate);
    }

    protected void createSocket() {
        L.e(TAG, "createSocket() called with: ip = [" + "192.168.100.1" + "], port = [" + 8888 + "]");

        //初始化线程池
        mThreadPool = Executors.newCachedThreadPool();

        //实例化主线程，用于更新接收过来的消息
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SOCKET:
                        String response = (String) msg.obj;
                        String sourceId = "";
                        int serialNum = 0;
                        String requestType = "";
                        String id = "";
                        int state = 0;
                        if ("".equals(response)) {
                            try {
                                JSONObject jsonObject = new JSONObject(response);
                                if (jsonObject.has("stateCode")) {
                                    state = jsonObject.getInt("stateCode");
                                    serialNum = jsonObject.getInt("serialNum");
                                    sourceId = jsonObject.getString("sourceId");
                                    requestType = jsonObject.getString("requestType");
                                    id = jsonObject.getString("id");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if (state == 1) {
                                L.e(TAG, "操作成功   " + "sourceId  " + sourceId + "   serialNum " + serialNum + "  requestType   " + requestType + "   id   " + id);
                            } else {
                                L.e(TAG, "操作失败");
                            }
                        }
                        break;
                }
            }
        };

        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket("192.168.100.1", 8888);
                    L.e(TAG, "Socket connected? " + socket.isConnected());
                } catch (IOException | NullPointerException e) {
                    L.e(TAG, e.getMessage() + "     " + e);
                }
            }
        });
    }

    private void closeSocket() {
        Log.d(TAG, "closeSocket() called");
        if (mThreadPool == null) {
            mThreadPool = Executors.newCachedThreadPool();
        }
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (br != null) {
                        br.close();
                    }
                    if (socket != null) {
                        socket.close();
                        L.e(TAG, "DisConnected? " + !socket.isConnected());
                    }
                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendData(final String content) {
        L.e(TAG, "sendData() called with: content = [" + content + "]");
        if (mThreadPool == null) {
            mThreadPool = Executors.newCachedThreadPool();
        }
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket == null) {
                        socket = new Socket("192.168.100.1", 8888);
                    }
                    OutputStream outputStream = socket.getOutputStream();
                    byte buffer[] = content.getBytes();
                    int temp = buffer.length;
                    outputStream.write(buffer, 0, buffer.length);
                    outputStream.flush();
                } catch (IOException | NullPointerException e) {
                    L.e(TAG, e.getMessage() + "    " + e);
                }
            }
        });
        receiveData();
    }

    private void receiveData() {
        Log.d(TAG, "receiveData() called");
        if (mThreadPool == null) {
            mThreadPool = Executors.newCachedThreadPool();
        }
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket == null) {
                        socket = new Socket("192.168.100.1", 8888);
                    }
                    is = socket.getInputStream();
                    isR = new InputStreamReader(is);
                    br = new BufferedReader(isR);
                    response = br.readLine();
                    L.d(TAG, "Result: " + response);

                    Message msg = Message.obtain();
                    msg.what = MSG_SOCKET;
                    msg.obj = response;
                    mMainHandler.sendMessage(msg);

                } catch (IOException | NullPointerException e) {
                    L.e(TAG, "操作失败" + e.getMessage() + "    " + e);
                }
            }
        });
    }
}
