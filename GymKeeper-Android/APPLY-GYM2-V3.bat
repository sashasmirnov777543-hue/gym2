@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo === GymKeeper Offline 3.0: H2 -^> V9 ===
echo.
if not exist "GymKeeper-Android\app\src\main\assets\index.html" (
  echo ОШИБКА: положите три файла из архива в корень репозитория gym2.
  echo Рядом должна находиться папка GymKeeper-Android.
  pause
  exit /b 1
)
if not exist "apply-gym2-v3.mjs" (
  echo ОШИБКА: не найден apply-gym2-v3.mjs.
  pause
  exit /b 1
)
if not exist "program-v3.js" (
  echo ОШИБКА: не найден program-v3.js.
  pause
  exit /b 1
)
node apply-gym2-v3.mjs
if errorlevel 1 (
  echo.
  echo Ошибка применения. Ничего не коммитьте и пришлите весь вывод.
  pause
  exit /b 1
)
echo.
echo Проверяю JavaScript...
node --check GymKeeper-Android\app\src\main\assets\program-v3.js
if errorlevel 1 goto :failed
node --check GymKeeper-Android\app\src\main\assets\app.js
if errorlevel 1 goto :failed
node --check GymKeeper-Android\app\src\main\assets\data-v2.js
if errorlevel 1 goto :failed
node --check GymKeeper-Android\app\src\main\assets\training-v2.js
if errorlevel 1 goto :failed
node --check GymKeeper-Android\app\src\main\assets\analytics-v2.js
if errorlevel 1 goto :failed
echo.
echo Запускаю тесты данных и логики...
cd GymKeeper-Android
node --test tests\*.test.cjs
if errorlevel 1 goto :failed2
cd ..
echo.
echo ГОТОВО. Проверки пройдены.
echo Удалите APPLY-GYM2-V3.bat, apply-gym2-v3.mjs и внешний program-v3.js.
echo Затем GitHub Desktop: Commit to main -^> Push origin.
pause
exit /b 0
:failed2
cd ..
:failed
echo.
echo ПРОВЕРКА НЕ ПРОШЛА. Ничего не коммитьте и пришлите весь вывод.
pause
exit /b 1
