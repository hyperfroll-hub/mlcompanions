@echo off
chcp 65001 >nul
set VERSION=1.0.0
echo Сборка мода... Первый запуск качает Minecraft и Forge, это 5-15 минут.
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo ОШИБКА СБОРКИ. Скопируй текст ошибки выше и отправь его Claude.
  pause
  exit /b 1
)
echo.
echo ГОТОВО! Файл мода: build\libs\mlcompanions-1.0.0.jar
explorer build\libs
pause
