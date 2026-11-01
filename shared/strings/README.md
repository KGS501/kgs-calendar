# Strings

The app's user-visible strings, in Android resource format. This is their single source:

- The Android app merges `res/` into its resources (`R.string` in `:app`).
- `:shared:app` generates `com.kgs.calendar.res.SharedR` and its string tables from it at build time,
  so shared screens use the same ids and texts on Android and on the desktop.

Add a string to `values/strings.xml` and its translation to `values-de/strings.xml`.
