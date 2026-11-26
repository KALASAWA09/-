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
import colorsys
PLUM=(42,30,58)
LIGHT=(255,244,214)
def mix(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def mute(c):
    r,g,b=[v/255 for v in c[:3]]
    h,l,s_=colorsys.rgb_to_hls(r,g,b)
    gray = s_ < 0.12
    s_*=0.8
    r,g,b=colorsys.hls_to_rgb(h,l,s_)
    c=(int(r*255),int(g*255),int(b*255))
    return mix(c,(110,140,160),0.22) if gray else c
def mix(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def image(name):
    g = bone() if name=="giant_bone" else grid(name)
    hl=set()
    if name!="giant_bone":
        for x,y,ch in I[name][3]:
            if ch in "wW": hl.add((x,y))
    filled=lambda x,y: 0<=x<16 and 0<=y<16 and g[y][x] is not None
    img=Image.new("RGBA",(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            if not g[y][x]: continue
            c=mute(g[y][x])
            if (x,y) not in hl:
                top=not filled(x,y-1) or not filled(x-1,y)
                bot=not filled(x,y+1) or not filled(x+1,y)
                far_bot = not filled(x,y+2) or not filled(x+2,y)
                if top and not bot: c=mix(c,LIGHT,0.32)
                elif bot and not top: c=mix(c,PLUM,0.36)
                elif not (top or bot) and far_bot: c=mix(c,PLUM,0.14)
            img.putpixel((x,y),c+(255,))
    for y in range(16):
        for x in range(16):
            if g[y][x]: continue
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                nx,ny=x+dx,y+dy
                if filled(nx,ny):
                    c=mute(g[ny][nx]); c=mix(tuple(int(v*.42) for v in c),PLUM,0.35)
                    img.putpixel((x,y),c+(255,)); break
    return img
names=list(I.keys())+["giant_bone"]
if __name__=="__main__":
    out=sys.argv[1]
    cols=9; S=8; pad=8
    sheet=Image.new("RGBA",(cols*(16*S+pad)+pad,((len(names)+cols-1)//cols)*(16*S+pad+12)+pad),(150,160,170,255))
    d=ImageDraw.Draw(sheet)
    for i,n in enumerate(names):
        im=image(n).resize((16*S,16*S),Image.NEAREST)
        x=pad+(i%cols)*(16*S+pad); y=pad+(i//cols)*(16*S+pad+12)
        sheet.alpha_composite(im,(x,y)); d.text((x,y+16*S),n,fill=(255,255,255,255))
    sheet.save(out)
    if len(sys.argv)>2:
        for n in names: image(n).save(f"{sys.argv[2]}/{n}.png")
    print(len(names))
