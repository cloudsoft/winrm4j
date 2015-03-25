package org.apache.brooklyn.windows.pywinrm;

public interface SessionType {
    ResponseType run_cmd(String command, String... args);
    ResponseType run_ps(String script);
}
