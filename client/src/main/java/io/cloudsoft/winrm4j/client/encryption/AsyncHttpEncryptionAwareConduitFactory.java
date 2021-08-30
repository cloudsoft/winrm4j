package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import java.io.IOException;
import java.util.Map;
import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class AsyncHttpEncryptionAwareConduitFactory extends AsyncHTTPConduitFactory {

    private final PayloadEncryptionMode payloadEncryptionMode;

    public AsyncHttpEncryptionAwareConduitFactory(PayloadEncryptionMode payloadEncryptionMode, Map<String, Object> conf) {
        super(conf);
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    @Override
    public HTTPConduit createConduit(Bus bus,
                                     EndpointInfo localInfo,
                                     EndpointReferenceType target) throws IOException {
        if (isShutdown()) {
            return null;
        }
        return new AsyncHttpEncryptionAwareConduit(payloadEncryptionMode, bus, localInfo, target, this);
    }

}
