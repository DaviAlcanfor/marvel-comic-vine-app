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
  (`MainActivity` só hospeda o `NavHostFragment` e a barra inferior Início · Descobrir · Álbum ·
  Jogos · Batalha; a barra aceita no máximo 5 abas, então o Perfil abre pelo avatar da Início; Descobrir é um ViewPager2 com Personagens, Times, Criadores, Filmes e Lugares — as páginas
  navegam pelo **id do destino**, não por action, porque o destino atual é o Descobrir).
- **MVVM** — detalhes em [ARCHITECTURE.md](ARCHITECTURE.md).
- **Coroutines/Flow** para assincronismo (`viewModelScope`, `StateFlow`).
- **Retrofit + OkHttp + Gson** para a API (já existia no scaffold inicial, ver
  `data/remote/ApiClient.kt`). **Não recriar essa camada** — estender.
- **Coil** para carregar imagens de personagens/times a partir de URL (única lib de
  imagem no projeto; não adicionar Glide/outra).
- **Sem DI framework.** Construtor com valor default (`ComicVineRepository()`,
  `ComicVineRepository = ComicVineRepository()` no service) faz o papel de injeção
  manual simples. Não trazer Hilt/Koin sem pedido explícito.
- **Firebase Auth** (e-mail/senha e Google, este via Credential Manager) para o login, via `data/AuthRepository.kt`. O
  `google-services.json` não é versionado; sem ele o plugin não é aplicado e o app
  compila normalmente (o login só mostra "Firebase não configurado").
- **App Check** (aplicado no console para o AI Logic): `MarvelApp` instala o provedor de
  `src/debug` (token de depuração impresso no Logcat, cadastrar no console) ou `src/release`
  (Play Integrity, app registrado com o SHA-256).
- **IA (Gemini via Firebase AI Logic)**: o Geek (`data/GeekAgent.kt`) é um agente com function
  calling; cada chamada de repositório vira uma ferramenta em `data/GeekTools.kt` (nova chamada de
  API = nova ferramenta lá). "Com qual herói você parece?" (`data/LookAlike.kt`) manda a foto só na
  requisição. Modelo e tratamento de sobrecarga/cota em `data/Gemini.kt`.
- **Progressão** (`data/Progression.kt`): só luta quem você tem no álbum; nível = figurinhas
  repetidas (até 10); cada nível dá 8 pontos que o jogador distribui no painel da carta em 3D
  (`UpgradeStore`; os não distribuídos entram divididos por igual). Carta Divina (`golden` no código; antes "dourada"): sorte no pacote
  (`goldenChance`) ou evoluir juntando 5 figurinhas (gasta 4), +5 em tudo e entra na luta com meia
  ultimate (`GOLDEN_START_ENERGY`). Raridade: comum bronze (1★), rara prata (2★), lendária ouro (3★), estrelas coloridas via
  `rarityBadge()`; aura de partículas por raridade em `ui/album/AuraView.kt` (álbum e arena, onde segue o lutador). A CPU vem 2 níveis abaixo do seu time
  e o chefe da trilha no seu nível. A FAMA acelera a barra da ultimate (`BattleUltimate.kt`).
- **Jogos da aba Jogos** (grade de capas em `ui/games/`): Quem é esse herói? (`ui/guess`), Com qual
  herói você parece? (`ui/lookalike`), Que herói é você? (`ui/quiz`), Super Trunfo (`ui/trunfo`: seu
  baralho = figurinhas que você tem, com nível/pontos/Divina; regras em `TrunfoRules.kt`), Memória
  (`ui/memory`, `MemoryRules.kt`) e Quem disse? (`ui/quote`: resumo com o nome escondido, `maskNames`).
  O "Monte sua HQ" foi removido a pedido do usuário. Recordes em
  `data/GameRecords.kt`; cada jogo tem evento de missão e paga pacote.
- **Missões** (`data/Missions.kt`): 3 diárias + 2 semanais sorteadas pela data, pagam pacotes.
  Telas registram eventos com `context.mission(MissionEvent.X)`; evento novo = entra no enum, numa
  missão da tabela, no texto e na onomatopeia do quadro (`ui/home/MissionList.kt`: cada quadro tem a
  arte de um herói e a onomatopeia do tipo de missão, nada de ícone genérico). Abertura do app: tela de HQ animada (`ui/ComicLoading.kt`), a splash do sistema
  fica só com o fundo.
- **Extras fora dos jogos** (todos grátis):
  - Ouvir a bio no Detalhe: `ui/detail/BioActions.kt` (`TextToSpeech`). A tradução pelo Gemini foi
    removida a pedido do usuário (lenta demais para o ganho).
  - Herói do clima na Início: `data/Weather.kt` (Open-Meteo, sem chave; regra `weatherHero` testada) e
    `ui/home/WeatherCard.kt` (localização aproximada pedida só ao tocar; cidade pelo `Geocoder`).
  - Bancas (aba do Descobrir): `data/Releases.kt` — edições por `store_date` da semana e editora pelo
    volume (as edições não dizem a editora); "Lembrar" abre o Calendário por intent, sem permissão.
  - Maratona do MCU (aba Filmes): tabela curada em `data/Marathon.kt` (ids da Comic Vine + minutos,
    porque o `runtime` da API vem bagunçado); vistos ficam na estante de filmes.
  - Por onde começar a ler? (Detalhe e ferramenta `series_para_comecar` do Geek): `data/ReadingGuide.kt`,
    séries reais com o nome do herói e o Gemini só ordena/explica (`volume_credits` vem vazio na API).
  - Teia de conexões (Detalhe): física pura em `ui/detail/ForceLayout.kt` (testada) desenhada por
    `ConnectionsView`; aliados/inimigos só vêm no detalhe completo (`full = true`).
  - Papel de parede animado: `ui/widget/HeroWallpaperService.kt` (monta o tema da época na mão, porque
    o serviço não tem Activity); abre pelo diálogo de tema do Perfil.
  - Shazam de herói (câmera no cabeçalho do Descobrir): `data/HeroShazam.kt` (Gemini Vision; nunca
    identifica pessoa real) e `ui/discover/ShazamFlow.kt`; o 1º reconhecimento do dia dá a figurinha
    dele (se estiver no álbum) ou um pacote Básico.
- **Luta com o corpo e a voz** (`ui/battle/BattleSenses.kt`, regras puras em `BattleSensesRules.kt`,
  testadas em `BattleSensesTest`): locutor com frases fixas (`commentary`, sem gastar cota) pela voz
  do Android; sacudir carrega o próximo golpe (`shakeBoost` → `Combatant.charge`, soma só no acerto e
  é gasta no golpe); microfone usa o reconhecimento de voz do sistema (`RecognizerIntent`, sem
  permissão de áudio no app) e `moveForSpeech` acha o golpe pelo nome.
- **Sem Safe Args.** Argumentos de navegação são lidos via `Bundle`/`SavedStateHandle`
  (ver `CharacterDetailViewModel`) para não adicionar mais um plugin Gradle.

## Estrutura de pacotes

```
app/src/main/java/com/projeto/marvel/
  MainActivity.kt              // NavHostFragment + barra inferior, nada de lógica de tela
  data/
    remote/                    // DTOs (Gson) + ComicVineService/CatalogService (Retrofit) + ApiClient
    ComicVineRepository.kt     // personagens, times, HQs e batalha
    CatalogRepository.kt       // catálogo do Descobrir: criadores e filmes
    AuthRepository.kt, CharacterChat.kt, *Store.kt  // Firebase Auth, IA (Gemini) e SharedPreferences
  ui/
    <feature>/                 // um pacote por tela: login, home, discover, characters, detail,
                               // teams, creators, movies, locations, info, battle, chat, comics,
                               // profile, quiz, photo, guess, album, compare,
                               // widget (widget e notificação "Herói do dia")
      <Feature>Fragment.kt
      <Feature>ViewModel.kt    // ausente quando a tela não tem estado/lógica
      <Feature>Adapter.kt      // quando a tela tem uma lista (ListAdapter + DiffUtil)
```

Regra: um arquivo novo de DTO/endpoint entra em `data/remote/`; lógica de
transformação/orquestração de dados entra num repositório em `data/` (ViewModels só falam com
repositórios, nunca com Retrofit). `ComicVineRepository` e `CatalogRepository` foram separados
por domínio para cada um caber no limite de funções do detekt — não afrouxar o detekt; se um
repositório crescer demais, separar de novo. Tudo que é Android UI entra em `ui/<feature>/`.

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
- O app roda sempre no modo noturno (`MarvelApp`): os tokens base estão em `values-night/colors.xml`
  e a época escolhida no Perfil (`data/ThemeStore.kt`) entra por cima. `values/colors.xml` (o antigo
  Retrô claro) só serve ao widget e aos tokens que não mudam. O Retrô claro foi removido a pedido do
  usuário ("tudo muito branco").
  **Épocas** (`ThemeMode`: Retrô = escuro + `ThemeOverlay.Marvel.RetroDark`, papel sépia com retícula
  visível, padrão; Anos 90 = escuro + `Nineties`; Moderno = escuro puro). No Retrô, balões e legendas
  vizinhos alternam as tintas de gráfica `retroInk()` (`ui/ComicBox.kt`) e há balão de pensamento
  (`BoxStyle.THOUGHT`); nada de tudo branco. Diálogos (`comicDialog()`) herdam título em legenda, corpo
  e itens de escolha de `ThemeOverlay.Marvel.Dialog`
  — **Design System v2 em [`docs/design-reference/DESIGN_SYSTEM_V2.md`](docs/design-reference/DESIGN_SYSTEM_V2.md)**:
  tokens `era*`, peças (`EraPanelDrawable`, `LogoTextView`, `comicBox`) e regras para tela nova.
  Cor nova: token nos dois arquivos, nunca hex no layout. Dourado como texto é `accent_text`
  (o `accent` não dá leitura no papel claro); texto em botão vermelho é sempre `white`.

## Limitações conhecidas (não mockar dado fake)

- A Comic Vine não tem filtro nativo de `publisher` nos endpoints `characters/` e
  `teams/` de forma simples/documentada — a lista pode incluir personagens/times de
  editoras além da Marvel. Marcado com `TODO` em
  `data/ComicVineRepository.kt`.
- A Comic Vine não tem atributos numéricos de combate nem tipos de poder. A Batalha
  (`ui/battle/`) usa atributos e golpes **derivados** de dados reais — nomes dos
  `powers` (palavra-chave → atributo / tipo de golpe) e `count_of_issue_appearances`
  (FAMA) — em `toFighter()` no `data/ComicVineRepository.kt`. Regras de dano/turno
  ficam em `ui/battle/BattleRules.kt` (funções puras, testadas em `BattleRulesTest`); o
  equilíbrio entre perfis é conferido por simulação em `BattleBalanceTest`.
- A Comic Vine não tem herói/vilão: o filtro da lista de personagens é por **origem**
  (`origins/`). Vídeos (`videos/`) estão mortos (MP4 em host desativado, YouTube 403/404) e o
  `release_date` de filmes é a data de cadastro — nenhum dos dois é usado.
- Lugares: a Comic Vine não tem coordenadas nem diz o que é real; quais lugares são reais (e a
  busca do mapa) é marcação nossa em `CatalogRepository.PLACES`. Mapa = intent `geo:` para o app
  de mapas (sem SDK nem chave).
- 3D: sem biblioteca 3D (SceneView traz o Compose, proibido). A arena é OpenGL ES 2.0 puro
  (`ui/battle/ArenaRenderer.kt`); os lutadores continuam 2D (não há modelos 3D oficiais).
  O troféu da vitória é uma `TextureView` com EGL próprio (`TrophyView`), não `GLSurfaceView`:
  a SurfaceView abria um "buraco" no painel por cima da arena.
- Conquistas (`data/Achievements.kt`) são regras puras sobre placar + estantes + contadores
  próprios (`AchievementStore`); nomes/ícones em `ui/profile/AchievementStyle.kt`.
- Batalha 2 jogadores (arg `pvp`): `Side.CPU` vira o P2; o VM guarda o golpe do P1 até o P2
  escolher e passa os dois para `playTurn(..., cpuChoice)`. Não conta no placar nem em conquistas.
- Batalha 3×3 (arg `playerUrls`, separadas por vírgula): bancos em `BattleUiState.Success`; troca
  no ViewModel com `Outcome.SWAP_IN` e `playTurn(playerMove = null)` quando o jogador gasta a vez.
  As regras 1×1 não mudam.
- Linha do tempo do Detalhe (`data/Timeline.kt`): `story_arc_credits` vem sempre vazio na API e
  a lista de inimigos é poluída; os marcos são estreia, estreia dos maiores times dele (não há
  data de entrada no time) e mortes, datados pela `cover_date` das edições.
- Cache HTTP: a Comic Vine não manda cabeçalho de cache; `ApiClient` guarda 6 h e, sem rede,
  serve o que tiver (até 7 dias). Ligado em `MarvelApp` (`ApiClient.init`).
- Álbum: pacotes Básico (grátis por dia), Prata (vitória) e Ouro (fechar trilha / vencer 3×3),
  com pesos e garantia em `PackType` (`data/Stickers.kt`); número da figurinha = posição fixa no
  álbum (lendárias primeiro, depois por nome). Busca, atalho de filtro (raridade, 2×/3×, pode virar
  Divina…) e ordem da grade são puros em `ui/album/AlbumFilter.kt` (`AlbumFilterTest`); o número não
  muda ao filtrar.
  Abertura do pacote: `PackMotion` (girar; a faixa de cima descola 1:1 com o dedo via `peel`, com
  borda `RipDrawable`, volta com mola ou termina no fling) e depois `PackReveal` (RIIIP!, cartas
  sobem da boca do pacote e se espalham, suspense por raridade, NOVA!/×N; um toque pula).
  Troca: `TRADE_COST` repetidas (nunca a última cópia, de quem tem mais: `tradePicks`) viram 1 Prata.
- Antes de cada luta, tela de VS com os atributos lado a lado (`ui/battle/BattleVersus.kt`, reusa
  as linhas do Comparar em `ui/compare/CompareRow.kt`).
- Herói do dia: `heroOfTheDay()` em `ui/home/Debut.kt` é a fonte única (Início, widget, notificação).

## Outras convenções

- Comentários em código: só quando o "porquê" não é óbvio (workaround de API,
  invariante não evidente). Não comentar o que o código já diz.
- Testes: `app/src/test/` com JUnit4 + `kotlinx-coroutines-test`; repository e
  ViewModels são testáveis por injeção de dependência via construtor (sem mockar
  Retrofit — implementar a interface do service diretamente, ver
  `ComicVineRepositoryTest.kt`).
- Ver [ARCHITECTURE.md](ARCHITECTURE.md) para como as camadas se encaixam.
