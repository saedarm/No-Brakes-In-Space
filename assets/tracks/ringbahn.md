# Ringbahn

A practice oval with a vertical loop, a banked hairpin and an octagon tunnel.

width: 12

Columns: `x y z` = control point (metres, y is up). `roll` = bank in degrees
(positive = right edge down). `tunnel` applies from this point to the next:
`-` none, `oct` octagon rings, `box` square rings.

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
| 110 | 15 | -180 | 0    | oct    |
| 110 | 0  | -80  | 0    | -      |
| 95  | 0  | 0    | 20   | -      |
| 60  | 0  | 45   | 30   | -      |
| 20  | 0  | 40   | 20   | -      |
