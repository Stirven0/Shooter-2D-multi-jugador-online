#!/usr/bin/env python3
"""Package the shooter game into a portable release for Linux."""

import os, shutil, glob

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RELEASE = os.path.join(PROJECT, "release")
CLIENT = os.path.join(RELEASE, "Shooter-Client")
SERVER = os.path.join(RELEASE, "Shooter-Server")
M2 = os.path.expanduser("~/.m2/repository")

def copy(src, dst_dir):
    name = os.path.basename(src)
    dst = os.path.join(dst_dir, name)
    if not os.path.exists(dst):
        shutil.copy2(src, dst)
    return dst

# Clean and recreate
shutil.rmtree(CLIENT, ignore_errors=True)
shutil.rmtree(SERVER, ignore_errors=True)
os.makedirs(CLIENT, exist_ok=True)
os.makedirs(os.path.join(CLIENT, "lib"), exist_ok=True)
os.makedirs(os.path.join(CLIENT, "javafx-sdk"), exist_ok=True)
os.makedirs(SERVER, exist_ok=True)

# ── Server ──────────────────────────────────────────
print("=== Server ===")
server_jar = os.path.join(PROJECT, "server", "target", "server-1.0-SNAPSHOT.jar")
shutil.copy2(server_jar, os.path.join(SERVER, "shooter-server.jar"))
print(f"  + shooter-server.jar ({os.path.getsize(os.path.join(SERVER, 'shooter-server.jar')):,d} bytes)")

with open(os.path.join(SERVER, "run-server.sh"), "w") as f:
    f.write("""#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/shooter-server.jar" "$@"
""")
os.chmod(os.path.join(SERVER, "run-server.sh"), 0o755)
print("  + run-server.sh")

with open(os.path.join(SERVER, "run-server.cmd"), "w") as f:
    f.write("""@echo off
set DIR=%~dp0
java -jar "%DIR%shooter-server.jar" %*
""")
print("  + run-server.cmd")

# ── Client JavaFX SDK ─────────────────────────────
print("\n=== Client ===")
jfx_dir = os.path.join(CLIENT, "javafx-sdk")
jfx_modules = ["javafx-base", "javafx-graphics", "javafx-controls", "javafx-media"]
jfx_paths = []
for mod in jfx_modules:
    jar = os.path.join(M2, "org", "openjfx", mod, "25", f"{mod}-25-linux.jar")
    if os.path.exists(jar):
        copy(jar, jfx_dir)
        jfx_paths.append(f"$DIR/javafx-sdk/{mod}-25-linux.jar")
        print(f"  + javafx-sdk/{mod}-25-linux.jar")

# ── Client libs ────────────────────────────────────
print("\n  lib/:")
lib_dir = os.path.join(CLIENT, "lib")

client_jar = os.path.join(PROJECT, "client", "target", "client-1.0-SNAPSHOT.jar")
copy(client_jar, lib_dir)
print(f"  + client-1.0-SNAPSHOT.jar")

shared_jar = os.path.join(PROJECT, "shared", "target", "shared-1.0-SNAPSHOT.jar")
copy(shared_jar, lib_dir)
print(f"  + shared-1.0-SNAPSHOT.jar")

# Non-JavaFX runtime deps — auto-discover from Maven cache
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

# Resolve transitive deps through Maven dependency tree
mvn_deps = {
    "org/java-websocket/Java-WebSocket/1.5.6/Java-WebSocket-1.5.6.jar": "Java-WebSocket-1.5.6.jar",
    "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar": "gson-2.10.1.jar",
    "io/modelcontextprotocol/sdk/mcp/0.17.2/mcp-0.17.2.jar": "mcp-0.17.2.jar",
}

# Auto-discover transitive deps
auto_deps = [
    ("com.fasterxml.jackson.core", "jackson-databind"),
    ("com.fasterxml.jackson.core", "jackson-core"),
    ("com.fasterxml.jackson.core", "jackson-annotations"),
    ("org.slf4j", "slf4j-api"),
    ("org.slf4j", "slf4j-simple"),
]

for m2path, name in mvn_deps.items():
    src = os.path.join(M2, m2path)
    if os.path.exists(src):
        copy(src, lib_dir)
        print(f"  + {name}")

for group, artifact in auto_deps:
    src = find_m2(group, artifact)
    if src:
        copy(src, lib_dir)
        print(f"  + {os.path.basename(src)}")
    else:
        print(f"  ! {artifact}: not found in M2")

# ── Client assets ──────────────────────────────────
print("\n  assets:")
for kind in ["sprites", "audio", "maps"]:
    src = os.path.join(PROJECT, "client", "src", "main", "resources", kind)
    dst = os.path.join(CLIENT, kind)
    if os.path.exists(src):
        shutil.copytree(src, dst)
        nfiles = sum(1 for _, _, fs in os.walk(dst) for _ in fs)
        print(f"  + {kind}/ ({nfiles} files)")

css = os.path.join(PROJECT, "client", "src", "main", "resources", "style.css")
if os.path.exists(css):
    copy(css, CLIENT)
    print("  + style.css")

# ── Linux launcher ─────────────────────────────────
with open(os.path.join(CLIENT, "run-client.sh"), "w") as f:
    f.write("""#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
JFX_MODS=""
for m in "$DIR"/javafx-sdk/*.jar; do
    JFX_MODS="$JFX_MODS:$m"
done
CLASSPATH=""
for jar in "$DIR"/lib/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done
java \\
    --module-path "${JFX_MODS:1}" \\
    --add-modules javafx.controls,javafx.graphics,javafx.media \\
    -cp "${CLASSPATH:1}" \\
    com.aa.client.Main "$@"
""")
os.chmod(os.path.join(CLIENT, "run-client.sh"), 0o755)
print("\n  + run-client.sh")

# ── Windows launcher ─────────────────────────────
with open(os.path.join(CLIENT, "run-client.cmd"), "w") as f:
    f.write("""@echo off
set DIR=%~dp0
set JFX_MODS=
for %%f in ("%DIR%javafx-sdk\\*.jar") do set JFX_MODS=!JFX_MODS!;%%f
set CLASSPATH=
for %%f in ("%DIR%lib\\*.jar") do set CLASSPATH=!CLASSPATH!;%%f
java --module-path "%JFX_MODS:~1%" --add-modules javafx.controls,javafx.graphics,javafx.media -cp "%CLASSPATH:~1%" com.aa.client.Main %*
""")
print("  + run-client.cmd")

# ── README ────────────────────────────────────────
with open(os.path.join(RELEASE, "README.txt"), "w") as f:
    f.write("""SHOOTER 2D — Multiplayer Game
================================

Requiere Java 25+ (JDK o JRE con java en PATH).

SERVIDOR
--------
  cd Shooter-Server
  ./run-server.sh           (Linux / Mac)
  run-server.cmd            (Windows)
  → Escucha en puerto 8080

CLIENTE
-------
  cd Shooter-Client
  ./run-client.sh           (Linux)
  run-client.cmd            (Windows)

  Modo MCP (IA):
  ./run-client.sh --mcp

  Servidor remoto:
  ./run-client.sh --host 192.168.1.100 --port 8080

Flags del cliente:
  --mcp              Activar servidor MCP TCP en :4567
  --host <ip>        IP del servidor (default: localhost)
  --port <puerto>    Puerto del servidor (default: 8080)
  --hostmcp <ip>     IP para MCP (default: localhost)
  --portmcp <n>      Puerto MCP (default: 4567)

Credenciales dev: player1/pass1, player2/pass2
""")

# ── Size report ───────────────────────────────────
total_server = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(SERVER) for f in fs)
total_client = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(CLIENT) for f in fs)
print(f"\n{'='*50}")
print(f"  Shooter-Server/  = {total_server:>10,} bytes")
print(f"  Shooter-Client/  = {total_client:>10,} bytes")
print(f"  Total           = {total_server + total_client:>10,} bytes")

# ── ZIP ───────────────────────────────────────────
import zipfile
zip_path = os.path.join(RELEASE, "Shooter-Release.zip")
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(CLIENT):
        for fn in files:
            fp = os.path.join(root, fn)
            zf.write(fp, os.path.relpath(fp, RELEASE))
    for root, dirs, files in os.walk(SERVER):
        for fn in files:
            fp = os.path.join(root, fn)
            zf.write(fp, os.path.relpath(fp, RELEASE))
    zf.write(os.path.join(RELEASE, "README.txt"), "README.txt")
print(f"  Release ZIP     = {os.path.getsize(zip_path):>10,} bytes")
print(f"  → {zip_path}")
