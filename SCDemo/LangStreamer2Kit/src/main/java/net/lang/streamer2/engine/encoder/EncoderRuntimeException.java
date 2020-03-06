package net.lang.streamer2.engine.encoder;

public class EncoderRuntimeException extends RuntimeException {

    public EncoderRuntimeException () {
    }

    public EncoderRuntimeException(String message) {
        super(message);
    }

    public EncoderRuntimeException(Throwable cause) {
        super(cause);
    }

    public EncoderRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
