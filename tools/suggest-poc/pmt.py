"""The tile reader moved to tools/suggest_tiles.py: the map pipeline's suggest-file step (tools/build_suggest.py) reads
the same tiles. Importing this also puts tools/ on the path for the POC's modules (suggest_file, build_suggest)."""
import os
import sys

_TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _TOOLS not in sys.path:
    sys.path.append(_TOOLS)  # at the end: the POC's own modules (cells.py …) keep their names

from suggest_tiles import PMTiles, layers, tile_id, tiles_of_cell  # noqa: E402,F401
