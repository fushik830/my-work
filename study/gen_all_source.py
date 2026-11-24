from pathlib import Path
root = Path(r'D:\小玩意\study\Stusy-complete\Stusy')
files = sorted(root.glob('app/src/main/java/**/*.kt')) + [root/'app/src/main/AndroidManifest.xml', root/'app/src/main/res/values/strings.xml']
out = ['# 哦鲸鲸喊你学习 · 完整源码合集', '', '> 工程内全部 Kotlin 源码、AndroidManifest 与 app_name，方便阅读/复制。实际工程以同目录 Gradle 项目为准。', '']
for f in files:
    rel = f.relative_to(root).as_posix()
    lang = 'kotlin' if f.suffix == '.kt' else 'xml'
    out += [f'## `{rel}`', '', f'```{lang}', f.read_text(encoding='utf-8').rstrip(), '```', '']
(root/'ALL_SOURCE.md').write_text('\n'.join(out), encoding='utf-8')
print('files:', len(files))
