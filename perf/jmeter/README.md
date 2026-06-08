# JMeter

Import `dropvox-files-flow.jmx` into JMeter to execute the Files Upload/Download flow on `http://localhost:8080`.

Start the app:

```powershell
docker compose up --build
jmeter
```


Or run the test headlessly:

```powershell
jmeter -n -t perf\jmeter\dropvox-files-flow.jmx -l perf\jmeter\results.jtl
```

Default variables in the plan:

- `host=localhost`
- `port=8080`
- `threads=5000`
- `ramp_up=60`
- `loops=1`
- `payload=dropvox-jmeter-payload`

You can override them on the command line, for example:

```powershell
jmeter.bat -n -t perf\jmeter\dropvox-files-flow.jmx -Jthreads=5000 -Jloops=1 -Jramp_up=60 -l perf\jmeter\results.jtl
```
