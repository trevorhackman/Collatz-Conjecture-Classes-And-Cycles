# What this proves

The Collatz conjecture is that every positive integer reaches 1. That is equivalent to: every integer $n > 1$
reaches a smaller positive integer.

If every positive integer reaches 1, then every $n > 1$ reaches a smaller positive integer, because 1 is smaller
than $n$.

If every positive integer $n > 1$ reaches a smaller positive integer, start at $n$ and keep taking the smaller
integer each one reaches. Positive integers cannot keep getting smaller forever, so every positive integer
reaches 1.

1 never reaches a smaller positive integer. Its orbit is $1 \to 4 \to 2 \to 1$, so the second statement starts
at $n > 1$.

This directory does not prove the second statement for every integer greater than 1. It proves the statement for
some residue classes modulo $2^b$.

# How a class is settled

Let $b$ be a positive integer. Let $r$ be an integer with $0 \le r < 2^b$. The residue class is every positive
integer $n$ such that $n \equiv r \pmod{2^b}$.

Example: $n \equiv 0 \pmod 2$ is the even numbers. Divide each even $n$ by 2 to get $n/2$. When $n > 1$, that
result is smaller than $n$. So every even $n > 1$ reaches a smaller positive integer. That is half of the
positive integers.

Example: $n \equiv 1 \pmod 4$. Write $n = 4t + 1$. Every such $n$ follows the same Collatz steps: $3n+1$, then
divide by 2, then divide by 2 again. The result is $3t+1$. For every $n > 1$ in this class, $3t+1$ is smaller
than $n$. Together with the even numbers, that is three quarters of the positive integers.

To check a class in general, write $n = 2^b t + r$ and apply the Collatz rules. As long as the next step does
not depend on the value of $t$, every member of the class takes that same step, and the result again has the
form $at + c$. Continue. If the result is smaller than $n$ for every $n > 1$ in the class, the class is
settled: every such $n$ reaches a smaller positive integer.

If the next step begins to depend on $t$ before that happens, the class is still open at this modulus. Split it
into the two finer classes modulo $2^{b+1}$, and check those. Only open classes are split. A settled class
remains settled at every larger power of two.

The class $n \equiv -1 \pmod{2^b}$ is never settled at modulus $2^b$. After $b$ odd steps of the form
$(3n+1)/2$, the value is still larger by about a factor of $(3/2)^b$. So no finite power of two settles every
residue class.

# What the other files are

`bits-01.txt` through `bits-16.txt` list each class examined at that modulus. For a settled class they show the
shared Collatz steps and the check that the result is smaller than $n$. For an open class they show the shared
steps and the point where the next step begins to depend on $t$. That is the residue walk. It is how a class is
shown to be settled, one modulus at a time. A line per class does not fit past bit 16.

`summary.txt` records the proved share. For each $b$ it is how many residue classes modulo $2^b$ are settled, as
a fraction of $2^b$ and as a percent. Call that fraction the proved share. The classes all have the same size,
so the proved share is also the fraction of all positive integers that belong to a settled class. The same file
then groups those rows by $m(b) = \lfloor \log_3(2^b) \rfloor$. Flat bit-ranges that share one proved percent
appear once, with proved and open percents.

The residue walk produced the share through $2^{36}$. The rows after $2^{36}$ are the parity-word count proved
below. They run through $2^{1000}$.

# Why a parity word settles the class

The shortcut step sends an even integer to $n/2$ and an odd integer to $(3n+1)/2$. This section proves an
equivalence. One side is: the shared Collatz prefix settles the class $n \equiv r \pmod{2^b}$. The other side
is: in the parity word of that class, some prefix of $j$ shortcut steps has $a$ odd steps and satisfies
$3^a < 2^j$.

The first $j$ shortcut steps are the same for every $n \equiv r \pmod{2^b}$ when $j \le b$. After $j$ steps and
$a$ odd steps, the result is $(3^a n + S)/2^j$, where $S$ is an integer that does not depend on $n$ and
$S \ge 0$. With $n = 2^b t + r$, that result is

$$
\operatorname{coef} \cdot t + \operatorname{const}
= (3^a \cdot 2^{b-j})\, t + \frac{3^a r + S}{2^j}.
$$

The constant starts at $r$ and stays in the range $0 \le \operatorname{const} < \operatorname{coef}$. Halving
preserves that range, and sending const to $3\cdot\operatorname{const}+1$ keeps it below
$3\cdot\operatorname{coef}$.

Equality $3^a = 2^j$ never happens for $a > 0$, so coef is never $2^b$ after a step. The coefficient of $t$ is
smaller than $2^b$ exactly when $3^a < 2^j$, and larger than $2^b$ exactly when $3^a > 2^j$.

If every prefix has $3^a > 2^j$, then coef is greater than $2^b$ on every shared prefix. A member with large
enough $t$ then comes out larger than it started, so no shared prefix settles the class. The class stays open
exactly when every prefix of length $j$ from 1 through $b$ has $3^a > 2^j$.

If some prefix has $3^a < 2^j$, take the shortest one. Then coef is below $2^b$, and

$$
n - (\operatorname{coef}\cdot t + \operatorname{const})
= (2^b - \operatorname{coef})\, t + (r - \operatorname{const}).
$$

The coefficient of $t$ is positive, so the right-hand side is positive for every admissible $t$ once it is
positive at the smallest one.

If $r$ is even, the shortest prefix is the single even step. const is $r/2$ and coef is $2^{b-1}$. For $r = 0$
the smallest member greater than 1 is $2^b$, and its image is $2^{b-1}$. For $r \ge 2$, const is below $r$, so
every $t \ge 0$ gives a smaller value.

If $r$ is odd and $r \equiv 1 \pmod 4$, the shortest prefix is one odd step and then one even step, because
$3 < 4$. The image of $r$ is $(3r+1)/4$, and coef is $3\cdot 2^{b-2}$. For $r = 1$, const returns to 1. The
smallest member greater than 1 is $2^b + 1$, and its image is smaller because coef is below $2^b$ and const
equals $r$. For $r > 1$, $(3r+1)/4 < r$, so every $t \ge 0$ gives a smaller value. If $r$ is 1 and $b$ is 1,
the single odd step has $3 > 2$, so the odds stay open.

If $r \equiv 3 \pmod 4$, the first steps stay above the line, and the class is still open at modulus 4. The
same comparison settles it at the first later prefix with $3^a < 2^j$. Take $r = 3$. Its word is odd, odd,
even, even, and $3 \to 5 \to 8 \to 4 \to 2$. Here $a = 2$ and $j = 4$, and $9 < 16$, so modulo 16 the shared
result is $9t + 2$. The member $t = 0$ is 3, and $2 < 3$, so $9t + 2 < 16t + 3$ for every $t \ge 0$. On every
such word, const is the image of $r$ under that first prefix, and const is below $r$. Every $t \ge 0$ then
gives a smaller value.

Thus the shared prefix settles every $n > 1$ in the class exactly when some prefix has $3^a < 2^j$.

The count stores those open words, grouped by the number of odd steps. One new bit appends an even step or an
odd step, and keeps a word only when the new length still has $3^a > 2^{b+1}$. The stored list has one integer
for each admissible number of odd steps, about $0.37b$ integers. While $m(b)$ stays fixed, the cutoff does not
move, every open word has two open extensions, and the open count doubles. The proved share is unchanged, so
that stretch is one row, reported at its last bit. There is no closed form for the count: the cutoff has
irrational slope $\log_3 2$. The open fraction tends to 0 as $b$ grows, so the proved share tends to 100%, and
it is never 100% at a finite $b$. The class $n \equiv -1 \pmod{2^b}$ stays open at every finite $b$.
