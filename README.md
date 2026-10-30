# Alatyr project site

This branch holds the static site published with GitHub Pages at <https://l2go.github.io/alatyr/>. It shares no history with `main`, and nothing in it is built: the pages are plain HTML, CSS, and a small script, served as they are.

- `index.html`: the project page.
- `notes/<slug>/index.html`: the engineering notes, one directory per note. The first series, "Reviving L2JFree", is complete in eight parts; a later series adds its notes beside them.
- `assets/`: styles, script, the Cinzel typeface (SIL Open Font License, see `assets/fonts/OFL.txt`), and images cut from the banner art.

Every page sends a strict Content Security Policy: no inline script or style and nothing from another origin.

Lineage II and NCSOFT are trademarks or registered trademarks of NCSOFT Corporation. Alatyr, formerly L2JFree, is an independent, non-commercial open-source project, not affiliated with, endorsed, or sponsored by NCSOFT.
