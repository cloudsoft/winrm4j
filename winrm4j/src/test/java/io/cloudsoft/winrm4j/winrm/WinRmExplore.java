package io.cloudsoft.winrm4j.winrm;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import org.apache.http.client.config.AuthSchemes;

public class WinRmExplore {

    public static String SERVER = "192.168.1.1";
    public static String USERNAME = "Administrator";
    public static String PASSWORD = "s3cr3t";

    public static void main(String[] args) {
        /*
         * If running from the IDE, make sure you do a maven build before running,
         * to get wsdl/WinRmService.wsdl installed
         */

        WinRmClientContext context = WinRmClientContext.newInstance();

        WinRmTool tool = WinRmTool.Builder.builder(
                SERVER, USERNAME, PASSWORD)
                .authenticationScheme(
//                        AuthSchemes.BASIC
                        AuthSchemes.NTLM
//                        AuthSchemes.SPNEGO
//                        AuthSchemes.KERBEROS
                        )

                .port(5985)
                .useHttps(false)
//                .payloadEncryptionMode(PayloadEncryptionMode.OPTIONAL)

//                .port(5986)
//                .useHttps(true)
//
//                .disableCertificateChecks(true)

                .context(context)
                .build();

        WinRmToolResponse response = tool.executePs("ls");
        System.out.println("OUT: "+response.getStdOut());

        if (context!=null) context.shutdown();
    }

}
