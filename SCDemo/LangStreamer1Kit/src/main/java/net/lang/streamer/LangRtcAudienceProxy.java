package net.lang.streamer;

import net.lang.streamer.rtc.LangRtcAudience;

import android.content.Context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by lang on 2018/3/20.
 */

public class LangRtcAudienceProxy {
    private Object mTarget;

    public LangRtcAudienceProxy(Object target) {
        this.mTarget = target;
    }

    public Object getProxyInstance() {
        return Proxy.newProxyInstance(
                mTarget.getClass().getClassLoader(),
                mTarget.getClass().getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        System.out.println("begin transaction");
                        Object returnValue = method.invoke(mTarget, args);
                        System.out.println("commit transaction");
                        return returnValue;
                    }
                }
        );
    }

    public static ILangRtcAudience newProxyInstance(Context context) {

        ILangRtcAudience rtcAudience = new LangRtcAudience(context);
        ILangRtcAudience rtcAudienceProxy = (ILangRtcAudience)new LangRtcAudienceProxy(rtcAudience).getProxyInstance();
        return rtcAudienceProxy;
        //return rtcAudience;
    }
}
