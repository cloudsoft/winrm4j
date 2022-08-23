package io.cloudsoft.winrm4j.service.enumerate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import java.util.ArrayList;
import java.util.List;

// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/10cfb548-845b-4979-aae3-3f39d7080e17
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Enumerate", propOrder = {
		"maxElements",
		"filter",
		"any"
})
public class EnumerateRequest {

	@XmlElement(name = "MaxElements", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration")
	@XmlSchemaType(name = "unsignedLong")
	protected Long maxElements;

	@XmlElement(name = "Filter", namespace = "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd", required = true)
	protected Filter filter;

	@XmlAnyElement(lax = true)
	protected List<Object> any;

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Filter {

		@XmlAttribute(name = "Dialect")
		protected String dialect;

		@XmlValue
		private String value;

		public String getDialect() {
			return dialect;
		}

		public void setDialect(final String dialect) {
			this.dialect = dialect;
		}

		public String getValue() {
			return value;
		}

		public void setValue(final String value) {
			this.value = value;
		}
	}

	public Long getMaxElements() {
		return maxElements;
	}

	public void setMaxElements(final Long maxElements) {
		this.maxElements = maxElements;
	}

	public Filter getFilter() {
		return filter;
	}

	public void setFilter(final Filter filter) {
		this.filter = filter;
	}

	public List<Object> getAny() {
		if (any == null) {
			any = new ArrayList<>();
		}
		return any;
	}

	public void setAny(final List<Object> any) {
		this.any = any;
	}

}
