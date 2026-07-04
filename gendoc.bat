@echo off
setlocal

rem ============================================================================
rem  gendoc.bat  --  Windows equivalent of gendoc.sh
rem
rem  Regenerates the Panola HelpSource *.schelp files from the class sources
rem  using the "whelk" documentation generator.
rem
rem  whelk runs from its own virtual environment (see WHELKPY below), which
rem  already has the required dependencies (toml, mako) installed. To recreate
rem  that environment from scratch:
rem      py -m venv D:\Projects\python\whelk\.venv
rem      D:\Projects\python\whelk\.venv\Scripts\python.exe -m pip install toml mako
rem ============================================================================

rem --- Location of the whelk generator on this machine ------------------------
set "WHELK=D:\Projects\python\whelk\whelk.py"

rem --- Python interpreter from whelk's virtual environment --------------------
set "WHELKPY=D:\Projects\python\whelk\.venv\Scripts\python.exe"

rem --- Panola project root (the folder this script lives in) ------------------
rem  %~dp0 expands to this script's drive+path and ends with a backslash.
set "PANOLA=%~dp0"

rem --- Sanity check ----------------------------------------------------------
if not exist "%WHELK%" (
    echo ERROR: whelk not found at "%WHELK%"
    echo        Edit the WHELK variable at the top of this script.
    exit /b 1
)
if not exist "%WHELKPY%" (
    echo ERROR: whelk virtual environment not found at "%WHELKPY%"
    echo        Create it with:
    echo            py -m venv D:\Projects\python\whelk\.venv
    echo            D:\Projects\python\whelk\.venv\Scripts\python.exe -m pip install toml mako
    exit /b 1
)

rem --- Remove previously generated help files --------------------------------
echo Removing old help files...
del /Q "%PANOLA%HelpSource\Classes\*.schelp" >nul 2>&1

rem --- Regenerate help files from the class sources --------------------------
rem  whelk expands the *.sc globs itself, so pass them as literal patterns.
echo Generating help files...
"%WHELKPY%" "%WHELK%" -i "%PANOLA%Classes\*.sc" "%PANOLA%Classes\tests\*.sc" -o "%PANOLA%HelpSource\Classes"

if errorlevel 1 (
    echo.
    echo ERROR: whelk failed. If it complained about a missing module, install
    echo        its dependencies with:
    echo            "%WHELKPY%" -m pip install toml mako
    exit /b 1
)

echo Done.
endlocal
