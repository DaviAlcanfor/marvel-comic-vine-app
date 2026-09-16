# AGENTS.md

## Visão geral

App Android nativo para o "Marvel API Challenge": consome a [Comic Vine
API](https://comicvine.gamespot.com/api/) para listar e detalhar personagens, e listar
times do universo Marvel. Objetivo do desafio é o MVP com 4 telas (Login, Home, Detalhe
do Personagem, Times) seguindo um design system dark fornecido em
`docs/design-reference/`.

## Stack e decisões técnicas

- **Kotlin puro.** Sem Java.
- **Compose está PROIBIDO.** UI 100% em Views + XML layouts + **ViewBinding**
  (`buildFeatures.viewBinding = true` em `app/build.gradle.kts`). Nada de
  `findViewById` manual, nada de Data Binding.
- **Navigation Component** (Fragments + um único `nav_graph.xml`), single-Activity
  (`MainActivity` só hospeda o `NavHostFragment`).
- **MVVM** — detalhes em [ARCHITECTURE.md](ARCHITECTURE.md).
- **Coroutines/Flow** para assincronismo (`viewModelScope`, `StateFlow`).
- **Retrofit + OkHttp + Gson** para a API (já existia no scaffold inicial, ver
  `data/remote/ApiClient.kt`). **Não recriar essa camada** — estender.
- **Coil** para carregar imagens de personagens/times a partir de URL (única lib de
  imagem no projeto; não adicionar Glide/outra).
- **Sem DI framework.** Construtor com valor default (`ComicVineRepository()`,
  `ComicVineRepository = ComicVineRepository()` no service) faz o papel de injeção
  manual simples. Não trazer Hilt/Koin sem pedido explícito.
- **Sem Safe Args.** Argumentos de navegação são lidos via `Bundle`/`SavedStateHandle`
  (ver `CharacterDetailViewModel`) para não adicionar mais um plugin Gradle.

## Estrutura de pacotes

```
app/src/main/java/com/projeto/marvel/
  MainActivity.kt              // host do NavHostFragment, nada de lógica de tela
  data/
    remote/                    // DTOs (Gson) + ComicVineService (Retrofit) + ApiClient/ApiConstants
    ComicVineRepository.kt     // única porta de entrada de dados; ViewModels só falam com ela
  ui/
    <feature>/                 // um pacote por tela: login, home, detail, teams
      <Feature>Fragment.kt
      <Feature>ViewModel.kt    // ausente quando a tela não tem estado/lógica (ex.: login)
      <Feature>Adapter.kt      // quando a tela tem uma lista (ListAdapter + DiffUtil)
```

Regra: um arquivo novo de DTO/endpoint entra em `data/remote/`; lógica de
transformação/orquestração de dados entra em `data/ComicVineRepository.kt`; tudo que é
Android UI entra em `ui/<feature>/`.

## Convenções de nomenclatura

- ViewModel: `<Feature>ViewModel` (ex. `HomeViewModel`), estado em `<Feature>UiState`
  (sealed interface com `Loading` / `Success` / `Error`, ver ARCHITECTURE.md).
- Fragment: `<Feature>Fragment`.
- Adapter: `<Entidade>Adapter` (ex. `CharacterAdapter`, não `HomeAdapter`).
- Layout de tela: `fragment_<feature>.xml` (snake_case, prefixo pelo tipo de
  componente, convenção padrão Android).
- Layout de item de lista: `item_<entidade>.xml`.
- Drawables reutilizáveis (shapes de background): `bg_<uso>.xml` (ex. `bg_card.xml`).
- Ícones: `ic_<nome>.xml`.

## Code style

Segue as convenções oficiais:
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)

Aplicadas via ferramenta, não de memória:
- **ktlint** (`org.jlleitschuh.gradle.ktlint`), config em `.editorconfig` na raiz.
  Rodar antes de commitar:
  ```
  ./gradlew ktlintCheck   # só verifica
  ./gradlew ktlintFormat  # corrige automaticamente
  ```
- **detekt**, ruleset default (`buildUponDefaultConfig = true`). `config/detekt/detekt.yml`
  desativa só `ForbiddenComment` (o projeto usa `TODO` de propósito, ver seção de
  limitações abaixo). `app/detekt-baseline.xml` grandfathera achados pré-existentes do
  scaffold inicial (magic numbers em `ApiClient.kt`, estilo do `ExampleUnitTest.kt`
  gerado pelo template) — não regenerar a baseline para "esconder" um achado novo,
  ela é só para dívida que já existia antes deste setup.
  Rodar:
  ```
  ./gradlew detekt
  ```

## Design

- Referências visuais das 4 telas do MVP (SVG) e o design system base ficam em
  `docs/design-reference/` (fora de `res/`, não são assets do app).
- **Antes de criar uma tela nova**, abrir o SVG correspondente em
  `docs/design-reference/`. Se a tela não tiver um SVG dedicado, usar
  `Design_System.svg` (via os tokens já extraídos em `DESIGN_TOKENS.md`) para manter
  consistência visual.
- `docs/design-reference/DESIGN_TOKENS.md` resume paleta, raios de borda, espaçamento
  e tamanhos de componente extraídos dos SVGs — consultar isso em vez de reabrir os
  SVGs (que são grandes: alguns têm imagens embutidas em base64 e passam de 1MB).
  Os tokens já estão traduzidos para `res/values/colors.xml`, `dimens.xml` e
  `themes.xml`.
- O design é **dark-only** (os SVGs não têm variante light), por isso o app fixa
  `Theme.Material3.Dark.NoActionBar` em vez de `DayNight`.

## Limitações conhecidas (não mockar dado fake)

- A Comic Vine não tem filtro nativo de `publisher` nos endpoints `characters/` e
  `teams/` de forma simples/documentada — a lista pode incluir personagens/times de
  editoras além da Marvel. Marcado com `TODO` em
  `data/ComicVineRepository.kt`.
- Os chips "Heróis"/"Vilões" na Home são apenas visuais (a API não expõe essa
  classificação); só "Todos" reflete dados reais. Marcado com `TODO` em
  `ui/home/HomeFragment.kt`.

## Outras convenções

- Comentários em código: só quando o "porquê" não é óbvio (workaround de API,
  invariante não evidente). Não comentar o que o código já diz.
- Testes: `app/src/test/` com JUnit4 + `kotlinx-coroutines-test`; repository e
  ViewModels são testáveis por injeção de dependência via construtor (sem mockar
  Retrofit — implementar a interface do service diretamente, ver
  `ComicVineRepositoryTest.kt`).
- Ver [ARCHITECTURE.md](ARCHITECTURE.md) para como as camadas se encaixam.
