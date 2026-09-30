# Design Tokens

Extraídos de `Design_System.svg`, `Login.svg`, `Home - Lista de Personagens.svg`,
`Detalhe do Personagem.svg` e `Times.svg` (mockups em 375×812, dark theme único —
não há variante light nos SVGs).

Cores e raios de borda vêm direto dos atributos `fill`/`rx`. A tipografia está escrita
no próprio `Design_System.svg` (seção "Tipografia", visível ao renderizar o arquivo).

## Cores

| Token              | Hex       | Uso                                          |
|--------------------|-----------|-----------------------------------------------|
| `background`       | `#121223` | Fundo de tela                                 |
| `surface`          | `#1A1A2E` | Cards (personagem, time)                      |
| `surface_variant`  | `#22223A` | Inputs, tabs/pills inativas                   |
| `border`           | `#33334D` | Stroke de inputs e cards                      |
| `text_primary`     | `#F5F5F7` | Títulos, nomes                                |
| `text_secondary`   | `#A0A0B8` | Corpo de texto, labels, placeholders          |
| `primary`          | `#C0101A` | Marca (Marvel red), botões, tab ativa         |
| `primary_variant`  | `#8C0C13` | Estado pressed/dark do primary                |
| `primary_text`     | `#FF4D55` | Vermelho para **texto** (erros, título do login): `primary` sobre o fundo dá 2,9:1 e reprova WCAG; este dá 5,7:1. Não está nos SVGs. |
| `accent`           | `#E6B800` | Destaques/badges (dourado)                    |
| `team_blue`        | `#1565C0` | Badge de time (ex: Avengers)                  |
| `team_green`       | `#2E7D32` | Badge de time (ex: X-Men)                     |
| `scrim`            | `#0A0A14` | Overlay/estado mais escuro                    |
| `ink`              | `#000000` | Tema HQ: contornos de nanquim e sombras duras (não está nos SVGs) |
| `poison`           | `#8E44AD` | Tema HQ: veneno na Batalha, distinto da cura verde (não está nos SVGs) |

### Tema claro ("papel de gibi")

Não está nos SVGs (que só têm o escuro, acima). Valores em `res/values/colors.xml`; o escuro em
`res/values-night/colors.xml`. `ink`, `paper`, `primary`, `accent`, cartas e pacotes não mudam.

| Token             | Claro       | Escuro      | Uso                                           |
|-------------------|-------------|-------------|------------------------------------------------|
| `background`      | `#FFF4DC`   | `#121223`   | Fundo de tela (papel creme + retícula)         |
| `surface`         | `#FFFFFF`   | `#1A1A2E`   | Cards                                          |
| `surface_variant` | `#F3E7C9`   | `#22223A`   | Inputs, trilhos de barra                       |
| `text_primary`    | `#1A1523`   | `#F5F5F7`   | Títulos, nomes                                 |
| `text_secondary`  | `#5B546A`   | `#A0A0B8`   | Corpo, rótulos                                 |
| `primary_text`    | `#B00E17`   | `#FF4D55`   | Vermelho como texto                            |
| `accent_text`     | `#8A6206`   | `#E6B800`   | Dourado como texto (o `accent` fica para fundos) |
| `overlay`         | `#F2FFF4DC` | `#F20B0B10` | Telas por cima de outras (VS, pacote)          |
| `halftone`        | `#000000`   | `#FFFFFF`   | Pontos da retícula                             |
| `move_*`          | versões escuras | claras  | Cor de cada golpe; 4,5:1 sobre `surface` nos dois |

## Raio de borda

Valores observados variam ligeiramente (9.25–16) por serem mockups desenhados à
mão; consolidados em uma escala de 3 níveis:

| Token          | dp | Uso                                    |
|----------------|----|------------------------------------------|
| `radius_small` | 10 | Avatar/ícone quadrado, badges pequenos   |
| `radius_large` | 16 | Cards, inputs, pills de filtro           |
| `radius_pill`  | 50%| Botões/tabs totalmente arredondados (altura ÷ 2) |

## Espaçamento

Grade observada em múltiplos de 8dp:

| Token          | dp | Uso                                    |
|----------------|----|------------------------------------------|
| `space_xs`     | 4  | Espaço entre ícone e label              |
| `space_sm`     | 8  | Espaço entre elementos próximos          |
| `space_md`     | 16 | Padding interno de card, gap entre cards |
| `space_lg`     | 24 | Margem lateral de tela                   |
| `space_xl`     | 32 | Separação entre seções                   |

## Tamanhos de componente

| Componente                  | Tamanho        |
|------------------------------|----------------|
| Campo de busca / input / botão | 48dp altura, `radius_large` (SVG usa 40dp; subido para o alvo de toque mínimo de 48dp) |
| Tab/pill de filtro           | 31dp altura, `radius_pill` |
| Avatar circular (lista/detalhe) | 56dp diâmetro (r=28) |
| Avatar circular (hero, Detalhe) | 100dp diâmetro (r=50) |
| Thumbnail quadrado (card personagem) | 40dp, `radius_small` |
| Card de personagem/time       | altura ~121dp, `radius_large` |

## Tipografia

Do `Design_System.svg`. Fontes em `res/font/` (OFL): `bebas_neue.ttf` e família `inter`.
Inter é a fonte padrão do tema; os papéis abaixo são estilos em `res/values/styles.xml`.

| Papel    | Fonte / tamanho            | Estilo                      | Uso                                  |
|----------|----------------------------|------------------------------|---------------------------------------|
| Display  | Bebas Neue 48 (telas: 32)   | `Widget.Marvel.ScreenTitle`  | Títulos de tela ("MARVEL HEROES")     |
| H1       | Inter Bold 28              | `Widget.Marvel.H1`           | Nome no Detalhe                       |
| H2       | Inter SemiBold 20          | `Widget.Marvel.H2`           | Títulos de seção ("Sobre")            |
| Body     | Inter Regular 15           | `Widget.Marvel.Body`         | Texto corrido                         |
| Caption  | Inter Medium 12, maiúsculas | `Widget.Marvel.Caption`      | Rótulos ("HUMANO · MARVEL")           |
| Botão    | Inter SemiBold, maiúsculas  | `TextAppearance.Marvel.Button` (tema) | "ENTRAR", "CRIAR CONTA"        |

## Tema HQ (camada sobre o Design System)

Não está nos SVGs; acrescentado para dar identidade de quadrinhos.

| Token / recurso   | Valor            | Uso                                             |
|-------------------|------------------|--------------------------------------------------|
| `ink_width`       | 2dp              | Contorno de nanquim (painéis, botões, legendas)  |
| `hard_shadow`     | 4dp              | Sombra dura deslocada, sem desfoque              |
| `COMIC_FPS`       | 24               | Animação em degraus, só na Batalha (`ui/Animations.kt`) |
| Bangers (OFL)     | `res/font/bangers.ttf` | Só onomatopeias ("POW!", "K.O.!")          |
