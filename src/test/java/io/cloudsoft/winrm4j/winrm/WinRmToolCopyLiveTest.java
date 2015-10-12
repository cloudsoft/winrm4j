package io.cloudsoft.winrm4j.winrm;

import static org.testng.Assert.assertEquals;

import java.io.ByteArrayInputStream;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * Tests execution of file upload.
 */
public class WinRmToolCopyLiveTest extends AbstractWinRmToolLiveTest {

	// TODO this actually just tests an approach, rather than code in WinRmTool!

    @Test(groups="Live")
    public void testCopyTo() throws Exception {
        String contents = "abcdef";
        runCopyTo(contents);
    }
    
    // Takes several minutes to upload/download!
    @Test(groups="Live")
    public void testLargeCopyTo() throws Exception {
        String contents = makeRandomString(65537);
        runCopyTo(contents);
    }
    
    protected void runCopyTo(String contents) throws Exception {
        String remotePath = "C:\\myfile-"+makeRandomString(8)+".txt";
        copyTo(new ByteArrayInputStream(contents.getBytes()), remotePath);
        
        WinRmToolResponse response = executeScript(ImmutableList.of("type "+remotePath));
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        assertEquals(response.getStatusCode(), 0, msg);
        assertEquals(response.getStdOut().trim(), contents, msg);
    }
}
