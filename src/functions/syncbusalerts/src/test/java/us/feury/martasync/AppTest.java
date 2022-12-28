package us.feury.martasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class AppTest {

    @Test
    public void handleRequest_shouldReturnConstantValue() {
        MartaSyncFunction function = new MartaSyncFunction();
        Object result = function.handleRequest("echo", null);
        assertEquals("echo", result);
    }
}
