# winrm4j

`winrm4j` is a project which enables Java applications to execute batch or PowerShell commands on a remote Windows server 
using [WinRM](https://msdn.microsoft.com/en-us/library/aa384426(v=vs.85).aspx)

You can download the latest binaries [here](http://mvnrepository.com/artifact/io.cloudsoft.windows/winrm4j), which also gives the details
for adding winrm4j as a dependency to your project.

If you wish to build the binaries yourself, you can clone this project, and build it using [Maven](https://maven.apache.org/):

`mvn clean install`

To build using the Apache CXF project tools use

`mvn clean install -Djax-ws-cxf`


### Maven dependency

Add the following to your `pom.xml`:

```
<dependency>
  <groupId>io.cloudsoft.windows</groupId>
  <artifactId>winrm4j</artifactId>
  <version>0.3.4</version> <!-- WINRM4J_VERSION -->
</dependency>
```

### Java client usage

To use winrm4j in Java code, you first create a `WinRmTool` object via the static `WinRmTool.Builder` class.
`WinRmTool.Builder` has several options which you might consider setting before instantiating a `WinRmTool`:

* `workingDirectory(String)`
* `environment(Map<String,String>)`
* `setAuthenticationScheme(String)`
* `disableCertificateChecks(boolean)`
* `useHttps(boolean)`
* `port(int)`

A `WinRmTool` instance supports the basic `executeCommand(String)` method which executes windows native commands.
Through `executeCommand(String)` method supports:

* `executeCommand(List<String>)` executeList of commands concatenated with &
* `executePs(String)` execute a PowerShell command with the native windows command
* `executePs(List<String>)` list of PowerShell commands

It exposes the methods
`executeScript` and `executePs`, which can be used to execute batch or PowerShell statements respectively.

``` java
WinRmTool.Builder builder = WinRmTool.Builder.builder("my.windows.server.com", "Administrator", "pa55w0rd!");
builder.setAuthenticationScheme(AuthSchemes.NTLM);
builder.port(5985);
builder.useHttps(false);
WinRmTool tool =  builder.build();
```

### License

Copyright 2015-2016 by Cloudsoft Corporation Limited

> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
> 
> http://www.apache.org/licenses/LICENSE-2.0
> 
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
