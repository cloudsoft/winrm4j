package io.cloudsoft.winrm4j.winrm;

import org.apache.http.client.config.AuthSchemes;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CustomizedWinRmToolTest extends AbstractWinRmToolLiveTest {
    @Override
    protected WinRmTool.Builder winRmTool() {
        final WinRmTool.Builder winRmToolBuilder = WinRmTool.Builder.builder(VM_ADDRESS, VM_USER, VM_PASSWORD);
        winRmToolBuilder.setAuthenticationScheme(AuthSchemes.NTLM);
        winRmToolBuilder.port(VM_PORT);
        winRmToolBuilder.useHttps(false);
        winRmToolBuilder.disableCertificateChecks(true);
        return winRmToolBuilder;
    }

    @Test(groups = "Live")
    public void testAssigningEnvironmentVariables() {
        final Map<String, String> environment = new HashMap<>();
        environment.put("TEST1", "Hello Test 1");
        environment.put("TEST2", "Hello Test 2");

        WinRmToolResponse cmdResponse = callWithRetries(new Callable<WinRmToolResponse>() {
            @Override public WinRmToolResponse call() throws Exception {
                WinRmTool.Builder tool = winRmTool();
                tool.environment(environment);
                return tool.build().executeCommand("echo %TEST1% & echo %TEST2%");
            }});

        assertEquals(cmdResponse.getStdOut(), "Hello Test 1 \r\nHello Test 2\r\n");
        WinRmToolResponse psResponse = callWithRetries(new Callable<WinRmToolResponse>() {
            @Override public WinRmToolResponse call() throws Exception {
                WinRmTool.Builder tool = winRmTool();
                tool.environment(environment);

                List<String> psScript = new ArrayList<>();
                psScript.add("Write-Host $env:TEST1");
                psScript.add("Write-Host $env:TEST2");
                return tool.build().executePs(psScript);
            }});

        assertEquals(psResponse.getStdOut(), "Hello Test 1\nHello Test 2\n");
    }

    @Test(groups = "Live")
    public void testSettingWorkingDirectories() {
        final String cmdWorkingDirectory = "%TEMP%";
        WinRmToolResponse cmdResponse = callWithRetries(new Callable<WinRmToolResponse>() {
            @Override public WinRmToolResponse call() throws Exception {
                WinRmTool.Builder tool = winRmTool();
                tool.workingDirectory(cmdWorkingDirectory);
                return tool.build().executeCommand("echo %cd%");
            }});
        assertTrue(cmdResponse.getStdOut().toLowerCase().contains("\\temp"), "should be somewhere in temp");

        final String psWorkingDirectory = "%TEMP%";
        WinRmToolResponse psResponse = callWithRetries(new Callable<WinRmToolResponse>() {
            @Override public WinRmToolResponse call() throws Exception {
                WinRmTool.Builder tool = winRmTool();
                tool.workingDirectory(psWorkingDirectory);
                return tool.build().executePs("Write-Host $pwd.Path");
            }});
        assertTrue(psResponse.getStdOut().toLowerCase().contains("\\temp"), "should be somewhere in temp");
    }
}
