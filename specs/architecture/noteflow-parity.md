# Noteflow configuration and dated links

Go discovers Studio's version-1 `.flow` markers and reads their title, links and weekly settings. The workflow picker uses the marker title, falling back to the directory name (or `Noteflow` at vault root). The old `Today.md` body is not displayed; its existing files are not modified. A stored `Today.md` selection is mapped to a marker in the same directory.

For each visible direct subdirectory except Inbox, the header links to its most recent valid `YYYY-MM-DD.md` dated no later than the local current day, or the earliest future note if none is current. A dot means the first H1 lacks ✅. Links refresh after sync, foreground return, workflow change and local day rollover. Date-row files alongside `.flow` remain week rows.

Marker discovery walks the vault because `.flow` files are not part of the Markdown registry. The walk and selected-note reads run on the IO dispatcher after launch has settled and a frame has rendered; the cached snapshot supplies first paint. Registry refresh and this additional walk may compete with background sync on large vaults. Measure cold-start first-frame and launch-settled times plus post-settle marker-discovery duration on representative large vaults when profiling; if discovery is costly, cache or integrate marker discovery with the existing registry scan.
