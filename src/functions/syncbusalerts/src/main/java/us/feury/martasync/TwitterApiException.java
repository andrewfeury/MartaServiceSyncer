package us.feury.martasync;

public class TwitterApiException extends Exception {

    public TwitterApiException(String message) {
        super(message);
    }

    public TwitterApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
