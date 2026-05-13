@echo off
set DLL_PATH=C:\Users\hp1\OneDrive\Desktop\sqljdbc_13.4\enu\auth\x64
echo Starting ChibiServer with Windows Authentication...
java -Djava.library.path="%DLL_PATH%" -jar target/chibiserver-1.0-SNAPSHOT.jar
pause
