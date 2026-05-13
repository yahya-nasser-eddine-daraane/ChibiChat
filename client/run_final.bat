@echo off
set JFX_PATH=C:\Users\hp1\.m2\repository\org\openjfx
set JARS=%JFX_PATH%\javafx-base\21\javafx-base-21-win.jar;%JFX_PATH%\javafx-controls\21\javafx-controls-21-win.jar;%JFX_PATH%\javafx-fxml\21\javafx-fxml-21-win.jar;%JFX_PATH%\javafx-graphics\21\javafx-graphics-21-win.jar;%JFX_PATH%\javafx-swing\21\javafx-swing-21-win.jar

echo Starting ChibiChat App...
java --module-path "%JARS%" --add-modules javafx.controls,javafx.fxml,javafx.swing -cp target/chibichat-1.0-SNAPSHOT.jar com.lanmessenger.ui.Launcher
pause
