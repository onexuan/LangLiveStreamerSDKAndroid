package net.lang.streamer2.engine.capture;

public class CaptureRuntimeException extends RuntimeException{
    public CaptureRuntimeException () {
    }

    public CaptureRuntimeException(String message) {
        super(message);
    }

    public CaptureRuntimeException(Throwable cause) {
        super(cause);
    }

    public CaptureRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
