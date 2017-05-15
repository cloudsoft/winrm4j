package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.http.client.config.AuthSchemes;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import io.cloudsoft.winrm4j.client.WinRmClientContext;

/**
 * Tests execution of commands (batch and powershell) on Windows over WinRM.
 * 
 * See Apache Brooklyn documentation: https://github.com/apache/brooklyn-docs/blob/master/guide/blueprints/winrm/index.md
 * Please contact the Apache Brooklyn community (or update their docs) if you encounter  
 * new situations, or change the behaviour of existing use-cases.
 */
public class AbstractWinRmToolLiveTest {

    private static final Logger LOG = Logger.getLogger(AbstractWinRmToolLiveTest.class.getName());

    protected static final int MAX_EXECUTOR_THREADS = 50;

    protected static final String INVALID_CMD = "thisCommandDoesNotExistAEFafiee3d";
    protected static final String PS_ERR_ACTION_PREF_EQ_STOP = "$ErrorActionPreference = \"Stop\"";

    // Run tests with:
    // mvn clean install -PLive -Dwinrm.livetest.address=<IP>
    protected static final String VM_ADDRESS = System.getProperty("winrm.livetest.address", "172.28.128.3");
    protected static final int VM_PORT = System.getProperty("winrm.livetest.port") == null ? 5985 : Integer.parseInt(System.getProperty("winrm.livetest.port"));
    // More often than note testing against a vagrant box
    protected static final String VM_USER = System.getProperty("winrm.livetest.user", "vagrant");
    protected static final String VM_PASSWORD = System.getProperty("winrm.livetest.password", "vagrant");

    private WinRmClientContext context;
    protected WinRmTool winRmTool;

    protected WinRmTool createWinRm() {
        return createWinRm(createWinRmBuilder());
    }

    protected WinRmTool createWinRm(WinRmTool.Builder builder) {
        return builder.build();
    }

    protected WinRmTool.Builder createWinRmBuilder() {
        WinRmTool.Builder builder = WinRmTool.Builder.builder(VM_ADDRESS, VM_USER, VM_PASSWORD);
        builder.authenticationScheme(AuthSchemes.NTLM);
        builder.port(VM_PORT);
        builder.useHttps(VM_PORT != 5985);
        builder.disableCertificateChecks(true);
        builder.context(context);
        return builder;
    }

    protected ListeningExecutorService executor;

    @BeforeClass(alwaysRun = true)
    public void initContext() {
        this.context = WinRmClientContext.newInstance();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupContext() {
        if (this.context != null) {
            this.context.shutdown();
        }
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(MAX_EXECUTOR_THREADS));
        winRmTool = createWinRm();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
    }

    protected void assertExecFails(String cmd) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmd, executeCommand(cmd), stopwatch);
    }

    protected void assertExecFails(List<String> cmds) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmds, executeCommand(cmds), stopwatch);
    }
    
    protected void assertExecPsFails(String cmd) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmd, executePs(cmd), stopwatch);
    }

    protected void assertExecPsFails(List<String> cmds) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmds, executePs(cmds), stopwatch);
    }

    protected void assertExecSucceeds(String cmd, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmd, executeCommand(ImmutableList.of(cmd)), stdout, stderr, stopwatch);
    }

    protected void assertExecCommand(String cmd, String stdout, String stderr, int exitCode) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertWinRmToolResponse(cmd, executeCommand(cmd), stdout, stderr, stopwatch, exitCode);
    }

    protected void assertExecSucceeds(List<String> cmds, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmds, executeCommand(cmds), stdout, stderr, stopwatch);
    }

    protected void assertExecPs(String cmd, String stdout, String stderr, int exitCode) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertWinRmToolResponse(cmd, executePs(cmd), stdout, stderr, stopwatch, exitCode);
    }

    protected void assertExecPsSucceeds(String cmd, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmd, executePs(cmd), stdout, stderr, stopwatch);
    }

    protected void assertExecPsSucceeds(List<String> cmds, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmds, executePs(cmds), stdout, stderr, stopwatch);
    }

    protected void assertFailed(Object cmd, WinRmToolResponse response, Stopwatch stopwatch) {
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+makeTimeStringRounded(stopwatch)+" (asserting failed): "+msg+"; cmd="+cmd);
        assertNotEquals(response.getStatusCode(), 0, msg);
    }
    
    protected WinRmToolResponse assertSucceeded(Object cmd, WinRmToolResponse response, String stdout, String stderr, Stopwatch stopwatch) {
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+makeTimeStringRounded(stopwatch)+" (asserting success): "+msg+"; cmd="+cmd);
        assertEquals(response.getStatusCode(), 0, msg);
        if (stdout != null) assertEquals(response.getStdOut().trim(), stdout, msg);
        if (stderr != null) assertEquals(response.getStdErr().trim(), stderr, msg);
        return response;
    }

    protected WinRmToolResponse assertWinRmToolResponse(Object cmd, WinRmToolResponse response, String stdout, String stderr, Stopwatch stopwatch, int exitCode) {
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+makeTimeStringRounded(stopwatch)+" (asserting success): "+msg+"; cmd="+cmd);
        assertEquals(response.getStatusCode(), exitCode, msg);
        if (stdout != null) assertEquals(response.getStdOut().trim(), stdout, msg);
        if (stderr != null) assertEquals(response.getStdErr().trim(), stderr, msg);
        return response;
    }
    
    protected WinRmToolResponse executeCommand(final String command) {
        return winRmTool.executeCommand(command);
    }

    protected WinRmToolResponse executeCommand(final List<String> commands) {
        return winRmTool.executeCommand(commands);
    }

    protected WinRmToolResponse executePs(final String command) {
        return winRmTool.executePs(command);
    }

    protected WinRmToolResponse executePs(final List<String> script) {
        return winRmTool.executePs(script);
    }

    protected WinRmToolResponse executePs(final WinRmTool winRmTool, final String command) {
      return winRmTool.executePs(command);
    }

    // TODO Add to WinRmTool?
    protected void copyTo(InputStream source, String destination) throws Exception {
        int chunkSize = 1024;

        byte[] inputData = new byte[chunkSize];
        int bytesRead;
        int expectedFileSize = 0;
        while ((bytesRead = source.read(inputData)) > 0) {
            byte[] chunk;
            if (bytesRead == chunkSize) {
                chunk = inputData;
            } else {
                chunk = Arrays.copyOf(inputData, bytesRead);
            }
            executePs("If ((!(Test-Path " + destination + ")) -or ((Get-Item '" + destination + "').length -eq " +
                    expectedFileSize + ")) {Add-Content -Encoding Byte -path " + destination +
                    " -value ([System.Convert]::FromBase64String(\"" + new String(BaseEncoding.base64().encode(chunk)) + "\"))}");
            expectedFileSize += bytesRead;
        }
    }

    protected String makeTimeStringRounded(Stopwatch stopwatch) {
        long millis = stopwatch.elapsed(TimeUnit.MILLISECONDS) % 1000;
        long secs = stopwatch.elapsed(TimeUnit.SECONDS);
        if (secs > 0) {
            return secs + "secs" + (millis > 0 ? " " + millis + "ms" : "");
        } else {
            return millis + "ms";
        }
    }

    protected String makeRandomString(int length) {
        Random rand = new Random();
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append( (char) ('a' + rand.nextInt(26)));
        }
        return result.toString();
    }
}
