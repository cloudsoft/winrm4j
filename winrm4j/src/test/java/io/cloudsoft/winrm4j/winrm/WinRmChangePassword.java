package io.cloudsoft.winrm4j.winrm;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import org.apache.http.client.config.AuthSchemes;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.logging.Logger;

import static org.testng.Assert.fail;

public class WinRmChangePassword extends AbstractWinRmToolLiveTest {
    private static final Logger LOG = Logger.getLogger(WinRmChangePassword.class.getName());

    @Test(groups="Live")
    public void testAuthSuccessAndFail() throws Exception {
        Stopwatch stopwatchSucceed = Stopwatch.createStarted();
        assertSucceeded("echo myline", winRmTool().build().executeCommand(ImmutableList.of("echo myline")),"myline","", stopwatchSucceed);

        WinRmTool.Builder builder = WinRmTool.Builder.builder(VM_ADDRESS, VM_USER, "wrOngPass" + new Random().nextInt());
        builder.setAuthenticationScheme(AuthSchemes.NTLM);
        builder.port(VM_PORT);
        builder.useHttps(VM_PORT != 5985);
        builder.disableCertificateChecks(true);
        builder.context(WinRmClientContext.newInstance());
        WinRmTool wrongPassTool = builder.build();
        wrongPassTool.setRetriesForConnectionFailures(0);
        try {
            wrongPassTool.executeCommand(ImmutableList.of("echo myline"));
            fail("should have failed");
        } catch (RuntimeException e) {
            Assert.assertEquals(e.getCause().getMessage(), "Could not send Message.");
        }
    }

    @Test(groups="Live")
    public void testAuthFailAndSuccess() throws Exception {
        WinRmTool.Builder builder = WinRmTool.Builder.builder(VM_ADDRESS, VM_USER, "wrOngPass" + new Random().nextInt());
        builder.setAuthenticationScheme(AuthSchemes.NTLM);
        builder.port(VM_PORT);
        builder.useHttps(VM_PORT != 5985);
        builder.disableCertificateChecks(true);
        builder.context(context);
        WinRmTool wrongPassTool = builder.build();
        wrongPassTool.setRetriesForConnectionFailures(0);
        try {
            wrongPassTool.executeCommand(ImmutableList.of("echo myline"));
            fail("should have failed");
        } catch (RuntimeException e) {
            Assert.assertEquals(e.getCause().getMessage(), "Could not send Message.");
        }

        Stopwatch stopwatchSucceed = Stopwatch.createStarted();
        assertSucceeded("echo myline", winRmTool().build().executeCommand(ImmutableList.of("echo myline")),"myline","", stopwatchSucceed);
    }
}
