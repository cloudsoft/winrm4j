package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.retry.RetryDecision;
import io.cloudsoft.winrm4j.client.retry.RetryPolicy;

class RetryingProxyHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RetryingProxyHandler.class);

    private final WinRm winrm;
    private RetryPolicy afterConnectionFailureRetryPolicy;

    public RetryingProxyHandler(WinRm winrm, RetryPolicy afterConnectionFailureRetryPolicy) {
        this.winrm = winrm;
        this.afterConnectionFailureRetryPolicy = afterConnectionFailureRetryPolicy;
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
        Throwable firstException = null;
        afterConnectionFailureRetryPolicy.clear();
        boolean tryAgain = true;

        for (int attempt = 1; tryAgain; attempt++) {
            try {
                return method.invoke(winrm, args);
            } catch (InvocationTargetException targetException) {
                Throwable e = targetException.getTargetException();
                if (e instanceof SOAPFaultException) {
                    throw (SOAPFaultException) e;
                }
                if (e instanceof WebServiceException == false) {
                    throw new IllegalStateException("Failure when calling " + method + args, e);
                }
                WebServiceException wsException = (WebServiceException) e;
                if (wsException.getCause() instanceof IOException == false) {
                    throw new RuntimeException("Exception occurred while making winrm call", wsException);
                }
                if (firstException == null) {
                    firstException = wsException;
                }
                RetryDecision retryDecision = afterConnectionFailureRetryPolicy.apply(wsException);
                if (retryDecision.retry()) {
                    LOG.debug("Ignoring exception and retrying (attempt " + attempt //
                            + afterConnectionFailureRetryPolicy.total().map(total -> " of " + (total + 1)).orElse("") + ")",
                            wsException);
                    try {
                        Thread.sleep(retryDecision.pause());
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Exception occured while making winrm call",
                                e.initCause(wsException));
                    }
                } else {
                    tryAgain = false;
                }
            }
        }
        throw new RuntimeException("failed task \"" + method.getName() + "\""
                + afterConnectionFailureRetryPolicy.total().map(total -> " after " + (total + 1) + " attempt(s)").orElse(""),
                firstException);
    }

    @Deprecated
    public void setRetriesForConnectionFailures(int retries) {
        LOG.warn("method RetryingProxyHandler#setRetriesForConnectionFailures has been deprecated,"
                + " please use instead WinRmClientBuilder#afterConnectionFailureRetryPolicy");
        afterConnectionFailureRetryPolicy = WinRmClientBuilder.simpleCounterRetryPolicy(retries);
    }

}
