package net.lang.streamer.widget;

public interface LangPushAuth {
    void authenticate(String app_id, String app_key, LangAuthCallBack callBack);
    boolean isAuthenticateSucceed();
    boolean isAuthenticating();
}
