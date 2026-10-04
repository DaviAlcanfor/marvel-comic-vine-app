# Design System v2 — Épocas dos quadrinhos

Aprovado a partir do canvas "Marvel App — Épocas dos quadrinhos" (proposta com Início, Álbum e
kit de peças das três épocas), feito sobre as referências de HQ em `comics_reference.zip`. Substitui o visual único do v1
(`DESIGN_TOKENS.md` continua valendo para espaçamentos e tamanhos).

O usuário escolhe a época no Perfil (`ThemeMode`): Retrô, **Retrô escuro**, Anos 90 ou Moderno;
"seguir o sistema" = Retrô de dia, Retrô escuro à noite. O Retrô escuro é o Retrô inteiro (formas,
letras, legendas amarelas) sobre papel envelhecido escuro `#1C1812`, com nanquim claro `#EFE3C6` nos
contornos (`ThemeOverlay.Marvel.RetroDark`); as figurinhas continuam papel branco com nome em nanquim. Ao entrar numa tela, a época tem de ser reconhecível só pelo traço.

| | Retrô (1962–1985) | Anos 90 / 2000 | Moderno (2010–hoje) |
|---|---|---|---|
| Como aplica | tema claro (`values/`) | escuro + `ThemeOverlay.Marvel.Nineties` | tema escuro (`values-night/`) |
| Fundo | papel jornal `#F2E3BD` + retícula Ben-Day vermelha | degradê `#1B1036` → `#0B2A3A` | grafite com luz de cima (`bg_era_screen` night) |
| Títulos (`LogoTextView`) | Bangers amarelo, contorno vermelho, sombra nanquim, -2° | Bungee amarelo, extrusão 3D azul, inclinado | Bebas Neue branco com traço vermelho em cima |
| Texto | Comic Neue (letra de balão) | Barlow Condensed | Inter |
| Botões / rótulos | Bangers | Barlow Condensed Bold Italic | Bebas Neue |
| Onomatopeias | Bangers | Bungee | Bebas Neue |
| Contorno (`eraOutline`) | nanquim `#141010`, 2,5dp | neon ciano `#00E5FF`, 1,5dp | grafite `#3A3A46`, 1dp |
| Sombra dura | 4dp | — | — |
| Cantos | retos | chanfrados (12dp) | arredondados (10dp quadros, 6dp botões) |
| Primária | vermelho `#C0101A` | magenta `#D81B60` | vermelho Marvel `#D81F26` |
| Legenda (`CaptionBox`) | caixa amarela "Enquanto isso…" | etiqueta magenta inclinada | rótulo vermelho |
| Balão de fala | oval branco de nanquim | caixa chanfrada lilás `#F4F0FF` | — (títulos de seção com traço vermelho) |
| Quadro (`bg_card`) | nanquim + sombra dura | moldura neon degradê ciano → magenta → amarelo | fosco, traço fino |
| Figurinha | margem branca, número em placa preta/amarela | borda foil (arco-íris) | moldura fosca arredondada |
| Chips | retos, marcado amarelo | canto cortado, contorno ciano, marcado amarelo | pílula, marcado branco |
| Barra inferior | creme, fio de 3dp, ativo (ícone + nome) em placa amarela torta | escura, fio ciano, ícones ciano, ativo amarelo | quase preta, ativo vermelho |
| Ícones da barra | contorno (`nav_*.xml`), iguais nas três | ← | ← |
| Legendas / rótulos | Comic Neue negrito itálico 14sp | Bungee 13sp | Bebas 18sp |
| Pacotes (cabeçalho do Álbum) | blocos na cor do pacote com nanquim | metal chanfrado | painel com faixa colorida no topo |
| Fundo extra | retícula em 2 cores (vermelho + azul deslocado) | faixas diagonais (`StreaksDrawable`) | — |
| Movimento (`staggerIn` / `eraEnter`) | quadros estouram no lugar com leve giro | entram deslizando de lado com tranco | sobem suave com fade |

## Onde está no código

- **Tokens:** `res/values/attrs_era.xml` declara os atributos `era*`. Os valores ficam em:
  - `themes.xml` (base e `ThemeOverlay.Marvel.Nineties`);
  - `colors.xml` (`era_*` e `nineties_*`), `dimens.xml` e `integers.xml` (`era_style`), cada um com sua versão em `values-night/`;
  - `font/` e `font-night/` (`era_title`, `era_body`, `era_display`, `era_sfx`).
- **Cores que mudam por época** (`background`, `surface`, `primary`…): `res/color/<token>.xml` aponta para `?attr/era*`. Por isso as referências `@color/x` existentes já seguem a época.
- **Peças:**
  - `EraPanelDrawable` (quadro, botão, secundário, legenda, campo, barra) é usado direto no XML (`bg_card`, `bg_button_comic_*`, `bg_caption`, `bg_input`, `bg_bottom_nav`).
  - `LogoTextView` cuida dos títulos de tela.
  - `ComicBox.comicBox()` faz balão, legenda e explosão no traço da época.
  - `Context.era()` serve para o que muda de layout (ex.: `ui/home/HomeEra.kt`).
- **Fora do tema** (widget e splash do sistema): usar `era_*` direto, nunca `?attr`.

## Regras para tela nova

1. Título da tela com `com.projeto.marvel.ui.LogoTextView` + `@style/Widget.Marvel.ScreenTitle`.
2. Fundos e bordas com `bg_card`, `bg_input`, `bg_caption`, `Widget.Marvel.ComicButton`. Nada de
   `GradientDrawable` com raio/traço fixos: se precisar em código, `EraPanelDrawable(context, kind)`.
3. Fonte só por atributo (`?attr/eraDisplayFont`, `?attr/eraSfxFont`, `?attr/eraTitleFont`); o
   texto comum herda `era_body` do tema.
4. Cor nova: token nos dois `colors.xml`. Se mudar nos Anos 90, um `nineties_*` + atributo `era*`.
5. Diferença de layout entre épocas (posição, peça extra): uma função `applyEra()` no pacote da
   tela, chamada uma vez em `onViewCreated`.
6. Entrada da tela com `eraEnter()` no container (ou `staggerIn(views)`): o movimento já sai da época.
7. Conferir as três épocas no aparelho antes de commitar (Perfil → Época), lado a lado com as
   pranchas do canvas quando a tela estiver nele.
