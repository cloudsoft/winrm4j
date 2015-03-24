package org.apache.brooklyn.windows.pywinrm;

import org.python.core.PyArray;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyType;
import org.python.util.PythonInterpreter;

public enum WinRMFactory {
    INSTANCE;

    private final PythonInterpreter interpreter;
    private final PyObject winrm;


    WinRMFactory() {
        this.interpreter = new PythonInterpreter();
        interpreter.exec("import winrm");
        winrm = interpreter.get("winrm");
    }

    public WinRMSession createSession(String hostname, String username, String password, String transport) {
        PyArray auth = new PyArray(PyType.fromClass(PyString.class));
        auth._add(new PyString(username));
        auth._add(new PyString(password));
        PyObject buildingObject = winrm.__call__(new PyString(hostname), auth, new PyString(transport));
        return (WinRMSession)buildingObject.__tojava__(WinRMSession.class);
    }

    public WinRMSession createSession(String hostname, String username, String password) {
        PyArray auth = new PyArray(PyType.fromClass(PyString.class));
        auth._add(new PyString(username));
        auth._add(new PyString(password));
        PyObject buildingObject = winrm.__call__(new PyString(hostname), auth);
        return (WinRMSession)buildingObject.__tojava__(WinRMSession.class);
    }

}
