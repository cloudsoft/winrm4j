package io.cloudsoft.winrm4j.client;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.Charsets;
import org.apache.http.client.config.AuthSchemes;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlunit.matchers.CompareMatcher;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

public class WinRmClientRecordedTest {
    private static final Logger LOG = Logger.getLogger(WinRmClientRecordedTest.class.getName());
    static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    static {
        documentBuilderFactory.setNamespaceAware(true);
    }

    @DataProvider
    public Object[][] recordings() {
        return new Object[][] {{"win7"}, {"win12"}};
    }
    
    @Test(dataProvider="recordings", timeOut=30000)
    public void testRecording(String recordingName) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            // Use to fix the port for network capture
            // server.start(5985);
            URL serverUrl = server.url("/wsman").url();
            RecordedSessionDispatcher dispatcher = new RecordedSessionDispatcher(recordingName, serverUrl.toString());
            server.setDispatcher(dispatcher);
            doRequest(serverUrl);
            assertFalse(dispatcher.hasErrors(), "Dispatcher reported errors, see logs for details");
        }
    }

    private void doRequest(URL url) {
        WinRmClient.Builder builder = WinRmClient.builder(url, AuthSchemes.BASIC);

        WinRmClient client = builder.build();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code;

        try {
            code = client.command("echo myline", out, err);
        } finally {
            client.disconnect();
        }

        assertEquals(out.toString(), "myline\r\n");
        assertEquals(err.toString(), "");
        assertEquals(code, 0);
    }

    private static class RecordedSessionDispatcher extends Dispatcher {

        String serverUrl;
        String messageId;
        Iterator<Item> requests;
        String recordingName;
        boolean hasErrors;

        public RecordedSessionDispatcher(String recordingName, String serverUrl) {
            this.serverUrl = serverUrl;
            this.recordingName = recordingName;
        }

        public boolean hasErrors() {
            return hasErrors;
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            try {
                return doDispatch(request);
            } catch (Throwable e) {
                hasErrors = true;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException)e;
                }
                LOG.log(Level.SEVERE, "Processing request " + request + " failed", e);
                return new MockResponse().setResponseCode(500);
            }
        }

        private MockResponse doDispatch(RecordedRequest request) {
            Document requestDoc = parseRequest(request);
            if (messageId == null) {
                initRecordedSession(requestDoc);
            }

            Item next = requests.next();

            String contentType = request.getHeader("Content-Type");
            assertEquals(contentType, "application/soap+xml; action=\"" + next.getAction() + "\"; charset=UTF-8");

            // Similar comparison is when all differences that have been found are recoverable
            // Namespace prefix differences are recoverable (see http://xmlunit.sourceforge.net/userguide/html/ar01s03.html#docleveldiff)
            // For example the following are similar, but not identical:
            // <ns1:a xmlns:ns1="test" />
            // vs
            // <a xmlns="test" />
            CompareMatcher.isSimilarTo(next.getRequest())
                .throwComparisonFailure()
                .ignoreWhitespace()
                .matches(requestDoc.getFirstChild());

            Buffer response = serializeResponse(next.getResponse());
            LOG.fine("Sending response: \n" + response.toString());
            return new MockResponse()
                    .setHeader("Content-Type", "application/soap+xml;charset=UTF-8")
                    .setBody(response);
        }

        private void initRecordedSession(Document requestDoc) {
            messageId = getMessageId(requestDoc);
            Document sessionDoc = parseSession(loadRecordedSession(recordingName, messageId, serverUrl));
            requests = new RecordedSessionIterable(sessionDoc).iterator();
        }

    }

    private static Buffer serializeResponse(Element response) {
        Element envelope = response;
        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer();
            Buffer buffer = new Buffer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(envelope), new StreamResult(buffer.outputStream()));
            return buffer;
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element getFirstElement(Node el) {
        while (el != null && el.getNodeType() != Node.ELEMENT_NODE) {
            el = el.getNextSibling();
        }
        return (Element)el;
    }

    private static String loadRecordedSession(String recordingName, String messageId, String serverUrl) {
        String xml = loadResource(recordingName);
        return xml.replace("${messageId}", messageId).replace("${serviceUrl}", serverUrl);
    }

    private static String loadResource(String recordingName) {
        try {
            URI recURI = WinRmClientRecordedTest.class.getClassLoader().getResource("recordings/" + recordingName + ".xml").toURI();
            return new String(Files.readAllBytes(Paths.get(recURI)), Charsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Couldn't load recording", e);
        }
    }
    private static Document parseSession(String loadRecordedSession) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(loadRecordedSession.getBytes(Charsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Document parseRequest(RecordedRequest request) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            return builder.parse(request.getBody().inputStream());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getMessageId(Document doc) {
        Element messageId = (Element) doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/ws/2004/08/addressing", "MessageID").item(0);
        return messageId.getTextContent();
    }

    private static class Item {
        Element request;
        Element response;
        public Item(Element item) {
            for (Element el = getFirstElement(item.getFirstChild()); el != null; el = getFirstElement(el.getNextSibling())) {
                String localName = el.getTagName();
                if (localName.equals("request")) {
                    request = el;
                } else if (localName.equals("response")) {
                    response = el;
                } else {
                    throw new IllegalStateException("Unexpected element " + el);
                }
            }
            if (request == null) {
                throw new IllegalStateException("No request child found in " + item);
            }
            if (response == null) {
                throw new IllegalStateException("No response child found in " + item);
            }
        }
        public Element getRequest() {
            return getFirstElement(request.getFirstChild());
        };
        public Element getResponse() {
            return getFirstElement(response.getFirstChild());
        };
        public String getAction() {
            return request.getAttribute("action");
        }
    }
    private static class RecordedSessionIterable implements Iterable<Item> {
        private NodeList nodes;
        public RecordedSessionIterable(Document sessionDoc) {
            this(sessionDoc.getDocumentElement().getChildNodes());
        }

        public RecordedSessionIterable(NodeList nodes) {
            this.nodes = nodes;
        }

        @Override
        public Iterator<Item> iterator() {
            return new Iterator<Item>() {
                private Element nextEl = getFirstElement(nodes.item(0));

                @Override
                public boolean hasNext() {
                    return nextEl != null;
                }

                @Override
                public Item next() {
                    if (nextEl == null) {
                        throw new NoSuchElementException();
                    }
                    Item ret = new Item(nextEl);
                    nextEl = getFirstElement(nextEl.getNextSibling());
                    return ret;
                }

                @Override
                public void remove() {
                    throw new IllegalStateException("Operation not supported");
                }
            };
        }

    }
}
