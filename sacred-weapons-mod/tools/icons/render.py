# Usage: python3 render.py preview.png ../../src/main/resources/assets/sacredsouls/textures/item
import sys
import os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import I
from PIL import Image, ImageDraw
def hexc(h): return tuple(int(h[i:i+2],16) for i in (0,2,4))+(255,)
def grid(name):
    pal, rows, m, over = I[name]
    if m: rows=[r+r[::-1] for r in rows]
    assert len(rows)==16, (name,len(rows))
    bad=[r for r in rows if len(r)!=16]
    if bad: print("BAD",name,bad)
    rows=[(r+"."*16)[:16] for r in rows]
    g=[[None]*16 for _ in range(16)]
    for y,r in enumerate(rows):
        for x,ch in enumerate(r):
            if ch!='.': g[y][x]=hexc(pal[ch])
    for x,y,ch in over: g[y][x]=hexc(pal[ch])
    return g
def bone():
    g=[[None]*16 for _ in range(16)]
    c=hexc("ede6d3"); d=hexc("b8ad90")
    for t in range(4,12):
        for (x,y) in ((t,t),(t+1,t),(t,t+1)): g[y][x]=c
    for cx,cy in ((2,4),(4,2),(13,11),(11,13)):
        for y in range(16):
            for x in range(16):
                if (x-cx)**2+(y-cy)**2<=2.3: g[y][x]=c
    for (x,y) in ((12,12),(13,12),(12,13),(14,11),(11,14)): 
        if g[y][x]: g[y][x]=d
    return g
def image(name):
    g = bone() if name=="giant_bone" else grid(name)
    img=Image.new("RGBA",(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            if g[y][x]: img.putpixel((x,y),g[y][x])
    # outline: transparent pixels touching a filled one get a dark version of it
    for y in range(16):
        for x in range(16):
            if g[y][x]: continue
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                nx,ny=x+dx,y+dy
                if 0<=nx<16 and 0<=ny<16 and g[ny][nx]:
                    r,gg,b,_=g[ny][nx]; img.putpixel((x,y),(int(r*.3),int(gg*.3),int(b*.3),255)); break
    return img
names=list(I.keys())+["giant_bone"]
if __name__=="__main__":
    out=sys.argv[1]
    cols=9; S=8; pad=8
    sheet=Image.new("RGBA",(cols*(16*S+pad)+pad,((len(names)+cols-1)//cols)*(16*S+pad+12)+pad),(60,60,70,255))
    d=ImageDraw.Draw(sheet)
    for i,n in enumerate(names):
        im=image(n).resize((16*S,16*S),Image.NEAREST)
        x=pad+(i%cols)*(16*S+pad); y=pad+(i//cols)*(16*S+pad+12)
        sheet.alpha_composite(im,(x,y)); d.text((x,y+16*S),n,fill=(255,255,255,255))
    sheet.save(out)
    if len(sys.argv)>2:
        for n in names: image(n).save(f"{sys.argv[2]}/{n}.png")
    print(len(names))
