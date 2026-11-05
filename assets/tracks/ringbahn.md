# Ringbahn

The practice course: a vertical loop, a banked hairpin, an S-bend tunnel run
and a recharge strip on the start straight.

tagline: The practice course. One loop, no excuses.
width: 20
scale: 2.5
pit: 60 360
obstacles: 12

Columns: `x y z` = control point (y is up; multiplied by `scale` to get metres).
`roll` = bank in degrees (positive = right edge down). `tunnel` applies from this
point to the next: `-` none, `oct` octagon rings, `box` square rings.
`pit: A B` = recharge strip from A to B metres after the start line (left third
of the track). `obstacles: N` = how many obstacle spots to scatter (same layout
every race).

| x   | y  | z    | roll | tunnel |
|-----|----|------|------|--------|
| 0   | 0  | 0    | 0    | -      |
| 0   | 0  | -90  | 0    | box    |
| 0   | 0  | -170 | 0    | -      |
| 0   | 4  | -225 | 0    | -      |
| 3   | 30 | -255 | 0    | -      |
| 6   | 55 | -225 | 0    | -      |
| 9   | 30 | -195 | 0    | -      |
| 12  | 4  | -225 | 0    | -      |
| 13  | 0  | -280 | 0    | -      |
| 15  | 0  | -360 | 10   | -      |
| 50  | 0  | -410 | 30   | -      |
| 95  | 0  | -370 | 30   | -      |
| 110 | 0  | -280 | 10   | oct    |
| 102 | 8  | -232 | 20   | oct    |
| 118 | 14 | -184 | -20  | -      |
| 102 | 8  | -136 | 20   | -      |
| 110 | 0  | -80  | 0    | -      |
| 95  | 0  | 0    | 20   | -      |
| 60  | 0  | 45   | 30   | -      |
| 20  | 0  | 40   | 20   | -      |
