package org.apache.brooklyn.windows.pywinrm;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import javax.script.ScriptException;

public class InitJython {

    public static void main(String[] args) throws ScriptException {
        WinRMSession session = WinRMFactory.INSTANCE.createSession("ec2-54-76-163-174.eu-west-1.compute.amazonaws.com",
                "Administrator", "mec)LQD9ME");


//        PythonInterpreter c = new PythonInterpreter();
//        c.exec("import winrm");
//        c.exec("s = winrm.Session('ec2-54-76-163-174.eu-west-1.compute.amazonaws.com', auth=('Administrator', 'mec)LQD9ME'))");
//        c.exec("r = s.run_cmd('ipconfig', ['/all'])");
//        PyObject result = c.get("r");
//        System.out.println(result.__getattr__("std_out").toString());
    }

}
