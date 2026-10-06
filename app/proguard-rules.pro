# DiscoverBounds resolves the public Window Extensions API by class and member name so it can
# retain the split-host fallback on devices whose extension implementation differs. R8 cannot
# infer these references from the strings used by Class.forName/getMethod/Proxy.
-keep class androidx.window.extensions.** { *; }

# The news-feed parser selects the platform XML pull parser through its factory; keep the
# provider so release builds don't fall back to a missing parser class.
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.**

# Android manifest components and directly constructed widget-host classes are traced by AGP/R8.
# Layout and backup persistence use org.json with explicit keys, so there are no model classes
# that require broad reflection or serialization keep rules.
