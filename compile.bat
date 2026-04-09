@echo off
echo Building Nifty 500 Momentum Backtest Platform...
mvn clean package -q
if %ERRORLEVEL% == 0 (
    echo Build successful. Fat jar: dist\momentum-1.0.0-fat.jar
    echo Run with: java -jar dist\momentum-1.0.0-fat.jar
) else (
    echo Build FAILED. Check Maven output above.
)
