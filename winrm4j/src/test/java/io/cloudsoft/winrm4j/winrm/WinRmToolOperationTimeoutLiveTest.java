package io.cloudsoft.winrm4j.winrm;

import com.google.common.base.Stopwatch;
import io.cloudsoft.winrm4j.client.WinRmClient;
import org.testng.annotations.Test;

import javax.xml.ws.WebServiceException;

import java.util.logging.Logger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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
        final Long operationTimeoutInSeconds = 10l; // Put WinRmClient.Builder.DEFAULT_OPERATION_TIMEOUT / 1000 to test the default value
        WinRmTool winRmTool = WINRM_TOOL.call();
        winRmTool.setOperationTimeout(operationTimeoutInSeconds * 1000l);

        try {
            winRmTool.executePs(String.format("Start-Sleep -s %d\r\nWrite-Host Test Completed", operationTimeoutInSeconds + 5));
            fail("Command should fail to complete on time.");
        } catch (WebServiceException e) {
            assertTrue(e.getMessage().contains("The WS-Management service cannot complete the operation within the time specified in OperationTimeout"));
        }
    }
}
