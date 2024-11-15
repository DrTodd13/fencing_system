import math
r1 = 1e5
r2 = 1e5
r3 = 1e5
r4 = 1e7
r5 = 4.7e5
r6 = 4.7e5
c1 = 3.3e-10
c2 = 3.3e-10

# r6 can be adjusted to tune f0:

#f = 3660 # a desired frequency
#f = 4800 # a desired frequency
f0 = 3700 # a desired frequency
f1 = 4900 # a desired frequency

def calc(f):
    r6 = (1/2/math.pi/f)**2 * r1/r3/c1/c2/r5
    print("r6:", r6)
    f0 = 1/2/math.pi*math.sqrt(r1/r3/c1/c2/r5/r6)
    print("f0:", f0)
    b = (1+r1/r3)*r2/(r2+r4)/c1/r5
    print("b:", b)
    Q = 2*math.pi*f0/b
    print("Q:", Q)
    gain = -r4/r2
    print("gain", gain)

calc(f0)
calc(f1)