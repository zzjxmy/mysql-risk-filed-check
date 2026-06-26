package com.fieldcheck.archive.engine;

import java.util.List;
import java.util.function.Consumer;

public interface ArchiveProcessRunner {
    int run(Long executionId, List<String> command, Consumer<String> outputConsumer) throws Exception;

    void stop(Long executionId);
}
