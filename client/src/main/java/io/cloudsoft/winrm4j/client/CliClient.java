package io.cloudsoft.winrm4j.client;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class CliClient {

    public static void main(String[] args) throws Exception{
//        System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.client.HttpTransportPipe.dump", "true");
//        System.setProperty("com.sun.xml.ws.transport.http.HttpAdapter.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dumpTreshold", Integer.toString(Integer.MAX_VALUE));

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
        try {
            exitCode = client.command(cmd, new PrintWriter(new OutputStreamWriter(System.out)), new PrintWriter(new OutputStreamWriter(System.err)));
        } finally {
            client.disconnect();
        }
        System.exit(exitCode);
    }
}
