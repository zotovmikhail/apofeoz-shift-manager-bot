# Theme Tokens Mapping

Сопоставление ключевых визуальных токенов дизайна с текущей Android-реализацией.

## Current UI Direction

Зафиксированный основной стиль проекта:

- `Dark Graphite` background system
- `Gold Accent` for primary actions and signal highlights
- выраженные панели, бордеры и dashboard-композиция
- контрастная, уверенная визуальная иерархия вместо нейтрального CRUD-вида

Этот стиль уже реализован в `web/` и должен быть целевым направлением для следующего обновления Android UI.

| Design token | Значение | Android source |
|---|---|---|
| Primary | `#D4AF37` | `ApofeozColors.Primary` |
| Background | `#0D0D0D` | `ApofeozColors.Background` |
| Surface/Card | `#171717` | `ApofeozColors.Surface` |
| Border/Outline | `#27272A` | `ApofeozColors.Outline` / `SurfaceVariant` |
| Error | `#DC2626` | `ApofeozColors.Error` |
| Heading font | `Space Grotesk` | `ApofeozFontSpaceGrotesk` |
| Body font | `Inter` | `ApofeozFontInter` |

Основной код темы:

- `android/app/src/main/java/com/apofeoz/shiftmanager/presentation/theme/ApofeozColorScheme.kt`
- `android/app/src/main/java/com/apofeoz/shiftmanager/presentation/theme/ApofeozTypography.kt`
- `android/app/src/main/java/com/apofeoz/shiftmanager/presentation/theme/ApofeozTheme.kt`

## Android Redesign Note

Текущая Android-тема уже близка по палитре, но ещё не полностью совпадает по продуктовой подаче с web-консолью.

При следующем редизайне Android нужно подтянуть:

- более сильную иерархию заголовков и панелей;
- более уверенные card/container patterns;
- более системные статусные pill-компоненты;
- унификацию login/admin/report visual language с web.
