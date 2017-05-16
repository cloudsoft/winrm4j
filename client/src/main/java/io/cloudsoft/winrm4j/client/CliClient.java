package io.cloudsoft.winrm4j.client;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class CliClient {

    public static void main(String[] args) throws Exception{
        if (args.length != 4) {
            System.out.println("Usage: CliClient <endpoint> <username> <password> <command>");
        }

        String endpoint = args[0];
        String username = args[1];
        String password = args[2];
        String cmd = args[3];

        WinRmClient client = WinRmClient.builder(endpoint)
                .disableCertificateChecks(true)
                .credentials(username, password)
                .workingDirectory("C:\\")
                .disableCertificateChecks(true)
//                .environment(env)
                .build();
        int exitCode = 999;
        try (ShellCommand shell = client.createShell()){
            exitCode = shell.execute(cmd, new PrintWriter(new OutputStreamWriter(System.out)), new PrintWriter(new OutputStreamWriter(System.err)));
        }
        System.exit(exitCode);
    }
}
