package io.cloudsoft.winrm4j.client;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.soap.SOAPFaultException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.cloudsoft.winrm4j.client.enumeration.EnumerateResponse;
import io.cloudsoft.winrm4j.client.enumeration.PullResponse;
import io.cloudsoft.winrm4j.client.wsman.Enumerate;
import io.cloudsoft.winrm4j.client.wsman.Filter;
import io.cloudsoft.winrm4j.client.wsman.Items;
import io.cloudsoft.winrm4j.client.wsman.Locale;
import io.cloudsoft.winrm4j.client.wsman.OptionSetType;
import io.cloudsoft.winrm4j.client.wsman.Pull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static io.cloudsoft.winrm4j.client.WinRmClient.MAX_ENVELOPER_SIZE;

public class EnumerateCommand implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(EnumerateCommand.class.getName());

	/**
	 * If no output is available before the wsman:OperationTimeout expires, the server MUST return a WSManFault with the Code attribute equal to "2150858793"
	 * https://msdn.microsoft.com/en-us/library/cc251676.aspx
	 */
	static final String WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED = "2150858793";

	private final WinRm winrm;
	private final String resourceUri;
	private final String sessionId;
	private final long maxElements;
	private final Supplier<String> operationTimeout;
	private final Supplier<Locale> locale;
	private final Predicate<String> retryReceiveAfterOperationTimeout;

	private final DocumentBuilder documentBuilder;

	public EnumerateCommand(final WinRm winrm,
	                        final String resourceUri,
	                        final long maxElements,
	                        final Supplier<String> operationTimeout,
	                        final Supplier<Locale> locale,
	                        final Predicate<String> retryReceiveAfterOperationTimeout) {
		this.winrm = winrm;
		this.resourceUri = resourceUri;
		this.sessionId = "uuid:" + UUID.randomUUID();
		this.maxElements = maxElements;
		this.operationTimeout = operationTimeout;
		this.locale = locale;
		this.retryReceiveAfterOperationTimeout = retryReceiveAfterOperationTimeout;
		try {
			this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("Failed to create instance of DocumentBuilder");
		}
	}

	public List<Node> execute(final String filter, final String dialect) {
		final EnumerateResponse enumerateResponse = enumerate(filter, dialect);
		final List<Node> result = new ArrayList<>();
		collectAndIterateEnumeratedResults(result, new EnumerationPullState(
				resourceUri,
				maxElements,
				enumerateResponse.getEnumerationContext(),
				enumerateResponse.getItems(),
				enumerateResponse.getEndOfSequence() != null
		));
		return result;
	}

	private EnumerateResponse enumerate(final String filter, final String dialect) {
		while (true) {
			try {
				final Enumerate enumerate = new Enumerate();
				enumerate.setFilter(new Filter());
				enumerate.getFilter().setValue(filter);
				enumerate.getFilter().setDialect(dialect);
				enumerate.setMaxElements(maxElements);
				return winrm.enumerate(
						enumerate,
						resourceUri,
						sessionId,
						MAX_ENVELOPER_SIZE,
						operationTimeout.get(),
						locale.get(),
						new OptionSetType()
				);
			} catch (final SOAPFaultException soapFault) {
				/**
				 * If such Exception which has a code 2150858793 the client is expected to again trigger immediately a receive request.
				 * https://msdn.microsoft.com/en-us/library/cc251676.aspx
				 */
				assertFaultCode(soapFault, WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED,
						retryReceiveAfterOperationTimeout);
			}
		}
	}

	private PullResponse pull(final EnumerationPullState state) {
		while (true) {
			try {
				final Pull pull = new Pull();
				pull.setEnumerationContext(state.getEnumerationContext());
				pull.setMaxElements(maxElements);
				return winrm.enumeratePull(
						pull,
						state.getResourceId(),
						sessionId,
						MAX_ENVELOPER_SIZE,
						operationTimeout.get(),
						locale.get(),
						new OptionSetType()
				);
			} catch (final SOAPFaultException soapFault) {
				/**
				 * If such Exception which has a code 2150858793 the client is expected to again trigger immediately a receive request.
				 * https://msdn.microsoft.com/en-us/library/cc251676.aspx
				 */
				assertFaultCode(soapFault, WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED,
						retryReceiveAfterOperationTimeout);
			}
		}
	}

	void collectAndIterateEnumeratedResults(final List<Node> result, final EnumerationPullState state) {

		final Document doc = documentBuilder.newDocument();
		final Element root = doc.createElement("results");
		doc.appendChild(root);

		final Items items = state.getItems();
		if (items != null) {
			final List<Object> elements = items.getAny();
			if (elements != null) {
				for (Object element : elements) {
					if (element instanceof Node) {
						final Node node = doc.importNode((Node) element, true);
						root.appendChild(node);
						result.add(node);
					} else {
						LOG.debug("{} unexpected element type {}", this, element.getClass().getCanonicalName());
					}
				}
			}
		}
		// There will be additional data available if context is given and the element sequence is not ended.
		if (state.getEnumerationContext() != null && !state.isEndOfSequence()) {
			final PullResponse next = pull(state);
			final boolean endOfSequence = next.getEndOfSequence() != null;
			LOG.debug("{} endOfSequence = {}", this, endOfSequence);
			collectAndIterateEnumeratedResults(result, new EnumerationPullState(
					state.getResourceId(),
					state.getMaxElements(),
					next.getEnumerationContext(),
					next.getItems(),
					endOfSequence
			));
		}
	}

	void assertFaultCode(SOAPFaultException soapFault, String code, Predicate<String> retry) {
		try {
			NodeList faultDetails = soapFault.getFault().getDetail().getChildNodes();
			for (int i = 0; i < faultDetails.getLength(); i++) {
				if (faultDetails.item(i).getLocalName().equals("WSManFault")) {
					if (faultDetails.item(i).getAttributes().getNamedItem("Code").getNodeValue().equals(code)
							&& retry.test(code)) {
						LOG.trace("winrm client {} received error 500 response with code {}, response {}", this, code, soapFault);
						return;
					} else {
						throw soapFault;
					}
				}
			}
			throw soapFault;
		} catch (NullPointerException e) {
			LOG.debug("Error reading Fault Code {}", soapFault.getFault());
			throw soapFault;
		}
	}

	@Override
	public void close() throws Exception {
	}

	static class EnumerationPullState {
		private final String resourceId;
		private final long maxElements;
		private final String enumerationContext;
		private final Items items;
		private final boolean endOfSequence;

		public EnumerationPullState(final String resourceId, final long maxElements, final String enumerationContext, final Items items, final boolean endOfSequence) {
			this.resourceId = resourceId;
			this.maxElements = maxElements;
			this.enumerationContext = enumerationContext;
			this.items = items;
			this.endOfSequence = endOfSequence;
		}

		public String getResourceId() {
			return resourceId;
		}

		public long getMaxElements() {
			return maxElements;
		}

		public String getEnumerationContext() {
			return enumerationContext;
		}

		public Items getItems() {
			return items;
		}

		public boolean isEndOfSequence() {
			return endOfSequence;
		}
	}

}
