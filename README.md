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
  "crafter_bloqueia_itens_de_mod": true,
  "mensagem_bloqueio": "Você não tem permissão pra craftar itens de {mod}!",
  "jogadores": {
    "Kaua":    ["create*"],
    "Player2": ["mekanism*"]
  }
}
```
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
- Craft 2x2 do inventário e mesa de craft (inclusive via livro de receitas)
- Mesa de ferraria e cortador de pedras
- Crafter (autocraft vanilla): não tem jogador, então só faz itens de `sempre_liberados`
  (desligue com `"crafter_bloqueia_itens_de_mod": false`)

**Não cobre (ainda):** mesas e autocraft próprios de outros mods (AE2, Refined Storage,
Mechanical Crafter do Create, etc.). Use `bloqueados_para_todos` para fechar brechas específicas.

## Desenvolvimento
A logo é gerada por `python art/make_logo.py` (precisa do Pillow).

```
./gradlew build   # compila e roda os testes (sobem um servidor Minecraft de verdade)
```
O jar sai em `build/libs/`. Para publicar uma versão, crie uma tag `v*` (ex.: `git tag v0.1.0 && git push --tags`) e o GitHub Actions anexa o jar na Release.

## Contribuindo
Issues e pull requests são bem-vindos, principalmente para cobrir mesas de craft de outros mods (AE2, Refined Storage, Create...).

## Licença
[MIT](LICENSE)
