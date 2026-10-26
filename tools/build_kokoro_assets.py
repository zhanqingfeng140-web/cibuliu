#!/usr/bin/env python3
"""
把 misaki 的 us_gold / us_silver 音素词典压缩成 App 能秒加载的格式。

Kokoro 不认文字，只认音素序列。所以「任意单词能不能读」完全取决于这张表
有没有收录。手机上没有 G2P 引擎（espeak 要 NDK 编译，这套方案刻意避开了），
查不到的词统一退回系统 TTS。

输出（都放进 app/src/main/assets/kokoro/）：
  lex.txt    word<TAB>phonemes，按 word 的 UTF-8 字节序排列，供二分查找
  vocab.txt  单个音素字符<TAB>id，114 行
  af_heart.bin  音色向量（510 × 256 float32），直接拷

用法：python tools/build_kokoro_assets.py <词典目录>
"""
import json
import os
import struct
import sys

src = sys.argv[1] if len(sys.argv) > 1 else "."
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "app", "src", "main", "assets", "kokoro")
os.makedirs(out, exist_ok=True)

vocab = json.load(open(os.path.join(src, "vocab_index.json"), encoding="utf-8"))["vocab"]
gold = json.load(open(os.path.join(src, "us_gold.json"), encoding="utf-8"))
silver = json.load(open(os.path.join(src, "us_silver.json"), encoding="utf-8"))

# 音素串里必须每个字符都在 vocab 里，否则模型会收到非法 id
def valid(ph):
    return bool(ph) and all(ch in vocab for ch in ph)

def resolve(ph):
    """misaki 里 790 个多音词形如 {"DEFAULT": ..., "VERB": ...}。

    名词重音靠前、动词靠后（ˈæbstɹækt / əbstɹˈækt），背单词默认按名词读，
    所以取 DEFAULT；没有 DEFAULT 就取第一个合法项。
    """
    if isinstance(ph, str):
        return ph
    if isinstance(ph, dict):
        if valid(ph.get("DEFAULT", "")):
            return ph["DEFAULT"]
        for v in ph.values():
            if isinstance(v, str) and valid(v):
                return v
    return None


# gold 优先（标注重音更可靠），silver 只补空缺
lex = {}
dropped = 0
for name, table in (("gold", gold), ("silver", silver)):
    for word, raw in table.items():
        ph = resolve(raw)
        if ph is None:
            dropped += 1
            continue                       # None / 空值
        if not valid(ph):
            dropped += 1
            continue                       # VBD / NOUN / None 之类的占位
        key = word.strip().lower()
        if not key or len(key) > 40:
            continue
        if key in lex:
            continue                       # gold 已经占位
        # 单词本身只保留字母数字与连字符撇号，避免奇怪的键
        if not all(c.isalnum() or c in " -'." for c in key):
            dropped += 1
            continue
        lex[key] = ph

items = sorted(lex.items(), key=lambda kv: kv[0].encode("utf-8"))

with open(os.path.join(out, "lex.txt"), "w", encoding="utf-8", newline="\n") as f:
    for w, p in items:
        f.write(f"{w}\t{p}\n")

with open(os.path.join(out, "vocab.txt"), "w", encoding="utf-8", newline="\n") as f:
    for ch, idx in sorted(vocab.items(), key=lambda kv: kv[1]):
        f.write(f"{ch}\t{idx}\n")

voice_src = os.path.join(src, "af_heart.bin")
if os.path.exists(voice_src):
    data = open(voice_src, "rb").read()
    floats = len(data) // 4
    assert floats % 256 == 0, "voice 形状不对"
    open(os.path.join(out, "af_heart.bin"), "wb").write(data)
    print(f"voice: {len(data)/1024:.0f} KB, {floats//256} 行 × 256")
else:
    print("!! 缺少 af_heart.bin")

# 英式音色：设置里选「英式」时用这个
for name in ("bf_emma", "bm_george"):
    p = os.path.join(src, name + ".bin")
    if os.path.exists(p):
        open(os.path.join(out, name + ".bin"), "wb").write(open(p, "rb").read())
        print(f"voice: {name}.bin 已拷贝")

size = os.path.getsize(os.path.join(out, "lex.txt"))
print(f"lex:  {len(items)} 词, {size/1024/1024:.2f} MB, 丢弃 {dropped} 条非法")
print(f"vocab: {len(vocab)} 个音素")
