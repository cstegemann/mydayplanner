# Garmin watch face source

Put the Connect IQ / Monkey C watch-face project in this directory, e.g.
`source/WatchFace.mc`, `manifest.xml`, `resources/`, and `monkey.jungle`.

This folder is **outside** `app/`, so Android Gradle does not compile or package
these files. You can keep Garmin and Android sources in the same Git repository.

The Android sender currently targets Connect IQ watch-face UUID
`36629bca-a6fa-4be8-a2b2-4a8a870c7415`. Preserve that ID when importing
the actual watch-face project, or update the sender deliberately.

Other watch ecosystems can have sibling folders under `watchfaces/`.
