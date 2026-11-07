# Reap – Spoons (Fabric 1.20.1)

Mod inspirado no jogo do vídeo: colheres giram em volta do jogador como hélice, cortam gosma rosa,
dão moedas/XP com multiplicador, sobem de nível (nova colher a cada 10 níveis) e fazem rebirth.

## Compilar
Requer JDK 17. Use o wrapper de qualquer projeto Fabric (ou instale o Gradle 8.6+):

    gradle build            # jar em build/libs/reap-1.0.0.jar
    gradle runClient        # testar no jogo

(O `gradle-wrapper.jar` não vai junto; copie a pasta `gradle/` + `gradlew` do template https://github.com/FabricMC/fabric-example-mod
 ou rode `gradle wrapper --gradle-version 8.6` uma vez.)

## GitHub Actions
O workflow `.github/workflows/build.yml` compila a cada push (ou manualmente em *Actions > build > Run workflow*) e publica o jar como artefato `reap-mod`.

## Jogar
- **Gelatinas** (aba Blocos de Construção): construa paredes. As colheres **devoram a gelatina aos poucos**: cada encostada
  tira um pedaço (8 estágios visuais), ela solta respingos e, quando acaba, dá moedas/XP com multiplicador e volta após ~10 s.
  Se você parar de comer, ela se recupera sozinha.
- 9 tipos, cada um mais demorado (e mais valioso) que o anterior. Cada fase é bem mais dura que a anterior (cerca de 2,5x o HP) e se cura mais devagar quando você para de comer. O poder das colheres sobe com nível e rebirths,
  então os tipos mais duros vão ficando viáveis conforme você evolui:

  | Bloco | Cor | HP | Moedas/XP base |
  |---|---|---|---|
  | `reap:goo` | Rosa | 14 | 1 |
  | `reap:goo_red` | Vermelha | 50 | 4 |
  | `reap:goo_orange` | Laranja | 150 | 12 |
  | `reap:goo_yellow` | Amarela | 420 | 35 |
  | `reap:goo_green` | Verde | 1100 | 100 |
  | `reap:goo_cyan` | Ciano | 3000 | 300 |
  | `reap:goo_blue` | Azul | 8500 | 900 |
  | `reap:goo_purple` | Roxa | 24000 | 2700 |
  | `reap:goo_black` | Negra | 70000 | 8000 |

- **Blocos de Retorno** (aba Blocos de Construção, `reap:return_<wins>`): plataforma fina quadriculada. Ao pisar nela você
  ganha os **wins** (contador de troféu no HUD) e volta para o spawn inicial do mundo. 9 variações, uma por fase, **com a mesma cor da gelatina da fase**: rosa +1, vermelha +3, laranja +8, amarela +20, verde +50, ciano +120, azul +300, roxa +750, negra +900 wins.
  - Plataformas **encostadas se conectam** (sem borda entre elas) e funcionam como **um bloco só**: um único letreiro e uma
    única pontuação, que pertencem ao bloco do canto do grupo (menor X, depois menor Z). Os wins **não** são somados.
  - Cada plataforma/grupo mostra o letreiro flutuante "Return +N WINS".
- **Loja de Reaps** (`reap:shop_1` .. `reap:shop_10`, aba Blocos de Construção): plataforma vermelha com uma colher giratória na cor da Reap e o letreiro
  "N Wins / xV Speed / Buy". Ao **pisar** nela o jogador gasta os wins e **equipa aquela Reap**: colher e rastro mudam de cor e a
  **velocidade de destruição** (`ReapData.bitePower()`) é multiplicada. Só compra Reap melhor que a atual (pode pular níveis); o letreiro mostra
  Buy / Need N more / Owned / Equipped. Para tentar de novo é preciso sair da plataforma e pisar outra vez. A tabela está em `Reaps.java`:

  | Bloco | Reap | Custo | Velocidade |
  |---|---|---|---|
  | (inicial) | Branca | 0 | x1 |
  | `shop_1` | Verde | 1 | x1.5 |
  | `shop_2` | Pêssego | 2 | x2 |
  | `shop_3` | Roxa | 4 | x2.75 |
  | `shop_4` | Prata | 7 | x3.75 |
  | `shop_5` | Rosa | 12 | x5 |
  | `shop_6` | Amarela | 20 | x7 |
  | `shop_7` | Laranja | 32 | x9.5 |
  | `shop_8` | Ciano | 45 | x13 |
  | `shop_9` | Vermelha | 60 | x18 |
  | `shop_10` | Arco-íris | **80** | x25 |

  **Rebirth zera a Reap** do jogador (volta para a branca, junto com os wins) e a progressão da loja recomeça. `/reap setreap <0-10>` (OP) para testar.
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
  | Rebirth 1 (verde) | 1 | x1.5 |
  | Rebirth 3 (amarela) | 3 | x2 |
  | Rebirth 5 (rosa) | 5 | x3 |
  | Rebirth 6 (gelo) | 6 | x9 |

  XP por contato de colher = `(1 + nível/3) * multiplicador` (`TRAIN_XP_BASE` / `TRAIN_XP_PER_LEVEL` em `ReapMod.java`).
- Colheres também ferem mobs/jogadores (PvP respeita server.properties).
- Comandos: `/reap` (status), `/reap toggle`, `/reap rebirth` (nível 50), `/reap setlevel <n>` e `/reap setrebirths <n>` e `/reap setwins <n>` (OP).
- Cor da colher/rastro vem da Reap equipada na loja (volta à branca no rebirth).

## Ajustes rápidos
- `ReapData`: curva de XP (`xpNeeded`), multiplicador, quantidade de colheres.
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
- Máximo de **100** rebirths. Nível exigido = `50 + 5*rebirths/3` (50 no primeiro, 70 no 13º, 215 no último).
- Cada rebirth dá **+25% de força** (`ReapData.STRENGTH_PCT_PER_REBIRTH`), que multiplica o poder das colheres e o dano nos mobs.
- Renascer zera nível, XP, moedas (força), wins, colheres e a Reap da loja. Saves antigos (formato < 2) recomeçam do nível 1 / rebirth 0.
