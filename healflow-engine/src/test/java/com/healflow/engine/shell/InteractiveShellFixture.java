package com.healflow.engine.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class InteractiveShellFixture {

  private InteractiveShellFixture() {}

  public static void main(String[] args) throws Exception {
    String mode = args.length == 0 ? "prompt" : args[0];
    if ("env".equals(mode)) {
      String value = System.getenv("HEALFLOW_TEST_ENV");
      System.out.print(value == null ? "" : value);
      return;
    }

    System.out.print("Continue? (y/n): ");
    System.out.flush();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if ("y".equalsIgnoreCase(line)) {
        System.out.println("ACK");
      } else {
        System.out.println("NACK");
      }
    }
  }
}

