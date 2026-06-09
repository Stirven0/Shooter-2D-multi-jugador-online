#!/usr/bin/env python3
"""Package the shooter game into portable releases for Linux, Windows, and Mac.
Produces platform-specific ZIPs with the correct JavaFX native JARs."""

import os, shutil, glob, zipfile, urllib.request

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RELEASE = os.path.join(PROJECT, "release")
M2 = os.path.expanduser("~/.m2/repository")
JFX_VERSION = "25"
JFX_MAVEN = f"https://repo1.maven.org/maven2/org/openjfx"

def copy(src, dst_dir):
    name = os.path.basename(src)
    dst = os.path.join(dst_dir, name)
    if not os.path.exists(dst):
        shutil.copy2(src, dst)
    return dst

def find_m2(group_id, artifact_id):
    """Find newest version of a JAR in Maven cache."""
    base = os.path.join(M2, group_id.replace(".", os.sep), artifact_id)
    if not os.path.exists(base):
        return None
    best_ver = None
    for ver in os.listdir(base):
        jar = os.path.join(base, ver, f"{artifact_id}-{ver}.jar")
        if os.path.exists(jar) and (best_ver is None or ver > best_ver):
            best_ver = ver
    return os.path.join(base, best_ver, f"{artifact_id}-{best_ver}.jar") if best_ver else None

def download_jfx(artifact, classifier, dst_dir):
    """Download a JavaFX JAR from Maven Central if not cached."""
    name = f"{artifact}-{JFX_VERSION}-{classifier}.jar"
    dst = os.path.join(dst_dir, name)
    if os.path.exists(dst):
        return dst
    url = f"{JFX_MAVEN}/{artifact}/{JFX_VERSION}/{name}"
    print(f"    Downloading {name}...", end=" ", flush=True)
    try:
        urllib.request.urlretrieve(url, dst)
        print(f"{os.path.getsize(dst):,d} bytes")
        return dst
    except Exception as e:
        print(f"FAILED: {e}")
        return None

def package_client(platform, platform_label):
    """Package client for a specific platform (linux, win, mac)."""
    class_name = {"linux": "linux", "win": "win", "mac": "mac"}[platform]
    ext = "sh" if platform == "linux" else "cmd"
    sep = ":" if platform in ("linux", "mac") else ";"
    
    base = os.path.join(RELEASE, f"Shooter-Client-{platform}")
    shutil.rmtree(base, ignore_errors=True)
    os.makedirs(base, exist_ok=True)
    os.makedirs(os.path.join(base, "lib"), exist_ok=True)
    os.makedirs(os.path.join(base, "javafx-sdk"), exist_ok=True)
    
    print(f"\n{'='*50}")
    print(f"  Client — {platform_label}")
    print(f"{'='*50}")
    
    # JavaFX platform JARs
    jfx_dir = os.path.join(base, "javafx-sdk")
    jfx_modules = ["javafx-base", "javafx-graphics", "javafx-controls", "javafx-media"]
    jfx_jars = []
    for mod in jfx_modules:
        # Try Maven cache first, then download
        jar = os.path.join(M2, "org", "openjfx", mod, JFX_VERSION,
                          f"{mod}-{JFX_VERSION}-{class_name}.jar")
        if not os.path.exists(jar):
            jar = download_jfx(mod, class_name, jfx_dir)
        if jar:
            copy(jar, jfx_dir)
            jfx_jars.append(os.path.basename(jar))
            print(f"  + javafx-sdk/{os.path.basename(jar)}")
    
    # Dependencies
    print("\n  lib/:")
    lib_dir = os.path.join(base, "lib")
    copy(os.path.join(PROJECT, "client", "target", "client-1.0-SNAPSHOT.jar"), lib_dir)
    copy(os.path.join(PROJECT, "shared", "target", "shared-1.0-SNAPSHOT.jar"), lib_dir)
    print("  + client-1.0-SNAPSHOT.jar")
    print("  + shared-1.0-SNAPSHOT.jar")
    
    mvn_deps = {
        "org/java-websocket/Java-WebSocket/1.5.6/Java-WebSocket-1.5.6.jar": "Java-WebSocket-1.5.6.jar",
        "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar": "gson-2.10.1.jar",
        "io/modelcontextprotocol/sdk/mcp/0.17.2/mcp-0.17.2.jar": "mcp-0.17.2.jar",
    }
    for m2path, name in mvn_deps.items():
        src = os.path.join(M2, m2path)
        if os.path.exists(src):
            copy(src, lib_dir)
            print(f"  + {name}")
    
    auto_deps = [
        ("com.fasterxml.jackson.core", "jackson-databind"),
        ("com.fasterxml.jackson.core", "jackson-core"),
        ("com.fasterxml.jackson.core", "jackson-annotations"),
        ("org.slf4j", "slf4j-api"),
        ("org.slf4j", "slf4j-simple"),
    ]
    for group, artifact in auto_deps:
        src = find_m2(group, artifact)
        if src:
            copy(src, lib_dir)
            print(f"  + {os.path.basename(src)}")
        else:
            print(f"  ! {artifact}: not found")
    
    # Assets
    print("\n  assets:")
    for kind in ["sprites", "audio", "maps"]:
        src = os.path.join(PROJECT, "client", "src", "main", "resources", kind)
        dst = os.path.join(base, kind)
        if os.path.exists(src):
            shutil.copytree(src, dst)
            nfiles = sum(1 for _, _, fs in os.walk(dst) for _ in fs)
            print(f"  + {kind}/ ({nfiles} files)")
    
    css = os.path.join(PROJECT, "client", "src", "main", "resources", "style.css")
    if os.path.exists(css):
        copy(css, base)
        print("  + style.css")
    
    # Launcher script
    if platform in ("linux", "mac"):
        script = os.path.join(base, "run-client.sh")
        with open(script, "w") as f:
            f.write(f"""#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
JFX_MODS=""
for m in "$DIR"/javafx-sdk/*.jar; do JFX_MODS="$JFX_MODS:$m"; done
CLASSPATH=""
for jar in "$DIR"/lib/*.jar; do CLASSPATH="$CLASSPATH:$jar"; done
java \\
    --module-path "${{JFX_MODS:1}}" \\
    --add-modules javafx.controls,javafx.graphics,javafx.media \\
    -cp "${{CLASSPATH:1}}" \\
    com.aa.client.Main "$@"
""")
        os.chmod(script, 0o755)
    else:
        script = os.path.join(base, "run-client.cmd")
        with open(script, "w") as f:
            f.write(f"""@echo off
setlocal enabledelayedexpansion
set DIR=%~dp0
set JFX_MODS=
for %%f in ("%DIR%javafx-sdk\\*.jar") do set JFX_MODS=!JFX_MODS!;%%f
set CLASSPATH=
for %%f in ("%DIR%lib\\*.jar") do set CLASSPATH=!CLASSPATH!;%%f
java --module-path "!JFX_MODS:~1!" --add-modules javafx.controls,javafx.graphics,javafx.media -cp "!CLASSPATH:~1!" com.aa.client.Main %*
endlocal
""")
    print(f"\n  + {os.path.basename(script)}")
    
    # README
    with open(os.path.join(base, "README.txt"), "w") as f:
        f.write(f"""SHOOTER 2D — Cliente v0.1 ({platform_label})
========================================

Requisito: Java 25+

Ejecutar:
  ./run-client.sh --host <ip-del-servidor>   (Linux/Mac)
  run-client.cmd --host <ip-del-servidor>    (Windows)

Modo MCP (IA):
  Agregar flag --mcp

Flags:
  --mcp              Activar servidor MCP TCP en :4567
  --host <ip>        IP del servidor (default: localhost)
  --port <puerto>    Puerto del servidor (default: 8080)
  --hostmcp <ip>     IP para MCP (default: localhost)
  --portmcp <n>      Puerto MCP (default: 4567)

Credenciales dev: player1/pass1, player2/pass2
Repo: https://github.com/Stirven0/Shooter-2D-multi-jugador-online
""")
    
    # ZIP
    total = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(base) for f in fs)
    zip_path = os.path.join(RELEASE, f"Shooter-Client-v0.1-{class_name}.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(base):
            for fn in files:
                fp = os.path.join(root, fn)
                zf.write(fp, os.path.relpath(fp, base))
    zip_size = os.path.getsize(zip_path)
    print(f"\n  → {os.path.basename(zip_path)}  ({zip_size:,d} bytes)")
    return zip_size

# ── Server (cross-platform) ────────────────────────
print("=== Server (all platforms) ===")
server_dir = os.path.join(RELEASE, "Shooter-Server")
shutil.rmtree(server_dir, ignore_errors=True)
os.makedirs(server_dir)

shutil.copy2(os.path.join(PROJECT, "server", "target", "server-1.0-SNAPSHOT.jar"),
             os.path.join(server_dir, "shooter-server.jar"))
print(f"  + shooter-server.jar ({os.path.getsize(os.path.join(server_dir, 'shooter-server.jar')):,d} bytes)")

for ext, script, content in [
    ("sh", "run-server.sh", "#!/bin/bash\nDIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\njava -jar \"$DIR/shooter-server.jar\" \"$@\"\n"),
    ("cmd", "run-server.cmd", "@echo off\nset DIR=%~dp0\njava -jar \"%DIR%shooter-server.jar\" %*\n"),
]:
    path = os.path.join(server_dir, script)
    with open(path, "w") as f:
        f.write(content)
    if ext == "sh":
        os.chmod(path, 0o755)
    print(f"  + {script}")

with open(os.path.join(server_dir, "README.txt"), "w") as f:
    f.write("""SHOOTER 2D — Servidor v0.1
===========================
Requisito: Java 25+
Funciona en Windows, Linux y Mac.

Ejecutar:
  ./run-server.sh           (Linux / Mac)
  run-server.cmd            (Windows)
  → Escucha en puerto 8080

Cambiar puerto: java -jar shooter-server.jar -Dserver.port=9090
Repo: https://github.com/Stirven0/Shooter-2D-multi-jugador-online
""")

# Server ZIP
total_srv = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(server_dir) for f in fs)
srv_zip = os.path.join(RELEASE, "Shooter-Server-v0.1.zip")
with zipfile.ZipFile(srv_zip, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(server_dir):
        for fn in files:
            fp = os.path.join(root, fn)
            zf.write(fp, os.path.relpath(fp, server_dir))
print(f"\n  → Shooter-Server-v0.1.zip  ({os.path.getsize(srv_zip):,d} bytes)")

# ── Clients per platform ──────────────────────────
client_sizes = {}
for plat, label in [("linux", "Linux"), ("win", "Windows"), ("mac", "Mac")]:
    client_sizes[plat] = package_client(plat, label)

# ── Summary ────────────────────────────────────────
print(f"\n{'='*60}")
print(f"  RELEASE v0.1")
print(f"{'='*60}")
print(f"  Shooter-Server-v0.1.zip      = {os.path.getsize(srv_zip):>10,} bytes  (all platforms)")
for plat, label in [("linux", "Linux"), ("win", "Windows"), ("mac", "Mac")]:
    z = os.path.join(RELEASE, f"Shooter-Client-v0.1-{plat}.zip")
    sz = os.path.getsize(z) if os.path.exists(z) else 0
    print(f"  Shooter-Client-v0.1-{plat}.zip  = {sz:>10,} bytes  ({label})")
print(f"\n  → {RELEASE}/")
