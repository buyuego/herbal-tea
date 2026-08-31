@echo off
REM ============================================================
REM 养生茶小程序 - 本地中间件一键启动（MySQL 8 + Redis 7）
REM 免安装版，仅绑 127.0.0.1（对齐设计文档"双中间件轻量化"口径）
REM 用法：双击运行，或 cmd 中执行 start-middleware.cmd
REM ============================================================
setlocal

echo [1/2] 启动 Redis 7.4.11 (127.0.0.1:6379) ...
start "herbal-redis" /min "C:\Users\a\devtools\redis\Redis-7.4.11-Windows-x64-msys2\redis-server.exe" "C:\Users\a\devtools\redis\Redis-7.4.11-Windows-x64-msys2\redis.conf"

echo [2/2] 启动 MySQL 8.0.44 (127.0.0.1:3306) ...
start "herbal-mysql" /min "C:\Users\a\devtools\mysql\mysql-8.0.44-winx64\bin\mysqld.exe" --basedir="C:\Users\a\devtools\mysql\mysql-8.0.44-winx64" --datadir="C:\Users\a\devtools\mysql\data" --port=3306 --bind-address=127.0.0.1 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --console

echo.
echo 中间件启动命令已发出，等待 8 秒后自动验证 ...
timeout /t 8 /nobreak >nul

"C:\Users\a\devtools\redis\Redis-7.4.11-Windows-x64-msys2\redis-cli.exe" -h 127.0.0.1 -p 6379 -a herbal_tea_dev ping 2>nul | findstr PONG >nul && echo [OK] Redis 连接正常 (PONG) || echo [FAIL] Redis 未就绪
"C:\Users\a\devtools\mysql\mysql-8.0.44-winx64\bin\mysql.exe" -u herbal_tea -pherbal_tea_dev --protocol=tcp -h 127.0.0.1 herbal_tea -e "SELECT 1" >nul 2>&1 && echo [OK] MySQL 连接正常 || echo [FAIL] MySQL 未就绪

echo.
echo 完成后可运行 start-dev.cmd 启动后端应用。
endlocal
