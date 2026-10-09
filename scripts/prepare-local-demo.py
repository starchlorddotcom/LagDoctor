#!/usr/bin/env python3
"""Prepare a new, loopback-only demo. Never overwrites an existing server."""
import argparse
import hashlib
import pathlib
import shutil
import urllib.request

VERSION = '26.3'
BUILD = 143
SHA256 = '32cf4a93545e218525bc4536b017c6b5d5085d27d449d64266a6b23ba4d0cbb9'
URL = f'https://fill-data.papermc.io/v1/objects/{SHA256}/paper-{VERSION}-{BUILD}.jar'
ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--accept-eula', action='store_true', help='Confirm you agree to https://aka.ms/MinecraftEULA')
parser.add_argument('--paper-jar', type=pathlib.Path, help='Use a previously downloaded pinned Paper JAR')
args = parser.parse_args()
jar = ROOT / 'build/libs/LagDoctor-0.3.0.jar'
if not jar.exists():
    parser.error('Build first: ./gradlew build')
server = ROOT / '.local-server-26.3'
if server.exists():
    parser.error('.local-server-26.3 already exists; use it or move it aside. No files changed.')
if not args.accept_eula:
    parser.error('Read https://aka.ms/MinecraftEULA then pass --accept-eula only if you agree.')
if args.paper_jar:
    data = args.paper_jar.read_bytes()
else:
    request = urllib.request.Request(URL, headers={'User-Agent': 'LagDoctor-local-demo/0.3.0 (https://github.com/starchlorddotcom/LagDoctor)'})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
if hashlib.sha256(data).hexdigest() != SHA256:
    parser.error('Paper checksum mismatch. No server created.')
server.mkdir()
(server / 'paper.jar').write_bytes(data)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25565
online-mode=true
enable-rcon=false
enable-query=false
white-list=true
enforce-whitelist=true
max-players=2
view-distance=4
simulation-distance=4
pause-when-empty-seconds=-1
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
motd=Lag Doctor private local demo
''')
plugin = server / 'plugins/LagDoctor'
plugin.mkdir(parents=True)
shutil.copy2(jar, server / 'plugins' / jar.name)
(plugin / 'config.yml').write_text('report-windows: 6\nconsole-alerts: true\nlocal-demo-enabled: true\n')
print('Prepared .local-server-26.3. Run with Java 25: cd .local-server-26.3 && java -Xms1G -Xmx2G -jar paper.jar --nogui')
