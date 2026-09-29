<img src="art/logo.png" width="128" align="right" alt="Logo do Mod Patents">

# Mod Patents (NeoForge 1.21.1)

[![Build](https://github.com/Rk7gamerYT/modpatents/actions/workflows/build.yml/badge.svg)](https://github.com/Rk7gamerYT/modpatents/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Bloqueia crafts de itens de mods por jogador. Só precisa estar no **servidor**.
Feito para séries de Minecraft em que cada jogador tem a **patente** de alguns mods: só quem tem a patente pode fabricar os itens daquele mod.

> 🇺🇸 *Server-side NeoForge 1.21.1 mod that restricts crafting of modded items per player ("patents"), driven by a JSON whitelist.*

**Download:** veja a página de [Releases](https://github.com/Rk7gamerYT/modpatents/releases).

## Instalação
1. Coloque `modpatents-<versão>.jar` na pasta `mods/` do servidor.
2. Abra o servidor uma vez. Ele cria:
   - `config/modpatents.json`: as regras
   - `config/modpatents_mods_disponiveis.txt`: todos os ids de mod com itens (atualizado a cada abertura)
3. Edite o JSON e use `/patents recarregar` (ou reinicie).

## modpatents.json
```json
{
  "sempre_liberados": ["minecraft", "#c:ingots"],
  "bloqueados_para_todos": [],
  "ignoram_bloqueio": ["NomeDoAdmin"],
  "autocraft_bloqueia_itens_de_mod": true,
  "mensagem_bloqueio": "Você não tem permissão pra craftar itens de {mod}!",
  "requisitos_proficiência": {
    "create:mechanical_press": {"crafting": 2},
    "create:mechanical_mixer": {"crafting": 2}
  },
  "mensagem_proficiência": "Você precisa de proficiência ({requisitos}) para craftar {item}.",
  "jogadores": {
    "Kaua":    ["create*"],
    "Player2": ["mekanism*"]
  }
}
```
Os requisitos PMMO são opcionais e só são verificados depois que a patente permite o craft manual. Use ids de item, padrões ou tags. O número é o nível mínimo; deixe níveis baixos (por exemplo, 2–4) para que a patente continue sendo a parte principal da progressão. O bypass administrativo em `ignoram_bloqueio` ignora os dois bloqueios.
Cada entrada pode ser:
| Entrada | Significa |
|---|---|
| `create` | todos os itens do mod `create` |
| `mekanism*` | todo mod cujo id começa com `mekanism` |
| `create:wrench` | um item só (`create:*_casing` também vale) |
| `#c:ingots` | todos os itens de uma tag |

Regras, em ordem: `ignoram_bloqueio` → `bloqueados_para_todos` → `sempre_liberados` → lista do jogador → **bloqueado**.
Jogadores podem ser listados por nome (sem diferenciar maiúsculas) ou UUID.
O que conta é o **item que sai do craft**, não o nome da receita.

## Comandos (op nível 2)
`/patentes` funciona igual a `/patents`.

- `/patents recarregar`
- `/patents lista`
- `/patents liberar <jogador> <entrada>`
- `/patents remover <jogador> <entrada>`
- `/patents testar <jogador>`: diz se o jogador pode craftar o item na sua mão

## O que é bloqueado
**Craft de jogador** (usa as patentes de cada um):
- Craft 2x2 do inventário e mesa de craft (inclusive via livro de receitas)
- Mesa de ferraria e cortador de pedras

**Qualquer outro craft** (só faz itens de `sempre_liberados`):
- Crafter do vanilla
- Autocraft de outros mods: AE2, Refined Storage, Mechanical Crafter do Create, Mekanism, Energized Power...
- Grades de craft de outros mods (terminal do AE2, grid do Refined Storage, upgrade de craft das mochilas...)

Ou seja: **itens de mod só podem ser feitos pelo dono da patente, na bancada ou no inventário.**
Desligue com `"autocraft_bloqueia_itens_de_mod": false` (o nome antigo `crafter_bloqueia_itens_de_mod` ainda funciona).

### Create
O Create também transforma receitas de craft em receitas de Mixer e Prensa, e isso não passa pela bancada.
Para fechar essa brecha, no `serverconfig/create-server.toml` do mundo, seção `[recipes]`:
```toml
allowShapelessInMixer = false
allowShapedSquareInPress = false
```

## Desenvolvimento
### Auditoria com o seu modpack
Copie os `.jar` do seu modpack para `build/minecraft-junit/mods/` e rode `./gradlew test`.
O `PackAuditTest` sobe um servidor com esses mods, monta **toda** receita de craft como se fosse
um autocrafter e lista as que escapam do bloqueio, agrupadas pela classe da receita.
Mods com receitas próprias entram em `ModRecipeResultMixin`. Sem mods na pasta, a auditoria é pulada.

A logo é gerada por `python art/make_logo.py` (precisa do Pillow).

```
./gradlew build   # compila e roda os testes (sobem um servidor Minecraft de verdade)
```
O jar sai em `build/libs/`. Para publicar uma versão, crie uma tag `v*` (ex.: `git tag v0.1.0 && git push --tags`) e o GitHub Actions anexa o jar na Release.

## Contribuindo
Issues e pull requests são bem-vindos, principalmente para cobrir mesas de craft de outros mods (AE2, Refined Storage, Create...).

## Licença
[MIT](LICENSE)
