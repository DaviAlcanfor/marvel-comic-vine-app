# Design Tokens

Extraídos de `Design_System.svg`, `Login.svg`, `Home - Lista de Personagens.svg`,
`Detalhe do Personagem.svg` e `Times.svg` (mockups em 375×812, dark theme único —
não há variante light nos SVGs).

Os SVGs vetorizam todo texto em `<path>` (sem `<text>`), então tipografia exata
(família/peso) não é extraível dos arquivos; os tamanhos abaixo são estimados pela
altura dos glifos. Cores e raios de borda vêm direto dos atributos `fill`/`rx`.

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
| `accent`           | `#E6B800` | Destaques/badges (dourado)                    |
| `team_blue`        | `#1565C0` | Badge de time (ex: Avengers)                  |
| `team_green`       | `#2E7D32` | Badge de time (ex: X-Men)                     |
| `scrim`            | `#0A0A14` | Overlay/estado mais escuro                    |

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
| Campo de busca / input       | 40dp altura, `radius_large` |
| Tab/pill de filtro           | 31dp altura, `radius_pill` |
| Avatar circular (lista/detalhe) | 56dp diâmetro (r=28) |
| Avatar circular (hero, Detalhe) | 100dp diâmetro (r=50) |
| Thumbnail quadrado (card personagem) | 40dp, `radius_small` |
| Card de personagem/time       | altura ~121dp, `radius_large` |

## Tipografia (estimada)

Sem acesso à família/peso reais (texto vetorizado). Usar a escala tipográfica
padrão do Material3 como base, mapeada pela altura relativa dos glifos nos SVGs:

| Papel        | sp aproximado | Peso    |
|--------------|----------------|---------|
| Título de tela | 20–22        | Bold    |
| Título de card | 16           | SemiBold/Bold |
| Corpo         | 14            | Regular |
| Legenda/label | 12            | Regular, cor `text_secondary` |

Se a fonte real do design system for confirmada depois (Figma/arquivo de fonte),
atualizar esta tabela e os estilos em `res/values/styles.xml`.
