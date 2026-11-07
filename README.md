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
- Bloco **Pink Goo** (aba Blocos de Construção): construa paredes. Colheres quebram, dão +multiplicador de moedas e XP, e a gosma volta após 10 s.
- Colheres também ferem mobs/jogadores (PvP respeita server.properties).
- Comandos: `/reap` (status), `/reap toggle`, `/reap rebirth` (nível 50), `/reap setlevel <n>` e `/reap setrebirths <n>` (OP).
- Cor da colher/rastro muda a cada rebirth.

## Ajustes rápidos
- `ReapData`: curva de XP (`xpNeeded`), multiplicador, quantidade de colheres.
- `SpoonLogic`: velocidade de giro, raio, comprimento.
- `ReapMod`: dano, regeneração da gosma (`GOO_REGEN_TICKS`), nível do rebirth.
- `client/Fx`: tudo dos efeitos (vida do rastro `TRAIL_NS`, gotas, anéis).
- Texturas dos efeitos: `assets/reap/textures/fx/*.png` (troque por arte sua à vontade).
