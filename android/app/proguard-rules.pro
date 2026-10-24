# The theme preference is persisted by enum name and restored with ThemeMode.valueOf().
-keepclassmembers enum dev.sohan.linkpilot.data.ThemeMode {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
