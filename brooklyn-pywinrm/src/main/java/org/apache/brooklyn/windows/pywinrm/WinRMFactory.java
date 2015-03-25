package org.apache.brooklyn.windows.pywinrm;

import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;

public enum WinRMFactory {
    INSTANCE;

    private final PythonInterpreter interpreter;
    private final PyObject sessionClass;


    WinRMFactory() {
        this.interpreter = new PythonInterpreter();
        interpreter.exec("from winrm import Session");
        sessionClass = interpreter.get("Session");
    }

    public SessionType createSession(String hostname, String username, String password, String transport) {
        PyString pyHostname = new PyString(hostname);
        PyString pyUsername = new PyString(username);
        PyString pyPassword = new PyString(password);
        PyString pyTransport = new PyString(transport);
        PyTuple pyAuth = new PyTuple(pyUsername, pyPassword);
        PyObject sessionObject = sessionClass.__call__(pyHostname, pyAuth, pyTransport);

        return (SessionType)sessionObject.__tojava__(SessionType.class);
    }

    public SessionType createSession(String hostname, String username, String password) {
        PyString pyHostname = new PyString(hostname);
        PyString pyUsername = new PyString(username);
        PyString pyPassword = new PyString(password);
        PyTuple pyAuth = new PyTuple(pyUsername, pyPassword);
        PyObject sessionObject = sessionClass.__call__(pyHostname, pyAuth);

        return (SessionType)sessionObject.__tojava__(SessionType.class);
    }

}
