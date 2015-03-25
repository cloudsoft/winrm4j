package org.apache.brooklyn.windows.pywinrm;

import javax.script.ScriptException;

public class InitJython {

    public static void main(String[] args) throws ScriptException {
        SessionType session = WinRMFactory.INSTANCE.createSession("159.8.55.232:5985",
                "Administrator", "PqR4crqQ");

        ResponseType response = session.run_cmd("ipconfig", "/all");
        System.out.println(response.getStdOut());
        System.out.println(response.getStdErr());
        System.out.println(response.getStatusCode());
    }

}
