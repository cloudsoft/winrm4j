package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;

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
        final Long operationTimeoutInSeconds = 10l;
        long sleepPeriod = operationTimeoutInSeconds - 5;
        int pollRetries = 1;

        doTestOperationTimeout(operationTimeoutInSeconds, sleepPeriod, pollRetries);
    }

    @Test(groups = "Live")
    public void testExceedingSpecifiedOperationTimeout() throws Exception {
        final Long operationTimeoutInSeconds = 5l;
        long sleepPeriod = 4 * operationTimeoutInSeconds + 1;
        int pollRetries = 5;

        doTestOperationTimeout(operationTimeoutInSeconds, sleepPeriod, pollRetries);
    }

    private void doTestOperationTimeout(long operationTimeoutInSeconds, long sleepPeriod, int pollRetries) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        WinRmTool winRmTool = createWinRm();
        winRmTool.setOperationTimeout(operationTimeoutInSeconds * 1000);

        String ps = String.format("Start-Sleep -s %d\r\nWrite-Host Test Completed", sleepPeriod);
        WinRmToolResponse response = winRmTool.executePs(ps);
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+makeTimeStringRounded(stopwatch)+" (asserting success): "+msg+"; cmd="+ps);
        assertEquals(response.getStatusCode(), 0, msg);
        assertEquals(response.getStdOut(), "Test Completed\n");
        assertEquals(response.getStdErr(), "");
        // TODO The test is time–dependent, also the only reason to have getNumberOfReceiveCalls. Can we assert the behaviour in another way?
        assertEquals(response.getNumberOfReceiveCalls(), pollRetries);
    }
}
