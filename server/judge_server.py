#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
回声背单词 · 电脑端服务（零依赖，只用标准库）

用法：
    python judge_server.py
    python judge_server.py --port 8765 --words D:\\path\\to\\words.tsv

手机和电脑连同一个 WiFi，在 App 的「设置 → 账号」里填
本机地址（启动时会打印，形如 http://192.168.1.5:8765）。

接口一览
    GET  /                 状态页（浏览器直接看）
    GET  /ping             健康检查
    POST /judge            判分  {"answer","meaning","mode"}
    GET  /words.tsv        词库
    POST /register         注册 {"user","pass"}
    POST /login            登录 {"user","pass"} -> {"token"}
    POST /logout           登出（Bearer）
    GET  /sync/status      云端数据概况（Bearer）
    POST /sync/push        上传整库（Bearer）
    GET  /sync/pull        拉取整库（Bearer）

数据都存在脚本同目录：
    accounts.json   账号（PBKDF2 加盐哈希，明文密码不落盘）
    tokens.json     登录令牌
    data/<user>.json 该账号的词库快照
"""

import argparse
import hashlib
import json
import os
import re
import secrets
import socket
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------------------------------------------------------- 配置

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://127.0.0.1:11434/api/generate")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "qwen2.5:3b")
LLM_TIMEOUT = float(os.environ.get("LLM_TIMEOUT", "20"))

WORDS_PATH = "words.tsv"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ACCOUNTS_PATH = os.path.join(BASE_DIR, "accounts.json")
TOKENS_PATH = os.path.join(BASE_DIR, "tokens.json")
DATA_DIR = os.path.join(BASE_DIR, "data")

_lock = threading.Lock()

# ---------------------------------------------------------------- 账号存储


def _load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _save_json(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)


def hash_password(password: str, salt: str = None):
    salt = salt or secrets.token_hex(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120_000)
    return salt, dk.hex()


def register(user: str, password: str):
    user = user.strip()
    if not re.fullmatch(r"[A-Za-z0-9_\u4e00-\u9fa5]{2,20}", user):
        return False, "用户名只能是 2~20 位中英文、数字或下划线"
    if len(password) < 6:
        return False, "密码至少 6 位"
    with _lock:
        accounts = _load_json(ACCOUNTS_PATH, {})
        if user in accounts:
            return False, "用户名已存在"
        salt, digest = hash_password(password)
        accounts[user] = {"salt": salt, "hash": digest, "createdAt": int(time.time())}
        _save_json(ACCOUNTS_PATH, accounts)
    return True, "注册成功"


def login(user: str, password: str):
    user = user.strip()
    with _lock:
        accounts = _load_json(ACCOUNTS_PATH, {})
        rec = accounts.get(user)
        if not rec:
            return None, "用户名或密码不对"
        _, digest = hash_password(password, rec["salt"])
        if not secrets.compare_digest(digest, rec["hash"]):
            return None, "用户名或密码不对"
        token = secrets.token_hex(24)
        tokens = _load_json(TOKENS_PATH, {})
        tokens[token] = {"user": user, "at": int(time.time())}
        _save_json(TOKENS_PATH, tokens)
    return token, "登录成功"


def user_by_token(token: str):
    if not token:
        return None
    tokens = _load_json(TOKENS_PATH, {})
    rec = tokens.get(token)
    return rec["user"] if rec else None


def drop_token(token: str):
    with _lock:
        tokens = _load_json(TOKENS_PATH, {})
        if token in tokens:
            tokens.pop(token)
            _save_json(TOKENS_PATH, tokens)


def user_data_path(user: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9_\u4e00-\u9fa5]", "_", user)
    return os.path.join(DATA_DIR, safe + ".json")


# ---------------------------------------------------------------- 规则判分
# 与 Android 端 FuzzyJudge.kt 保持一致的阈值与同义词表，
# 这样「电脑模式」开或关，判分口径不会飘。

PASS = 0.62
NEAR = 0.42

POS_RE = re.compile(r"\b(v|n|adj|adv|prep|conj|pron|num|art|vt|vi|aux|int|pl)\.\s*", re.I)
SPLIT_RE = re.compile(r"[；;，,、/|（）()【】\[\]\s]+")
FILLER_RE = re.compile(r"[\s，。,.!！?？、；;：:\"'“”‘’（）()【】\[\]]")

SYNONYMS = [
    {"放弃", "抛弃", "舍弃", "丢弃"},
    {"提高", "提升", "增加", "增强", "加强"},
    {"减少", "降低", "减弱", "削减", "下降"},
    {"重要", "关键", "重大", "要紧"},
    {"明显", "显著", "清楚", "显然"},
    {"困难", "艰难", "不容易", "棘手"},
    {"开始", "着手", "启动", "发起"},
    {"结束", "终止", "停止", "完结"},
    {"获得", "得到", "取得", "赢得"},
    {"认为", "觉得", "以为", "主张"},
    {"巨大", "庞大", "宏大", "极大"},
    {"谨慎", "小心", "慎重", "审慎"},
    {"愤怒", "生气", "恼火", "愤慨"},
    {"导致", "引起", "造成", "引发"},
    {"支持", "赞成", "拥护", "支援"},
    {"反对", "抵制", "抗拒", "不赞成"},
    {"利用", "使用", "运用", "采用"},
    {"改变", "转变", "更改", "变革"},
    {"限制", "约束", "制约", "限定"},
    {"假设", "假定", "推测", "设想"},
]


def normalize_cn(raw: str) -> str:
    s = FILLER_RE.sub("", raw or "")
    for p in ("意思是", "意思就是", "就是", "表示", "它的意思是", "含义是", "嗯", "呃", "啊"):
        if s.startswith(p):
            s = s[len(p):]
    return s


def split_senses(meaning: str):
    cleaned = POS_RE.sub(" ", meaning or "")
    return [x.strip() for x in SPLIT_RE.split(cleaned) if x.strip()]


def lcs_len(a: str, b: str) -> int:
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        for j in range(1, len(b) + 1):
            cur[j] = prev[j - 1] + 1 if a[i - 1] == b[j - 1] else max(prev[j], cur[j - 1])
        prev = cur
    return prev[len(b)]


def pair_score(answer: str, sense: str) -> float:
    if not answer or not sense:
        return 0.0
    if answer == sense:
        return 1.0
    if len(sense) >= 2 and sense in answer:
        return 0.95
    if len(answer) >= 2 and answer in sense:
        return max(0.55, 0.92 * len(answer) / len(sense))
    if len(sense) == 1 or len(answer) == 1:
        return 0.0
    return 2.0 * lcs_len(answer, sense) / (len(answer) + len(sense))


def synonym_score(answer: str, sense: str) -> float:
    for group in SYNONYMS:
        if any(w in answer for w in group) and any(w in sense for w in group):
            return 0.85
    return 0.0


def edit_distance(a: str, b: str) -> int:
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[len(b)]


def rule_judge(answer: str, meaning: str, mode: str):
    """返回 (correct, confidence, note)"""
    answer = (answer or "").strip()
    if not answer:
        return False, 0.0, "没听到回答"

    if mode == "CN2EN":
        target = (meaning or "").strip().lower()
        a = re.sub(r"[^a-z'\-]", "", answer.lower())
        if a == target:
            return True, 1.0, ""
        d = edit_distance(a, target)
        tol = max(1, len(target) // 4)
        sim = 1.0 - d / max(len(a), len(target), 1)
        if d <= tol:
            return True, sim, "拼写略有偏差（正确：%s）" % target
        return False, sim, "正确拼写：%s" % target

    a = normalize_cn(answer)
    senses = split_senses(meaning)
    if not senses:
        return False, 0.0, "词库缺少释义"

    best, best_sense = 0.0, senses[0]
    for s in senses:
        sc = max(pair_score(a, s), synonym_score(a, s))
        if sc > best:
            best, best_sense = sc, s

    if best >= PASS:
        return True, best, ""
    if best >= NEAR:
        return False, best, "很接近（你说的是「%s」，标准释义「%s」）" % (a, best_sense)
    return False, best, "标准释义：%s" % meaning


# ---------------------------------------------------------------- LLM 判分


def llm_available() -> bool:
    try:
        req = urllib.request.Request(
            OLLAMA_URL.replace("/api/generate", "/api/tags"), method="GET"
        )
        with urllib.request.urlopen(req, timeout=2) as r:
            return r.status == 200
    except Exception:
        return False


def llm_judge(answer: str, meaning: str):
    """返回 True/False；模型不可用或答不出来返回 None"""
    prompt = (
        "判断学生给出的中文释义是否与标准释义意思一致。只回答一个字：对 或 错。\n"
        "标准释义：%s\n学生答案：%s\n回答：" % (meaning, answer)
    )
    body = json.dumps({
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": 0, "num_predict": 4},
    }).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA_URL, data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=LLM_TIMEOUT) as r:
            text = json.loads(r.read().decode("utf-8")).get("response", "")
    except Exception:
        return None
    text = text.strip()
    if "对" in text and "错" not in text:
        return True
    if "错" in text:
        return False
    return None


def judge(answer: str, meaning: str, mode: str):
    correct, conf, note = rule_judge(answer, meaning, mode)
    engine = "rule"
    # 只在模糊区间问模型，省算力也省时间
    if 0.15 < conf < 0.9 and not correct:
        verdict = llm_judge(answer, meaning)
        if verdict is not None:
            engine = "llm"
            if verdict:
                return True, 0.8, "模型判定：意思对得上", "llm"
    return correct, conf, note, engine


# ---------------------------------------------------------------- 工具


def lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def read_words() -> list:
    if not os.path.exists(WORDS_PATH):
        return []
    rows = []
    with open(WORDS_PATH, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) >= 2:
                rows.append((parts[0].strip(), parts[1].strip()))
    return rows


class Handler(BaseHTTPRequestHandler):
    server_version = "WordEchoServer/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("  %s\n" % (fmt % args))

    # ---------- helpers ----------

    def _json(self, code: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _body(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            return json.loads(self.rfile.read(length).decode("utf-8") or "{}")
        except Exception:
            return None

    def _token(self):
        auth = self.headers.get("Authorization", "")
        return auth[7:].strip() if auth.startswith("Bearer ") else ""

    def _require_user(self):
        user = user_by_token(self._token())
        if not user:
            self._json(401, {"ok": False, "error": "未登录或登录已过期"})
        return user

    # ---------- GET ----------

    def do_GET(self):
        path = self.path.split("?")[0]

        if path == "/ping":
            words = read_words()
            self._json(200, {
                "ok": True,
                "llm": "ollama" if self.server.has_llm else "none",
                "model": OLLAMA_MODEL if self.server.has_llm else "",
                "words": len(words),
                "accounts": len(_load_json(ACCOUNTS_PATH, {})),
            })
            return

        if path == "/words.tsv":
            if not os.path.exists(WORDS_PATH):
                self._json(404, {"ok": False, "error": "服务器上没有 words.tsv"})
                return
            with open(WORDS_PATH, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "text/tab-separated-values; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        if path == "/sync/status":
            user = self._require_user()
            if not user:
                return
            snap = _load_json(user_data_path(user), {})
            self._json(200, {
                "ok": True,
                "user": user,
                "words": len(snap.get("words", [])),
                "logs": len(snap.get("logs", [])),
                "savedAt": snap.get("savedAt", 0),
            })
            return

        if path == "/sync/pull":
            user = self._require_user()
            if not user:
                return
            snap = _load_json(user_data_path(user), {})
            self._json(200, {
                "ok": True,
                "user": user,
                "words": snap.get("words", []),
                "logs": snap.get("logs", []),
                "savedAt": snap.get("savedAt", 0),
            })
            return

        if path == "/":
            words = read_words()
            accounts = _load_json(ACCOUNTS_PATH, {})
            html = """<!doctype html><meta charset=utf-8>
<title>回声背单词 · 电脑端</title>
<style>body{font-family:system-ui,-apple-system,'Segoe UI',sans-serif;max-width:640px;margin:40px auto;padding:0 20px;color:#16181d;line-height:1.8}
h1{font-size:20px}code{background:#f1f3f6;padding:2px 6px;border-radius:4px}
.ok{color:#12a150;font-weight:600}table{border-collapse:collapse;margin-top:8px}
td{padding:2px 10px 2px 0;font-size:14px;vertical-align:top}</style>
<h1>回声背单词 · 电脑端服务</h1>
<table>
<tr><td>状态</td><td><span class=ok>运行中</span></td></tr>
<tr><td>判分引擎</td><td>%s%s</td></tr>
<tr><td>词库</td><td>%d 条</td></tr>
<tr><td>账号</td><td>%d 个</td></tr>
<tr><td>手机里填</td><td><code>http://%s:%d</code></td></tr>
</table>
<p>接口：<code>/ping</code> <code>/judge</code> <code>/words.tsv</code>
<code>/register</code> <code>/login</code> <code>/sync/push</code> <code>/sync/pull</code></p>
""" % (
                "Ollama 小模型" if self.server.has_llm else "本地规则（未装 Ollama）",
                ("（%s）" % OLLAMA_MODEL) if self.server.has_llm else "",
                len(words), len(accounts), lan_ip(), self.server.server_port,
            )
            data = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        self._json(404, {"ok": False, "error": "not found"})

    # ---------- POST ----------

    def do_POST(self):
        path = self.path.split("?")[0]

        if path == "/judge":
            payload = self._body()
            if payload is None:
                self._json(400, {"ok": False, "error": "bad json"})
                return
            correct, conf, note, engine = judge(
                str(payload.get("answer", "")),
                str(payload.get("meaning", "")),
                str(payload.get("mode", "EN2CN")),
            )
            self._json(200, {
                "correct": correct, "confidence": round(conf, 3),
                "note": note, "engine": engine,
            })
            return

        if path == "/register":
            payload = self._body() or {}
            ok, msg = register(str(payload.get("user", "")), str(payload.get("pass", "")))
            self._json(200 if ok else 400, {"ok": ok, "message": msg})
            return

        if path == "/login":
            payload = self._body() or {}
            token, msg = login(str(payload.get("user", "")), str(payload.get("pass", "")))
            if token:
                self._json(200, {"ok": True, "token": token,
                                 "user": str(payload.get("user", "")).strip(),
                                 "message": msg})
            else:
                self._json(401, {"ok": False, "message": msg})
            return

        if path == "/logout":
            drop_token(self._token())
            self._json(200, {"ok": True})
            return

        if path == "/sync/push":
            user = self._require_user()
            if not user:
                return
            payload = self._body() or {}
            words = payload.get("words", [])
            logs = payload.get("logs", [])
            if not isinstance(words, list) or not isinstance(logs, list):
                self._json(400, {"ok": False, "error": "words/logs 必须是数组"})
                return
            os.makedirs(DATA_DIR, exist_ok=True)
            with _lock:
                _save_json(user_data_path(user), {
                    "user": user,
                    "words": words,
                    "logs": logs,
                    "savedAt": int(time.time() * 1000),
                })
            self._json(200, {"ok": True, "words": len(words), "logs": len(logs)})
            return

        self._json(404, {"ok": False, "error": "not found"})


def main():
    global WORDS_PATH

    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--words", default="words.tsv")
    args = ap.parse_args()
    WORDS_PATH = args.words
    os.makedirs(DATA_DIR, exist_ok=True)

    has_llm = llm_available()
    words = read_words()
    accounts = _load_json(ACCOUNTS_PATH, {})

    srv = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    srv.has_llm = has_llm

    ip = lan_ip()
    print("=" * 56)
    print("  回声背单词 · 电脑端服务已启动")
    print("=" * 56)
    print("  手机里填这个地址：  http://%s:%d" % (ip, args.port))
    print("  判分引擎：          %s" % ("Ollama / " + OLLAMA_MODEL if has_llm else "本地规则（未检测到 Ollama）"))
    print("  词库：              %d 条（%s）" % (len(words), WORDS_PATH))
    print("  已有账号：          %d 个" % len(accounts))
    print("  浏览器自测：        http://127.0.0.1:%d" % args.port)
    print("=" * 56)
    print("  Ctrl+C 停止\n")

    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
        srv.shutdown()


if __name__ == "__main__":
    main()
