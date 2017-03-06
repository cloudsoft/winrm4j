package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.logging.Logger;

import org.testng.annotations.Test;

import com.google.common.base.Stopwatch;

public class WinRmToolOperationTimeoutLiveTest extends AbstractWinRmToolLiveTest {
    private static final Logger LOG = Logger.getLogger(WinRmToolExecLiveTest.class.getName());

    @Test(groups="Live")
    public void testExecScript() throws Exception {
        assertExecSucceeds("echo myline", "myline", "");
    }

    @Test(groups="Live")
    public void testSpecifiedRequestTimeout() throws Exception {
        final Long operationTimeoutInSeconds = 10l; // Put WinRmClient.Builder.DEFAULT_OPERATION_TIMEOUT / 1000 to test the default value
        String ps = String.format("Start-Sleep -s %d\r\nWrite-Host Test Completed", operationTimeoutInSeconds - 5);

        Stopwatch stopwatch = Stopwatch.createStarted();
        WinRmTool winRmTool = WINRM_TOOL.call();
        winRmTool.setOperationTimeout(operationTimeoutInSeconds * 1000l);
        WinRmToolResponse response = winRmTool.executePs(ps);
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+makeTimeStringRounded(stopwatch)+" (asserting success): "+msg+"; cmd="+ps);
        assertEquals(response.getStatusCode(), 0, msg);
        assertTrue(response.getStdOut().endsWith("Test Completed\n"), msg);
    }

    @Test(groups = "Live")
    public void testExceedingSpecifiedOperationTimeout() throws Exception {
        final Long operationTimeoutInSeconds = 5l; // Put WinRmClient.Builder.DEFAULT_OPERATION_TIMEOUT / 1000 to test the default value
        WinRmTool winRmTool = WINRM_TOOL.call();
        winRmTool.setOperationTimeout(operationTimeoutInSeconds * 1000l);

        WinRmToolResponse response = winRmTool.executePs(String.format("Start-Sleep -s %d\r\nWrite-Host Test Completed", 4 * operationTimeoutInSeconds));

        assertEquals(response.getStdOut(), "Test Completed\n");
        assertEquals(response.getStdErr(), "");
        // TODO The test is time–dependent, also the only reason to have getNumberOfReceiveCalls. Can we assert the behaviour in another way?
        assertTrue(response.getNumberOfReceiveCalls() >= 4);
    }
}
