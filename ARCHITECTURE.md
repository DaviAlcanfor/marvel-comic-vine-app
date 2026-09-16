# ARCHITECTURE.md

Referência técnica de como as camadas se encaixam. Convenções de projeto (naming,
estrutura de pacotes, code style, design) estão em [AGENTS.md](AGENTS.md).

## Camadas

```
┌─────────────┐     eventos de usuário      ┌──────────────┐
│    View     │ ──────────────────────────▶ │  ViewModel   │
│ (Fragment)  │ ◀────────────────────────── │              │
└─────────────┘   StateFlow<UiState>         └──────┬───────┘
                                                     │ chama
                                                     ▼
                                              ┌──────────────┐
                                              │  Repository  │
                                              └──────┬───────┘
                                                     │ chama
                                                     ▼
                                              ┌──────────────┐
                                              │   Retrofit   │
                                              │   (service)  │
                                              └──────────────┘
```

**Regra de dependência:** a seta só aponta para baixo. `data/` nunca importa nada de
`ui/`. Uma Fragment nunca instancia `ComicVineService`/`ApiClient` diretamente — só
fala com sua ViewModel; a ViewModel só fala com `ComicVineRepository`.

## Estado de UI

Cada feature declara seu próprio `sealed interface <Feature>UiState` com três casos:

```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val characters: List<CharacterSummary>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

A ViewModel expõe isso como `StateFlow<HomeUiState>` (nunca `MutableStateFlow`
publicamente). A View observa em `onViewCreated`, dentro de
`viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(STARTED)` — nunca em
`lifecycleScope` da própria Fragment/Activity, para não coletar enquanto a view não
existe:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect(::render)
    }
}
```

Telas sem estado/lógica de negócio (ex.: `LoginFragment`) não têm ViewModel — não criar
um `ViewModel` vazio só para seguir o padrão.

## Erro e loading

- O Repository nunca lança exceção "solta": todo método retorna `Result<T>`
  (`runCatching { ... }`), inclusive erros de negócio da Comic Vine (que respondem
  HTTP 200 com `error` != "OK" no corpo — o Repository converte isso em falha via
  `check(...)`).
- A ViewModel consome o `Result` com `.fold(onSuccess, onFailure)` e mapeia para
  `UiState.Success`/`UiState.Error`. Nunca deixa uma exceção subir até a View.
- A View só faz `when (state)` e mostra/esconde `ProgressBar` e um `TextView` de erro
  (com clique para retry, quando a tela tem um `retry()`/`load()` na ViewModel). Não há
  um componente de erro compartilhado — cada tela tem seus próprios ids
  (`progressBar`, `errorText`) porque as 3 telas com estado (`Home`, `Detalhe`,
  `Times`) são poucas e simples o bastante para não justificar extrair um
  `<include>` genérico ainda.

## Threading / coroutines

- Toda chamada assíncrona roda em `viewModelScope.launch { ... }` — cancelada
  automaticamente quando a ViewModel é destruída.
- Retrofit com `suspend fun` já roda em thread de I/O por conta do próprio Retrofit
  (dispatcher do OkHttp); não é preciso `withContext(Dispatchers.IO)` manual nas
  chamadas de `ComicVineService`.
- Nenhum uso de `GlobalScope` ou threads manuais.

## Navegação

- Um único `nav_graph.xml` (`res/navigation/nav_graph.xml`), `MainActivity` só
  hospeda o `NavHostFragment` (`activity_main.xml`).
- Sem Safe Args (evita mais um plugin Gradle para 1 argumento). Argumentos declarados
  no grafo (`<argument>`) são lidos via `Bundle` normal na Fragment
  (`arguments?.getString(...)`) ou via `SavedStateHandle` na ViewModel
  (`checkNotNull(savedStateHandle["apiDetailUrl"])`) — o Fragment KTX injeta os
  argumentos da Fragment no `SavedStateHandle` automaticamente.
- Back stack padrão do Navigation Component; `Login → Home` usa
  `popUpTo`/`popUpToInclusive` para tirar o Login da pilha (não faz sentido voltar
  para o login com o botão de voltar do sistema).

## Estrutura de pastas — exemplo completo (feature Home)

```
app/src/main/java/com/projeto/marvel/
  ui/home/
    HomeFragment.kt          // View: observa state, dispara search()/retry(), navega
    HomeViewModel.kt         // HomeUiState + StateFlow, chama ComicVineRepository
    CharacterAdapter.kt      // ListAdapter<CharacterSummary, ViewHolder> + DiffUtil
  data/
    ComicVineRepository.kt   // searchCharacters(query, offset): Result<List<CharacterSummary>>
    remote/
      ComicVineService.kt    // GET characters/
      CharacterDto.kt        // CharacterListResponse, CharacterSummary, ComicVineImage, Publisher

app/src/main/res/
  layout/
    fragment_home.xml        // título, busca, chips de filtro, RecyclerView, loading/erro
    item_character.xml       // card usado pelo CharacterAdapter
  navigation/
    nav_graph.xml            // destino homeFragment + actions para detail/teams
```

O mesmo padrão se repete para `detail` e `teams`; `login` só tem `LoginFragment` +
`fragment_login.xml` (sem ViewModel/Adapter, por não ter estado nem lista).
