package net.lang.streamer2.engine.publish;

import net.lang.streamer2.engine.data.LangFrameStatistics;
import net.lang.streamer2.engine.data.LangRtmpBufferStatus;
import net.lang.streamer2.engine.data.LangRtmpStatus;

public interface INetworkListener {

    void onSocketBufferStatus(LangRtmpBufferStatus status);

    void onSocketStatus(LangRtmpStatus status);

    void onSocketStatistics(LangFrameStatistics frameStatistics);
}
