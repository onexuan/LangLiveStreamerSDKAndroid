package net.lang.streamer.engine;

/**
 * Created by lichao on 17-5-4.
 */

public interface IMediaPublisherListener {
    enum Value {
        kFailed,
        kSucceed
    }

    enum Type {
        kTypeMic,
        kTypeAudioEncoder,
        kTypeVideoEncoder,
        kTypeRecord
    }

    void onPublisherEvent(IMediaPublisherListener.Type t, IMediaPublisherListener.Value v);
}
