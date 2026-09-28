# Marvel App

App Android nativo (Kotlin) para o desafio "Marvel API Challenge": lista e detalha
personagens e times do universo Marvel usando a [Comic Vine API](https://comicvine.gamespot.com/api/).

## Telas

- **Login** — Firebase Auth com e-mail e senha (entrar ou criar conta) ou Google; a sessão fica
  salva e o botão "Sair" na Home desloga.
- **Home** — busca e lista personagens em grade. Navegação por barra inferior:
  Início · Times · Batalha.
- **Detalhe do Personagem** — o card clicado se expande até virar a tela: foto, nome,
  nome real · origem · editora, números (aparições, times, poderes) e seções Sobre,
  Poderes e Times.
- **Times** — lista de times/equipes.
- **Batalha** — escolha seu lutador e o adversário (ou aleatório) e lute por turnos:
  golpes de soco, rajada, veneno, cura, defesa e esquiva, derivados dos poderes reais do
  personagem na Comic Vine. O placar (vitórias/derrotas) fica salvo no aparelho
  (SharedPreferences).

## Visual

Segue o design system de `docs/design-reference/` (paleta escura, Bebas Neue + Inter) com
uma camada de **tema de HQ** por cima:

- animações "em twos" (~12 quadros/s, estilo flipbook/stop motion) em todo o app;
- na Batalha, onomatopeias ("POW!", "BAM!", "K.O.!") em explosões serrilhadas que
  "fervem", quadro de impacto e tremor de tela;
- fundo com retícula, painéis com contorno de nanquim, botões com sombra dura e títulos
  de seção em caixa de legenda amarela.

As animações respeitam a opção do sistema de remover animações.

## Stack

Kotlin · Views + ViewBinding (sem Compose) · Navigation Component · MVVM ·
Coroutines/Flow · Retrofit + OkHttp + Gson · Coil · Firebase Auth (+ Credential Manager
para o login com Google) · SharedPreferences.

Detalhes de arquitetura e convenções: [ARCHITECTURE.md](ARCHITECTURE.md) e
[AGENTS.md](AGENTS.md).

## Como rodar

1. Pegue uma API key gratuita em https://comicvine.gamespot.com/api/.
2. Crie/edite `local.properties` na raiz do projeto (não é versionado) e adicione:
   ```
   COMIC_VINE_API_KEY=sua_chave_aqui
   ```
3. Firebase (login):
   1. No [console do Firebase](https://console.firebase.google.com), crie um projeto e
      adicione um app Android com o pacote `com.projeto.marvel`.
   2. Baixe o `google-services.json` e coloque em `app/` (não é versionado).
   3. Em **Authentication → Sign-in method**, ative **E-mail/senha** e **Google**.
   4. Login com Google: rode `./gradlew signingReport`, copie o **SHA1** da variante
      `debug` e cadastre em Configurações do projeto → seu app Android → Adicionar
      impressão digital. Depois baixe o `google-services.json` de novo (o SHA-1 é por
      máquina: cada computador que rodar o app precisa cadastrar o seu).
4. Abra o projeto no Android Studio e rode o módulo `app` (minSdk 33).

## Qualidade

```
./gradlew testDebugUnitTest   # testes unitários
./gradlew ktlintCheck         # estilo de código
./gradlew detekt              # code smells
```

## Limitações conhecidas

- A Comic Vine não tem filtro nativo por editora nos endpoints usados, então as
  listas de personagens/times podem incluir itens de fora da Marvel — ver os `TODO`
  em `ComicVineRepository.kt`.
- A API não tem atributos de combate nem tipos de poder: os atributos e golpes da
  Batalha são **derivados** dos nomes dos poderes e do número de aparições (regras em
  `toFighter()` e `ui/battle/BattleRules.kt`), não inventados.
- A Comic Vine limita as requisições (~200/hora por recurso); cada batalha usa 2–3.
- O placar da Batalha é por aparelho, não por usuário logado.
