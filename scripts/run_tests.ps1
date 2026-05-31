param(
    [switch]$KeepGoing
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$javaCmd = (Get-Command java.exe).Source
$javacCmd = (Get-Command javac.exe).Source
$sources = Get-ChildItem -Path "src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force "build/classes" | Out-Null

Write-Host "Compiling compiler..."
& $javacCmd -encoding UTF-8 -d "build/classes" $sources

$failed = 0

function Invoke-TestCommand {
    param(
        [string]$Name,
        [string[]]$Command,
        [int]$ExpectedExitCode,
        [string[]]$MustContain,
        [string]$InputText
    )

    Write-Host ""
    Write-Host "== $Name =="

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $Command[0]
    $processInfo.Arguments = (($Command | Select-Object -Skip 1) -join " ")
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.RedirectStandardInput = $true
    $processInfo.UseShellExecute = $false

    $process = [System.Diagnostics.Process]::Start($processInfo)
    if ($null -ne $InputText) {
        $process.StandardInput.Write($InputText)
    }
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $output = $stdout + $stderr
    $exit = $process.ExitCode
    Write-Host $output

    $ok = $exit -eq $ExpectedExitCode
    foreach ($needle in $MustContain) {
        if (-not $output.Contains($needle)) {
            Write-Host "Missing expected text: $needle" -ForegroundColor Red
            $ok = $false
        }
    }

    if ($ok) {
        Write-Host "PASS: $Name" -ForegroundColor Green
    } else {
        Write-Host "FAIL: $Name (exit code $exit, expected $ExpectedExitCode)" -ForegroundColor Red
        $script:failed++
        if (-not $KeepGoing) {
            exit 1
        }
    }
}

Invoke-TestCommand `
    -Name "positive: arrays, math, control flow, function, codegen" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run", "tests/positive/arrays_math_control.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("OK arrays math control 20 3.5")

Invoke-TestCommand `
    -Name "positive: strings, sys funcs, function return" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run", "tests/positive/strings_functions.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("LEN 5", "POS 3", "COPY bcd", "REV edcba")

Invoke-TestCommand `
    -Name "positive: existing recursive/simple demo" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run", "examples/demo.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("2 3 5 7 11 13 17 19")

Invoke-TestCommand `
    -Name "vm: arrays, math, control flow, function" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run-vm", "tests/positive/arrays_math_control.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("OK arrays math control 20 3.5")

Invoke-TestCommand `
    -Name "vm: strings, builtins, downto function" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run-vm", "tests/positive/strings_functions.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("LEN 5", "POS 3", "COPY bcd", "REV edcba")

Invoke-TestCommand `
    -Name "vm: recursion, break, exit" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run-vm", "examples/demo.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("2 3 5 7 11 13 17 19")

Invoke-TestCommand `
    -Name "vm: repeat-until, continue, break" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run-vm", "tests/positive/vm_loop_control.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("VM loop 8")

Invoke-TestCommand `
    -Name "jvm: readln and output" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run", "tests/manual/read_io.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("HELLO Ilya 21") `
    -InputText "Ilya`n21`n"

Invoke-TestCommand `
    -Name "vm: readln and output" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--run-vm", "tests/manual/read_io.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("HELLO Ilya 21") `
    -InputText "Ilya`n21`n"

Invoke-TestCommand `
    -Name "optimizer: opt-level 0 preserves result" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--opt-level", "0", "--run-vm", "tests/positive/optimizer_flow.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("OPT 24")

Invoke-TestCommand `
    -Name "optimizer: opt-level 1 preserves result" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--opt-level", "1", "--run-vm", "tests/positive/optimizer_flow.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("OPT 24")

Invoke-TestCommand `
    -Name "negative: undeclared, assignment, condition, break" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "tests/negative/semantic_errors.pas") `
    -ExpectedExitCode 1 `
    -MustContain @("Semantic diagnostics", "Undeclared identifier 'y'", "Cannot assign boolean to integer", "Boolean expression expected", "Break is allowed only inside a loop")

Invoke-TestCommand `
    -Name "negative: calls and system function types" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "tests/negative/bad_calls_and_types.pas") `
    -ExpectedExitCode 1 `
    -MustContain @("Routine 'add' expects 2 arguments", "Cannot assign integer to string", "Integer expression expected, got string", "Length requires string or array argument")

Invoke-TestCommand `
    -Name "vm listing: emit .pvm file" `
    -Command @($javaCmd, "-cp", "build/classes", "pascal.Main", "--no-ast", "--emit-vm", "build/generated/tests/strings_functions.pvm", "tests/positive/strings_functions.pas") `
    -ExpectedExitCode 0 `
    -MustContain @("VM listing:")

$vmListing = Get-Content "build/generated/tests/strings_functions.pvm" -Raw
foreach ($needle in @("STRING_LENGTH", "COPY", "CALL reverse", "HALT")) {
    if (-not $vmListing.Contains($needle)) {
        Write-Host "Missing expected VM instruction text: $needle" -ForegroundColor Red
        $failed++
        if (-not $KeepGoing) {
            exit 1
        }
    }
}

if ($failed -gt 0) {
    Write-Host ""
    Write-Host "$failed test(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "All tests passed." -ForegroundColor Green
