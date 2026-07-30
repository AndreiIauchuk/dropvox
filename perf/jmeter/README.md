# JMeter

Import `dropvox-files-flow.jmx` into JMeter to execute the Files Upload/Download flow on `http://localhost:8080`.

The plan is tuned for load-style execution: finite loops by default and GUI listeners disabled.

Start the app:

```powershell
docker compose up --build
jmeter
```

Or run the test headlessly:

```powershell
Remove-Item "perf\jmeter\results.jtl" -ErrorAction SilentlyContinue; jmeter -n -t perf\jmeter\dropvox-files-flow.jmx -l perf\jmeter\results.jtl
```

Default variables in the plan:

- `host=localhost`
- `port=8080`
- `threads=200`
- `ramp_up=60`
- `loops=50`
- `payload=dropvox-jmeter-payload`
- `max_status_polls=20`
- `status_poll_delay_ms=1000`

You can override them on the command line, for example:

```powershell
jmeter.bat -n -t perf\jmeter\dropvox-files-flow.jmx -Jthreads=1000 -Jloops=1 -Jramp_up=60 -Jmax_status_polls=30 -Jstatus_poll_delay_ms=1000 -l perf\jmeter\results.jtl
```
