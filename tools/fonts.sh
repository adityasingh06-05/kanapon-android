#!/usr/bin/env bash
# Subsets the two typefaces the app ships into app/src/main/res/font.
#
#   pip install fonttools && tools/fonts.sh
#
# Sources are the upstream TTFs in google/fonts (SIL OFL 1.1). The cut keeps
# what the interface and worksheets draw: Latin for the interface face, the
# kana blocks, CJK punctuation and the few kanji in sheet titles and the
# chart's 行/段 facts. Klee One stays Japanese-only, as on the web, so Latin
# text never picks up its handwriting shapes.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/app/src/main/res/font"
CACHE="$ROOT/.cache/fonts"
REPO="https://raw.githubusercontent.com/google/fonts/main/ofl"
mkdir -p "$OUT" "$CACHE"

fetch() { [ -s "$CACHE/$2" ] || curl -fsSL "$REPO/$1/$2" -o "$CACHE/$2"; }
fetch kleeone KleeOne-Regular.ttf
fetch zenkakugothicnew ZenKakuGothicNew-Regular.ttf
fetch zenkakugothicnew ZenKakuGothicNew-Medium.ttf

KANA="U+3000-303F,U+3041-3096,U+3099-309F,U+30A0-30FF,U+FF01-FF0F,U+FF1F,U+FF5E"
KANJI="練習弱点名前日付五十音行段仮名帳"
LATIN="U+0020-007E,U+00A0-00FF,U+0100-0101,U+0112-0113,U+012A-012B,U+014C-014D,U+016A-016B,U+2010-2014,U+2018-201D,U+2022,U+2026,U+2039-203A,U+2190-2193,U+2212,U+00D7"

subset() { # src out unicodes
  pyftsubset "$CACHE/$1" --output-file="$OUT/$2" --unicodes="$3" --text="$KANJI" \
    --layout-features='kern,liga,calt,ccmp,locl,mark,mkmk,palt,halt' --no-hinting --desubroutinize
  echo "$2: $(wc -c < "$OUT/$2") bytes"
}
subset KleeOne-Regular.ttf klee_one_400.ttf "$KANA"
subset ZenKakuGothicNew-Regular.ttf zen_kaku_gothic_new_400.ttf "$LATIN,$KANA"
subset ZenKakuGothicNew-Medium.ttf zen_kaku_gothic_new_500.ttf "$LATIN,$KANA"
