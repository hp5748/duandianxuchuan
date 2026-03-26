# 设置 Maven 环境变量
$mvnHome = "C:\tools\apache-maven-3.9.9"
$mvnBin = "$mvnHome\bin"

# 设置 MAVEN_HOME
[Environment]::SetEnvironmentVariable("MAVEN_HOME", $mvnHome, "User")
Write-Host "MAVEN_HOME = $mvnHome"

# 添加到 PATH
$userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
if ($userPath -notlike "*apache-maven-3.9.9*") {
    $newPath = "$userPath;$mvnBin"
    [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
    Write-Host "PATH updated: added $mvnBin"
} else {
    Write-Host "PATH already contains Maven"
}

Write-Host ""
Write-Host "Environment variables set successfully!"
Write-Host "Please restart your terminal to apply changes."
