package net.lang.streamer.widget;

public class LangPushAuthentication {
    private static LangPushAuth langPushAuthentication;

    private LangPushAuthentication() {

    }

    public static LangPushAuth getInstance() {
        if (langPushAuthentication == null)
            langPushAuthentication = new LangPushAuthenticationImp();
        return langPushAuthentication;
    }
}
