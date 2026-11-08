import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops
import math

SS = 4  # supersampling

def grad(size, c1, c2, angle=90):
    w,h=size
    y,x=np.mgrid[0:h,0:w].astype(np.float32)
    t=(x*math.cos(math.radians(angle-90))+y*math.sin(math.radians(angle-90)))
    t=(t-t.min())/(t.max()-t.min()+1e-6)
    out=np.zeros((h,w,4),np.uint8)
    for i in range(3): out[...,i]=(c1[i]*(1-t)+c2[i]*t).astype(np.uint8)
    out[...,3]=255
    return Image.fromarray(out,'RGBA')

def jelly_mask(S):
    """Pudim/gelatina: base larga, topo arredondado, com leve cintura."""
    m=Image.new('L',(S,S),0); d=ImageDraw.Draw(m)
    pts=[]
    for i in range(0,181):
        a=math.radians(i)
        # topo: semi-elipse
        pts.append((S*0.50-math.cos(a)*S*0.30, S*0.40-math.sin(a)*S*0.20))
    pts+= [(S*0.83,S*0.48),(S*0.87,S*0.70),(S*0.82,S*0.80)]
    pts+= [(S*0.18,S*0.80),(S*0.13,S*0.70),(S*0.17,S*0.48)]
    # topo reorganizado: começa no lado esquerdo
    top=[(S*0.50-math.cos(math.radians(i))*S*0.34, S*0.40-math.sin(math.radians(i))*S*0.22) for i in range(0,181)]
    body=top+[(S*0.86,S*0.50),(S*0.90,S*0.72),(S*0.84,S*0.82),(S*0.16,S*0.82),(S*0.10,S*0.72),(S*0.14,S*0.50)]
    d.polygon(body,fill=255)
    m=m.filter(ImageFilter.GaussianBlur(S*0.012)).point(lambda v:255 if v>127 else 0)
    return m

def make_jelly(S, c_light=(255,150,215), c_dark=(214,30,140), bite=True):
    S2=S
    mask=jelly_mask(S2)
    if bite:
        d=ImageDraw.Draw(mask)
        for cx,cy,r in [(0.80,0.40,0.085),(0.88,0.52,0.075),(0.76,0.30,0.06),(0.90,0.64,0.06)]:
            d.ellipse([S2*(cx-r),S2*(cy-r),S2*(cx+r),S2*(cy+r)],fill=0)
    body=grad((S2,S2),c_light,c_dark,angle=100)
    # núcleo mais escuro (volume translúcido)
    core=Image.new('RGBA',(S2,S2),(0,0,0,0)); cd=ImageDraw.Draw(core)
    cd.ellipse([S2*0.26,S2*0.42,S2*0.74,S2*0.80],fill=(255,60,170,110))
    core=core.filter(ImageFilter.GaussianBlur(S2*0.05))
    body=Image.alpha_composite(body,core)
    # brilho (highlight)
    hl=Image.new('RGBA',(S2,S2),(0,0,0,0)); hd=ImageDraw.Draw(hl)
    hd.ellipse([S2*0.20,S2*0.24,S2*0.46,S2*0.40],fill=(255,255,255,200))
    hd.ellipse([S2*0.20,S2*0.46,S2*0.26,S2*0.64],fill=(255,255,255,150))
    hl=hl.filter(ImageFilter.GaussianBlur(S2*0.012))
    body=Image.alpha_composite(body,hl)
    # borda mais escura
    edge=ImageChops.subtract(mask,mask.filter(ImageFilter.MinFilter(max(3,int(S2*0.03))|1)))
    edge_img=Image.new('RGBA',(S2,S2),(150,10,90,255)); edge_img.putalpha(edge.point(lambda v:int(v*0.8)))
    body=Image.alpha_composite(body,edge_img)
    out=Image.new('RGBA',(S2,S2),(0,0,0,0)); out.paste(body,(0,0),mask)
    return out


def make_spoon(S, angle=-32, center=(0.70,0.50)):
    """Colher de verdade: concha oval + cabo afinando, girada; 'center' = onde fica o centro da concha."""
    L=Image.new('RGBA',(S,S),(0,0,0,0)); d=ImageDraw.Draw(L)
    cx=0.5
    # cabo (trapézio estreito -> largo) com ponta arredondada
    d.polygon([(S*(cx-0.020),S*0.60),(S*(cx+0.020),S*0.60),(S*(cx+0.045),S*0.24),(S*(cx-0.045),S*0.24)],fill=(205,214,228,255))
    d.ellipse([S*(cx-0.040),S*0.20,S*(cx+0.040),S*0.28],fill=(205,214,228,255))
    d.polygon([(S*(cx-0.020),S*0.60),(S*(cx-0.004),S*0.60),(S*(cx+0.010),S*0.24),(S*(cx-0.045),S*0.24)],fill=(250,252,255,255))
    # concha
    d.ellipse([S*(cx-0.125),S*0.54,S*(cx+0.125),S*0.90],fill=(176,188,205,255))
    d.ellipse([S*(cx-0.105),S*0.565,S*(cx+0.105),S*0.875],fill=(232,238,247,255))
    d.ellipse([S*(cx-0.075),S*0.60,S*(cx-0.005),S*0.70],fill=(255,255,255,235))
    bowl=(S*cx,S*0.72)
    L=L.rotate(angle,resample=Image.BICUBIC,center=bowl)
    # leva o centro da concha até 'center'
    dx=int(S*center[0]-bowl[0]); dy=int(S*center[1]-bowl[1])
    out=Image.new('RGBA',(S,S),(0,0,0,0)); out.paste(L,(dx,dy),L)
    return out

def scene(big, with_sparkles=True):
    j=make_jelly(big); j=shadow(j,(0,int(big*0.02)),big*0.015,150)
    sp=make_spoon(big,-32,(0.71,0.58))
    sp=shadow(sp,(int(big*0.01),int(big*0.015)),big*0.01,130)
    layer=Image.alpha_composite(j,sp)
    if with_sparkles:
        d=ImageDraw.Draw(layer)
        sparkle(d,big*0.86,big*0.17,big*0.065,(255,255,255,240))
        sparkle(d,big*0.15,big*0.16,big*0.04,(255,255,255,210))
        sparkle(d,big*0.93,big*0.80,big*0.035,(255,255,255,200))
    return layer

def shadow(img, off, blur, alpha=120, color=(40,0,40)):
    a=img.split()[3]
    sh=Image.new('RGBA',img.size,color+(0,)); sh.putalpha(a.point(lambda v:int(v*alpha/255)))
    sh=sh.filter(ImageFilter.GaussianBlur(blur))
    out=Image.new('RGBA',img.size,(0,0,0,0)); out.paste(sh,off,sh)
    return Image.alpha_composite(out,img)

def sparkle(d,cx,cy,r,fill):
    d.polygon([(cx,cy-r),(cx+r*0.22,cy-r*0.22),(cx+r,cy),(cx+r*0.22,cy+r*0.22),(cx,cy+r),(cx-r*0.22,cy+r*0.22),(cx-r,cy),(cx-r*0.22,cy-r*0.22)],fill=fill)

def emblem(S):
    """Emblema quadrado S x S (sem texto)."""
    big=S*SS
    img=Image.new('RGBA',(big,big),(0,0,0,0))
    # fundo: quadrado arredondado com degradê roxo -> magenta
    bg=grad((big,big),(88,40,180),(236,64,170),angle=135)
    m=Image.new('L',(big,big),0); ImageDraw.Draw(m).rounded_rectangle([0,0,big-1,big-1],radius=int(big*0.2),fill=255)
    img.paste(bg,(0,0),m)
    # brilho de fundo
    glow=Image.new('RGBA',(big,big),(0,0,0,0)); ImageDraw.Draw(glow).ellipse([big*0.1,big*0.15,big*0.9,big*0.95],fill=(255,170,235,95))
    glow=glow.filter(ImageFilter.GaussianBlur(big*0.10))
    img=Image.alpha_composite(img,Image.composite(glow,Image.new('RGBA',(big,big),(0,0,0,0)),m))
    # gelatina + colher
    sc=scene(big,with_sparkles=False).resize((int(big*0.84),int(big*0.84)),Image.LANCZOS)
    lay=Image.new('RGBA',(big,big),(0,0,0,0)); lay.alpha_composite(sc,(int(big*0.07),int(big*0.12)))
    d=ImageDraw.Draw(lay)
    sparkle(d,big*0.84,big*0.14,big*0.065,(255,255,255,240))
    sparkle(d,big*0.15,big*0.16,big*0.04,(255,255,255,210))
    sparkle(d,big*0.88,big*0.84,big*0.035,(255,255,255,200))
    img=Image.alpha_composite(img,lay)
    return img.resize((S,S),Image.LANCZOS)

def font(size,bold=True):
    return ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',size)

def outlined_text(img,xy,text,fnt,fill_top,fill_bot,outline,ow,shadow_off=(0,6)):
    W,H=img.size
    layer=Image.new('L',(W,H),0); ImageDraw.Draw(layer).text(xy,text,font=fnt,fill=255)
    ol=layer.filter(ImageFilter.MaxFilter(ow*2+1))
    # sombra
    sh=Image.new('RGBA',(W,H),(30,0,40,0)); sh.putalpha(ol.point(lambda v:int(v*0.55)))
    sh=sh.filter(ImageFilter.GaussianBlur(5))
    tmp=Image.new('RGBA',(W,H),(0,0,0,0)); tmp.paste(sh,shadow_off,sh)
    img.alpha_composite(tmp)
    oi=Image.new('RGBA',(W,H),outline+(255,)); oi.putalpha(ol)
    img.alpha_composite(oi)
    bbox=layer.getbbox()
    g=grad((W,H),fill_top,fill_bot,angle=90)
    gi=Image.new('RGBA',(W,H),(0,0,0,0)); gi.paste(g,(0,0),layer)
    img.alpha_composite(gi)
    # brilho superior
    if bbox:
        hh=Image.new('L',(W,H),0); ImageDraw.Draw(hh).rectangle([0,bbox[1],W,bbox[1]+(bbox[3]-bbox[1])*0.38],fill=90)
        hl=Image.new('RGBA',(W,H),(255,255,255,0)); hl.putalpha(ImageChops.multiply(hh,layer))
        img.alpha_composite(hl)

def banner(W=1600,H=520):
    big=Image.new('RGBA',(W,H),(0,0,0,0))
    bg=grad((W,H),(60,22,128),(214,48,150),angle=120)
    m=Image.new('L',(W,H),0); ImageDraw.Draw(m).rounded_rectangle([0,0,W-1,H-1],radius=44,fill=255)
    big.paste(bg,(0,0),m)
    # bolhas translúcidas (camada própria para misturar de verdade)
    bub=Image.new('RGBA',(W,H),(0,0,0,0)); bd=ImageDraw.Draw(bub)
    rnd=np.random.default_rng(7)
    for _ in range(26):
        x=int(rnd.integers(0,W)); y=int(rnd.integers(0,H)); r=int(rnd.integers(8,46))
        bd.ellipse([x-r,y-r,x+r,y+r],fill=(255,255,255,int(rnd.integers(14,34))))
    bub=bub.filter(ImageFilter.GaussianBlur(1.2))
    big=Image.alpha_composite(big,bub)
    # cena (gelatina + colher) sem caixa, grande, à esquerda
    sc=scene(440*SS).resize((440,440),Image.LANCZOS)
    big.alpha_composite(sc,(50,40))
    outlined_text(big,(530,38),"JELLY",font(170),(255,246,170),(255,170,40),(80,18,90),9)
    outlined_text(big,(530,200),"GOBBLER",font(170),(255,190,235),(255,60,170),(80,18,90),9)
    f2=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',38)
    ImageDraw.Draw(big).text((540,440),"EAT  •  LEVEL UP  •  REBIRTH",font=f2,fill=(255,255,255,235))
    return Image.composite(big,Image.new('RGBA',(W,H),(0,0,0,0)),m)

if __name__=='__main__':
    import os
    out='.'
    emblem(512).save(f'{out}/icon_512.png')
    emblem(128).save(f'{out}/icon_128.png')
    banner().save(f'{out}/banner.png')
    print('ok')
