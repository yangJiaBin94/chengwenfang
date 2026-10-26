package com.workbench.app.adapter.web.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {
  @Test
  void detects_windows_client_abort_message() {
    assertTrue(GlobalExceptionHandler.isClientDisconnected(
        new IOException("你的主机中的软件中止了一个已建立的连接。")));
    assertTrue(GlobalExceptionHandler.isClientDisconnected(
        new IOException("Broken pipe")));
    assertFalse(GlobalExceptionHandler.isClientDisconnected(
        new IOException("disk full")));
  }
}
