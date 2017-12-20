package com.zhy.lierdafridge.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Created by lijin on 2016/7/26.
 * okhttp封装类
 */
public class OkHttpUtils {
    //创建okHttpClient对象
    private OkHttpClient mOkHttpClient = new OkHttpClient();
    private Gson mGson;
    private Handler mDelivery;
    private static OkHttpUtils mInstance;

    /**
     * 初始化
     */
    private OkHttpUtils() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(20000, TimeUnit.SECONDS);
        builder.readTimeout(30, TimeUnit.SECONDS);//读取超时
        builder.connectTimeout(10, TimeUnit.SECONDS);//连接超时
        builder.writeTimeout(60, TimeUnit.SECONDS);//写入超时
        mOkHttpClient = builder.build();
        //cookie enabled
        mDelivery = new Handler(Looper.getMainLooper());
        mGson = new Gson();
    }

    /**
     * 单例模式
     *
     * @return
     */
    public static OkHttpUtils getInstance() {
        if (mInstance == null) {
            synchronized (OkHttpUtils.class) {
                if (mInstance == null) {
                    mInstance = new OkHttpUtils();
                }
            }
        }
        return mInstance;
    }


    /**
     * 开启异步线程访问网络
     *
     * @param request
     * @param callback
     */
    public void enqueue(Request request, final RequestCallBack callback) {
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                sendFailedStringCallback(call, e, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final String string = response.body().string();
                    if (callback.mType == String.class) {
                        sendSuccessResultCallback(string, callback);
                    } else {
                        Object o = mGson.fromJson(string, callback.mType);//解析返回数据
                        sendSuccessResultCallback(o, callback);
                    }
                } catch (IOException e) {
                    sendFailedStringCallback(call, e, callback);
                } catch (com.google.gson.JsonParseException e) {//Json解析的错误
                    sendFailedStringCallback(call, e, callback);
                }
            }
        });
    }

    /**
     * 开启异步线程访问网络, 且不在意返回结果（实现空callback）
     *
     * @param request
     */
    public void enqueue(Request request) {
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
    }

    public static void get(String url) {
        //创建okHttpClient对象
        OkHttpClient mOkHttpClient = new OkHttpClient();
        //创建一个Request
        final Request request = new Request.Builder()
                .url(url)
                .build();
        //new call
        Call call = mOkHttpClient.newCall(request);
        //非阻塞式访问，请求加入调度
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //String htmlStr =  response.body().string();
                //byte[] resultBytes = response.body().bytes();
                //InputStream inputStream = response.body().byteStream();
            }
        });
//        阻塞式访问
//        Response response = call.execute();
    }

    /**
     * 下载文件
     *
     * @param url
     * @throws Exception
     */
    public void getForAsynchronization(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                Headers responseHeaders = response.headers();
                for (int i = 0; i < responseHeaders.size(); i++) {
                    System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
                }
                System.out.println(response.body().string());
            }
        });
    }

    /**
     * 下载文件  --对外暴露的接口
     *
     * @param url
     */
    public static void getFile(String url) {
        getInstance().getForAsynchronization(url);
    }

    /**
     * 以post方式提交json数据
     *
     * @param url
     * @param json
     * @param callback
     * @throws IOException
     */
    public void postForJsonAsynchronization(String url, String json, RequestCallBack callback) {
        Request request = buildPostRequest(url, json);
        getInstance().enqueue(request, callback);
    }


    /**
     * Post方式提交表单数据
     *
     * @param url
     * @param map
     * @param callback
     * @return
     * @throws IOException
     */
    public void postForMapAsynchronization(String url, Map<String, Object> map, RequestCallBack callback) {
        Request request = buildPostRequest(url, map);
        getInstance().enqueue(request, callback);
    }

    public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("text/x-markdown; charset=utf-8");

    /**
     * 以post方式提交string数据
     *
     * @param url
     * @param str
     * @param callback
     * @throws Exception
     */
    public static void postStringForAsynchronization(String url, String str, RequestCallBack callback) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MEDIA_TYPE_MARKDOWN, str))
                .build();
        getInstance().enqueue(request, callback);
    }

    /**
     * 以post方式提交Byte数据
     *
     * @param url
     * @param str
     * @param callback
     * @throws Exception
     */
    public static void postByteForAsynchronization(String url, final String str, RequestCallBack callback) {
        RequestBody requestBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MEDIA_TYPE_MARKDOWN;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(str);
                for (int i = 2; i <= 997; i++) {
                    sink.writeUtf8(String.format(str, i, factor(i)));
                }
            }

            private String factor(int n) {
                for (int i = 2; i < n; i++) {
                    int x = n / i;
                    if (x * i == n) return factor(x) + " × " + i;
                }
                return Integer.toString(n);
            }
        };
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        getInstance().enqueue(request, callback);
    }


    private static final String IMGUR_CLIENT_ID = "...";
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");

    /**
     * 分块请求
     *
     * @param url
     * @param json
     * @param filePath
     * @param callback
     * @throws Exception
     */
    public void postMultipartForAsynchronization(String url, String json, final String filePath, RequestCallBack callback) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"title\""),
                        RequestBody.create(null, json))
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"image\""),
                        RequestBody.create(MEDIA_TYPE_PNG, new File(filePath)))
                .build();
        Request request = new Request.Builder()
                .header("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                .url(url)
                .post(requestBody)
                .build();
        getInstance().enqueue(request, callback);
    }

    /**
     * 回掉接口
     *
     * @param <T>
     */
    public static abstract class RequestCallBack<T> {
        Type mType;

        public RequestCallBack() {
            mType = getSuperclassTypeParameter(getClass());
        }

        static Type getSuperclassTypeParameter(Class<?> subclass) {
            Type superclass = subclass.getGenericSuperclass();
            if (superclass instanceof Class) {
                throw new RuntimeException("Missing type parameter.");
            }
            ParameterizedType parameterized = (ParameterizedType) superclass;
            return $Gson$Types.canonicalize(parameterized.getActualTypeArguments()[0]);
        }

        public abstract void onError(Call call, Exception e);

        public abstract void onResponse(T response);
    }

    /**
     * 请求错误
     *
     * @param call
     * @param e
     * @param callback
     */
    private void sendFailedStringCallback(final Call call, final Exception e, final RequestCallBack callback) {
        mDelivery.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null)
                    callback.onError(call, e);
            }
        });
    }

    /**
     * 请求成功
     *
     * @param object
     * @param callback
     */
    private void sendSuccessResultCallback(final Object object, final RequestCallBack callback) {
        mDelivery.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onResponse(object);
                }
            }
        });
    }

    /**
     * 组装Request
     *
     * @param url
     * @param object
     * @return
     */
    private Request buildPostRequest(String url, Object object) {
        String token = "";
        Param[] params = null;
        if (object instanceof String) {
            params = json2Params((String) object);
        } else if (object instanceof Map) {
            params = map2Params((Map<String, Object>) object);
        }
        if (params == null) {
            params = new Param[0];
        }
        MultipartBody.Builder builder = new MultipartBody.Builder();
        //设置类型
        builder.setType(MultipartBody.FORM);
        for (Param param : params) {
            if (param.value != null) {
                if (param.value instanceof File) {
                    File file = (File) param.value;
                    builder.addFormDataPart(param.key, file.getName(), RequestBody.create(MEDIA_TYPE_PNG, file));
                } else if (param.value instanceof String) {
                    if (param.key.equals("token")) {
                        token = (String) param.value;
                    }
                    builder.addFormDataPart(param.key, (String) param.value);
                } else if (param.value instanceof Integer) {
                    builder.addFormDataPart(param.key, String.valueOf(param.value));
                } else if (param.value instanceof Double) {
                    builder.addFormDataPart(param.key, String.valueOf(param.value));
                } else if (param.value instanceof Float) {
                    builder.addFormDataPart(param.key, String.valueOf(param.value));
                } else if (param.value instanceof Long) {
                    builder.addFormDataPart(param.key, String.valueOf(param.value));
                } else {
                    builder.addFormDataPart(param.key, (String) param.value);
                }
            }
        }
        //创建RequestBody
        RequestBody requestBody = builder.build();

        return new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("token", token)
                .build();
    }

    /**
     * param类
     */
    public static class Param {
        public Param() {
        }

        public Param(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        String key;
        Object value;
    }


    private Param[] map2Params(Map<String, Object> params) {
        if (params == null) return new Param[0];
        int size = params.size();
        Param[] res = new Param[size];
        Set<Map.Entry<String, Object>> entries = params.entrySet();
        int i = 0;
        for (Map.Entry<String, Object> entry : entries) {
            res[i++] = new Param(entry.getKey(), entry.getValue());
        }
        return res;
    }

    private Param[] json2Params(String params) {
        if (params == null) return new Param[0];
        Gson gson = new Gson();
        Map infoMap = gson.fromJson(params, new TypeToken<Map<String, Object>>() {
        }.getType());
        return map2Params(infoMap);
    }
}
