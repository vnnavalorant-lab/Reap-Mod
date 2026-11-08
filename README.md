<p align="center"><img src="logo.png" alt="Jelly Gobbler" width="640"></p>

# Jelly Gobbler (Fabric 1.20.1)

Mod inspirado no jogo do vídeo: colheres giram em volta do jogador como hélice, cortam gosma rosa,
dão moedas/XP com multiplicador, sobem de nível (nova colher a cada 3 rebirths) e fazem rebirth.

## Compilar
Requer JDK 17. Use o wrapper de qualquer projeto Fabric (ou instale o Gradle 8.6+):

    gradle build            # jar em build/libs/jelly-gobbler-1.1.0.jar
    gradle runClient        # testar no jogo

> O id interno do mod continua `reap` (blocos `reap:goo`, comandos `/reap`...), assim mundos e mapas antigos não quebram. Só o nome exibido e o jar mudaram.

(O `gradle-wrapper.jar` não vai junto; copie a pasta `gradle/` + `gradlew` do template https://github.com/FabricMC/fabric-example-mod
 ou rode `gradle wrapper --gradle-version 8.6` uma vez.)

## GitHub Actions
O workflow `.github/workflows/build.yml` compila a cada push (ou manualmente em *Actions > build > Run workflow*) e publica o jar como artefato `jelly-gobbler`.

## Jogar
- **Gelatinas** (aba Blocos de Construção): construa paredes. As colheres **devoram a gelatina aos poucos**: cada encostada
  tira um pedaço (8 estágios visuais), ela solta respingos e, quando acaba, dá moedas/XP com multiplicador e volta após ~10 s.
  Se você parar de comer, ela se recupera sozinha.
- 9 tipos, cada um mais demorado (e mais valioso) que o anterior. Cada fase é bem mais dura que a anterior (cerca de 3x o HP) e se cura mais devagar quando você para de comer. O poder das colheres sobe com nível e rebirths,
  então os tipos mais duros vão ficando viáveis conforme você evolui:

  | Bloco | Cor | HP | Moedas/XP base |
  |---|---|---|---|
  | `reap:goo` | Rosa | 20 | 1 |
  | `reap:goo_red` | Vermelha | 70 | 4 |
  | `reap:goo_orange` | Laranja | 220 | 12 |
  | `reap:goo_yellow` | Amarela | 650 | 36 |
  | `reap:goo_green` | Verde | 1900 | 105 |
  | `reap:goo_cyan` | Ciano | 5500 | 310 |
  | `reap:goo_blue` | Azul | 16000 | 900 |
  | `reap:goo_purple` | Roxa | 48000 | 2600 |
  | `reap:goo_black` | Negra | 140000 | 7500 |

- **Blocos de Retorno** (aba Blocos de Construção, `reap:return_<wins>`): plataforma fina quadriculada. Ao pisar nela você
  ganha os **wins** (contador de troféu no HUD) e volta para o spawn inicial do mundo. 9 variações, uma por fase, **com a mesma cor da gelatina da fase**: rosa +1, vermelha +3, laranja +8, amarela +20, verde +50, ciano +120, azul +300, roxa +750, negra +900 wins.
  - Plataformas **encostadas se conectam** (sem borda entre elas) e funcionam como **um bloco só**: um único letreiro e uma
    única pontuação, que pertencem ao bloco do canto do grupo (menor X, depois menor Z). Os wins **não** são somados.
  - Cada plataforma/grupo mostra o letreiro flutuante "Return +N WINS".
- **Loja de Reaps** (`reap:shop_1` .. `reap:shop_10`, aba Blocos de Construção): plataforma vermelha com uma colher giratória na cor da Reap e o letreiro
  "N Wins / xV Speed / Buy". Ao **pisar** nela o jogador gasta os wins e **equipa aquela Reap**: colher e rastro mudam de cor e a
  **velocidade de destruição** (`ReapData.bitePower()`) é multiplicada. As compras são **em ordem**: só dá para comprar a Reap seguinte à atual. A plataforma de uma Reap já comprada fica **azul** (visual por jogador) e sem texto; só a plataforma da Reap seguinte mostra o custo, quanto de velocidade ela adiciona e Buy / Need N more (as mais distantes ficam sem texto). Para tentar de novo é preciso sair da plataforma e pisar outra vez. A tabela está em `Reaps.java`:

  | Bloco | Reap | Custo | Velocidade |
  |---|---|---|---|
  | (inicial) | Branca | 0 | x1 |
  | `shop_1` | Verde | 3 | x1.3 |
  | `shop_2` | Pêssego | 8 | x1.7 |
  | `shop_3` | Roxa | 18 | x2.2 |
  | `shop_4` | Prata | 40 | x2.8 |
  | `shop_5` | Rosa | 90 | x3.5 |
  | `shop_6` | Amarela | 200 | x4.4 |
  | `shop_7` | Laranja | 420 | x5.5 |
  | `shop_8` | Ciano | 850 | x7 |
  | `shop_9` | Vermelha | 1700 | x9 |
  | `shop_10` | Arco-íris | **3200** | x12 |

  **Rebirth zera a Reap** do jogador (volta para a branca, junto com os wins) e a progressão da loja recomeça. `/reap setreap <0-10>` (OP) para testar. `/reap resetshop [jogadores]` (OP) reseta a loja (volta para a primeira Reap; wins não são devolvidos).
  Para mudar preços/velocidades/cores edite `Reaps.TIERS` (e `SHOP` em `tools/gen_assets.py` se mudar nomes/custos dos blocos).
- **Gelatinas de Treino** (`reap:train_starter`, `train_r1`, `train_r3`, `train_r5`, `train_r6`): **grandes como no jogo original**
  (gelatina de ~2 blocos de largura e 1,8 de altura sobre uma placa ~3,3x3,3). Ao colocar o bloco ele ocupa uma área livre de **2x2x2**
  (o bloco clicado é o canto da base, e a estrutura se estende para +X, +Z e para cima; precisa de espaço). São **inquebráveis** (só no criativo) e nunca são devoradas, balançam como
  gelatina e balançam mais quando as colheres encostam. Enquanto as colheres encostam, o jogador ganha **XP** para subir de nível e chegar ao Rebirth mais rápido.
  Cada uma tem um multiplicador de XP e exige rebirths (senão aparece "Requer Rebirth N"). O texto sobre cada uma mostra o nome
  (verde, ou vermelho se ainda bloqueada) e o multiplicador:

  | Gelatina | Rebirths | XP |
  |---|---|---|
  | Starter (ciano) | 0 | x1 |
  | Rebirth 1 (verde) | 1 | x1.4 |
  | Rebirth 3 (amarela) | 3 | x1.9 |
  | Rebirth 5 (rosa) | 5 | x2.5 |
  | Rebirth 6 (gelo) | 6 | x3.5 |

  XP por contato de colher = `(1 + nível/3) * multiplicador` (`TRAIN_XP_BASE` / `TRAIN_XP_PER_LEVEL` em `ReapMod.java`).
- Colheres também ferem mobs/jogadores (PvP respeita server.properties).
- Comandos: `/reap` (status), `/reap toggle`, `/reap rebirth` (nível 50), `/reap setlevel <n>` e `/reap setrebirths <n>` e `/reap setwins <n>` (OP).
- **Força** (o contador de moedas no HUD) acelera a colher: bônus de velocidade de destruição = `1 + sqrt(moedas) / 60` (`ReapData.COIN_DIV`). `/reap resetstrength [jogadores]` (OP) zera a Força.
- Cor da colher/rastro vem da Reap equipada na loja (volta à branca no rebirth).

## Balanceamento (v1.1.0): ~1 hora de jogo
Meta: uma sessão de ~1 hora com progresso constante, sem dar para "zerar" rápido. Antes desta versão a simulação mostrava
o 1º rebirth em ~7 min, **todas** as Reaps compradas em ~7 min e ~19 rebirths em 1 hora (um único retorno roxo, +750 wins,
comprava a loja inteira, que custava só 263 wins). O que mudou:

| Item | Antes | Agora |
|---|---|---|
| HP das gelatinas | 14 ... 70000 | 20 ... 140000 (~3x por fase) |
| Custo total da loja | 263 wins | 6.529 wins (última Reap: 3200) |
| Velocidade da última Reap | x25 | x12 |
| Bônus de poder por nível | +25% | +10% |
| XP mínimo por nível | 25 | 40 (curva 5·nível² igual) |
| Dano em mobs | 3 + 0,2·nível | 3 + 0,1·nível |
| Divisor da Força (moedas) | 25 | 60 |
| Força por rebirth | +25% | +15% |
| Multiplicador de recompensa | rebirths + 2 | 2 + rebirths/2 |
| Colher nova | a cada 2 rebirths | a cada 3 rebirths |
| Nível p/ rebirth | 50 + 5·r/3 | 50 + 4·r |
| Gelatina de treino r6 | x9 XP | x3.5 XP (demais também reduzidas) |

Ritmo estimado (simulação em `tools/balance_sim.py`, perfis médio / rápido / casual):

| Marco | Médio | Rápido | Casual |
|---|---|---|---|
| Reap 4 (Prata) | ~12 min | ~12 min | ~16 min |
| Reap 8 (Ciano) | ~35 min | ~29 min | ~46 min |
| 1º Rebirth | ~35 min | ~28 min | ~46 min |
| 2º Rebirth | ~68 min | ~56 min | ~88 min |
| Reaps 9 e 10 (Vermelha e Arco-íris) | só depois do 1º rebirth | idem | idem |

Ou seja: em 1 hora dá para chegar ao 1º rebirth (o 2º no perfil rápido), e as duas últimas Reaps ficam como meta do
segundo ciclo. É uma **estimativa de modelo**, não medição no jogo: o ritmo real depende do layout do seu mapa
(tamanho das paredes de gelatina, distância dos retornos). Se ficar rápido/lento demais, os botões principais são
`ReapData.LEVEL_POWER`, `ReapData.COIN_DIV`, `Reaps.TIERS` (custos) e o HP em `ReapMod.TYPES`.

## Ajustes rápidos
- `ReapData`: curva de XP (`xpNeeded`), multiplicador, `SPOON_EVERY`, `LEVEL_POWER`.
- `SpoonLogic`: velocidade de giro, raio, comprimento.
- `ReapMod`: dano, tabela `TYPES` (HP, recompensa e cor de cada gelatina), tempo de volta (`GOO_REGEN_TICKS`), recuperação (`HEAL_PER_TICK`), nível do rebirth.
- `ReapData.bitePower()`: quanto HP cada colher tira por contato (sobe com nível/rebirths).
- `client/Fx`: tudo dos efeitos (vida do rastro `TRAIL_NS`, gotas, anéis).
- Texturas dos efeitos: `assets/reap/textures/fx/*.png` (troque por arte sua à vontade).

## Animação de destruição da gelatina
Ao devorar uma gelatina: ela se parte em **pedaços macios** (tamanhos variados) que saem pelos lados abertos, esticam no ar e balançam;
os maiores soltam **fios de gelatina** que afinam e arrebentam. Ao cair, o pedaço **espirra, quica uma vez** e vira uma **poça oval brilhante**
(com gotinhas ao redor); se bate na parede, **gruda e escorre** derretendo. Tudo na cor da gelatina e escurecendo em lugares sem luz.
Cada mordida solta pedacinhos menores. Os parâmetros (quantidade, tamanhos, gravidade, quique) estão em `client/Fx.java`
(`spawnChunks`, `simulateChunks`). As texturas `jblob*`/`puddle*` são geradas por `tools/gen_assets.py` (`gen_fx`).

## Gerar as texturas/modelos das gelatinas
Texturas animadas (brilho ondulante, bolhas, núcleo colorido), os 8 modelos de "mordida", blockstates e lang são gerados por script:

    pip install pillow numpy
    python3 tools/gen_assets.py

Para mudar cores ou nomes edite `TIERS` em `tools/gen_assets.py` (e `TYPES` em `ReapMod.java`, os ids precisam bater) e rode de novo.

## Rebirth (GUI)
- Abra com a tecla **R** (configurável em Controles > Reap) ou `/reap rebirth`. Tela igual ao jogo (ícone HD em `assets/reap/textures/gui/rebirth.png`, textos com contorno, botão **x** minúsculo): "You get: +X% -> +Y%", "You need: Level N / M", botões **Rebirth**, **Skip** (só operadores) e **X**.
- Máximo de **100** rebirths. Nível exigido = `50 + 4*rebirths` (50 no primeiro, 74 no 7º, 446 no último).
- Cada rebirth dá **+15% de força** (`ReapData.STRENGTH_PCT_PER_REBIRTH`), que multiplica o poder das colheres e o dano nos mobs.
- Renascer zera nível, XP, moedas (força), wins e a Reap da loja (as colheres ganhas por rebirth ficam). Além disso, o **multiplicador** de recompensas é `2 + rebirths/2`. Saves antigos (formato < 3) recomeçam do nível 1 / rebirth 0.

## Logo
`logo.png` (banner), `icon_512.png` e `assets/reap/icon.png` (128x128, usado pelo Mod Menu). Gerados por `python3 tools/make_logo.py` (Pillow + numpy).
