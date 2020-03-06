package net.lang.streamer2.engine.publish;

public interface IRecordListener {
    void onRecordStart(String url);
    void onRecordProgress(String url, long currentMillSeconds);
    void onRecordEnd(String url, long totalTimeMilliSeconds);
    void onRecordError(String url, int errorCode, String errorDescription);
}
