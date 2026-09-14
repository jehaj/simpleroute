# SimpleRoute — Roadmap & TODO

A prioritized roadmap of features and improvements for the SimpleRoute standalone cycling navigation app on Wear OS (`:wear`) and Android Companion (`:mobile`).

---

## Phase 1: Wear OS Rider Safety & Navigation Essentials (`:wear`)

- [ ] **Off-Route Detection & Haptic Warning**
  - **Context:** [`NavigationEngine.kt`](wear/src/main/java/dk/jehaj/simpleroute/navigation/NavigationEngine.kt) calculates `offRouteDistanceMeters` and jumps window search on $> 120\text{ m}$, but emits no alert event.
  - **Task:** Trigger an alert when off-route distance exceeds $45\text{ m}$ for $> 5\text{ s}$. Add distinct vibration pattern (e.g., `[0, 100, 100, 100, 100, 300]`) in [`HapticManager.kt`](wear/src/main/java/dk/jehaj/simpleroute/haptics/HapticManager.kt) and show an amber off-route banner with directional heading arrow back to the nearest route point. Emit a soft recovery chirp when back on track.

- [ ] **Rotary Bezel Zoom for Breadcrumb Map**
  - **Context:** [`BreadcrumbMapView.kt`](wear/src/main/java/dk/jehaj/simpleroute/presentation/components/BreadcrumbMapView.kt) hardcodes `viewRadiusMeters = 300f`.
  - **Task:** Implement rotary bezel input (`Modifier.rotaryScrollable` / `onRotaryScrollEvent`) on the Galaxy Watch4 to dynamically zoom between $100\text{ m}$ (complex intersections) and $1{,}000\text{ m}$ (open road), with subtle haptic detent feedback on zoom step.

- [ ] **Auto-Pause & Manual Ride Pause**
  - **Context:** Stationary stops (traffic lights, cafes) cause GPS drift and distort average speed/distance metrics.
  - **Task:** Add auto-pause logic when speed drops below $0.6\text{ m/s}$ for $> 15\text{ s}$. Provide manual pause/resume via short hold or overlay button on [`NavigationScreen.kt`](wear/src/main/java/dk/jehaj/simpleroute/presentation/NavigationScreen.kt).

- [ ] **Pre-Ride Route Detail Preview Sheet**
  - **Context:** Tapping a route in [`RouteListScreen.kt`](wear/src/main/java/dk/jehaj/simpleroute/presentation/RouteListScreen.kt) starts navigation immediately with no confirmation.
  - **Task:** Add an intermediate summary sheet showing total distance ($42.5\text{ km}$), elevation gain ($+350\text{ m}$), turn count, and a prominent "Start Ride" button.

---

## Phase 2: Mobile Companion App & Transfer Smarts (`:mobile`)

- [ ] **GPX Turn-Cue Validator & BRouter Inspection**
  - **Context:** Standard GPX exports without `turnInstructionMode = 3` lack `<rtept>` cues, leaving the watch with a breadcrumb map but no turn arrows or haptics.
  - **Task:** Parse GPX on the phone before transmission in [`MainActivity.kt`](mobile/src/main/java/dk/jehaj/simpleroute/MainActivity.kt). Warn if turn cues are missing and display a guide on exporting BRouter OsmAnd format (`turnInstructionMode=3`).

- [ ] **In-App Route Preview (Map & Elevation)**
  - **Context:** Mobile UI currently only displays file name and size in KB.
  - **Task:** Render a Compose Canvas polyline preview with elevation profile and key route statistics (distance, climb, estimated ride time) before sending to watch.

- [ ] **Two-Way Watch Storage Management**
  - **Context:** Watch routes can currently only be deleted directly on the watch screen.
  - **Task:** Query installed routes on the watch via Wearable `MessageClient` / `ChannelClient`. Allow viewing watch storage usage and deleting routes remotely from the phone companion.

- [ ] **Phone Route Library & History**
  - **Context:** Users must use the system file picker every time.
  - **Task:** Cache recently shared/opened GPX files locally in phone storage with search and favorite tagging.

---

## Phase 3: Wear OS Platform Integration & Ride Analytics (`:wear`)

- [ ] **Trip Computer / Metrics Mode (Display Mode C)**
  - **Context:** Cyclists need standard cycling computer data while on the road.
  - **Task:** Add a 3rd swipeable screen in [`NavigationScreen.kt`](wear/src/main/java/dk/jehaj/simpleroute/presentation/NavigationScreen.kt) displaying large glanceable metrics: Current Speed, Average Speed, Moving Time, Distance Traveled / Remaining, Elevation Gain, and estimated ETA.

- [ ] **Ride Summary & GPX/FIT Recording**
  - **Context:** Navigated rides are not logged or stored as activity tracks.
  - **Task:** Record actual rider GPS coordinates during active navigation into `context.filesDir/recorded/`. Display a completion summary on stop (duration, distance, average speed, max climb), with option to export/share track to phone.

- [ ] **Wear OS Tiles & Complications**
  - **Context:** Launching SimpleRoute requires navigating app drawer or list.
  - **Task:** Create a Wear OS Tile showing the last used route with a 1-tap "Start" shortcut, and a watch face complication for remaining distance or next-turn arrow.

- [ ] **Adaptive GPS Polling on Long Straights**
  - **Context:** 1 Hz GPS drains Galaxy Watch4 battery in 2–4 hours.
  - **Task:** When the next turn cue is $> 1.5\text{ km}$ away on an uninterrupted road, dynamically relax FusedLocation sampling from $1\text{ Hz}$ to $3\text{ Hz}$ or $5\text{ Hz}$, throttling back to $1\text{ Hz}$ within $300\text{ m}$ of the junction.

---

## Phase 4: Universal GPX Support & Ecosystem (`:core` / Shared)

- [ ] **Algorithmic Turn Generator for Standard GPX**
  - **Context:** Enable turn-by-turn guidance for GPX files from Strava, Komoot, AllTrails, Garmin, or RideWithGPS without pre-baked BRouter cues.
  - **Task:** Implement an offline geometric turn detector that analyzes trackpoint deflection angles $(\Delta \theta > 35^\circ)$ over distance windows, generating synthetic `TurnCue` instances (`TL`, `TR`, `TU`, etc.).

- [ ] **Dual-Screen Handlebar Companion Mode**
  - **Context:** Cyclists with phone handlebar mounts want a large screen map while using watch haptics.
  - **Task:** Stream live `NavigationState` from watch to phone via Bluetooth DataLayer. Phone displays full-color navigation map and trip computer while the watch delivers turn haptics and glanceable wrist alerts.

- [ ] **Settings Sync & Units Preference (Metric / Imperial)**
  - **Context:** Distance units are currently hardcoded to Metric (`m` / `km`).
  - **Task:** Allow toggling between Metric (`km`, `m`, `km/h`) and Imperial (`mi`, `ft`, `mph`), plus customizable dynamic warning distance multiplier (e.g. 15s for road bike descents vs. 8s for city traffic), synced across devices via `DataClient`.

- [ ] **Reverse Route Navigation**
  - **Context:** Returning home along the same route in reverse.
  - **Task:** Provide a "Navigate in Reverse" option that inverts the trackpoint array and flips turn cue directions (`TL` $\leftrightarrow$ `TR`).
