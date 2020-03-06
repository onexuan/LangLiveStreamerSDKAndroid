package net.lang.streamer.widget;

public interface LangAuthCallBack {
    void onAuthenticateSuccess();
    void onAuthenticateError(int ret_code, String ret_msg);
}
