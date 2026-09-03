# NOTES (scratchpad while building run-panoramic-mall)

Environment: Windows 10, git-bash. Runtimes: JDK 21 (E:\tools\jdk-21.0.12), Maven 3.9.16,
Node v24.20.0. Infra on this box:
- Nacos 3.2.4 source build at `E:\tools\nacos` (run from `target/nacos-server.jar`).
- MySQL: NO local server. REMOTE at 123.56.117.17:3306 root/root (db `panoramic_mall`) is reachable.
- Chrome installed at `C:\Program Files\Google\Chrome\Application\chrome.exe` (for headless screenshots).

## Nacos launch (learned from bin/startup.cmd)
startup.cmd runs: java ... -Dnacos.standalone=true -Dnacos.home=BASE -jar BASE\target\nacos-server.jar
  --spring.config.additional-location=file:BASE/conf/ --logging.config=BASE/conf/nacos-logback.xml nacos.nacos -m standalone

TODO: verify this exact java line works from git-bash.
