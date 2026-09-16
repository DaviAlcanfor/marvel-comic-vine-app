# Marvel App

App Android nativo (Kotlin) para o desafio "Marvel API Challenge": lista e detalha
personagens e times do universo Marvel usando a [Comic Vine API](https://comicvine.gamespot.com/api/).

## Telas

- **Login** — sem autenticação real, só navega para a Home.
- **Home** — busca e lista personagens (grid), com chips de filtro (heróis/vilões
  são apenas visuais — ver limitações abaixo).
- **Detalhe do Personagem** — foto, nome, editora e descrição.
- **Times** — lista de times/equipes.

## Stack

Kotlin · Views + ViewBinding (sem Compose) · Navigation Component · MVVM ·
Coroutines/Flow · Retrofit + OkHttp + Gson · Coil.

Detalhes de arquitetura e convenções: [ARCHITECTURE.md](ARCHITECTURE.md) e
[AGENTS.md](AGENTS.md).

## Como rodar

1. Pegue uma API key gratuita em https://comicvine.gamespot.com/api/.
2. Crie/edite `local.properties` na raiz do projeto (não é versionado) e adicione:
   ```
   COMIC_VINE_API_KEY=sua_chave_aqui
   ```
3. Abra o projeto no Android Studio e rode o módulo `app` (minSdk 33).

## Qualidade

```
./gradlew testDebugUnitTest   # testes unitários
./gradlew ktlintCheck         # estilo de código
./gradlew detekt              # code smells
```

## Limitações conhecidas

A Comic Vine não tem filtro nativo por editora nos endpoints usados, então as
listas de personagens/times podem incluir itens de fora da Marvel — ver os `TODO`
em `ComicVineRepository.kt`.
