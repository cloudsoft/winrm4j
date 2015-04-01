package io.cloudsoft.winrm4j.pywinrm;

import javax.script.ScriptException;

public class InitJython {

    public static void main(String[] args) throws ScriptException {
        SessionType session = WinRMFactory.INSTANCE.createSession(args[0],
                args[1], args[2]);

        ResponseType response = session.run_cmd("ipconfig", "/all");
        System.out.println(response.getStdOut());
        System.out.println(response.getStdErr());
        System.out.println(response.getStatusCode());
    }

}
