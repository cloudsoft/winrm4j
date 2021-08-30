package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class AsyncHttpEncryptionAwareConduitFactory extends AsyncHTTPConduitFactory {

    final PayloadEncryptionMode payloadEncryptionMode;
    Collection<String> targetAuthSchemes;

    public AsyncHttpEncryptionAwareConduitFactory(PayloadEncryptionMode payloadEncryptionMode, Collection<String> targetAuthSchemes, Map<String, Object> conf) {
        super(conf);
        this.payloadEncryptionMode = payloadEncryptionMode;
        this.targetAuthSchemes = targetAuthSchemes;
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

    public void targetAuthSchemes(Set<String> authSchemes) {
        this.targetAuthSchemes = authSchemes;
    }
}
