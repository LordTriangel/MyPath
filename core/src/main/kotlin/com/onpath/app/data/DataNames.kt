package com.onpath.app.data

/** File names of the offline map data that pure code (import, search index) needs; [DataFiles] re-exports them. */
object DataNames {
    /** Format 2 (docs/design/map-data-size.md): routes, trails, paths, ways, trail names as one vector tile file. */
    const val OVERLAYS = "overlays.pmtiles"

    /** Written by tools/build_region.sh into the package: region, bbox, OSM date, size of every file. */
    const val PACKAGE_FILE = "package.json"
}
