param()

$projectDir = Split-Path -Parent $PSScriptRoot
$m2 = "$env:USERPROFILE\.m2\repository"
$javaHome = if (Get-Command "java" -ErrorAction SilentlyContinue) {
    (Get-Command "java").Source
} else {
    throw "Java not found"
}

# Build classpath
$cp = @(
    "$projectDir\client\target\classes",
    "$projectDir\target\project-local-repo\com.aa\shared\1.0-SNAPSHOT\shared-1.0-SNAPSHOT.jar",
    "$m2\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar",
    "$m2\org\java-websocket\Java-WebSocket\1.5.6\Java-WebSocket-1.5.6.jar",
    "$m2\io\modelcontextprotocol\sdk\mcp-core\0.17.2\mcp-core-0.17.2.jar",
    "$m2\io\modelcontextprotocol\sdk\mcp-json\0.17.2\mcp-json-0.17.2.jar",
    "$m2\io\modelcontextprotocol\sdk\mcp-json-jackson2\0.17.2\mcp-json-jackson2-0.17.2.jar",
    "$m2\org\slf4j\slf4j-api\2.0.6\slf4j-api-2.0.6.jar",
    "$m2\com\fasterxml\jackson\core\jackson-databind\2.19.2\jackson-databind-2.19.2.jar",
    "$m2\com\fasterxml\jackson\core\jackson-core\2.19.2\jackson-core-2.19.2.jar",
    "$m2\com\fasterxml\jackson\core\jackson-annotations\2.19.2\jackson-annotations-2.19.2.jar",
    "$m2\io\projectreactor\reactor-core\3.7.0\reactor-core-3.7.0.jar",
    "$m2\org\reactivestreams\reactive-streams\1.0.4\reactive-streams-1.0.4.jar",
    "$m2\com\networknt\json-schema-validator\2.0.0\json-schema-validator-2.0.0.jar",
    "$m2\com\ethlo\time\itu\1.14.0\itu-1.14.0.jar",
    "$m2\com\fasterxml\jackson\dataformat\jackson-dataformat-yaml\2.18.3\jackson-dataformat-yaml-2.18.3.jar",
    "$m2\org\yaml\snakeyaml\2.3\snakeyaml-2.3.jar"
) -join ';'

# Build JavaFX module path
$jfxVersion = "25"
$modulePath = @(
    "$m2\org\openjfx\javafx-base\$jfxVersion\javafx-base-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-base\$jfxVersion\javafx-base-$jfxVersion.jar",
    "$m2\org\openjfx\javafx-controls\$jfxVersion\javafx-controls-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-controls\$jfxVersion\javafx-controls-$jfxVersion.jar",
    "$m2\org\openjfx\javafx-fxml\$jfxVersion\javafx-fxml-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-fxml\$jfxVersion\javafx-fxml-$jfxVersion.jar",
    "$m2\org\openjfx\javafx-graphics\$jfxVersion\javafx-graphics-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-graphics\$jfxVersion\javafx-graphics-$jfxVersion.jar",
    "$m2\org\openjfx\javafx-media\$jfxVersion\javafx-media-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-media\$jfxVersion\javafx-media-$jfxVersion.jar",
    "$m2\org\openjfx\javafx-swing\$jfxVersion\javafx-swing-$jfxVersion-win.jar",
    "$m2\org\openjfx\javafx-swing\$jfxVersion\javafx-swing-$jfxVersion.jar"
) -join ';'

# Build program arguments
$args = @()
$args += "--mcp"

Write-Host "[launch] Starting MCP client" | Out-Host

# Launch Java directly - stdin/stdout from this process are inherited by java
& "java" `
    "--module-path" $modulePath `
    "--add-modules" "javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media" `
    "-classpath" $cp `
    "com.aa.client.Main" `
    $args
