package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.encryption.SignAndEncryptOutInterceptor.ContentWithType;
import io.cloudsoft.winrm4j.client.encryption.SignAndEncryptOutInterceptor.EncryptAndSignOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import org.apache.cxf.Bus;
import org.apache.cxf.io.CacheAndWriteOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transport.http.asyncclient.CXFHttpRequest;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.auth.Credentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an output stream which sends back the appropriate encrypted or unencrypted stream,
 * based on the {@link SignAndEncryptOutInterceptor} -- which normally does the right thing,
 * but during auth events it will "guess" wrongly, and we have to change the payload and
 * the headers. {@link io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption} will do
 * that by finding the {@link EncryptionAwareHttpEntity}.
 */
public class AsyncHttpEncryptionAwareConduit extends AsyncHTTPConduit {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHttpEncryptionAwareConduit.class);

    public static final byte[] PRE_AUTH_BOGUS_PAYLOAD = "AWAITING_ENCRYPTION_KEYS".getBytes();

    static ContentWithType getAppropriate(Message msg) {
        EncryptAndSignOutputStream encryptingStream = msg.getContent(EncryptAndSignOutputStream.class);
        if (encryptingStream==null) {
            throw new IllegalStateException("No SignAndEncryptOutInterceptor applied to message");
        }
        return encryptingStream.getAppropriate();
    }

    private final PayloadEncryptionMode payloadEncryptionMode;
    private final Collection<String> targetAuthSchemes;

    public AsyncHttpEncryptionAwareConduit(PayloadEncryptionMode payloadEncryptionMode, Bus b, EndpointInfo ei, EndpointReferenceType t, AsyncHttpEncryptionAwareConduitFactory factory) throws IOException {
        super(b, ei, t, factory);
        this.payloadEncryptionMode = factory.payloadEncryptionMode;
        this.targetAuthSchemes = factory.targetAuthSchemes;
    }

    protected OutputStream createOutputStream(Message message,
                                              boolean needToCacheRequest,
                                              boolean isChunking,
                                              int chunkThreshold) throws IOException {
        NtlmEncryptionUtils encryptor = NtlmEncryptionUtils.of(message.get(Credentials.class), payloadEncryptionMode);
        if (encryptor == null) {
            return super.createOutputStream(message, needToCacheRequest, isChunking, chunkThreshold);

        } else {

            if (Boolean.TRUE.equals(message.get(USE_ASYNC))) {
                if (!needToCacheRequest) {
                    LOG.warn("WinRM conduit assuming need to cache request, even though not requested");
                }
                if (isChunking) {
                    LOG.warn("WinRM conduit preventing chunking, even though requested");
                }

                // copied from super, but for our class
                CXFHttpRequest requestEntity = message.get(CXFHttpRequest.class);
                AsyncWrappedEncryptionAwareOutputStream out = new AsyncWrappedEncryptionAwareOutputStream(
                        message,
                        true,
                        false,
                        chunkThreshold,
                        getConduitName(),
                        requestEntity.getURI());
                requestEntity.setOutputStream(out);
                return out;
            }

            throw new IllegalStateException("Encryption only available with ASYNC at present");
            // if needed could also subclass the URL stream used by super.super.createOutput
        }
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        super.setupConnection(message, address, csPolicy);

        // replace similar logic in super method, but with a refreshHeaders method available

        CXFHttpRequest requestEntity = message.get(CXFHttpRequest.class);
        BasicHttpEntity entity = new EncryptionAwareHttpEntity() {
            public boolean isRepeatable() {
                return requestEntity.getOutputStream().retransmitable();
            }

            @Override
            protected ContentWithType getAppropriate() {
                return AsyncHttpEncryptionAwareConduit.getAppropriate(message);
            }
        };
        entity.setChunked(true);
        entity.setContentType((String)message.get(Message.CONTENT_TYPE));
        requestEntity.setEntity(entity);

        requestEntity.setConfig(RequestConfig.copy( requestEntity.getConfig() )
                        .setTargetPreferredAuthSchemes(targetAuthSchemes)
                        .build());
    }

    public abstract static class EncryptionAwareHttpEntity extends BasicHttpEntity {

        public void refreshHeaders(HttpEntityEnclosingRequest request) {
            if (request.getEntity() != this) {
                LOG.warn("Request entity mismatch "+request+" sought "+request.getEntity()+" but we are "+this);
            }

            ContentWithType appropriate = getAppropriate();
            setContentLength(appropriate.payload.length);
            request.setHeader(HTTP.CONTENT_LEN, ""+appropriate.payload.length);
            request.setHeader(HTTP.CONTENT_TYPE, appropriate.contentType);
        }

        protected abstract ContentWithType getAppropriate();

    }

    public class AsyncWrappedEncryptionAwareOutputStream extends AsyncWrappedOutputStream {

        public AsyncWrappedEncryptionAwareOutputStream(Message message, boolean needToCacheRequest, boolean isChunking, int chunkThreshold, String conduitName, URI uri) {
            super(message, needToCacheRequest, isChunking, chunkThreshold, conduitName, uri);
        }

        @Override
        protected void setupWrappedStream() throws IOException {
            LOG.trace("Setting up wrapped stream with {}", cachedStream);
            super.setupWrappedStream();

            if (cachedStream.getFlowThroughStream() instanceof EncryptionAwareCacheAndWriteOutputStream) {
                // nothing to do
                LOG.warn("Duplicate calls to setupWrappedStream; ignoring");
            } else {
                wrappedStream = cachedStream = new EncryptionAwareCacheAndWriteOutputStream( cachedStream.getFlowThroughStream() );
            }
        }

        private class EncryptionAwareCacheAndWriteOutputStream extends CacheAndWriteOutputStream {
            public EncryptionAwareCacheAndWriteOutputStream(OutputStream outbufFlowThroughStream) {
                super(outbufFlowThroughStream);
            }

            @Override
            public byte[] getBytes() throws IOException {
                return AsyncWrappedEncryptionAwareOutputStream.this.getAppropriate().payload;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(getBytes());
            }
        }

        private ContentWithType getAppropriate() {
            return AsyncHttpEncryptionAwareConduit.getAppropriate(outMessage);
        }

    }

}
