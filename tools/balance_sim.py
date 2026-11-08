import math, sys

def run(cfg, minutes=60, verbose=True, tmax=150, K=24, H_WALL=1.0, H_TRAIN=0.4, WALK=25):
    HP = cfg['hp']; RW = cfg['reward']; PADW = cfg['padwins']
    reaps = cfg['reaps']            # list of (cost, speed)
    TRAIN = cfg['train']            # (req, mult)
    lvl=1; xp=0.0; coins=0.0; wins=0.0; tier=0; reb=0
    t=0.0; log=[]; maxreap_t={}; tl={}
    def xpneed(L): return cfg['xpneed'](L)
    def spoons(): return min(12, 1+reb//cfg.get('spoon_every',2))
    def mult(): return cfg['multf'](reb)
    def power():
        return (1+cfg['lvl_pow']*(lvl-1))*(1+cfg['reb_pct']/100*reb)*reaps[tier][1]*(1+math.sqrt(coins)/cfg['coin_div'])
    def gain_xp(a):
        nonlocal xp,lvl
        xp+=a
        while xp>=xpneed(lvl):
            xp-=xpneed(lvl); lvl+=1
    end=minutes*60
    while t<end:
        n=spoons(); pw=power()
        # opções
        best=None
        # treino
        trm=max([m for (r,m) in TRAIN if reb>=r])
        tr_rate=(cfg['train_base']+lvl*cfg['train_per_lvl'])*trm*H_TRAIN*n*20
        best=('train',tr_rate,0)
        for p in range(len(HP)-1,-1,-1):
            dmg=pw*n*H_WALL*20
            T=K*HP[p]/dmg+WALK
            if T<=tmax or p==0:
                rate=K*RW[p]*mult()/T
                # só vale se mais XP que treino (ou se quer wins)
                if rate>tr_rate*cfg.get('farm_bias',1.0) or tier<len(reaps)-1:
                    best=('farm',rate,p,T)
                break
        if best[0]=='train':
            dt=10; gain_xp(best[1]*dt); t+=dt
        else:
            p=best[2]; T=best[3]
            gain=K*RW[p]*mult()
            coins+=gain; gain_xp(gain); wins+=PADW[p]; t+=T
        tl[int(t//300)*5]=(lvl,tier,reb,best[0],(best[2] if best[0]=='farm' else None),round(pw,1))
        # compras
        while tier<len(reaps)-1 and wins>=reaps[tier+1][0]:
            wins-=reaps[tier+1][0]; tier+=1
            maxreap_t.setdefault((reb,tier),t/60)
        # rebirth
        if reb<100 and lvl>=cfg['rebreq'](reb):
            log.append((round(t/60,1),reb+1,lvl,tier))
            reb+=1; lvl=1; xp=0; coins=0; wins=0; tier=0
    return dict(tl=tl,level=lvl,coins=coins,tier=tier,reb=reb,rebirths=log,reapt=maxreap_t,power=power())

OLD=dict(multf=lambda r:r+2, spoon_every=2,
 hp=[14,50,150,420,1100,3000,8500,24000,70000],
 reward=[1,4,12,35,100,300,900,2700,8000],
 padwins=[1,3,8,20,50,120,300,750,900],
 reaps=[(0,1),(1,1.5),(2,2),(4,2.75),(7,3.75),(12,5),(20,7),(32,9.5),(45,13),(60,18),(80,25)],
 train=[(0,1),(1,1.5),(3,2),(5,3),(6,9)],
 xpneed=lambda L:max(25,5*L*L), lvl_pow=0.25, reb_pct=25, coin_div=25,
 train_base=1, train_per_lvl=1/3,
 rebreq=lambda r:50+(5*r)//3)

if __name__=='__main__':
    r=run(OLD,60)
    print("OLD 60min:", r['level'], 'tier',r['tier'],'reb',r['reb'])
    print("rebirths (min, n, lvl, tier):", r['rebirths'])
    print("reap purchase times (reb,tier)->min:", {k:round(v,1) for k,v in r['reapt'].items() if k[0]==0})


# Balanceamento v1.1.0 (os mesmos números de ReapMod / Reaps / ReapData)
NEW = dict(
    hp=[20, 70, 220, 650, 1900, 5500, 16000, 48000, 140000],
    reward=[1, 4, 12, 36, 105, 310, 900, 2600, 7500],
    padwins=[1, 3, 8, 20, 50, 120, 300, 750, 900],
    reaps=[(0, 1), (3, 1.3), (8, 1.7), (18, 2.2), (40, 2.8), (90, 3.5), (200, 4.4), (420, 5.5), (850, 7.0), (1700, 9.0), (3200, 12.0)],
    train=[(0, 1), (1, 1.4), (3, 1.9), (5, 2.5), (6, 3.5)],
    xpneed=lambda L: max(40, 5 * L * L), lvl_pow=0.10, reb_pct=15, coin_div=60,
    train_base=1, train_per_lvl=1 / 3, multf=lambda r: 2 + r // 2, spoon_every=3,
    rebreq=lambda r: 50 + 4 * r)

if __name__ == '__main__':
    for nome, cfg in (("ANTES", OLD), ("AGORA", NEW)):
        r = run(cfg, 60)
        print(nome, "- em 60 min: rebirths =", r['reb'], "| Reap =", r['tier'], "| marcos:", [a for a, _, _, _ in r['rebirths'][:4]])
