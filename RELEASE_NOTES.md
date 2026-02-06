# Energy Drink - Release Notes

## v1.10 (Build 10)

### Build & Toolchain
- Upgraded Android target/compile SDK from 34 to 35
- Updated Android Gradle Plugin from 8.8.0 to 9.0.0
- Updated Kotlin from 1.9.24 to 2.2.10
- Updated Gradle wrapper from 8.10.2 to 9.1.0
- Added new Gradle property flags for compatibility with AGP 9

### Features
- Added dark theme support
- Modernized MainActivity with ViewBinding and new UI features
- Modernized FloatingWidgetService with foreground service and coroutines
- Updated PowerButtonReceiver and TileService for graceful shutdown

### Improvements
- Tuned floating widget physics for smoother toss/glide behavior (adjusted friction and velocity multiplier)

### Testing
- Added unit and instrumented tests
