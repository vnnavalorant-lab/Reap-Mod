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
- 9 tipos, cada um mais demorado (e mais valioso) que o anterior. O poder das colheres sobe com nível e rebirths,
  então os tipos mais duros vão ficando viáveis conforme você evolui:

  | Bloco | Cor | HP | Moedas/XP base |
  |---|---|---|---|
  | `reap:goo` | Rosa | 6 | 1 |
  | `reap:goo_red` | Vermelha | 14 | 3 |
  | `reap:goo_orange` | Laranja | 32 | 7 |
  | `reap:goo_yellow` | Amarela | 70 | 16 |
  | `reap:goo_green` | Verde | 150 | 36 |
  | `reap:goo_cyan` | Ciano | 320 | 80 |
  | `reap:goo_blue` | Azul | 680 | 180 |
  | `reap:goo_purple` | Roxa | 1400 | 400 |
  | `reap:goo_black` | Negra | 2900 | 900 |

- **Blocos de Retorno** (aba Blocos de Construção, `reap:return_<wins>`): plataforma fina quadriculada. Ao pisar nela você
  ganha os **wins** (contador de troféu no HUD) e volta para o spawn inicial do mundo. 8 variações: +1, +2, +3, +5, +8, +12, +25 e +50 wins.
  - Plataformas **encostadas se conectam** (sem borda entre elas) e funcionam como **um bloco só**: um único letreiro e uma
    única pontuação, que pertencem ao bloco do canto do grupo (menor X, depois menor Z). Os wins **não** são somados.
  - Cada plataforma/grupo mostra o letreiro flutuante "Return +N WINS".
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

## Gerar as texturas/modelos das gelatinas
Texturas animadas (brilho ondulante, bolhas, núcleo colorido), os 8 modelos de "mordida", blockstates e lang são gerados por script:

    pip install pillow numpy
    python3 tools/gen_assets.py

Para mudar cores ou nomes edite `TIERS` em `tools/gen_assets.py` (e `TYPES` em `ReapMod.java`, os ids precisam bater) e rode de novo.
