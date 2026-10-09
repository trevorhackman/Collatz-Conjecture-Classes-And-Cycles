---
name: collatz-coverage
description: >
  How this repo identifies settled Collatz residue classes with the parity-word count,
  and how data/classes/summary.txt and explanation.md must be written. Use when editing
  CoverageShare.kt, the classes summary or explanation, the proved-share percent, or a
  heap run of extendCoverageSummary; when extending the bit table; or when explaining
  why a class modulo 2^b is settled or still open.
---

# Collatz coverage

The pattern and the prose live in the repo. This skill is how not to misstate them.

## The pattern

A class `n ≡ r (mod 2^b)` is settled exactly when some prefix of its parity word has `a` odd steps and `3^a < 2^j`. It is open exactly when every prefix stays on `3^a > 2^j`. The parity-word count enumerates those words. That sameness is the argument, at every bit length.

A written stopping bit is how far a file was generated. It is not a bound on the pattern. Do not support the pattern with a published computational range.

`3^a > 2^j` fails to settle the class because some large member of the class comes out larger. That is the only use of "for large t".

`data/classes/explanation.md` already states the `const < r` claim for words with two or more odd steps. Leave that claim as written. Do not add a proof of it, and do not add a lecture that it is an open conjecture. Do not claim a proof of the Collatz conjecture.

## Prose

`data/classes/explanation.md` is the prose source. Read it before editing it. Preserve wording the user already accepted. Follow the `clear-explanations` skill. Do not paste planning notes into the file.

IntelliJ's bundled Markdown plugin renders `$...$` and `$$...$$` in the preview pane only. The source keeps the dollar signs.

## The table

Keep the in-memory residue walk: `coverageShares`, `advanceCoverageShare`, `CoverageProbe`, `bits-01.txt` through `bits-16.txt`, and the UI open-residue list. Do not bring back `open.bin` checkpoints.

`CoverageShareRow`, `openStoppingCount`, and the signed-modulus parsers stay on `Long`, so they stop at `2^62`. Do not run `rewrite-summary` on a summary whose moduli exceed that.

The long table is `writeParityCoverageUntilHeap` (`BigInteger`). `PARITY_PROOF_BITS` is 1000. `extendCoverageSummary` writes through `2^1000` and no further, including `-PheapMb`. It replaces `data/classes/summary.txt` only after the run finishes. Do not edit `summary.txt.partial`, `summary.txt.mbuckets`, or `summary.txt.assembled` while that process has them open.

A heap run still stops early when a second copy of the active-word counters would pass one third of the heap. The resource preface is written at the end from measured numbers.

Do not write summary rows past `2^1000`.

Bit lines are `2^b`, the proved percent, then `proved/modulus`. An m-bucket row is `m`, the bit range, the proved percent, then `proved/modulus`. The open share is the rest of the proved share and is not a column. In the written m table, `m` and the bit range are padded on the left so the proved percent starts in one column, and the proved percent is padded on the right so the fraction starts in one column.

## Percents

`formatPercent` keeps two-decimal half-up while that rounding is neither `0%` nor `100%` (`99.23%`, `99.3%`, `99.85%`). When two decimals would hide a nonzero remainder, print eight significant digits of that gap as more digits of the percent (`2^135` is `99.9951121472%` and `0.0048878528%`). A proved share near 100 stays a `99.99…%` decimal. A tiny open share stays a plain decimal too (`0.0000000000000000000000000001%`). Never print `0%` or `100%` for a share that is not exact.
