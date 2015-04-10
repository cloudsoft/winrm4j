package io.cloudsoft.winrm4j.pywinrm;

import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;

public enum WinRMFactory {
    INSTANCE;

    private final PythonInterpreter interpreter;
    private final PyObject sessionClass;


    WinRMFactory() {
        // Prevent memory 'leak' leading to OOME
        // https://wiki.python.org/jython/NewUsersGuide#java-classes-unloading
        System.setProperty("python.options.internalTablesImpl", "weak");
        this.interpreter = new PythonInterpreter();
        interpreter.exec("from winrm import Session");
        sessionClass = interpreter.get("Session");
    }

    public Session createSession(String hostname, String username, String password, String transport) {
        PyString pyHostname = new PyString(hostname);
        PyString pyUsername = new PyString(username);
        PyString pyPassword = new PyString(password);
        PyString pyTransport = new PyString(transport);
        PyTuple pyAuth = new PyTuple(pyUsername, pyPassword);
        PyObject sessionObject = sessionClass.__call__(pyHostname, pyAuth, pyTransport);

        return (Session)sessionObject.__tojava__(Session.class);
    }

    public Session createSession(String hostname, String username, String password) {
        PyString pyHostname = new PyString(hostname);
        PyString pyUsername = new PyString(username);
        PyString pyPassword = new PyString(password);
        PyTuple pyAuth = new PyTuple(pyUsername, pyPassword);
        PyObject sessionObject = sessionClass.__call__(pyHostname, pyAuth);

        return (Session)sessionObject.__tojava__(Session.class);
    }

}
