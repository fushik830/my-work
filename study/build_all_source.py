from pathlib import Path

root = Path(r"D:\小玩意\study\Stusy-complete\Stusy")
src = root / "app/src/main"
files = sorted((src / "java").rglob("*.kt")) + [src / "AndroidManifest.xml", src / "res/values/strings.xml"]
out = [
    "# 哦鲸鲸喊你学习 · 完整源码合集",
    "",
    "> 工程内全部 Kotlin 源码、AndroidManifest 与 app_name，方便阅读/复制。实际工程以同目录 Gradle 项目为准。",
    "",
]
for path in files:
    rel = path.relative_to(root).as_posix()
    lang = "kotlin" if path.suffix == ".kt" else ("xml" if path.suffix == ".xml" else "text")
    out.append(f"## `{rel}`")
    out.append("")
    out.append(f"```{lang}")
    out.append(path.read_text(encoding="utf-8").rstrip())
    out.append("```")
    out.append("")
(root / "ALL_SOURCE.md").write_text("\n".join(out), encoding="utf-8")
print(f"wrote {len(files)} files")
