#!/usr/bin/env python3
"""
Empaqueta servidor/cliente como ejecutables nativos via jpackage.

Requiere:
  - JDK 17+ con jpackage
  - Cliente: JavaFX module JARs (usa Maven cache o --javafx-home)
  - Windows: WiX Toolset (https://wixtoolset.org/) para .exe
  - macOS: Xcode para .dmg
  - Linux: dpkg-dev para .deb

Uso:
  python tools/package-native.py                        # server .deb + client .deb
  python tools/package-native.py --type exe             # server.exe + client.exe
  python tools/package-native.py --server-only          # solo servidor
  python tools/package-native.py --client-only          # solo cliente
"""

import argparse
import platform
import subprocess
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent


def detect_platform():
    system = platform.system().lower()
    valid = {"linux", "windows", "darwin"}
    if system not in valid:
        print(f"[!] Plataforma no soportada: {system}")
        sys.exit(1)
    return system


def find_maven_javafx() -> Path | None:
    """Busca JavaFX platform JARs en el cache de Maven (~/.m2/repository)."""
    repo = Path.home() / ".m2" / "repository" / "org" / "openjfx"
    if not repo.exists():
        return None
    modules = []
    for mod in ["javafx-base", "javafx-controls", "javafx-graphics",
                 "javafx-fxml", "javafx-media", "javafx-swing"]:
        jars = list(repo.glob(f"{mod}/25/{mod}-25-*.jar"))
        platform_jar = None
        for j in jars:
            name = j.name
            if "sources" not in name and "javadoc" not in name and "-win" not in name and "-mac" not in name and "-linux" in name:
                platform_jar = j
                break
        if platform_jar:
            modules.append(platform_jar)
    if not modules:
        return None
    jfx_dir = PROJECT / "target" / "javafx-modules"
    jfx_dir.mkdir(parents=True, exist_ok=True)
    for j in modules:
        (jfx_dir / j.name).symlink_to(j) if not (jfx_dir / j.name).exists() else None
    return jfx_dir


def find_system_javafx():
    """Busca JavaFX SDK en ubicaciones comunes del sistema."""
    candidates = [
        "/usr/lib/jvm/javafx-sdk/lib",
        "/usr/share/java/javafx/lib",
        "/opt/javafx-sdk/lib",
        str(Path.home() / ".javafx-sdk" / "25" / "lib"),
        str(Path.home() / "javafx-sdk-25" / "lib"),
    ]
    for c in candidates:
        p = Path(c)
        if p.exists():
            return p
    return None


def resolve_javafx():
    """Resuelve JavaFX module-path."""
    # 1. Maven cache
    maven_jfx = find_maven_javafx()
    if maven_jfx:
        return maven_jfx
    # 2. Sistema
    sys_jfx = find_system_javafx()
    if sys_jfx:
        return sys_jfx
    return None


def build_fat_jars():
    print("[ ] Compilando fat JARs...", flush=True)
    r = subprocess.run(
        ["mvn", "package", "-DskipTests", "-q"],
        cwd=PROJECT, capture_output=True, text=True,
    )
    if r.returncode != 0:
        print(f"[!] Error compilando:\n{r.stderr}")
        sys.exit(1)
    print("[✓] Fat JARs compilados", flush=True)


def jpackage_app(name, input_dir, main_jar, main_class,
                 output_dir, pkg_type, javafx_path, app_version="1.0"):
    print(f"\n[ ] Empaquetando {name}...", flush=True)
    cmd = [
        "jpackage",
        "--name", name,
        "--app-version", app_version,
        "--vendor", "Stirven",
        "--input", str(input_dir),
        "--main-jar", main_jar,
        "--main-class", main_class,
        "--dest", str(output_dir),
        "--java-options", "-Dprism.order=sw",
    ]
    if pkg_type:
        cmd.extend(["--type", pkg_type])
    if javafx_path:
        cmd.extend([
            "--module-path", str(javafx_path),
            "--add-modules", "javafx.controls,javafx.fxml,javafx.media,javafx.swing",
        ])
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        for line in r.stderr.split("\n"):
            if "error" in line.lower():
                print(f"  {line}")
        print(f"  ... salida completa en stderr, exit={r.returncode}")
        return False
    print(f"[✓] {name} empaquetado", flush=True)
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Empaqueta el juego como ejecutable nativo")
    parser.add_argument("--server-only", action="store_true")
    parser.add_argument("--client-only", action="store_true")
    parser.add_argument("--type", default=None,
                        help="exe, msi, deb, rpm, dmg, pkg")
    parser.add_argument("--javafx-home", default=None,
                        help="Ruta al JavaFX SDK (lib/)")
    parser.add_argument("--output", default=None,
                        help="Directorio de salida")
    parser.add_argument("--skip-build", action="store_true",
                        help="Saltar compilacion (usar JARs existentes)")
    args = parser.parse_args()

    platform_name = detect_platform()
    output_dir = Path(args.output or PROJECT / "dist")

    # Default package type
    pkg_type = args.type
    if not pkg_type:
        types = {"linux": "deb", "windows": "exe", "darwin": "dmg"}
        pkg_type = types[platform_name]
    print(f"[i] Plataforma: {platform_name}, Tipo: {pkg_type}")

    # Build
    if not args.skip_build:
        build_fat_jars()

    # JavaFX
    javafx_path = None
    if not args.server_only:
        if args.javafx_home:
            javafx_path = Path(args.javafx_home)
        else:
            javafx_path = resolve_javafx()
            if javafx_path:
                print(f"[i] JavaFX encontrado: {javafx_path}")
            else:
                print("[i] Sin JavaFX. Solo se empaquetara el servidor.")
                print("    Pasa --javafx-home <ruta> para empaquetar el cliente.")
                args.server_only = True

    output_dir.mkdir(parents=True, exist_ok=True)

    # Server
    if not args.client_only:
        server_dir = PROJECT / "server" / "target"
        if (server_dir / "server.jar").exists():
            jpackage_app(
                name="Shooter-Server",
                input_dir=server_dir,
                main_jar="server.jar",
                main_class="com.aa.server.Main",
                output_dir=output_dir,
                pkg_type=pkg_type,
                javafx_path=None,
            )

    # Client
    if not args.server_only and javafx_path:
        client_dir = PROJECT / "client" / "target"
        if (client_dir / "client.jar").exists():
            jpackage_app(
                name="Shooter-Client",
                input_dir=client_dir,
                main_jar="client.jar",
                main_class="com.aa.client.Main",
                output_dir=output_dir,
                pkg_type=pkg_type,
                javafx_path=javafx_path,
            )

    print(f"\n[✓] Completado. Archivos en: {output_dir}")
    for f in sorted(output_dir.iterdir()):
        size = f.stat().st_size
        print(f"  {f.name} ({size/1024/1024:.0f} MB)")


if __name__ == "__main__":
    main()
