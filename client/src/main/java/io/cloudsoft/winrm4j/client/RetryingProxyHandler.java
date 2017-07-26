package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RetryingProxyHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RetryingProxyHandler.class);

    private final WinRm winrm;
    private int retriesForConnectionFailures;


    public RetryingProxyHandler(WinRm winrm, int retriesForConnectionFailures) {
        this.winrm = winrm;
        this.retriesForConnectionFailures = retriesForConnectionFailures;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Don't retry the "command" - could lead to unexpected side effects of having the script run multiple times.
        if (method.getName().equals("command")) {
            return method.invoke(winrm, args);
        } else {
            return invokeWithRetry(method, args);
        }
    }

    public Object invokeWithRetry(Method method, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        List<Throwable> exceptions = new ArrayList<>();

        for (int i = 0; i < retriesForConnectionFailures + 1; i++) {
            try {
                return method.invoke(winrm, args);
            } catch (InvocationTargetException targetException) {
                Throwable e = targetException.getTargetException();
                if (e instanceof SOAPFaultException) {
                    throw (SOAPFaultException)e;
                } else if (e instanceof WebServiceException) {
                    WebServiceException wsException = (WebServiceException) e;
                    if (!(wsException.getCause() instanceof IOException)) {
                        throw new RuntimeException("Exception occurred while making winrm call", wsException);
                    }
                    LOG.debug("Ignoring exception and retrying (attempt " + (i + 1) + " of " + (retriesForConnectionFailures + 1) + ") {}", wsException);
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Exception occured while making winrm call", e.initCause(wsException));
                    }
                    exceptions.add(wsException);
                } else {
                    throw new IllegalStateException("Failure when calling " + method + args, e);
                }
            }
        }
        throw new RuntimeException("failed task " + method.getName(), exceptions.get(0));
    }

    @Deprecated
    public void setRetriesForConnectionFailures(int retries) {
        this.retriesForConnectionFailures = retries;
    }

}
