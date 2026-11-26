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
GLOSS={'serpent_eye','mirror_shadow','time_crystal','haunted_compass','eternal_ice','chameleon_scale','gravity_orb','tide_pearl','ender_shard','essence_siphon','soul_flame','cleansing_water','shield_crystal','golden_scarab','spirit_bell','sun_shard','obsidian_shard','ember_coal','lifebloom','purity_charm'}
LIGHT=(255,244,214)
def mix(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def mute(c):
    r,g,b=[v/255 for v in c[:3]]
    h,l,s_=colorsys.rgb_to_hls(r,g,b)
    gray = s_ < 0.12
    s_=min(1.0,s_*1.15)
    r,g,b=colorsys.hls_to_rgb(h,l,s_)
    c=(int(r*255),int(g*255),int(b*255))
    return mix(c,(120,140,170),0.12) if gray else c
def mix(a,b,t): return tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
def image(name):
    g = bone() if name=="giant_bone" else grid(name)
    hl=set()
    if name!="giant_bone":
        for x,y,ch in I[name][3]:
            if ch in "wW": hl.add((x,y))
    filled=lambda x,y: 0<=x<16 and 0<=y<16 and g[y][x] is not None
    img=Image.new("RGBA",(16,16),(0,0,0,0))
    # distance to the nearest empty pixel
    INF=99; dist=[[0 if not g[y][x] else INF for x in range(16)] for y in range(16)]
    for _ in range(16):
        for y in range(16):
            for x in range(16):
                if dist[y][x]:
                    n=[dist[y+dy][x+dx] if 0<=x+dx<16 and 0<=y+dy<16 else 0 for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))]
                    dist[y][x]=min(dist[y][x],min(n)+1)
    for y in range(16):
        for x in range(16):
            if not g[y][x]: continue
            c=mute(g[y][x])
            if (x,y) not in hl and sum(g[y][x][:3])>150:
                lit=sum(1 for k in (1,2) for dx,dy in ((0,-k),(-k,0),(-k,-k)) if not filled(x+dx,y+dy))
                dark=sum(1 for k in (1,2) for dx,dy in ((0,k),(k,0),(k,k)) if not filled(x+dx,y+dy))
                v=lit-dark
                if v>=2: c=mix(c,(255,255,240),0.45)
                elif v<=-2: c=mix(c,(40,16,40),0.42)
            img.putpixel((x,y),c+(255,))
    if name in GLOSS:
        best=None
        for y in range(16):
            for x in range(16):
                if g[y][x] and dist[y][x]>=2 and (best is None or x+y<best[0]+best[1]): best=(x,y)
        if best:
            x,y=best
            for px,py,t in ((x,y,0.95),(x+1,y,0.8),(x,y+1,0.8),(x+1,y+1,0.45)):
                if filled(px,py): img.putpixel((px,py),mix(img.getpixel((px,py))[:3],(255,255,250),t)+(255,))
            # small second glint lower right
            cand=[(xx,yy) for yy in range(16) for xx in range(16) if g[yy][xx] and dist[yy][xx]>=2]
            if cand:
                xx,yy=max(cand,key=lambda p:p[0]+p[1])
                img.putpixel((xx-1,yy-1) if filled(xx-1,yy-1) else (xx,yy),mix(img.getpixel((xx,yy))[:3],(255,255,250),0.45)+(255,))
    OUT=(26,14,20,255); SHADOW=(14,8,12,255)
    shape=set((x,y) for y in range(16) for x in range(16) if g[y][x])
    for y in range(16):
        for x in range(16):
            if g[y][x]: continue
            if any((x+dx,y+dy) in shape for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):
                img.putpixel((x,y),OUT); shape_o=True
    solid=set((x,y) for y in range(16) for x in range(16) if img.getpixel((x,y))[3])
    for (x,y) in solid:
        for sx,sy in ((x+1,y+1),(x,y+1),(x+1,y)):
            if 0<=sx<16 and 0<=sy<16 and (sx,sy) not in solid and (sx-1,sy-1) in solid:
                img.putpixel((sx,sy),SHADOW)
    return img
names=list(I.keys())+["giant_bone"]
if __name__=="__main__":
    out=sys.argv[1]
    cols=9; S=8; pad=8
    sheet=Image.new("RGBA",(cols*(16*S+pad)+pad,((len(names)+cols-1)//cols)*(16*S+pad+12)+pad),(70,72,76,255))
    d=ImageDraw.Draw(sheet)
    for i,n in enumerate(names):
        im=image(n).resize((16*S,16*S),Image.NEAREST)
        x=pad+(i%cols)*(16*S+pad); y=pad+(i//cols)*(16*S+pad+12)
        sheet.alpha_composite(im,(x,y)); d.text((x,y+16*S),n,fill=(255,255,255,255))
    sheet.save(out)
    if len(sys.argv)>2:
        for n in names: image(n).save(f"{sys.argv[2]}/{n}.png")
    print(len(names))
