package io.cloudsoft.winrm4j.client;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory.UseAsyncPolicy;

public class WinRmClientContext {
    public static WinRmClientContext newInstance() {
        Bus bus = configureBus(BusFactory.newInstance().createBus());
        return new WinRmClientContext(bus);
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

    private WinRmClientContext(Bus bus) {
        this.bus = bus;
    }

    public void shutdown() {
        bus.shutdown(true);
    }

    Bus getBus() {
        return bus;
    }

}
