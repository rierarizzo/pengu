// Regenerates ui/icons/MaterialIcons.kt from the Material Symbols Rounded set
// (Apache-2.0, Google), published as @material-symbols/svg-<weight> on npm. Run:
//   node scripts/gen_icons.mjs > app/shared/src/commonMain/kotlin/com/misgus/pengu/ui/icons/MaterialIcons.kt
//
// The package ships one SVG per glyph, on a 960x960 grid whose origin sits at
// the top-left but whose y axis runs from -960 to 0, so every vector is built
// inside a group translated by +960.

const VERSION = "0.48.0";
const PACKAGE = (weight) => `https://cdn.jsdelivr.net/npm/@material-symbols/svg-${weight}@${VERSION}/rounded`;

// Glyphs fetched from the weight-700 package instead of the 400 one. `search` is
// the only glyph in the set with no `-fill` variant (the package ships it with
// the outline path under both names), so at 400 it read lighter than the filled
// glyphs around it; the heavier weight is what makes it match them.
const HEAVY = new Set(["search"]);

// [Kotlin name, package file name, autoMirror]
const icons = [
  ["Home", "home-fill", false],
  ["Search", "search", false],
  ["Bookmark", "bookmark-fill", false],
  ["Settings", "settings-fill", false],
  ["Play", "play_arrow-fill", false],
  ["Plus", "add-fill", false],
  ["Check", "check-fill", false],
  ["CheckCircle", "check_circle-fill", false],
  ["CheckCircleOutline", "check_circle", false],
  ["ArrowBack", "arrow_back", true],
  ["ChevronRight", "chevron_right", false],
  ["ChevronDown", "keyboard_arrow_down", false],
  ["Close", "close", false],
  ["Pause", "pause", false],
  ["Refresh", "refresh", false],
  ["Replay10", "replay_10", false],
  ["Forward10", "forward_10", false],
  ["Fullscreen", "fullscreen", false],
  ["Volume", "volume_up", false],
  ["VolumeDown", "volume_down", false],
  ["VolumeMute", "volume_mute", false],
  ["Visibility", "visibility", false],
  ["VisibilityOff", "visibility_off", false],
  ["Subtitles", "subtitles", false],
  ["GraphicEq", "graphic_eq", false],
  ["Error", "error", false],
  ["Compass", "explore", false],
  ["FolderOpen", "folder_open", false],
  ["Inbox", "inbox", false],
  ["Movie", "movie", false],
  ["User", "person", false],
  ["Copy", "content_copy", false],
  ["Login", "login", true],
  ["Logout", "logout", true],
  ["DragIndicator", "drag_indicator", false],
  ["Delete", "delete", false],
];

const constName = (kotlin) =>
  kotlin
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .toUpperCase();

const paths = new Map();
for (const [, file] of icons) {
  if (paths.has(file)) continue;
  const weight = HEAVY.has(file) ? 700 : 400;
  const response = await fetch(`${PACKAGE(weight)}/${file}.svg`);
  if (!response.ok) throw new Error(`${file}: HTTP ${response.status}`);
  const svg = await response.text();
  if (!svg.includes('viewBox="0 -960 960 960"')) {
    throw new Error(`${file}: expected the 960 grid`);
  }
  const d = [...svg.matchAll(/ d="([^"]+)"/g)].map((m) => m[1]);
  if (d.length === 0) throw new Error(`${file}: no path`);
  paths.set(file, d.join(""));
}

let out = `package com.misgus.pengu.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set vendored from Material Symbols Rounded (Apache-2.0, Google).
 *
 * The whole UI uses these glyphs; call sites size them with
 * [com.misgus.pengu.ui.theme.AppIconSize]. \`scripts/gen_icons.mjs\` regenerates
 * the file from the \`@material-symbols/svg-400\` package, except \`Search\`,
 * which comes from the 700-weight package: it is the one glyph with no filled
 * variant, so the heavier outline is what makes it match the filled ones. Only
 * the glyphs the UI uses are kept, and each pair (a filled and an outline glyph,
 * e.g. check circle and its outline) mirrors the solid/outline split the UI had
 * before.
 */
object MaterialIcons {
`;

for (const [kotlin, , autoMirror] of icons) {
  const flag = autoMirror ? ", autoMirror = true" : "";
  out += `    val ${kotlin}: ImageVector by lazy { material("${kotlin}", ${constName(kotlin)}${flag}) }\n`;
}

out += `}

/**
 * Builds one [ImageVector] from its Material Symbols path.
 *
 * The glyphs live on a 960x960 grid whose y axis runs from -960 to 0, so the
 * path is wrapped in a group translated by +960; the vector still renders at
 * the 24dp the call sites ask for. \`autoMirror\` flips the directional glyphs
 * (the back arrow and sign-in/out) in RTL.
 */
private fun material(
    name: String,
    pathData: String,
    autoMirror: Boolean = false,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = autoMirror,
        ).apply {
            addGroup(translationY = 960f)
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
`;

out += "\n";
for (const [kotlin, file] of icons) {
  const chunks = paths.get(file).match(/.{1,110}/g);
  out += `private const val ${constName(kotlin)} =\n`;
  chunks.forEach((chunk, index) => {
    const indent = index === 0 ? "    " : "        ";
    out += `${indent}"${chunk}"${index === chunks.length - 1 ? "" : " +"}\n`;
  });
}

process.stdout.write(out);
