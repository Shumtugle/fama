# The mark of the herald: a long fanfare trumpet raised to the upper right,
# its bell flared, a gold ball on its shaft, and a banner hung under it,
# cream with a dark band where it wraps the shaft and three quiet dots.
import math
TH = math.radians(-22)          # the trumpet is raised this far
PX, PY = 54.0, 50.0             # it turns about this point
AX = PY                         # the shaft's axis, before turning

def R(x, y):
    dx, dy = x - PX, y - PY
    return (PX + dx*math.cos(TH) - dy*math.sin(TH), PY + dx*math.sin(TH) + dy*math.cos(TH))

OX, OY = -1.40, -0.83               # the whole moved, to sit in the middle of the round

def poly(pts):
    return "M" + "L".join("%.2f,%.2f" % (x + OX, y + OY) for x, y in pts) + "Z"

def circ(cx, cy, r):
    cx += OX; cy += OY
    return "M%.2f,%.2fa%.2f,%.2f 0 1,0 %.2f,0a%.2f,%.2f 0 1,0 %.2f,0Z" % (cx-r, cy, r, r, 2*r, r, r, -2*r)

def ell(cx, cy, rx, ry, n=36):
    return [(cx + rx*math.cos(2*math.pi*i/n), cy + ry*math.sin(2*math.pi*i/n)) for i in range(n)]

def bez(p0, p1, p2, p3, n=16):
    out = []
    for i in range(1, n+1):
        t = i/n; u = 1-t
        out.append((u**3*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t**3*p3[0],
                    u**3*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t**3*p3[1]))
    return out

def turned(pts):
    return [R(x, y) for x, y in pts]

GOLD, DEEP, LIGHT = "#E9AA2E", "#B8741A", "#FFF3CF"
CREAM, SHADE, DOT, BAND = "#FAF0E1", "#F1E4D0", "#DCC6A8", "#6B1B12"

# ---- the trumpet, drawn level, then turned
T = 2.25                                    # half the shaft's thickness
shaft = [(31, AX-T), (65, AX-T), (65, AX+T), (31, AX+T)]
shank = [(27.5, AX-1.4), (31.5, AX-1.4), (31.5, AX+1.4), (27.5, AX+1.4)]
cup = ell(26.4, AX, 2.3, 3.4)
bell = [(64, AX-T)] + bez((64, AX-T), (71.5, AX-T-0.3), (77.5, AX-5.2), (82, AX-12)) \
     + [(82, AX+12)] + bez((82, AX+12), (77.5, AX+5.2), (71.5, AX+T+0.3), (64, AX+T))
bell_low = [(64, AX)] + [(82, AX)] + bez((82, AX+10), (77.5, AX+4.5), (71.5, AX+T+0.3), (64, AX+T))[::-1][:0] \
     + [(82, AX+10)] + bez((82, AX+10), (77.5, AX+4.5), (71.5, AX+T+0.3), (64, AX+T))
rim = ell(82, AX, 2.2, 12)
mouth = ell(82.6, AX, 1.3, 10.1)
ring1 = [(60.6, AX-2.9), (62.4, AX-2.9), (62.4, AX+2.9), (60.6, AX+2.9)]
ring2 = [(32.4, AX-2.7), (34.0, AX-2.7), (34.0, AX+2.7), (32.4, AX+2.7)]
gleam = [(35, AX-1.55), (59.5, AX-1.55), (59.5, AX-0.8), (35, AX-0.8)]
knob_c = R(41, AX)

# ---- the banner: hung from two points of the shaft, its sides falling straight down
A = R(34, AX+T); B = R(58, AX+T)
H = 19.5; NOTCH = 5.0; BANDH = 3.6
mid = ((A[0]+B[0])/2, (A[1]+B[1])/2)
banner = [A, B, (B[0], B[1]+H), (mid[0], mid[1]+H-NOTCH), (A[0], A[1]+H)]
shadow = [(x+1.3, y+2.2) for x, y in banner]
band = [A, B, (B[0], B[1]+BANDH), (A[0], A[1]+BANDH)]
dots = []
for k in (0.28, 0.5, 0.72):
    x = A[0] + (B[0]-A[0])*k; y = A[1] + (B[1]-A[1])*k + BANDH + 6.6
    dots.append(circ(x, y, 1.75))

def path(color, d, alpha=None):
    a = '' if alpha is None else '\n        android:fillAlpha="%s"' % alpha
    return '    <path android:fillColor="%s"%s\n        android:pathData="%s" />\n' % (color, a, d)

head = '''<?xml version="1.0" encoding="utf-8"?>
<!-- %s -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
'''

fg = head % ("The mark: a herald's trumpet raised to the upper right, as Fama blows hers, "
             "with a gold ball on its shaft and a banner hung under it, cream with a dark band "
             "where it wraps the shaft and three quiet dots on it.")
fg += path("#000000", poly(shadow), "0.24")
fg += path(CREAM, poly(banner))
fg += path(SHADE, poly([(mid[0], mid[1]), B, (B[0], B[1]+H), (mid[0], mid[1]+H-NOTCH)]), "0.9")
fg += path(BAND, poly(band))
fg += path(DOT, "".join(dots))
fg += path(DEEP, poly(turned(cup)))
fg += path(GOLD, poly(turned(shank)))
fg += path(GOLD, poly(turned(shaft)))
fg += path(GOLD, poly(turned(bell)))
fg += path(DEEP, poly(turned([(64, AX)] + bez((64, AX), (69, AX), (76, AX), (82, AX)) + [(82, AX+12)]
                             + bez((82, AX+12), (77.5, AX+5.2), (71.5, AX+T+0.3), (64, AX+T)))), "0.45")
fg += path(DEEP, poly(turned(ring1)) + poly(turned(ring2)))
fg += path(LIGHT, poly(turned(gleam)), "0.75")
fg += path(DEEP, poly(turned(rim)))
fg += path(BAND, poly(turned(mouth)))
fg += path(DEEP, circ(knob_c[0], knob_c[1], 4.3))
fg += path(GOLD, circ(knob_c[0]-0.25, knob_c[1]-0.35, 3.65))
fg += path(LIGHT, circ(knob_c[0]-1.35, knob_c[1]-1.45, 1.2))
fg += '</vector>\n'

mono = head % "The same mark in one colour, for launchers that tint icons themselves."
mono += path("#FFFFFF", poly(banner), "0.55")
mono += path("#FFFFFF", poly(band))
mono += path("#FFFFFF", poly(turned(cup)) + poly(turned(shank)) + poly(turned(shaft)) + poly(turned(bell)))
mono += path("#FFFFFF", circ(knob_c[0], knob_c[1], 4.3))
mono += '</vector>\n'

import sys
out = sys.argv[1]
open(out + '/ic_fg.xml', 'w').write(fg)
open(out + '/ic_mono.xml', 'w').write(mono)
