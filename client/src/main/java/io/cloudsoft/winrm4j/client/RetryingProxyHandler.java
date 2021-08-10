package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.retry.RetryDecision;
import io.cloudsoft.winrm4j.client.retry.RetryPolicy;

class RetryingProxyHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RetryingProxyHandler.class);

    private final WinRm winrm;
    private RetryPolicy failureRetryPolicy;

    public RetryingProxyHandler(WinRm winrm, RetryPolicy failureRetryPolicy) {
        this.winrm = winrm;
        this.failureRetryPolicy = failureRetryPolicy;
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
        boolean tryAgain = true;
        int attempt = 0;

        do {
        	attempt++;
            try {
                return method.invoke(winrm, args);
            } catch (InvocationTargetException targetException) {
                Throwable e = targetException.getTargetException();
                Throwable root = e;
                while (root.getCause()!=null && root.getCause()!=e) root = root.getCause();
                if (root.toString().contains("Authorization loop detected on Conduit")) {
                    throw new IllegalStateException("Incompatible authentication schemes", e);
                }
                if (e instanceof SOAPFaultException) {
                    throw (SOAPFaultException) e;
                }
                if (!(e instanceof WebServiceException)) {
                    throw new IllegalStateException("Failure when calling " + method + args, e);
                }
                WebServiceException wsException = (WebServiceException) e;
                if (!(wsException.getCause() instanceof IOException)) {
                    throw new RuntimeException("Exception occurred while making winrm call", wsException);
                }
                if (firstException == null) {
                    firstException = wsException;
                }
                RetryDecision retryDecision = failureRetryPolicy.onWebServiceException(wsException, attempt);
                if (retryDecision.shouldRetry()) {
                    LOG.debug("On attempt " + attempt + ", ignoring exception and retrying (" + retryDecision.reason() + ")", 
                            wsException);
                    try {
                        Thread.sleep(retryDecision.pauseTimeMillis());
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Exception occured while making winrm call",
                                targetException);
                    }
                } else {
                    tryAgain = false;
                }
            }
        } while (tryAgain);
        
        LOG.debug("failed task \"" + method.getName() + "\" after " + attempt + " attempt(s), rethrowing first exception");
        throw new RuntimeException("failed task \"" + method.getName() + "\" after " + attempt + " attempt(s)", firstException);
    }

    /**
     * @deprecated instated use {@link WinRmClientBuilder#failureRetryPolicy(RetryPolicy)}
     */
    @Deprecated
    public void setRetriesForConnectionFailures(int retries) {
        LOG.warn("method RetryingProxyHandler#setRetriesForConnectionFailures has been deprecated,"
                + " please use instead WinRmClientBuilder#failureRetryPolicy");
        failureRetryPolicy = WinRmClientBuilder.simpleCounterRetryPolicy(retries);
    }

}
