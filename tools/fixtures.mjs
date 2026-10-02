#!/usr/bin/env node
/* Golden fixtures for the Kotlin ports, computed by the web app's own code.
 *
 *   (cd .cache/node && npm i perfect-freehand@1.2.3)   # once
 *   node tools/fixtures.mjs [path/to/kanapon]           # default ../kanapon
 *
 * Writes app/src/test/resources/fixtures/:
 *   matcher.json   synthetic ink for every kana, built as scripts/check-scoring.mjs builds it
 *                  (seeded), with what matchStrokes and describeAttempt make of it
 *   feedback.json  describeAttempt / whyNotCounted on hand-picked arguments
 *   ink.json       strokePath outlines: the exact moveTo / quadraticCurveTo sequence
 *
 * Inputs are rounded to 3 decimals before they are scored, so both sides start from the same
 * numbers. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WEB = path.resolve(process.argv[2] || path.join(ROOT, '..', 'kanapon'));
const OUT = path.join(ROOT, 'app', 'src', 'test', 'resources', 'fixtures');
const web = rel => pathToFileURL(path.join(WEB, rel)).href;

const { ALL } = await import(web('src/data/kana.js'));
const { reference, matchStrokes, resample } = await import(web('src/lib/stroke-match.js'));
const { describeAttempt, toScore, whyNotCounted } = await import(web('src/lib/feedback.js'));
const { VIEWBOX } = await import(web('src/data/strokes.js'));

const r3 = v => Math.round(v * 1000) / 1000;
const write = (name, data) => {
  fs.mkdirSync(OUT, { recursive: true });
  const file = path.join(OUT, name);
  fs.writeFileSync(file, JSON.stringify(data) + '\n');
  console.log(`wrote ${path.relative(ROOT, file)} (${Math.round(fs.statSync(file).size / 1024)} kB)`);
};

/* ---------- matcher, as scripts/check-scoring.mjs builds its ink ---------- */
const CELL = 200;
const SIZE = { width: CELL, height: CELL };
const px = CELL / VIEWBOX;
function rng(seed) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = rng(1234);
const jitter = () => rand() * 2 - 1;
const ink = char => reference(char).dense.map(pts => pts.map(p => ({ x: p.x * px, y: p.y * px })));
const length = pts => pts.reduce((s, p, i) => (i ? s + Math.hypot(p.x - pts[i - 1].x, p.y - pts[i - 1].y) : 0), 0);
const even = (pts, step = 2) => resample(pts, Math.max(2, Math.round(length(pts) / step)));
const centre = pts => pts.reduce((m, p) => ({ x: m.x + p.x / pts.length, y: m.y + p.y / pts.length }), { x: 0, y: 0 });
const scaleAbout = (pts, s, c = centre(pts)) => pts.map(p => ({ x: c.x + (p.x - c.x) * s, y: c.y + (p.y - c.y) * s }));
const wobble = (strokes, drift, noise) =>
  strokes.map(pts => {
    const phase = rand() * Math.PI * 2;
    return even(pts).map((p, i) => ({
      x: p.x + drift * CELL * Math.sin(i / 11 + phase) + noise * CELL * jitter(),
      y: p.y + drift * CELL * Math.cos(i / 13 + phase) + noise * CELL * jitter(),
    }));
  });
const DISTORTIONS = {
  'sensor noise 1%': s => s.map(pts => even(pts).map(p => ({ x: p.x + 0.01 * CELL * jitter(), y: p.y + 0.01 * CELL * jitter() }))),
  'jittery slow pen': s => s.map(pts => even(pts, 0.4).map(p => ({ x: p.x + jitter(), y: p.y + jitter() }))),
  'steady wobble 2%': s => wobble(s, 0.02, 0.005),
  'wobble 5%': s => wobble(s, 0.05, 0.01),
  'squashed to 70%': s => {
    const cx = centre(s.flat()).x;
    return s.map(pts => pts.map(p => ({ x: cx + (p.x - cx) * 0.7, y: p.y })));
  },
  'one stroke off 5%': s => s.map((pts, i) => (i === 0 ? pts.map(p => ({ x: p.x + 0.035 * CELL, y: p.y + 0.035 * CELL })) : pts)),
  'last stroke at 75%': s => s.map((pts, i) => (i === s.length - 1 ? scaleAbout(pts, 0.75) : pts)),
  'strokes 25% short': s =>
    s.map(pts => {
      const keep = even(pts, 0.5);
      return keep.slice(0, Math.max(2, Math.round(keep.length * 0.75)));
    }),
  'written at 80%': s => s.map(pts => scaleAbout(pts, 0.8, { x: CELL / 2, y: CELL / 2 })),
  scribble: s =>
    s.map(() => {
      let x = rand() * CELL,
        y = rand() * CELL;
      return Array.from({ length: 30 }, () => {
        x = Math.min(CELL, Math.max(0, x + jitter() * 25));
        y = Math.min(CELL, Math.max(0, y + jitter() * 25));
        return { x, y };
      });
    }),
};

const cases = [];
const add = (name, char, strokes, prevBest = 0) => {
  const rounded = strokes.map(pts => pts.map(p => ({ x: r3(p.x), y: r3(p.y) })));
  const r = matchStrokes(rounded, char, SIZE);
  const expected = reference(char).strokes.length;
  const score = toScore(r.raw);
  cases.push({
    name,
    char,
    size: CELL,
    prevBest,
    ink: rounded.map(pts => pts.flatMap(p => [p.x, p.y])),
    raw: r.raw,
    score,
    diagnostics: r.diagnostics,
    swapped: r.order.swapped,
    reversed: r.order.reversed,
    strokes: r.strokes.map(s => ({ ref: s.ref, d: s.d, score: s.score, reversed: s.reversed })),
    note: describeAttempt({ score, prevBest, drawn: rounded.length, expected, diagnostics: r.diagnostics, order: r.order, strokes: r.strokes }),
    notCounted: whyNotCounted({ drawn: rounded.length, expected, order: r.order }),
  });
};

ALL.forEach(([char, , expected], idx) => {
  const strokes = ink(char);
  add('exact trace', char, strokes);
  const i = strokes.findIndex(pts => length(pts) > 20 * px);
  if (i >= 0) add('reversed stroke', char, strokes.map((pts, j) => (j === i ? [...pts].reverse() : pts)));
  if (expected >= 2) add('swapped order', char, [strokes[1], strokes[0], ...strokes.slice(2)]);
  add('offset right 10%', char, strokes.map(pts => pts.map(p => ({ x: p.x + 0.1 * CELL, y: p.y }))));
  if (expected >= 2) add('missing stroke', char, strokes.slice(0, -1));
  add('one too many', char, [...strokes, strokes[0]]);
  /* Distortions use the shared RNG, so run them for every kana to keep the sequence identical to
   * check-scoring.mjs, but only keep a quarter of them to hold the file size down. */
  for (const [name, distort] of Object.entries(DISTORTIONS)) {
    const d = distort(strokes);
    if (idx % 4 === 0 && name !== 'jittery slow pen') add(name, char, d, name === 'written at 80%' ? 50 : 0);
  }
});
/* The wrong character, written perfectly. */
for (const [char, other] of [['あ', 'お'], ['へ', 'ヘ'], ['シ', 'ツ'], ['ソ', 'ン'], ['く', 'し']]) add('wrong character', char, ink(other));
write('matcher.json', cases);

/* ---------- feedback ---------- */
const fb = [];
const diag = (dx, dy, extentRatio) => ({ dx, dy, extentRatio });
const st = (...xs) => xs.map(([ref, score]) => ({ ref, d: 1, score, reversed: false }));
const args = [
  { score: 90, prevBest: 0, drawn: 4, expected: 3 },
  { score: 90, prevBest: 0, drawn: 6, expected: 3 },
  { score: 50, prevBest: 0, drawn: 2, expected: 3 },
  { score: 50, prevBest: 0, drawn: 1, expected: 4 },
  { score: 96, prevBest: 0, drawn: 3, expected: 3, order: { swapped: [[1, 2]], reversed: [] } },
  { score: 96, prevBest: 0, drawn: 3, expected: 3, order: { swapped: [[1, 3]], reversed: [] } },
  { score: 96, prevBest: 0, drawn: 3, expected: 3, order: { swapped: [[1, 2], [2, 3]], reversed: [] } },
  { score: 96, prevBest: 0, drawn: 3, expected: 3, order: { swapped: [], reversed: [2] } },
  { score: 96, prevBest: 0, drawn: 3, expected: 3, order: { swapped: [], reversed: [1, 2, 3] } },
  { score: 70, prevBest: 60, drawn: 2, expected: 2, order: { swapped: [], reversed: [] } },
  { score: 60, prevBest: 60, drawn: 2, expected: 2, diagnostics: diag(0, 0, 0.7), order: { swapped: [], reversed: [] } },
  { score: 24, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0, 0, 0.7), order: { swapped: [], reversed: [] } },
  { score: 40, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0, 0, 1.3) },
  { score: 40, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(-0.08, 0.06, 1) },
  { score: 40, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0.06, 0.06, 1) },
  { score: 40, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0.01, -0.09, 1) },
  { score: 40, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0.01, 0.07, 1) },
  { score: 85, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0, 0, 1), strokes: st([1, 0.9], [2, 0.7]) },
  { score: 95, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0, 0, 1), strokes: st([1, 0.9], [2, 0.7]) },
  { score: 85, prevBest: 0, drawn: 2, expected: 2, diagnostics: diag(0, 0, 1), strokes: st([1, 0.9], [2, 0.85]) },
  { score: 65, prevBest: 0, drawn: 2, expected: 2, strokes: st([0, 0.1], [2, 0.85]) },
  { score: 30, prevBest: 0, drawn: 2, expected: 2 },
];
for (const a of args) {
  fb.push({
    ...a,
    note: describeAttempt(a),
    notCounted: whyNotCounted(a),
  });
}
write('feedback.json', fb);

/* ---------- ink outlines ---------- */
let pf;
for (const base of [path.join(WEB, 'node_modules'), path.join(ROOT, '.cache', 'node', 'node_modules')]) {
  const p = path.join(base, 'perfect-freehand', 'dist', 'esm', 'index.mjs');
  if (fs.existsSync(p)) pf = p;
}
if (!pf) throw new Error('perfect-freehand not found; run (cd .cache/node && npm i perfect-freehand@1.2.3)');
const src = fs
  .readFileSync(path.join(WEB, 'src', 'lib', 'ink.js'), 'utf8')
  .replace("from 'perfect-freehand'", `from '${pathToFileURL(pf).href}'`)
  .replace("from './stroke-match.js'", `from '${web('src/lib/stroke-match.js')}'`);
const copy = path.join(ROOT, '.cache', 'ink-copy.mjs');
fs.writeFileSync(copy, src);
/* strokePath builds a Path2D; record what it is told instead. */
globalThis.Path2D = class {
  constructor() {
    this.cmds = [];
  }
  moveTo(x, y) {
    this.cmds.push(['M', x, y]);
  }
  quadraticCurveTo(a, b, c, d) {
    this.cmds.push(['Q', a, b, c, d]);
  }
  closePath() {
    this.cmds.push(['Z']);
  }
};
const { strokePath } = await import(pathToFileURL(copy).href);
const inkCases = [];
const pr = (i, n) => 0.3 + 0.5 * Math.sin((i / Math.max(1, n - 1)) * Math.PI);
const strokeFrom = (char, k, scale) =>
  reference(char).dense[k].map((p, i, a) => ({ x: r3(p.x * scale), y: r3(p.y * scale), p: r3(pr(i, a.length)) }));
const samples = {
  'あ stroke 3 at 300': strokeFrom('あ', 2, 300 / VIEWBOX),
  'く at 180': strokeFrom('く', 0, 180 / VIEWBOX),
  'し at 340': strokeFrom('し', 0, 340 / VIEWBOX),
  tap: [{ x: 50, y: 50, p: 0.5 }],
  'two points': [{ x: 10, y: 10, p: 0.4 }, { x: 60, y: 90, p: 0.6 }],
  zigzag: Array.from({ length: 40 }, (_, i) => ({ x: 10 + i * 4, y: 50 + (i % 10 < 5 ? i % 10 : 10 - (i % 10)) * 12, p: 0.5 })),
  trackpad: Array.from({ length: 60 }, (_, i) => ({ x: Math.round(20 + i * 3 + Math.sin(i) * 1.2), y: Math.round(80 + Math.sin(i / 9) * 30), p: 0.5 })),
};
for (const [name, pts] of Object.entries(samples)) {
  for (const [width, pressure, done] of [[6.2, true, true], [6.2, false, true], [3.2, true, false], [14, true, true]]) {
    inkCases.push({ name, width, pressure, done, pts: pts.flatMap(p => [p.x, p.y, p.p]), path: strokePath(pts, { width, pressure, done }).cmds });
  }
}
write('ink.json', inkCases);
