package io.cloudsoft.winrm4j.winrm;

import org.apache.http.client.config.AuthSchemes;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by valentin on 1/27/16.
 */
public class WinRmWithNTLMExecLiveTest extends AbstractWinRmToolLiveTest {

    @Test(groups="Live")
    public void testExecScript() throws Exception {
        assertExecSucceeds("echo myline", "myline", "");
    }

    @Override
    protected WinRmTool connect() throws Exception {
        return callWithRetries(new Callable<WinRmTool>() {
            @Override public WinRmTool call() throws Exception {
                return WinRmTool.connect(VM_HOST + ":" + VM_PORT, VM_USER, VM_PASSWORD, AuthSchemes.NTLM);
            }});
    }

    protected WinRmToolResponse executeCommand(final List<String> commands) {
        return callWithRetries(new Callable<WinRmToolResponse>() {
            @Override public WinRmToolResponse call() throws Exception {
                WinRmTool winRmTool = WinRmTool.connect(VM_HOST + ":" + VM_PORT, VM_USER, VM_PASSWORD, AuthSchemes.NTLM);
                return winRmTool.executeCommand(commands);
            }});
    }
}
