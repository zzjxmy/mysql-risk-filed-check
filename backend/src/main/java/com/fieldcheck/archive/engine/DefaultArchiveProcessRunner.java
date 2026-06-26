package com.fieldcheck.archive.engine;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class DefaultArchiveProcessRunner implements ArchiveProcessRunner {

    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    @Override
    public int run(Long executionId, List<String> command, Consumer<String> outputConsumer) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        runningProcesses.put(executionId, process);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputConsumer.accept(line);
            }
            return process.waitFor();
        } finally {
            runningProcesses.remove(executionId);
        }
    }

    @Override
    public void stop(Long executionId) {
        Process process = runningProcesses.get(executionId);
        if (process != null) {
            process.destroy();
        }
    }
}
