package net.lang.streamer.rtc.io.agora.avcapture.model;

/**
 * Created by lang on 2017/9/1.
 */

public class Message {
    private User mSender;

    private String mContent;

    private int mType;

    public Message(int type, User sender, String content) {
        mType = type;
        mSender = sender;
        mContent = content;
    }

    public Message(User sender, String content) {
        this(0, sender, content);
    }

    public User getSender() {
        return mSender;
    }

    public String getContent() {
        return mContent;
    }

    public int getType() {
        return mType;
    }

    public static final int MSG_TYPE_TEXT = 1; // CHANNEL
}
