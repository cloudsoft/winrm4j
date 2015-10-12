# winrm4j

`winrm4j` is a project which enables Java applications to execute batch or PowerShell commands on a remote Windows server 
using [WinRM](https://msdn.microsoft.com/en-us/library/aa384426(v=vs.85).aspx)

The code is based on the Python project [pywinrm](https://github.com/diyan/pywinrm) and uses [jython](http://www.jython.org/)
to make the Python classes accessible to Java.

You can download the latest binaries [here](http://mvnrepository.com/artifact/io.cloudsoft.windows/winrm4j), which also gives the details
for adding winrm4j as a dependency to your project.

If you wish to build the binaries yourself, you can clone this project, and build it using [Maven](https://maven.apache.org/):

`mvn clean install`

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

To test connectivity, you can install [Python](https://www.python.org/) and [pywinrm](https://pypi.python.org/pypi/pywinrm) on you development machine,
then use pywinrm directly in a Python console:

``` python
import winrm
s = winrm.Session('my.windows.server.com', auth=('Administrator', 'mypassword'))
r = s.run_ps("ls")
r.std_out
r.std_err
r.status_code
```

To use winrm4j in Java code, you first create a `Session` object via the `WinRMFactory` class. The session object exposes the methods
`run_cmd` and `run_ps`, which can be used to execute batch or PowerShell statements respectively.

``` java
Session session = WinRMFactory.INSTANCE.createSession("my.windows.server.com", "Administrator", "mypassword");

Response response = session.run_cmd("dir C:\\");
System.out.println(response.getStdOut());

response = session.run_ps("ls C:\\");
System.out.println(response.getStdOut());
```
