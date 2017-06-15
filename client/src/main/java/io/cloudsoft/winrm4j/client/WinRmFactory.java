package io.cloudsoft.winrm4j.client;

import java.lang.reflect.Field;
import java.util.Arrays;

import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.ServiceDelegate;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.addressing.WSAddressingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WinRmFactory {
    private static final Logger LOG = LoggerFactory.getLogger(WinRmClient.class.getName());

    public static WinRm newInstance(Bus bus) {
        Bus prevBus = BusFactory.getAndSetThreadDefaultBus(bus);
        try {
            // The default thread bus is set on the ClientImpl and used for further requests
            return createService(bus);
        } finally {
            if (BusFactory.getThreadDefaultBus(false) != prevBus) {
                BusFactory.setThreadDefaultBus(prevBus);
            }
        }
    }

    private static WinRm createService(Bus bus) {
        RuntimeException lastException = null;

        try {
            return doCreateServiceWithBean(bus);
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with mbean strategy (trying other strategies): "+e, e);
            lastException = e;
        }

        /*
         * It's tedious getting the right Provider esp in OSGi.
         * 
         * We've tried a bunch of strategies, with the most promising tried here,
         * and detailed notes below.
         */

        try {
            return doCreateServiceWithReflectivelySetDelegate();
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with reflective delegate (trying other strategies): "+e, e);
            lastException = e;
        }

        try {
            return doCreateServiceNormal();
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with many strategies (giving up): "+e, e);
            lastException = e;
        }

        throw lastException;
    }

    // normal approach
    private static WinRm doCreateServiceNormal() {
        WinRmService service = doCreateService_1_CreateMinimalServiceInstance();
        return doCreateService_2_GetClient(service);
    }

    // force delegate
    // based on http://stackoverflow.com/a/31892206/109079
    private static WinRm doCreateServiceWithReflectivelySetDelegate() {
        WinRmService service = doCreateService_1_CreateMinimalServiceInstance();

        try {
            Field delegateField = javax.xml.ws.Service.class.getDeclaredField("delegate"); //ALLOW CXF SPECIFIC SERVICE DELEGATE ONLY!
            delegateField.setAccessible(true);
            ServiceDelegate previousDelegate = (ServiceDelegate) delegateField.get(service);
            if (!previousDelegate.getClass().getName().contains("cxf")) {
                ServiceDelegate serviceDelegate = ((Provider) Class.forName("org.apache.cxf.jaxws.spi.ProviderImpl").newInstance())
                        .createServiceDelegate(WinRmService.WSDL_LOCATION, WinRmService.SERVICE, service.getClass());
                delegateField.set(service, serviceDelegate);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reflectively setting CXF WS service delegate", e);
        }
        return doCreateService_2_GetClient(service);
    }
    
    // approach using JaxWsProxyFactoryBean
    
    private static WinRm doCreateServiceWithBean(Bus bus) {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.getClientFactoryBean().getServiceFactory().setWsdlURL(WinRmService.WSDL_LOCATION);
        factory.setServiceName(WinRmService.SERVICE);
        factory.setEndpointName(WinRmService.WinRmPort);
        factory.setFeatures(Arrays.asList((Feature)newMemberSubmissionAddressingFeature()));
        factory.setBus(bus);
        return factory.create(WinRm.class);
    }

    private static WinRmService doCreateService_1_CreateMinimalServiceInstance() {
        return new WinRmService();
    }

    private static WinRm doCreateService_2_GetClient(WinRmService service) {
        return service.getWinRmPort(
                // * Adds WS-Addressing headers and uses the submission spec namespace
                //   http://schemas.xmlsoap.org/ws/2004/08/addressing
                newMemberSubmissionAddressingFeature());
    }
    
    private static WebServiceFeature newMemberSubmissionAddressingFeature() {
        /*
         * Requires the following dependency so the feature is visible to maven.
         * But is it included in the IBM dist?
<dependency>
    <groupId>com.sun.xml.ws</groupId>
    <artifactId>jaxws-rt</artifactId>
    <version>2.2.10</version>
</dependency>
         */
        try {
            // com.ibm.websphere.wsaddressing.jaxws21.SubmissionAddressingFeature for IBM java (available only in WebSphere?)

            WSAddressingFeature webServiceFeature = new WSAddressingFeature();
//            webServiceFeature.setResponses(WSAddressingFeature.AddressingResponses.ANONYMOUS);
            webServiceFeature.setAddressingRequired(true);

            return webServiceFeature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
