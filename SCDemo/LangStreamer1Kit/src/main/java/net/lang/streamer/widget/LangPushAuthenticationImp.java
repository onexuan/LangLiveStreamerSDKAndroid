package net.lang.streamer.widget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class LangPushAuthenticationImp implements LangPushAuth {
    private final String TAG = LangPushAuthenticationImp.class.getSimpleName();
   // private static LangPushAuthenticationImp langPushAuthentication;
    private boolean authenticateSucceed;
    private boolean authenticating;
    private String app_id = "";
    private String app_key;
    private LangAuthCallBack mCallback;
    private final String DATE = "date";
    private long httpHeaderTime;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg != null) {
                Bundle bundle = msg.getData();
                if (bundle != null && bundle.getString(DATE) != null) {
                    dealAuthResult(bundle.getString(DATE));
                    return;
                }
            }
            authenticating = false;
            authenticateSucceed = false;
        }
    };
    private Runnable authRunnable = new Runnable() {
        @Override
        public void run() {
            OutputStreamWriter osw = null;
            try {
                URL url = new URL("https://api.s.lang.live/sdk/auth");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("platform", "Android");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.connect();
                osw = new OutputStreamWriter(connection.getOutputStream(), "utf-8");
                String params = String.format("app_id=%s&app_key=%s", app_id, app_key);
                Log.d(TAG, params);
                osw.write(params);
                osw.flush();
                osw.close();
                httpHeaderTime = connection.getHeaderFieldDate("Date", System.currentTimeMillis())/1000;
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
                    ByteArrayOutputStream bas = new ByteArrayOutputStream();
                    byte[] bytes = new byte[1024];
                    int len;
                    while ((len = bis.read(bytes)) > 0) {
                        bas.write(bytes, 0, len);
                    }
                    bas.flush();
                    String result = new String(bas.toByteArray(), "utf-8");
                    bis.close();
                    bas.close();
                    Log.d(TAG, result);
                    Message msg = Message.obtain();
                    Bundle bundle = new Bundle();
                    bundle.putString(DATE, result);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                    return;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            authenticating = false;
            authenticateSucceed = false;
        }
    };

    private void dealAuthResult(String result) {
        AuthData authData = parseResult(result);
        if (authData != null && authData.authInfo != null &&
                app_id.equals(authData.authInfo.appid) && httpHeaderTime < authData.authInfo.valid_until) {
            authenticating = false;
            authenticateSucceed = true;
            if (mCallback != null)
                mCallback.onAuthenticateSuccess();
            return;
        } else {
            if (mCallback != null && authData != null)
                mCallback.onAuthenticateError(authData.ret_code, authData.ret_msg);
        }
        authenticating = false;
        authenticateSucceed = false;
    }

    private AuthData parseResult(String result) {
        AuthData authData = new AuthData();
        AuthInfo authInfo = new AuthInfo();
        authData.authInfo = authInfo;
        if (!isStringEmpty(result)) {
            try {
                JSONObject baseJson = new JSONObject(result);
                int ret_code = baseJson.getInt("ret_code");
                String ret_msg = baseJson.optString("ret_msg");
                String data = baseJson.optString("data");
                if (!isStringEmpty(data) && data.startsWith("{") && data.endsWith("}")) {
                    JSONObject jsonObject = new JSONObject(data);
                    String appid = jsonObject.optString("appid");
                    String platform = jsonObject.optString("platform");
                    long valid_start = jsonObject.getLong("valid-start");
                    long valid_util = jsonObject.getLong("valid-until");
                    authInfo.appid = appid;
                    authInfo.platform = platform;
                    authInfo.valid_start = valid_start;
                    authInfo.valid_until = valid_util;
                }
                authData.ret_code = ret_code;
                authData.ret_msg = ret_msg;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return authData;
    }

    private boolean isStringEmpty(String s) {
        if (s == null || s.trim().equals("null") || s.trim().equals("")) {
            return true;
        }
        return false;
    }

    public void authenticate(String app_id, String app_key, LangAuthCallBack callBack) {
        if (this.authenticateSucceed) {
            Log.d(TAG, "authenticate has already succeeded !");
        } else {
            this.authenticateSucceed = false;
            this.authenticating = true;
            this.mCallback = callBack;
            this.app_id = app_id;
            this.app_key = app_key;
            new Thread(authRunnable).start();
        }
    }

    public boolean isAuthenticateSucceed() {
        return this.authenticateSucceed;
    }

    public boolean isAuthenticating() {
        return this.authenticating;
    }

    private class AuthData {
        public int ret_code;
        public String ret_msg;
        public AuthInfo authInfo;
    }

    private class AuthInfo {
        public String appid;
        public String platform;
        public long valid_start;
        public long valid_until;
    }
}
