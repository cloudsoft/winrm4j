/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
