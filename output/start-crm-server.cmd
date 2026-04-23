@echo off
cd /d E:\studio\test\seedcrm\crm_server
"C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin\java.exe" -jar target\crm_server-0.0.1-SNAPSHOT.jar 1>>E:\studio\test\seedcrm\output\crm-server-run.log 2>>E:\studio\test\seedcrm\output\crm-server-run.err.log
