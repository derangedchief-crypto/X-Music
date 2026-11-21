# X-Music artwork

`xmusic-logo.svg` is the vector master. `xmusic-logo.png` is a 512-pixel preview.
`scripts/render_xmusic_logo.py` renders the matching Android and shared raster
assets with Pillow. The app's XML vectors use the same paths.

`album-artwork-sources.json` records the album names, artists, artwork URLs and
album-page URLs used for the activation background. Artwork was retrieved from
Apple's album metadata on 2026-11-21 at 400 × 400 pixels. The bundled WebP arranges
the twelve covers in alternating tilted columns. Album artwork belongs to its
respective rights holders; it is not original X-Music artwork.
