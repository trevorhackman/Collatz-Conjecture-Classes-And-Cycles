# CollatzConjecture

Residue-class attack on the Collatz conjecture. A class is settled when shared parities force every integer `n > 1` in it to an iterate strictly smaller than `n`.

- `:core` does that work. `CoverageProbe` settles residue classes. `CycleProbe` solves the accelerated cycle equation for length 1, 2, 3, … and rules out every whole number x > 1.
- `:ui` is a Compose desktop window. It shows those probes and plots a trajectory. It does not implement the mathematics.

```
./gradlew :core:test
./gradlew :core:logProofs
./gradlew :ui:run
```

`./gradlew :core:logProofs` writes the casework into `data/`. Cycle files rule out length `n` for every odd `x > 1`. Classes files record the forced steps for every class through 2^16. `./gradlew :core:coverageShare -Pbits=16` repeats that residue walk and prints the share. `./gradlew :core:mBucketShares -Pm=40` counts those same settled classes by parity words. The argument is in `data/classes/explanation.md`.
