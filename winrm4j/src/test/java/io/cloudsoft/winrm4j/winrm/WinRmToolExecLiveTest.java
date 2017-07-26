package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Tests execution of commands (batch and powershell) on Windows over WinRM.
 * 
 * There are limitations with what is supported by PyWinRM. These are highlighted in
 * tests marked as "WIP" (see individual tests).
 * 
 * See Apache Brooklyn documentation: https://github.com/apache/brooklyn-docs/blob/master/guide/blueprints/winrm/index.md
 * Please contact the Apache Brooklyn community (or update their docs) if you encounter  
 * new situations, or change the behaviour of existing use-cases.
 */
public class WinRmToolExecLiveTest extends AbstractWinRmToolLiveTest {

    private static final Logger LOG = Logger.getLogger(WinRmToolExecLiveTest.class.getName());

    @Test(groups="Live")
    public void testExecScript() throws Exception {
        assertExecSucceeds("echo myline", "myline", "");
    }

    @Test(groups={"Live"})
    public void testExecMultiPartScript() throws Exception {
        assertExecSucceeds(ImmutableList.of("echo first", "echo second"), "first "+"\r\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecFailingScript() throws Exception {
        final String INVALID_CMD = "thisCommandDoesNotExistAEFafiee3d";
        
        // Single commands
        assertExecFails(INVALID_CMD);
        assertExecFails(ImmutableList.of(INVALID_CMD));
    }

    @Test(groups="Live")
    public void testExecScriptExit0() throws Exception {
        assertExecSucceeds("exit /B 0", "", "");
    }

    @Test(groups = "Live")
    public void testChainCommands() {
        assertExecCommand("echo Hi & echo World", "Hi \r\nWorld", "", 0);
    }

    /**
     * Demonstrates that {@code "\r\n"} cannot be used to concatenate commands.
     * Instead see {@link #testChainCommands()}.
     * 
     * This is not "desired" behaviour, but is expected behaviour. 
     * 
     * We (Aled) have also seen WinRM fail with exitCode 1, stdout "Hi" and stderr showing
     * {@code '#xD' is not recognized as an internal or external command, operable program or batch file.}.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecRNSplitExit() throws Exception {
        assertExecCommand("echo Hi\r\necho World\r\n", "Hi", "", 0);
        assertExecCommand("echo Hi\r\necho World", "Hi", "", 0);
        assertExecCommand("echo Hi\necho World\n", "Hi", "", 0);
        assertExecCommand("echo Hi\necho World", "Hi", "", 0);
    }

    /**
     * Also see {@link #testExecScriptExit1()}, which shows problems - where it returns zero
     * instead of non-zero.
     */
    @Test(groups="Live")
    public void testExecCommandExit() throws Exception {
        assertExecCommand("exit /B 0", "", "", 0);
        assertExecCommand("dslfkdsfjskl", "", null, 1);
    }

    @Test(groups = "Live")
    public void testExecPowershellExit() throws Exception {
        assertExecPs("exit 123", "", "", 123);
        assertExecPs("Write-Host Hi World\r\nexit 123", "Hi World", "", 123);
    }

    /*
     * TODO Was not supported in PyWinRM either.
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('1.2.3.4', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("exit /B 1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecScriptExit1() throws Exception {
        // Single commands
        assertExecFails("exit /B 1");
        assertExecFails(ImmutableList.of("exit /B 1"));
        assertExecFails("exit 1");
    }

    @Test(groups="Live")
    public void testExecBatchFileSingleLine() throws Exception {
        String script = "EXIT /B 0";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath, null, "");
    }

    @Test(groups="Live")
    public void testExecBatchFileMultiLine() throws Exception {
        String script = Joiner.on("\n").join(
                "@ECHO OFF",
                "echo first", 
                "echo second", 
                "EXIT /B 0");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath, "first"+"\r\n"+"second", "");
    }

    @Test(groups="Live")
    public void testExecBatchFileWithArgs() throws Exception {
        String script = Joiner.on("\n").join(
                "@ECHO OFF",
                "echo got %1", 
                "echo got %2", 
                "EXIT /B 0");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath+" first second", "got first"+"\r\n"+"got second", "");
    }

    @Test(groups="Live")
    public void testExecBatchFileWithExit1() throws Exception {
        String script = "EXIT /B 1";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails(scriptPath);
    }

    @Test(groups="Live")
    public void testExecCorruptExe() throws Exception {
        String exe = "garbage";
        String exePath = "C:\\myscript-"+makeRandomString(8)+".exe";
        copyTo(new ByteArrayInputStream(exe.getBytes()), exePath);

        assertExecFails(exePath);
    }

    @Test(groups="Live")
    public void testExecFilePs() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds(
                "PowerShell -NonInteractive -NoProfile -Command "+scriptPath,
                "myline",
                "");
    }

    @Test(groups="Live")
    public void testExecFilePsWithExit1() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 1");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails("PowerShell -NonInteractive -NoProfile -Command "+scriptPath);
    }

    /*
     * TODO Not supported in PyWinRM - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('1.2.3.4', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineExit1.ps1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecFilePsWithSingleLineExit1() throws Exception {
        String script = "exit 1";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails("PowerShell -NonInteractive -NoProfile -Command "+scriptPath);
    }

    @Test(groups="Live")
    public void testExecPsScript() throws Exception {
        assertExecPsSucceeds("Write-Host myline", "myline", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiLineScript() throws Exception {
        // Note stdout is "\n" rather than "\r\n" (the latter is returned for run_cmd, versus run_ps)
        assertExecPsSucceeds("Write-Host first" + "\r\n" + "Write-Host second", "first"+"\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiLineScriptWithoutSlashR() throws Exception {
        assertExecPsSucceeds("Write-Host first" + "\n" + "Write-Host second", "first"+"\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiPartScript() throws Exception {
        assertExecPsSucceeds(ImmutableList.of("Write-Host first", "Write-Host second"), "first"+"\n"+"second", "");
    }

    @Test(groups="Live")
    public void testExecPsBatchFile() throws Exception {
        String script = "EXIT /B 0";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& '"+scriptPath+"'", null, "");
    }
    
    @Test(groups="Live")
    public void testExecPsBatchFileExit1() throws Exception {
        String script = "EXIT /B 1";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& '"+scriptPath+"'");
    }

    /*
     * TODO Was not supported in PyWinRM either - gives exit status 1, rather than the 3 from the 
     * batch file.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsBatchFileExit3() throws Exception {
        String script = "EXIT /B 3";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".bat";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        WinRmToolResponse response = executePs("& '"+scriptPath+"'");
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        assertEquals(response.getStatusCode(), 3, msg);
    }

    @Test(groups="Live")
    public void testExecPsCorruptExe() throws Exception {
        String exe = "garbage";
        String exePath = "C:\\myscript-"+makeRandomString(8)+".exe";
        copyTo(new ByteArrayInputStream(exe.getBytes()), exePath);

        assertExecPsFails("& '"+exePath+"'");
    }

    @Test(groups="Live")
    public void testExecPsFileWithArg() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Param(",
                "  [string]$myarg",
                ")",
                "Write-Host got $myarg", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& "+scriptPath+" -myarg myval", "got myval", "");
    }

    @Test(groups="Live")
    public void testExecPsFilePs() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& "+scriptPath, "myline", "");
    }

    @Test(groups="Live")
    public void testExecPsFilePsWithExit1() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 1");
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);
        System.out.println(scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    /*
     * TODO Not supported in PyWinRM - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('1.2.3.4', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineExit1.ps1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsFilePsSingleLineWithExit1() throws Exception {
        String script = "exit 1";
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    /*
     * TODO Was not supported in PyWinRM either - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('1.2.3.4', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineGarbage.ps1")
     * gives exit code 0.
     * 
     * It gave the following stderr:
     *     #< CLIXML
     *     <Objs Version="1.1.0.1" xmlns="http://schemas.microsoft.com/powershell/2004/04"><S S="Error">thisCommandDoesNotExistAEFafiee3d : The term _x000D__x000A_</S><S S="Error">'thisCommandDoesNotExistAEFafiee3d' is not recognized as the name of a cmdlet, _x000D__x000A_</S><S S="Error">function, script file, or operable program. Check the spelling of the name, or _x000D__x000A_</S><S S="Error">if a path was included, verify that the path is correct and try again._x000D__x000A_</S><S S="Error">At C:\myscript-pnsduoir.ps1:1 char:1_x000D__x000A_</S><S S="Error">+ thisCommandDoesNotExistAEFafiee3d_x000D__x000A_</S><S S="Error">+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~_x000D__x000A_</S><S S="Error">    + CategoryInfo          : ObjectNotFound: (thisCommandDoesNotExistAEFafiee _x000D__x000A_</S><S S="Error">   3d:String) [], CommandNotFoundException_x000D__x000A_</S><S S="Error">    + FullyQualifiedErrorId : CommandNotFoundException_x000D__x000A_</S><S S="Error"> _x000D__x000A_</S></Objs>
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsFilePsSingleLineWithInvalidCommand() throws Exception {
        String script = INVALID_CMD;
        String scriptPath = "C:\\myscript-"+makeRandomString(8)+".ps1";
        copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    @Test(groups="Live")
    public void testConfirmUseOfErrorActionPreferenceDoesNotCauseErr() throws Exception {
        // Confirm that ErrorActionPreference=Stop does not itself cause a failure, and still get output on success.
        assertExecPsSucceeds(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline"), "myline", "");
    }

    @Test(groups="Live")
    public void testExecPsExit1() throws Exception {
        // Single commands
        assertExecPsFails("exit 1");
        assertExecPsFails("exit 1");
        
        // Multi-part
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline", "exit 1"));
        
        // Multi-line
        assertExecPsFails(PS_ERR_ACTION_PREF_EQ_STOP + "\n" + "Write-Host myline" + "\n" + "exit 1");
    }

    @Test(groups="Live")
    public void testExecFailingPsScript() throws Exception {
        // Single commands
        assertExecPsFails(INVALID_CMD);
        assertExecPsFails(ImmutableList.of(INVALID_CMD));
        
        // Multi-part commands
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline", INVALID_CMD));
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, INVALID_CMD, "Write-Host myline"));
    }
    
    @Test(groups="Live")
    public void testCustomStreams() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        StringBuilder inBuff = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            inBuff.append("line " + i + "\r\n");
        }
        StringReader in = new StringReader(inBuff.toString());
        int result = winRmTool.executePs("tee -FilePath tmpfile", in, out, err);
        assertEquals(result, 0, "out=" + out.toString() + "\nerr=" + err.toString());
        assertEquals(out.toString(), inBuff.toString(), "err=" + err.toString());
    }
    
    @Test(groups="Live")
    public void testCustomStreamsAsync() throws Exception {
        PipedReader in = new PipedReader();
        PipedWriter inWriter = new PipedWriter(in);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        Thread execute = new Thread() {
            @Override
            public void run() {
                winRmTool.executePs("tee -FilePath tmpfile", in, out, err);
            }
        };
        execute.start();

        assertEquals(out.toString(), "");

        String input = "my input to send\r\n";
        inWriter.append(input);
        succeedsEventually(() -> {
            assertEquals(out.toString(), input);
        });
        assertTrue(execute.isAlive());

        inWriter.close();
        succeedsEventually(() -> {
            assertFalse(execute.isAlive());
        });
        assertEquals(out.toString(), input);
    }
    
    @Test(groups="Live")
    public void testToolReuse() throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        WinRmToolResponse response = executePs(winRmTool, "echo myline");
        assertSucceeded("echo myline", response, "myline", "", stopwatch);
        
        stopwatch = Stopwatch.createStarted();
        WinRmToolResponse response2 = executePs(winRmTool, "echo myline");
        assertSucceeded("echo myline", response2, "myline", "", stopwatch);
    }

    /** Use Z letter in front of test name so it is executed on last place in InteliJ. */
    @Test(groups="Live")
    public void testZToolConcurrentReuse() throws Exception {
        final int NUM_RUNS = 20;
        final int TIMEOUT_MINS = 30;
        final AtomicInteger counter = new AtomicInteger();

        Stopwatch stopwatch = Stopwatch.createStarted();
        LOG.info("Connected to winRmTool in "+makeTimeStringRounded(stopwatch)+"; now executing commands");

        List<ListenableFuture<?>> results = Lists.newArrayList();
        for (int i = 0; i < NUM_RUNS; i++) {
            results.add(executor.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    String line = "myline" + makeRandomString(8);
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    try {
                        WinRmToolResponse response = executePs(winRmTool, "echo " + line);
                        assertSucceeded("echo " + line, response, line, "", stopwatch);
                        LOG.info("Executed `echo "+line+"` in "+makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                        return null;
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Execute failed for `echo "+line+"` after "+makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                        throw e;
                    }
                }}));
        }
        
        Futures.allAsList(results).get(TIMEOUT_MINS, TimeUnit.MINUTES);
    }

    /** Use Z letter in front of test name so it is executed on last place in InteliJ. */
    @Test(groups={"Live", "Acceptance"})
    public void testZExecConcurrently() throws Exception {
        final int NUM_RUNS = 3;
        final int TIMEOUT_MINS = 30;
        final AtomicInteger counter = new AtomicInteger();
        
        // Find the test methods that are enabled, and that are not WIP 
        List<Method> methodsToRun = Lists.newArrayList();
        Method[] allmethods = WinRmToolExecLiveTest.class.getMethods();
        for (Method method : allmethods) {
            Test annotatn = method.getAnnotation(Test.class);
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            if (method.getName().equals("testExecConcurrently")) {
                continue;
            }
            if (annotatn == null || !annotatn.enabled()) {
                continue;
            }
            String[] groups = annotatn.groups();
            if (groups != null && Arrays.asList(groups).contains("WIP")) {
                continue;
            }
            methodsToRun.add(method);
        }

        // Execute all the methods many times
        LOG.info("Executing "+methodsToRun.size()+" methods "+NUM_RUNS+" times each, with "+MAX_EXECUTOR_THREADS+" threads for concurrent execution; max permitted time "+TIMEOUT_MINS+"mins; methods="+methodsToRun);
        
        List<ListenableFuture<?>> results = Lists.newArrayList();
        for (int i = 0; i < NUM_RUNS; i++) {
            for (final Method method : methodsToRun) {
                results.add(executor.submit(new Callable<Void>() {
                    public Void call() throws Exception {
                        LOG.info("Executing "+method.getName()+" in thread "+Thread.currentThread());
                        Stopwatch stopwatch = Stopwatch.createStarted();
                        try {
                            method.invoke(WinRmToolExecLiveTest.this);
                            LOG.info("Executed "+method.getName()+" in "+makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                            return null;
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "Execute failed for "+method.getName()+" after "+makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                            throw e;
                        }
                    }}));
            }
        }
        
        Futures.allAsList(results).get(TIMEOUT_MINS, TimeUnit.MINUTES);
    }

    private static void succeedsEventually(Runnable r) throws InterruptedException {
        long start = System.currentTimeMillis();
        long maxWait = start + 10000;
        boolean success = false;
        AssertionError lastError = null;
        while (maxWait > System.currentTimeMillis()) {
            try {
                r.run();
                success = true;
                break;
            } catch (AssertionError e) {
                lastError = e;
                Thread.sleep(100);
            }
        }
        if (!success) {
            throw lastError;
        }
    }
}
