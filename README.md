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
- **Gelatinas de Treino** (`reap:train_starter`, `train_r1`, `train_r3`, `train_r5`, `train_r6`): ficam paradas no lobby sobre uma placa,
  são **inquebráveis** e nunca somem. Enquanto as colheres encostam, o jogador ganha **XP** para subir de nível e chegar ao Rebirth mais rápido.
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
- Cor da colher/rastro muda a cada rebirth.

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
