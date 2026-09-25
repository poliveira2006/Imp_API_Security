//.bat utilizado para facilitar a execução do programa
@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERRO: o JDK nao foi encontrado.
    echo Instale um JDK 17 ou superior e tente novamente.
    echo.
    pause
    exit /b 1
)

if not exist out mkdir out

dir /s /b "src\java\*.java" > sources.txt

echo Compilando o projeto...
javac -encoding UTF-8 -d out @sources.txt

if errorlevel 1 (
    echo.
    echo ERRO: a compilacao falhou.
    echo.
    pause
    exit /b 1
)

echo.
echo Projeto iniciado em:
echo http://localhost:8080
echo.
echo Para encerrar, feche esta janela.
echo.

java -cp out br.com.securityapi.Main

pause
