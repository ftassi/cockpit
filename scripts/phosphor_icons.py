"""Download Phosphor "fill" icons and write them as Android vector drawables.

Usage: phosphor_icons.py OUT_DIR VERSION NAME...
Each NAME becomes OUT_DIR/ph_<name with - as _>.xml.
"""

import re
import sys
import urllib.request

out_dir, version, names = sys.argv[1], sys.argv[2], sys.argv[3:]

for name in names:
    url = f"https://unpkg.com/@phosphor-icons/core@{version}/assets/fill/{name}-fill.svg"
    svg = urllib.request.urlopen(url).read().decode()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    if not paths or 'viewBox="0 0 256 256"' not in svg:
        sys.exit(f"{name}: unexpected SVG at {url}")
    body = "\n".join(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{d}" />' for d in paths)
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Phosphor Icons "{name}" (fill), MIT License, https://phosphoricons.com -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
{body}
</vector>
"""
    with open(f"{out_dir}/ph_{name.replace('-', '_')}.xml", "w") as f:
        f.write(xml)
    print(f"ph_{name.replace('-', '_')}.xml")
