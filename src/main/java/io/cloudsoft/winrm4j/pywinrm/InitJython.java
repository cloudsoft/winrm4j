package io.cloudsoft.winrm4j.pywinrm;

import javax.script.ScriptException;

public class InitJython {

    public static void main(String[] args) throws ScriptException {
        SessionType session = WinRMFactory.INSTANCE.createSession(args[0],
                args[1], args[2]);

        ResponseType response = session.run_ps("(new-object System.Net.WebClient).DownloadFile(\"http://www.7-zip.org/a/7z938-x64.msi\", \"C:\\setup.msi\")");
        System.out.println(response.getStdOut());
        System.out.println(response.getStdErr());
        System.out.println(response.getStatusCode());

        response = session.run_ps("Start-Process msiexec.exe -ArgumentList \"/i c:\\setup.msi /qn\" -Wait");
        System.out.println(response.getStdOut());
        System.out.println(response.getStdErr());
        System.out.println(response.getStatusCode());
    }

}
