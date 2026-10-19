// Regenerates ui/icons/FluentIcons.kt from the Fluent UI System Icons set on
// Iconify. Source: @fluent (MIT, Microsoft). Run:
//   node scripts/gen_fluent_icons.mjs > app/shared/src/commonMain/kotlin/com/misgus/pengu/ui/icons/FluentIcons.kt

const API = "https://api.iconify.design/fluent.json";

// [Kotlin name, Iconify name, autoMirror]
const icons = [
  ["Home", "home-24-filled", false],
  ["Search", "search-24-regular", false],
  ["Bookmark", "bookmark-24-filled", false],
  ["Calendar", "calendar-24-filled", false],
  ["Settings", "settings-24-filled", false],
  ["Play", "play-24-filled", false],
  ["Plus", "add-24-filled", false],
  ["Check", "checkmark-24-filled", false],
  ["CheckCircle", "checkmark-circle-24-filled", false],
  ["CheckCircleOutline", "checkmark-circle-24-regular", false],
  ["ArrowBack", "arrow-left-24-regular", true],
  ["ChevronLeft", "chevron-left-24-regular", false],
  ["ChevronRight", "chevron-right-24-regular", false],
  ["ChevronDown", "chevron-down-24-regular", false],
  ["Close", "dismiss-24-regular", false],
  ["Pause", "pause-24-regular", false],
  ["Refresh", "arrow-clockwise-24-regular", false],
  ["Forward", "fast-forward-24-regular", false],
  ["Replay", "rewind-24-regular", false],
  ["Fullscreen", "full-screen-maximize-24-regular", false],
  ["Volume", "speaker-2-24-regular", false],
  ["Visibility", "eye-24-regular", false],
  ["VisibilityOff", "eye-off-24-regular", false],
  ["Subtitles", "subtitles-24-regular", false],
  ["Audiotrack", "music-note-2-24-regular", false],
  ["Error", "error-circle-24-regular", false],
  ["Compass", "compass-northwest-24-regular", false],
  ["FolderOpen", "folder-open-24-regular", false],
  ["Inbox", "mail-inbox-24-regular", false],
  ["Movie", "filmstrip-24-regular", false],
  ["User", "person-24-regular", false],
  ["SearchOff", "zoom-out-24-regular", false],
  ["CalendarMonth", "calendar-24-regular", false],
  ["Copy", "copy-24-regular", false],
  ["Login", "arrow-enter-24-regular", true],
  ["Logout", "arrow-exit-24-regular", true],
];

const constName = (kotlin) =>
  kotlin
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .toUpperCase();

const names = [...new Set(icons.map(([, snake]) => snake))];
const response = await fetch(`${API}?icons=${names.join(",")}`);
if (!response.ok) throw new Error(`HTTP ${response.status}`);
const collection = await response.json();

const paths = new Map();
for (const name of names) {
  const icon = collection.icons?.[name] ?? collection.icons?.[collection.aliases?.[name]?.parent];
  if (!icon) throw new Error(`${name}: not found`);
  if (icon.width !== 24 || icon.height !== 24) {
    throw new Error(`${name}: expected a 24dp viewport, got ${icon.width}x${icon.height}`);
  }
  const d = [...icon.body.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1]);
  if (d.length === 0) throw new Error(`${name}: no path`);
  paths.set(name, d.join(""));
}

let out = `package com.misgus.pengu.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set vendored from Fluent UI System Icons (MIT, Microsoft).
 *
 * The whole UI uses these glyphs, on Fluent's 24dp viewport; call sites
 * size them with [com.misgus.pengu.ui.theme.AppIconSize]. \`scripts/gen_fluent_icons.mjs\`
 * regenerates the file from the Fluent set on Iconify. Only the glyphs the UI
 * uses are kept, and each pair (a filled and a regular glyph, e.g. check circle
 * and its outline) mirrors the solid/outline split the UI had before.
 */
object FluentIcons {
`;

for (const [kotlin, , autoMirror] of icons) {
  const flag = autoMirror ? ", autoMirror = true" : "";
  out += `    val ${kotlin}: ImageVector by lazy { fluent("${kotlin}", ${constName(kotlin)}${flag}) }\n`;
}

out += `}

/**
 * Builds one [ImageVector] from its Fluent path, on the 24dp viewport.
 *
 * \`autoMirror\` flips the glyph in RTL, which Fluent's directional icons
 * (the back arrow and sign-in/out) need alongside the layout.
 */
private fun fluent(
    name: String,
    pathData: String,
    autoMirror: Boolean = false,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
`;

out += "\n";
for (const [kotlin, snake] of icons) {
  const chunks = paths.get(snake).match(/.{1,110}/g);
  out += `private const val ${constName(kotlin)} =\n`;
  chunks.forEach((chunk, index) => {
    const indent = index === 0 ? "    " : "        ";
    out += `${indent}"${chunk}"${index === chunks.length - 1 ? "" : " +"}\n`;
  });
}

process.stdout.write(out);
