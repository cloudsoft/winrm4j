package io.cloudsoft.winrm4j.client;

import static java.util.Objects.requireNonNull;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory.UseAsyncPolicy;

public class WinRmClientContext {
    public static WinRmClientContext newInstance() {
        Bus bus = configureBus(BusFactory.newInstance().createBus());
        return new WinRmClientContext(bus, true);
    }

    /**
     * Uses the given {@code bus}.
     * 
     * The bus should be pre-configured with:
     * <pre>
     * bus.getProperties().put(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);
     * bus.getProperties().put(AsyncHTTPConduitFactory.USE_POLICY, UseAsyncPolicy.ALWAYS);
     * </pre>
     * This needs to be async to force the use of Apache HTTP Components client.
     * Details at http://cxf.apache.org/docs/asynchronous-client-http-transport.html.
     * 
     * Note the {@link #shutdown()} method will not shutdown the given bus - that is
     * the responsibility of the caller who supplied the bus. 
     */
    public static WinRmClientContext newInstance(Bus bus) {
        return new WinRmClientContext(bus, false);
    }
    
    static Bus configureBus(Bus bus) {
        // Needed to be async to force the use of Apache HTTP Components client.
        // Details at http://cxf.apache.org/docs/asynchronous-client-http-transport.html.
        // Apache HTTP Components needed to support NTLM authentication.
        bus.getProperties().put(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);
        bus.getProperties().put(AsyncHTTPConduitFactory.USE_POLICY, UseAsyncPolicy.ALWAYS);
        return bus;
    }

    private final Bus bus;
    private final boolean cleanupBus;

    private WinRmClientContext(Bus bus, boolean cleanupBus) {
        this.bus = requireNonNull(bus, "bus");
        this.cleanupBus = cleanupBus;
    }

    public void shutdown() {
    	if (cleanupBus) {
    		bus.shutdown(true);
    	}
    }

    Bus getBus() {
        return bus;
    }

}
