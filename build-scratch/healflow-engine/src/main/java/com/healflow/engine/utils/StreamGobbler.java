package com.healflow.engine.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * 专门用于吞噬子进程输出流，防止死锁
 */
public class StreamGobbler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(StreamGobbler.class);

    private final InputStream inputStream;
    private final Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
            }
        } catch (Exception e) {
            log.error("Error reading stream", e);
        }
    }
}
