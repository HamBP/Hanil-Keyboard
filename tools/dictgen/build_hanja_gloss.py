#!/usr/bin/env python3
"""
한자 해설(한국어 뜻·음) 데이터를 생성한다.

  python tools/dictgen/build_hanja_gloss.py [--src-dir DIR]

--src-dir 를 생략하면 아래 원본을 내려받아 tools/dictgen/.cache 에 저장한다.
  - libhangul data/hanja/hanja.txt (BSD-3-Clause): 한자별 훈음 ("학:學:배울 학"), 한자어 목록
  - Unicode Unihan (Unicode License V3): 이체자, 상용·인명용 한자의 구자체 대응
출력: app/src/main/assets/dict/hanja_gloss.tsv.gzip, app/src/main/assets/licenses/hanja.txt

출력 형식(UTF-8, 줄마다 한 글자, 탭 구분):
  한자 \t 정자(같으면 빈 값) \t 훈음 \t 훈음 ...
훈음은 자주 쓰이는 음부터 최대 MAX_READINGS 개 ("배울 학").

일본 신자체는 한국 사전에 없거나 다른 글자와 겹치므로(芸 = 향풀 운, 일본에서는 藝) 다음 순서로 찾는다.
  1. JOYO_KYUJITAI: 상용한자표 괄호 안 구자체 중 Unihan 에 대응이 없거나 한국 훈음이 다른 글자
  2. 글자 자체의 훈음
  3. Unihan 상용·인명용 한자의 구자체 대응 (kJoyoKanji, kJinmeiyoKanji)
  4. Unihan 이체자 (kTraditionalVariant, kZVariant, kSemanticVariant, kSpecializedSemanticVariant)
"""
import argparse
import gzip
import os
import re
import urllib.request
import zipfile
from collections import Counter, defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LIBHANGUL_REV = "23d2cd9d10d516d523d60c562dbcc7ea08139a09"
HANJA_URL = f"https://raw.githubusercontent.com/libhangul/libhangul/{LIBHANGUL_REV}/data/hanja/hanja.txt"
UNIHAN_URL = "https://www.unicode.org/Public/18.0.0/ucd/Unihan.zip"
UNICODE_LICENSE_URL = "https://www.unicode.org/license.txt"
MAX_READINGS = 2

# 상용한자표(2010, 내각 고시)의 구자체. Unihan 에 대응이 없거나 신자체가 한국에서 다른 글자인 것만 적는다.
JOYO_KYUJITAI = dict(pair for pair in (
    "亀龜 剤劑 労勞 勧勸 営營 囲圍 図圖 圧壓 壌壤 壱壹 姫姬 対對 尚尙 帰歸 廃廢 弐貳 悩惱 戻戾 択擇 拠據 "
    "拡擴 挙擧 斎齋 既旣 桟棧 権權 歓歡 歯齒 沢澤 清淸 済濟 渓溪 満滿 犠犧 猟獵 発發 穏穩 粛肅 経經 絵繪 "
    "継繼 続續 総總 縄繩 脳腦 舎舍 舗舖 蛍螢 覚覺 観觀 訳譯 読讀 軽輕 辺邊 遅遲 釈釋 鉱鑛 銭錢 閲閱 闘鬪 "
    "隠隱 霊靈 青靑 駅驛 髄髓 齢齡 麺麵 "
    "芸藝 欠缺 予豫 余餘 台臺 糸絲 缶罐 体體 灯燈 弁辨 証證 医醫 円圓 錬鍊 党黨 点點 並竝 当當 号號 声聲 "
    "宝寶 旧舊 与與 処處 双雙 乱亂 竜龍 虫蟲 蚕蠶"
).split())

VARIANT_FIELDS = ["kTraditionalVariant", "kZVariant", "kSemanticVariant", "kSpecializedSemanticVariant"]
HANJA = "[㐀-䶿一-鿿豈-﫿\U00020000-\U0003ffff]"
# "假의 略字", "侃과 同字", "十의 갖은자" 처럼 다른 글자를 가리키는 풀이
REFERENCE = re.compile(f"^({HANJA})(?:·{HANJA})*(?:의|과|와) ")
HAS_HANJA = re.compile(HANJA)


def download(cache: str) -> str:
    os.makedirs(cache, exist_ok=True)
    for url, name in [(HANJA_URL, "hanja.txt"), (UNIHAN_URL, "Unihan.zip"), (UNICODE_LICENSE_URL, "unicode-license.txt")]:
        path = os.path.join(cache, name)
        if not os.path.exists(path):
            print("download", name)
            urllib.request.urlretrieve(url, path)
    return cache


def is_syllable(c: str) -> bool:
    return "가" <= c <= "힣"


def initial_dropped(syllable: str) -> set:
    """두음법칙으로 바뀐 음 (락 → 낙, 량 → 양, 녀 → 여)."""
    code = ord(syllable) - 0xAC00
    cho, rest = divmod(code, 21 * 28)
    vowel = rest // 28
    y_like = vowel in (2, 6, 7, 12, 17, 20)  # ㅑ ㅕ ㅖ ㅛ ㅠ ㅣ
    out = set()
    if cho == 5:  # ㄹ
        out.add(chr(0xAC00 + (11 if y_like else 2) * 21 * 28 + rest))
    if cho == 2 and y_like:  # ㄴ
        out.add(chr(0xAC00 + 11 * 21 * 28 + rest))
    return out


def read_hanja(path: str):
    """(한자 → [(음, 풀이)], (한자, 음) → 한자어 빈도)"""
    glosses = defaultdict(list)
    freq = Counter()
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            p = line.rstrip("\n").split(":")
            if len(p) != 3:
                continue
            key, value, comment = p
            if len(key) == 1 and len(value) == 1:
                if comment and is_syllable(key):
                    glosses[value].append((key, comment))
            elif len(key) == len(value):
                for k, v in zip(key, value):
                    if k != v and is_syllable(k):
                        freq[(v, k)] += 1
    return glosses, freq


def read_unihan(path: str):
    """(구자체 대응: 신자체 → [구자체], 이체자: 글자 → [이체자])"""
    kyujitai = defaultdict(list)
    variants = defaultdict(list)
    with zipfile.ZipFile(path) as z:
        for name in ("Unihan_OtherMappings.txt", "Unihan_Variants.txt"):
            for raw in z.read(name).decode("utf-8").splitlines():
                p = raw.split("\t")
                if raw.startswith("#") or len(p) != 3:
                    continue
                ch = chr(int(p[0][2:], 16))
                field, value = p[1], p[2]
                if field == "kJoyoKanji" and value.startswith("U+"):
                    kyujitai[chr(int(value[2:], 16))].append(ch)
                elif field == "kJinmeiyoKanji" and ":U+" in value:
                    kyujitai[chr(int(value.split(":U+")[1], 16))].append(ch)
                elif field in VARIANT_FIELDS:
                    for v in value.split():
                        variants[ch].append(chr(int(v.split("<")[0][2:], 16)))
    return kyujitai, variants


def build(glosses, freq):
    """한자 → 훈음 목록 (자주 쓰는 음부터)."""

    def resolve(ch: str, depth: int):
        entries = glosses.get(ch)
        if not entries:
            return []
        # 같은 음의 풀이를 모으고, 두음법칙으로 바뀐 음은 본음에 합친다 (즐길 낙 → 즐거울 락)
        by_reading = {}
        for reading, comment in entries:
            by_reading.setdefault(reading, []).extend(comment.split(", "))
        counts = Counter({r: freq[(ch, r)] for r in by_reading})
        for r in list(by_reading):
            if r not in by_reading:
                continue
            for dropped in initial_dropped(r):
                if dropped in by_reading and dropped != r:
                    counts[r] += counts.pop(dropped, 0)
                    by_reading.pop(dropped)
        order = sorted(by_reading, key=lambda r: -counts[r])  # 빈도가 같으면 원본 순서
        out = []
        for reading in order:
            phrases = by_reading[reading]
            own = [ph for ph in phrases if not HAS_HANJA.search(ph)]
            if own:
                phrase = own[0]
                if not phrase.endswith(reading):
                    phrase = f"{phrase} {reading}"
                out.append(phrase)
                continue
            # 풀이가 "燈의 略字" 뿐이면 가리키는 글자의 풀이를 쓴다
            m = REFERENCE.match(phrases[0])
            if m and depth < 2:
                target = [g for g in resolve(m.group(1), depth + 1) if g.endswith(reading)]
                if target:
                    out.append(target[0])
        return out[:MAX_READINGS]

    return {ch: g for ch in glosses if (g := resolve(ch, 0))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-dir")
    args = ap.parse_args()
    src = args.src_dir or download(os.path.join(os.path.dirname(__file__), ".cache"))

    glosses, freq = read_hanja(os.path.join(src, "hanja.txt"))
    kyujitai, variants = read_unihan(os.path.join(src, "Unihan.zip"))
    table = build(glosses, freq)

    for shin, kyu in JOYO_KYUJITAI.items():
        if kyu not in table:
            print(f"warning: {shin}({kyu}) 훈음 없음")

    def candidates(ch: str):
        if ch in JOYO_KYUJITAI:
            yield JOYO_KYUJITAI[ch]
        yield ch
        yield from kyujitai.get(ch, [])
        yield from variants.get(ch, [])

    chars = set(table) | set(kyujitai) | set(variants) | set(JOYO_KYUJITAI)
    rows = []
    for ch in sorted(chars):
        base = next((c for c in candidates(ch) if c in table), None)
        if base is None:
            continue
        rows.append("\t".join([ch, "" if base == ch else base] + table[base]))

    out = ("\n".join(rows) + "\n").encode("utf-8")
    dst = os.path.join(ROOT, "app", "src", "main", "assets", "dict", "hanja_gloss.tsv.gzip")
    with gzip.GzipFile(dst, "wb", mtime=0) as f:
        f.write(out)

    lic_dir = os.path.join(ROOT, "app", "src", "main", "assets", "licenses")
    os.makedirs(lic_dir, exist_ok=True)
    with open(os.path.join(src, "hanja.txt"), encoding="utf-8") as f:
        libhangul = "".join(line[2:] if line.startswith("# ") else line[1:] for line in f if line.startswith("#"))
    with open(os.path.join(lic_dir, "hanja.txt"), "w", encoding="utf-8") as f:
        f.write("libhangul hanja.txt (https://github.com/libhangul/libhangul)\n\n")
        f.write(libhangul)
        f.write("\n\n===== Unicode Unihan Database (https://www.unicode.org/charts/unihan.html) =====\n\n")
        f.write(open(os.path.join(src, "unicode-license.txt"), encoding="utf-8").read())

    print(f"chars={len(rows)} raw={len(out)} gz={os.path.getsize(dst)}")


if __name__ == "__main__":
    main()
