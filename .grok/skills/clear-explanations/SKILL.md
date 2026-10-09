---
name: clear-explanations
description: >
  Write and edit clear mathematical and technical explanations in plain, speakable prose.
  Use when drafting or rewriting explanation.md, explanation.txt, proof notes, README math sections, or any
  user-facing argument the reader must follow; when the user says the wording is unclear,
  redundant, or "not something you could speak"; when proving an equivalence or implication;
  or when the user runs /clear-explanations.
---

# Clear explanations

Write so a careful reader can say the sentences out loud and know what was claimed.

## Voice

- Prefer words you would speak. No parenthetical asides that redefine the claim mid-sentence (`Claim (long restatement) is…`).
- Keep one verb for one idea across the whole argument. If the claim says **reaches**, every step and the conclusion say **reaches** — not stop, end, arrive, or "last value". Do not reuse that verb for a different idea (e.g. a percent "reaches" 99%).
- Keep parallel grammar when two claims are compared: `reaches` with `reaches`, not `reaches` with `reaching`.
- Cut empty academic filler (`finitely many`, `the other way`, `namely`, `it follows that`) unless the next sentence says what that buys you in concrete terms.
- No math-lecture slang aimed at the reader: **Fix**, **Pick**, **Write** (as a command), **skip it**. Use ordinary English (`Let b be…`, `Omit that member`, `Divide n by 2`).
- Subjects do the right actions. Numbers do not "divide by 2"; you divide them. A file does not "Write that count"; it records it.
- Prepositions must mean what they say. Prefer `members that are greater than 1` over `members with n > 1` when n is the member itself.
- Every pronoun needs one obvious antecedent. If `it` could be the residue, the class, or the number 1, name the thing.

## Claims and proofs

- State what you are doing first when it is not obvious (e.g. proving an **equivalence**, not assuming either side).
- Name both sides of an equivalence in the opening lines. Never write "two claims" / "the other claim" / "the second statement" before the reader has seen the actual statements — or restate the statement instead of numbering it.
- Do not assert the conjecture or theorem as fact while proving an equivalence. Put each direction under an explicit `If …`.
- The conclusion of a direction must restate **the same claim** that direction was proving — same words, same verb.
- One direction, then the other. Do not braid both directions into one paragraph.
- Do not add extra consequences (cycles, divergent trajectories, edge stories) unless the user asked for them or they are required to define the claim.
- Do not write correction-asides (`Not every term is smaller…`). State the objects you mean; do not narrate a previous mistake.
- Do not introduce a named quantity with **the** before it exists (`The proved share is…`). Define it when it first appears, or describe it without a name until then.
- Prefer the notation the reader already knows when it is enough (`n ≡ r (mod 2^b)`). Bring in a parametric form (`n = 2^b * t + r`) only when you need that parameter, and name every symbol the first time it appears.
- Cut implementation edge cases that the claim already covers. If the claim is only about n > 1, do not digress into "skipping 1 when r = 1" unless the reader must know that for the next step.
- Concrete examples before the general machine when the general form would otherwise feel alien.

## Editing

- Once the user is editing the prose, **preserve their wording**. Change only what they asked for (clarity of a sentence, wrap, typo). Do not "improve" nearby lines.
- Prefer the user's term choices over synonyms you invent.
- Hand-written explanation files are the source of truth. Do not regenerate them from code strings that overwrite edits.
- If a sentence adds nothing, delete it. Do not keep it for completeness.

## Line length

- Respect the project's line limit (this repo: **120** characters).
- Wrap at word boundaries. Fill up to the limit; do not break early into short lines.
- Do not break inside a token the reader must keep together when a nearby word boundary works (`(4 - 3)`, `2^b`, `n > 1`, `1 -> 4 -> 2 -> 1`).
- Do not leave a dangling word alone on a line (`that`, `with`, `t =`) when an earlier break reads better.

## Before you finish

Read the paragraph out loud. Check: same verb end-to-end, both claims named before "if", conclusion uses the claim's own words, every symbol explained or unused, no undefined "the X", no slang imperatives, no useless edge-case digression, lines ≤ limit, user's phrasing kept where they already chose it.
