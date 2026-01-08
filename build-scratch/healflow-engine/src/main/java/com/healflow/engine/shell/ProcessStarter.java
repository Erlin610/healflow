package com.healflow.engine.shell;

import java.io.IOException;

@FunctionalInterface
public interface ProcessStarter {

  Process start(ShellCommand command) throws IOException;
}

