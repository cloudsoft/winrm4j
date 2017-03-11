package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPFaultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

import io.cloudsoft.winrm4j.client.WinRmClient.CallableFunction;
import io.cloudsoft.winrm4j.client.shell.CommandLine;
import io.cloudsoft.winrm4j.client.shell.CommandStateType;
import io.cloudsoft.winrm4j.client.shell.DesiredStreamType;
import io.cloudsoft.winrm4j.client.shell.Receive;
import io.cloudsoft.winrm4j.client.shell.ReceiveResponse;
import io.cloudsoft.winrm4j.client.shell.StreamType;
import io.cloudsoft.winrm4j.client.wsman.CommandResponse;
import io.cloudsoft.winrm4j.client.wsman.DeleteResponse;
import io.cloudsoft.winrm4j.client.wsman.Locale;
import io.cloudsoft.winrm4j.client.wsman.OptionSetType;
import io.cloudsoft.winrm4j.client.wsman.OptionType;
import io.cloudsoft.winrm4j.client.wsman.SelectorSetType;
import io.cloudsoft.winrm4j.client.wsman.SelectorType;
import io.cloudsoft.winrm4j.client.wsman.Signal;
import io.cloudsoft.winrm4j.client.wsman.SignalResponse;

public class ShellCommand implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ShellCommand.class.getName());

    private static final String COMMAND_STATE_DONE = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/CommandState/Done";

    /**
     * If no output is available before the wsman:OperationTimeout expires, the server MUST return a WSManFault with the Code attribute equal to "2150858793"
     * https://msdn.microsoft.com/en-us/library/cc251676.aspx
     */
    private static final String WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED = "2150858793";

    /**
     * Example response:
     *   [truncated]The request for the Windows Remote Shell with ShellId xxxx-yyyy-ccc... failed because the shell was not found on the server.
     *   Possible causes are: the specified ShellId is incorrect or the shell no longer exi
     */
    private static final String WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND = "2150858843";

    private WinRm winrm;
    private SelectorSetType shellSelector;

    private String operationTimeout;
    private final Locale locale;
    private final Integer retriesForConnectionFailures;

    private int numberOfReceiveCalls;

    public ShellCommand(WinRm winrm, String shellId, String operationTimeout, Locale locale, Integer retriesForConnectionFailures) {
        this.winrm = winrm;
        this.shellSelector = createShellSelector(shellId);
        this.operationTimeout = operationTimeout;
        this.locale = locale;
        this.retriesForConnectionFailures = retriesForConnectionFailures;
    }

    private SelectorSetType createShellSelector(String shellId) {
        SelectorSetType shellSelector = new SelectorSetType();
        SelectorType sel = new SelectorType();
        sel.setName("ShellId");
        sel.getContent().add(shellId);
        shellSelector.getSelector().add(sel);
        return shellSelector;
    }

    public int execute(String cmd, Writer out, Writer err) {
        WinRmClient.checkNotNull(cmd, "command");

        final CommandLine cmdLine = new CommandLine();
        cmdLine.setCommand(cmd);
        final OptionSetType optSetCmd = new OptionSetType();
        OptionType optConsolemodeStdin = new OptionType();
        optConsolemodeStdin.setName("WINRS_CONSOLEMODE_STDIN");
        optConsolemodeStdin.setValue("TRUE");
        optSetCmd.getOption().add(optConsolemodeStdin);
        OptionType optSkipCmdShell = new OptionType();
        optSkipCmdShell.setName("WINRS_SKIP_CMD_SHELL");
        optSkipCmdShell.setValue("FALSE");
        optSetCmd.getOption().add(optSkipCmdShell);

        numberOfReceiveCalls = 0;
        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
        WinRmClient.setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Command");
        CommandResponse cmdResponse = winrm.command(cmdLine, WinRmClient.RESOURCE_URI, WinRmClient.MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector, optSetCmd);

        String commandId = cmdResponse.getCommandId();

        try {
            return receiveCommand(commandId, out, err);
        } finally {
            try {
                releaseCommand(commandId);
            } catch (SOAPFaultException soapFault) {
                assertFaultCode(soapFault, WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND);
            }
        }
    }

    private int receiveCommand(String commandId, Writer out, Writer err) {
        while(true) {
            final Receive receive = new Receive();
            DesiredStreamType stream = new DesiredStreamType();
            stream.setCommandId(commandId);
            stream.setValue("stdout stderr");
            receive.setDesiredStream(stream);


            try {
                numberOfReceiveCalls++;
                ReceiveResponse receiveResponse = WinRmClient.winrmCallRetryConnFailure(new WinRmClient.CallableFunction<ReceiveResponse>() {
                    @Override
                    public ReceiveResponse call() {
                        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                        WinRmClient.setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Receive");
                        return winrm.receive(receive, WinRmClient.RESOURCE_URI, WinRmClient.MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
                    }
                }, retriesForConnectionFailures);
                getStreams(receiveResponse, out, err);

                CommandStateType state = receiveResponse.getCommandState();
                if (COMMAND_STATE_DONE.equals(state.getState())) {
                    return state.getExitCode().intValue();
                } else {
                    LOG.debug("{} is not done. Response it received: {}", this, receiveResponse);
                }
            } catch (SOAPFaultException soapFault) {
                /**
                 * If such Exception which has a code 2150858793 the client is expected to again trigger immediately a receive request.
                 * https://msdn.microsoft.com/en-us/library/cc251676.aspx
                 */
                assertFaultCode(soapFault, WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED);
            }
        }
    }

    private void assertFaultCode(SOAPFaultException soapFault, String code) {
        try {
            NodeList faultDetails = soapFault.getFault().getDetail().getChildNodes();
            for (int i = 0; i < faultDetails.getLength(); i++) {
                if (faultDetails.item(i).getLocalName().equals("WSManFault")) {
                    if (faultDetails.item(i).getAttributes().getNamedItem("Code").getNodeValue().equals(code)) {
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

    public int getNumberOfReceiveCalls() {
        return numberOfReceiveCalls;
    }

    private void getStreams(ReceiveResponse receiveResponse, Writer out, Writer err) {
        List<StreamType> streams = receiveResponse.getStream();
        for (StreamType s : streams) {
            byte[] value = s.getValue();
            if (value == null) continue;
            if (out != null && "stdout".equals(s.getName())) {
                try {
                    //TODO use passed locale?
                    if (value.length > 0) {
                        out.write(new String(value));
                    }
                    if (Boolean.TRUE.equals(s.isEnd())) {
                        out.close();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (err != null && "stderr".equals(s.getName())) {
                try {
                    //TODO use passed locale?
                    if (value.length > 0) {
                        err.write(new String(value));
                    }
                    if (Boolean.TRUE.equals(s.isEnd())) {
                        err.close();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        try {
            out.close();
            err.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void releaseCommand(String commandId) {
        final Signal signal = new Signal();
        signal.setCommandId(commandId);
        signal.setCode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/signal/terminate");

        WinRmClient.winrmCallRetryConnFailure(new CallableFunction<SignalResponse>() {
            @Override
            public SignalResponse call() {
                //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                WinRmClient.setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Signal");
                return winrm.signal(signal, WinRmClient.RESOURCE_URI, WinRmClient.MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
            }
        }, retriesForConnectionFailures);

    }


    @Override
    public void close() {
        try {
            WinRmClient.winrmCallRetryConnFailure(new CallableFunction<DeleteResponse>() {
                @Override
                public DeleteResponse call() {
                    //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                    WinRmClient.setActionToContext((BindingProvider) winrm, "http://schemas.xmlsoap.org/ws/2004/09/transfer/Delete");
                    return winrm.delete(null, WinRmClient.RESOURCE_URI, WinRmClient.MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
                }
            }, retriesForConnectionFailures);
        } catch (SOAPFaultException soapFault) {
            assertFaultCode(soapFault, WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND);
        }
    }
}
