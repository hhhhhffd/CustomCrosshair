# Format tests

Run the share-code/pattern test with Java 21, no Fabric setup needed:

```sh
out=$(mktemp -d)
javac -d "$out" src/client/java/com/customcrosshair/CrosshairPatterns.java tests/CrosshairPatternsTest.java
java -cp "$out" CrosshairPatternsTest
```

The four sample codes are from the [official Lunar Client crosshair guide](https://www.lunarclient.com/news/how-to-make-a-custom-crosshair-on-lunar-client). Tests verify canonical encoding, orientation, round-trip, and bounds. These tests do not run Lunar Client itself.
