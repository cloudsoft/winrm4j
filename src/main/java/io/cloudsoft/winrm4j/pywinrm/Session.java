package io.cloudsoft.winrm4j.pywinrm;

public interface Session {
    Response run_cmd(String command, String... args);
    Response run_ps(String script);
}
