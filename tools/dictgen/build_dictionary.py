#!/usr/bin/env python3
"""
Mozc OSS 사전(dictionary_oss)으로부터 앱 내장 가나→한자 변환 사전을 생성한다.

  python tools/dictgen/build_dictionary.py [--mozc-dir DIR] [--max-cost 7000]

--mozc-dir 를 생략하면 GitHub(google/mozc)에서 사전 파일을 내려받아 tools/dictgen/.cache 에 저장한다.
출력: app/src/main/assets/dict/ja_dict.bin.gz, app/src/main/assets/dict/connection.bin.gz,
      app/src/main/assets/licenses/mozc.txt

바이너리 포맷(빅엔디언, gzip 압축)은 docs/SPEC.md 의 "사전 포맷" 절과
app/src/main/java/.../conversion/BinaryDictionary.kt 를 참고.
"""
import argparse
import gzip
import os
import re
import struct
import urllib.request
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MOZC_BASE = "https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/"
MOZC_LICENSE = "https://raw.githubusercontent.com/google/mozc/master/LICENSE"
FILES = [f"dictionary0{i}.txt" for i in range(10)] + ["id.def", "README.txt", "connection_single_column.txt"]

MAGIC = b"HKD2"
CONNECTION_MAGIC = b"HKC1"
CONNECTION_STEP = 64  # 연접 비용 양자화 단위 (1바이트 저장)
MAX_SURFACES_PER_READING = 60
KANA_ONLY = re.compile("^[ぁ-ゖー]+$")
FLAG_FUNCTION_WORD = 1


def normalize_reading(r: str) -> str:
    # 한글 입력으로는 ぢ/づ 와 じ/ず 를 구분하기 어려우므로 사전 키에서 통합한다.
    return r.replace("ぢ", "じ").replace("づ", "ず")


def download(cache: str) -> str:
    os.makedirs(cache, exist_ok=True)
    for name in FILES:
        path = os.path.join(cache, name)
        if not os.path.exists(path):
            print("download", name)
            urllib.request.urlretrieve(MOZC_BASE + name, path)
    lic = os.path.join(cache, "LICENSE")
    if not os.path.exists(lic):
        urllib.request.urlretrieve(MOZC_LICENSE, lic)
    return cache


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mozc-dir")
    ap.add_argument("--max-cost", type=int, default=7000)
    args = ap.parse_args()

    src = args.mozc_dir or download(os.path.join(os.path.dirname(__file__), ".cache"))

    pos = {}
    with open(os.path.join(src, "id.def"), encoding="utf-8") as f:
        for line in f:
            i, name = line.split(" ", 1)
            pos[int(i)] = name.strip()

    # (reading, surface, lid, rid) -> (cost, flags)
    best = {}
    for i in range(10):
        with open(os.path.join(src, f"dictionary0{i}.txt"), encoding="utf-8") as f:
            for line in f:
                p = line.rstrip("\n").split("\t")
                if len(p) != 5:  # SPELLING_CORRECTION 등 부가 컬럼이 있는 항목은 제외
                    continue
                reading, lid, _rid, cost, surface = p
                cost = int(cost)
                if cost >= args.max_cost or not KANA_ONLY.match(reading):
                    continue
                key = (normalize_reading(reading), surface, int(lid), int(_rid))
                flags = FLAG_FUNCTION_WORD if pos[int(lid)].startswith(("助詞", "助動詞")) else 0
                if key not in best or best[key][0] > cost:
                    best[key] = (cost, flags)

    by_reading = defaultdict(list)
    for (reading, surface, lid, rid), (cost, flags) in best.items():
        by_reading[reading].append((cost, surface, lid, rid, flags))

    readings = sorted(by_reading)
    reading_off, entry_start = [0], [0]
    surface_off, costs, flags_arr, lids, rids = [0], [], [], [], []
    reading_chars, surface_chars = [], []

    def utf16(s):
        b = s.encode("utf-16-be")
        return [b[k:k + 2] for k in range(0, len(b), 2)]

    for r in readings:
        reading_chars.extend(utf16(r))
        reading_off.append(len(reading_chars))
        entries = sorted(by_reading[r])[:MAX_SURFACES_PER_READING]
        for cost, surface, lid, rid, fl in entries:
            surface_chars.extend(utf16(surface))
            surface_off.append(len(surface_chars))
            costs.append(min(cost, 32767))
            lids.append(lid)
            rids.append(rid)
            flags_arr.append(fl)
        entry_start.append(len(costs))

    out = bytearray()
    out += MAGIC
    out += struct.pack(">5i", len(readings), len(costs), len(reading_chars), len(surface_chars),
                       max(len(r) for r in readings))
    out += struct.pack(f">{len(reading_off)}i", *reading_off)
    out += struct.pack(f">{len(entry_start)}i", *entry_start)
    out += struct.pack(f">{len(surface_off)}i", *surface_off)
    out += struct.pack(f">{len(costs)}h", *costs)
    out += struct.pack(f">{len(lids)}h", *lids)
    out += struct.pack(f">{len(rids)}h", *rids)
    out += bytes(flags_arr)
    out += b"".join(reading_chars)
    out += b"".join(surface_chars)

    dst = os.path.join(ROOT, "app", "src", "main", "assets", "dict", "ja_dict.bin.gz")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with gzip.GzipFile(dst, "wb", compresslevel=9, mtime=0) as g:
        g.write(out)

    # 연접 비용 행렬: matrix[rid * n + lid] (앞 단어의 우문맥 ID → 뒷 단어의 좌문맥 ID)
    with open(os.path.join(src, "connection_single_column.txt"), encoding="utf-8") as f:
        values = f.read().split()
    n = int(values[0])
    matrix = bytes(min(255, (int(x) + CONNECTION_STEP // 2) // CONNECTION_STEP) for x in values[1:])
    assert len(matrix) == n * n
    conn_dst = os.path.join(ROOT, "app", "src", "main", "assets", "dict", "connection.bin.gz")
    with gzip.GzipFile(conn_dst, "wb", compresslevel=9, mtime=0) as g:
        g.write(CONNECTION_MAGIC + struct.pack(">2i", n, CONNECTION_STEP) + matrix)

    lic_dir = os.path.join(ROOT, "app", "src", "main", "assets", "licenses")
    os.makedirs(lic_dir, exist_ok=True)
    with open(os.path.join(lic_dir, "mozc.txt"), "w", encoding="utf-8") as f:
        f.write("Mozc (https://github.com/google/mozc)\n\n")
        f.write(open(os.path.join(src, "LICENSE"), encoding="utf-8").read())
        f.write("\n\n===== dictionary_oss/README.txt =====\n\n")
        f.write(open(os.path.join(src, "README.txt"), encoding="utf-8").read())

    print(f"readings={len(readings)} entries={len(costs)} raw={len(out)} gz={os.path.getsize(dst)}")


if __name__ == "__main__":
    main()
