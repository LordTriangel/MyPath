# docs/team/items – detail of backlog items
One file per backlog item whose full text does not fit its line in `docs/team/backlog.md`: `<yyyy-mm-dd>-<slug>.md`, starting
with `# <title>`. The backlog line ends with ` → [detail](items/<file>.md)`.
The gate caps `docs/team/backlog.md` (cap in `tools/dev/backlog_slim.py`, target 20 KB): delete role lines every tag has read
and items that are done (line and file together), then run `tools/dev/backlog_slim.py`.
To add detail: write the file here and end the backlog line with the link; a long line is moved here by the script.
