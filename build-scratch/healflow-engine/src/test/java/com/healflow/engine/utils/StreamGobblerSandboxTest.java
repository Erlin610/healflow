package com.healflow.engine.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamGobblerSandboxTest {

  @Test
  void streamsLinesToConsumer() {
    List<String> lines = new ArrayList<>();
    InputStream inputStream = new ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8));

    new StreamGobbler(inputStream, lines::add).run();

    assertEquals(List.of("a", "b"), lines);
  }

  @Test
  void swallowsReadFailures() {
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("boom");
          }
        };

    assertDoesNotThrow(() -> new StreamGobbler(failing, line -> fail("unexpected: " + line)).run());
  }
}

