# winrm4j

`winrm4j` is a project which enables Java applications to execute batch or PowerShell commands on a remote Windows server 
using [WinRM](https://msdn.microsoft.com/en-us/library/aa384426(v=vs.85).aspx)

You can download the latest binaries [here](http://mvnrepository.com/artifact/io.cloudsoft.windows/winrm4j), which also gives the details
for adding winrm4j as a dependency to your project.

If you wish to build the binaries yourself, you can clone this project, and build it using [Maven](https://maven.apache.org/):

`mvn clean install`

To build using the Apache CXF project tools use

`mvn clean install -Djax-ws-cxf`

Before connecting to a Windows server, you will need to ensure that the server is accessible and has been configured to allow
unencrypted WinRM connections over http. The following batch script will configure WinRM and open port 5985 (the default WinRM
port) on the local firewall.

``` bat
winrm quickconfig -q
winrm set winrm/config/service/auth @{Basic="true"}
winrm set winrm/config/service/auth @{CredSSP="true"}
winrm set winrm/config/client/auth @{CredSSP="true"}
winrm set winrm/config/client @{AllowUnencrypted="true"}
winrm set winrm/config/service @{AllowUnencrypted="true"}
winrm set winrm/config/winrs @{MaxConcurrentUsers="100"}
winrm set winrm/config/winrs @{MaxMemoryPerShellMB="0"}
winrm set winrm/config/winrs @{MaxProcessesPerShell="0"}
winrm set winrm/config/winrs @{MaxShellsPerUser="0"}
netsh advfirewall firewall add rule name=RDP dir=in protocol=tcp localport=3389 action=allow profile=any
netsh advfirewall firewall add rule name=WinRM dir=in protocol=tcp localport=5985 action=allow profile=any
```

To use winrm4j in Java code, you first create a `WinRmTool` object via the static `connect` method. It exposes the methods
`executeScript` and `executePs`, which can be used to execute batch or PowerShell statements respectively.

``` java
WinRMTool winrm = WinRmTool.connect("my.windows.server.com", "Administrator", "password");

WinRmToolResponse response = winrm.executeScript(ImmutableList.of("dir C:\\"));
System.out.println(response.getStdOut());

response = session.executePs(ImmutableList.of("ls C:\\"));
System.out.println(response.getStdOut());
```
