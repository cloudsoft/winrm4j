package io.cloudsoft.winrm4j.winrm;

import org.apache.cxf.bus.CXFBusFactory;

public class CliClient {

    public static void main(String[] args) throws Exception{
//        System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.client.HttpTransportPipe.dump", "true");
//        System.setProperty("com.sun.xml.ws.transport.http.HttpAdapter.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dump", "true");
//        System.setProperty("com.sun.xml.internal.ws.transport.http.HttpAdapter.dumpTreshold", Integer.toString(Integer.MAX_VALUE));

        if (args.length != 4) {
            System.out.println("Usage: CliClient <endpoing> <username> <password> <command>");
        }

        String endpoint = args[0];
        String username = args[1];
        String password = args[2];
        String cmd = args[3];

        WinRmTool client = WinRmTool.Builder.builder(endpoint, username, password)
                .disableCertificateChecks(true)
                .workingDirectory("C:\\")
                .disableCertificateChecks(true)
//                .environment(env)
                .build();
        int exitCode = 999;
        try {
            WinRmToolResponse response = client.executeCommand(cmd);
            System.err.print(response.getStdErr());
            System.out.println(response.getStdOut());
            exitCode = response.getStatusCode();
        } finally {
            CXFBusFactory.clearDefaultBusForAnyThread(CXFBusFactory.getDefaultBus());
        }
        System.exit(exitCode);
    }
}
