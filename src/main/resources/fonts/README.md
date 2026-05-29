# Bundled Fonts

This directory contains open-source fonts bundled with the API so HTML-to-PDF and HTML-to-image rendering can work without system font dependencies.

Each font family lives in its own folder with:

- `LICENSE.txt`
- `SOURCE.md`
- upstream metadata when available, such as `METADATA.pb` or `FONTLOG.txt`
- the bundled `.ttf` files

The runtime registry is `fonts.json`. It is intentionally explicit so font loading works from the packaged application and Docker image without classpath directory scanning.

## Families

- Fira Code
- Inter
- JetBrains Mono
- Lato
- Liberation Mono
- Liberation Sans
- Liberation Serif
- Merriweather
- Montserrat
- Noto Sans
- Noto Sans Mono
- Noto Serif
- Nunito
- Open Sans
- Oswald
- Playfair Display
- Poppins
- Raleway
- Roboto
- Rubik

## License Notes

All currently bundled families are distributed under the SIL Open Font License 1.1. Keep each family license with the family folder when adding, updating, or removing fonts.
