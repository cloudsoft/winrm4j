package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

public class CustomizedWinRmToolTest extends AbstractWinRmToolLiveTest {

    @Test(groups = "Live")
    public void testAssigningEnvironmentVariables() {
        final Map<String, String> environment = new HashMap<>();
        environment.put("TEST1", "Hello Test 1");
        environment.put("TEST2", "Hello Test 2");

        WinRmTool.Builder cmdTool = createWinRmBuilder();
        cmdTool.environment(environment);
        WinRmToolResponse cmdResponse = cmdTool.build().executeCommand("echo %TEST1% & echo %TEST2%");

        assertEquals(cmdResponse.getStdOut(), "Hello Test 1 \r\nHello Test 2\r\n");
        WinRmTool.Builder psTool = createWinRmBuilder();
        psTool.environment(environment);

        List<String> psScript = new ArrayList<>();
        psScript.add("Write-Host $env:TEST1");
        psScript.add("Write-Host $env:TEST2");
        WinRmToolResponse psResponse = psTool.build().executePs(psScript);

        assertEquals(psResponse.getStdOut(), "Hello Test 1\nHello Test 2\n");
    }

    @Test(groups = "Live")
    public void testSettingWorkingDirectories() {
        final String cmdWorkingDirectory = "%TEMP%";
        WinRmTool.Builder cmdTool = createWinRmBuilder();
        cmdTool.workingDirectory(cmdWorkingDirectory);
        WinRmToolResponse cmdResponse = cmdTool.build().executeCommand("echo %cd%");
        assertTrue(cmdResponse.getStdOut().toLowerCase().contains("\\temp"), "should be somewhere in temp");

        final String psWorkingDirectory = "%TEMP%";
        WinRmTool.Builder psTool = createWinRmBuilder();
        psTool.workingDirectory(psWorkingDirectory);
        WinRmToolResponse psResponse = psTool.build().executePs("Write-Host $pwd.Path");
        assertTrue(psResponse.getStdOut().toLowerCase().contains("\\temp"), "should be somewhere in temp");
    }
}
