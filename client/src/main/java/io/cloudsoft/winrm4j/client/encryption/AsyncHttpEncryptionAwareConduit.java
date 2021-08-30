package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.encryption.AsyncHttpEncryptionAwareConduit.EncryptionAwareHttpEntity;
import io.cloudsoft.winrm4j.client.encryption.SignAndEncryptOutInterceptor.ContentWithType;
import io.cloudsoft.winrm4j.client.encryption.SignAndEncryptOutInterceptor.EncryptAndSignOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.cxf.Bus;
import org.apache.cxf.io.CacheAndWriteOutputStream;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit.AsyncWrappedOutputStream;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transport.http.asyncclient.CXFHttpRequest;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.auth.Credentials;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncHttpEncryptionAwareConduit extends AsyncHTTPConduit {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHttpEncryptionAwareConduit.class);

    public static final byte[] PRE_AUTH_BOGUS_PAYLOAD = "AWAITING_ENCRYPTION_KEYS".getBytes();

    private final PayloadEncryptionMode payloadEncryptionMode;

    public AsyncHttpEncryptionAwareConduit(PayloadEncryptionMode payloadEncryptionMode, Bus b, EndpointInfo ei, EndpointReferenceType t, AsyncHTTPConduitFactory factory) throws IOException {
        super(b, ei, t, factory);
        this.payloadEncryptionMode = payloadEncryptionMode;
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

                CXFHttpRequest entity = message.get(CXFHttpRequest.class);
                CachedEncryptingOutputStream out = new CachedEncryptingOutputStream(encryptor,
                        message,
                        true,
                        false,
                        chunkThreshold,
                        getConduitName(),
                        entity.getURI());
                entity.setOutputStream(out);
                if (entity.getEntity() instanceof EncryptionAwareHttpEntity) {
                    EncryptionAwareHttpEntity encryptingEntity = (EncryptionAwareHttpEntity) entity.getEntity();
                    out.setHttpEntity(encryptingEntity);
                    encryptingEntity.setEncryptingOutputStream(out);
                }
                return out;
            }

            throw new IllegalStateException("Encryption only available with ASYNC at present");
            // if needed could also subclass the URL stream used by super.super.createOutput
        }
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        super.setupConnection(message, address, csPolicy);
        CXFHttpRequest e = message.get(CXFHttpRequest.class);
        if (e!=null) {
            LOG.info("XXX-e2-creation");
            BasicHttpEntity entity = new EncryptionAwareHttpEntity(e);
            entity.setChunked(true);
            entity.setContentType((String)message.get(Message.CONTENT_TYPE));

            e.setEntity(entity);
        }
    }

    public static class EncryptionAwareHttpEntity extends BasicHttpEntity {
        private final CXFHttpRequest request;
        private CachedEncryptingOutputStream encryptingOutputStream;

        public EncryptionAwareHttpEntity(CXFHttpRequest e) {
            this.request = e;
        }

        public boolean isRepeatable() {
            return request.getOutputStream().retransmitable();
        }

        @Override
        public long getContentLength() {
            LOG.info("XXX-e2-getCL-x");
            if (encryptingOutputStream!=null) {
                refreshHeaders(request);
            }
            return
                    // don't know content length the first time we are called from the wrapped request, and buffer not yet flushed to us
//                            -1;
                    super.getContentLength();
        }

        public void refreshHeaders() {
            refreshHeaders(request);
        }

        public void refreshHeaders(HttpEntityEnclosingRequest request) {
            if (request.getEntity() != this) {
                LOG.warn("Request entity mismatch "+request+" sought "+request.getEntity()+" but we are "+this);
            }
            if (encryptingOutputStream!=null) {
                ContentWithType appropriate = encryptingOutputStream.getAppropriate();
                setContentLength(appropriate.payload.length);
                request.setHeader(HTTP.CONTENT_LEN, ""+appropriate.payload.length);
//                request.setHeader(HTTP.CONTENT_ENCODING, appropriate.encoding);
                request.setHeader(HTTP.CONTENT_TYPE, appropriate.contentType);
            }
        }

        @Override
        public Header getContentType() {
            LOG.info("XXX-e2-getCT");
            return super.getContentType();
        }

        public void setEncryptingOutputStream(CachedEncryptingOutputStream out) {
            this.encryptingOutputStream = out;
        }

    }

    public class CachedEncryptingOutputStream extends AsyncWrappedOutputStream {

        private final NtlmEncryptionUtils encryptor;
        private EncryptionAwareHttpEntity encryptingHttpEntity;

        public CachedEncryptingOutputStream(NtlmEncryptionUtils encryptor, Message message, boolean needToCacheRequest, boolean isChunking, int chunkThreshold, String conduitName, URI uri) {
            super(message, needToCacheRequest, isChunking, chunkThreshold, conduitName, uri);
            this.encryptor = encryptor;
        }

        @Override
        public CachedOutputStream getCachedStream() {
            LOG.info("XXX-ns-getCached");
            return super.getCachedStream();
        }

        @Override
        protected void onFirstWrite() throws IOException {  // only done once
            LOG.info("XXX-ns-on1");
//            outMessage.set(HTTP.CONTENT_LEN, -1);
            super.onFirstWrite();
        }

//        public boolean retransmitable() {
//            return false;
//        }

        @Override
        public void close() throws IOException {
            LOG.info("XXX-ns-close");

//            OutputStream oldWrappedStream = wrappedStream;
//            wrappedStream = new CachedOutputStream();

            // AAA
//            wrappedStream.write(getAppropriate());

//            if (!isOpen()) {
//                return;
//            }
//            if (lastPayload==null) {
//                lastPayload = ((CachedOutputStream)wrappedStream).getBytes();
//            }
//            CachedOutputStream newWrappedStream = new CachedOutputStream();
//            if (!encryptor.credentials.isAuthenticated()) {
//                // if not yet authenticated, don't allow the output to be written
//                newWrappedStream.write("AWAITING_ENCRYPTION_KEYS".getBytes());
//            } else {
//                byte[] thisPayload = encryptor.encryptAndSign(outMessage, lastPayload);
//                newWrappedStream.write(thisPayload);
//                lastPayload = null;
//            }
//            wrappedStream = newWrappedStream;

            super.close();
//            oldWrappedStream.close();

//            if (encryptingHttpEntity!=null) {
//                encryptingHttpEntity.refreshHeaders();
//            }
        }

        @Override
        protected void retransmitStream() throws IOException {
            LOG.info("XXX-ns-retransmit");
            super.retransmitStream();
        }

//        protected boolean isRealContentAllowed() {
////            return true;
//            if (!encryptor.credentials.isAuthenticated()) {
//                return false;
//
//            } else {
//                return true;
//            }
//        }

        @Override
        protected void setupWrappedStream() throws IOException {
            LOG.info("XXX-ns-setupWrapped");
            super.setupWrappedStream();

            // AAA
            final OutputStream outbufFlowThroughStream = cachedStream.getFlowThroughStream() instanceof EncryptingCacheAndWriteOutputStream
                    ? null
                    : cachedStream.getFlowThroughStream();
            wrappedStream = cachedStream = new EncryptingCacheAndWriteOutputStream(outbufFlowThroughStream);
        }

        @Override
        public void flush() throws IOException {
            LOG.info("XXX-ns-flush");
            super.flush();
        }

        public void setHttpEntity(EncryptionAwareHttpEntity encryptingEntity) {
            this.encryptingHttpEntity = encryptingEntity;
        }

//        public int getLength() {
//            try {
//                if (cachedStream instanceof EncryptingCacheAndWriteOutputStream) {
//                    return cachedStream.getBytes().length;
//                } else {
//                    return -1;
//                }
//            } catch (IOException e) {
//                throw new IllegalStateException(e);
//            }
//        }

        private class EncryptingCacheAndWriteOutputStream extends CacheAndWriteOutputStream {
            private final OutputStream outbufFlowThroughStream;
            private boolean flushed = false;

            public EncryptingCacheAndWriteOutputStream(OutputStream outbufFlowThroughStream) {
                super(new NullOutputStream());
                this.outbufFlowThroughStream = outbufFlowThroughStream!=null ? outbufFlowThroughStream : new NullOutputStream();
            }

            // we assume that 'writeCacheTo' is only used by logging and/or should have the unencrypted payload

            @Override
            public byte[] getBytes() throws IOException {
                return CachedEncryptingOutputStream.this.getAppropriate().payload;
//                LOG.info("XXX-ecs-getBytes "+isRealContentAllowed()+" - size "+super.getBytes().length);
//                return isRealContentAllowed()
//                        ?
////                            encryptor.encryptAndSign(outMessage, super.getBytes())
//                            super.getBytes()
//                        :
//                            PRE_AUTH_BOGUS_PAYLOAD
////                          super.getBytes()
//                ;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                LOG.info("XXX-ecs-getIS");
                return new ByteArrayInputStream(getBytes());
            }

            @Override
            public void flush() throws IOException {
                if (flushed) return;
                // only allow flushing once; assume caller writes everything before flushing
                flushed = true;

                LOG.info("XXX-ecs-flush "+outbufFlowThroughStream);

                super.flush();
                copyStream(getInputStream(), outbufFlowThroughStream, 4096);
                outbufFlowThroughStream.flush();

                if (encryptingHttpEntity!=null) {
                    encryptingHttpEntity.refreshHeaders();
                }
                outbufFlowThroughStream.close();
            }

            @Override
            public void close() throws IOException {
                flush();
                LOG.info("XXX-ecs-close");
                outbufFlowThroughStream.close();
                super.close();
//                copyStream(getInputStream(), outbufFlowThroughStream, 4096);
//                outbufFlowThroughStream.flush();
            }

            @Override
            public void resetOut(OutputStream out, boolean copyOldContent) throws IOException {
                LOG.info("XXX-ecs-resetOut");
                super.resetOut(out, copyOldContent);
            }
        }

        private ContentWithType getAppropriate() {
            EncryptAndSignOutputStream encryptingStream = outMessage.getContent(EncryptAndSignOutputStream.class);
            if (encryptingStream==null) {
                throw new IllegalStateException("No SignAndEncryptOutInterceptor applied to message");
            }
            return encryptingStream.getAppropriate();
        }
    }

}
