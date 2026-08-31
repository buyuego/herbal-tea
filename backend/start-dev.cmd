@echo off
REM ============================================================
REM 养生茶小程序后端 - 本地开发一键启动脚本（Windows）
REM 前置：MySQL 8 (127.0.0.1:3306) + Redis 7 (127.0.0.1:6379) 已启动
REM 用法：双击运行，或 cmd 中执行 start-dev.cmd
REM 固定端口 8080（本机 SERVER__PORT=0 环境变量会干扰，需显式覆盖）
REM ============================================================
setlocal

set "JAVA_HOME=C:\Users\a\devtools\jdk17"
set "MAVEN_HOME=C:\Users\a\devtools\apache-maven-3.9.16"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

cd /d "D:\WorkBuddySpace\workspace\herbal-tea\backend"

echo [1/2] 启动后端应用，端口 8080 ...
echo       （Ctrl+C 停止；首次启动含 Flyway 建表）

mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080

endlocal
