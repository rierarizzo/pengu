// Rebuilds every committed raster from the generated SVGs: the store PNGs, the
// Android mipmaps, the desktop ladder and pengu.ico. Rasters are committed because
// the builds consume them, but they are outputs — never edit them by hand.
//
//   node scripts/gen_brand_rasters.mjs        (needs sharp: npm i --no-save sharp)
//
// Run gen_brand_icons.mjs first: this reads the SVGs it writes.

import { readFileSync, writeFileSync } from "node:fs";

let sharp;
try {
  ({ default: sharp } = await import("sharp"));
} catch {
  console.error("gen_brand_rasters: needs sharp — run `npm i --no-save sharp` first.");
  process.exit(1);
}

const BRAND = "branding/";
const RES = "app/androidApp/src/main/res";
const DESKTOP = "app/desktopApp/src/main/resources/pengu-icon";
const MD = "app/androidApp/src/main/res/mipmap";
const STORE = 512;
const MARK_FILL = 0.72; // the master's rule: the mark fills 72 % of the tile, tall
const DPI = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
const DESKTOP_SIZES = [16, 20, 24, 32, 40, 48, 256];
const ICO_SIZES = [16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 96, 128, 256];

const master = readFileSync(`${BRAND}pengu.svg`, "utf8");
const rounded = readFileSync(`${BRAND}pengu-rounded.svg`, "utf8");
const frameless = readFileSync(`${BRAND}pengu-frameless.svg`, "utf8");

// The desktop ladder needs the outline to be one device pixel at every size, so the
// frameless SVG is re-stroked per size. The numbers come from the files: the mark's
// scale as the generator baked it, and the beak's own stroke width.
const beakStroke = Number(/<path id="beak" d="[^"]+" stroke-width="([^"]+)"/.exec(master)[1]);
const scale = Number(/scale\(([\d.]+)\)/.exec(frameless)[1]);
const outline = (size) => beakStroke + (2 * 1024) / (size * scale);
const framelessAt = (size) => frameless.replace(/(stroke="#0B0B0B" stroke-width=")[^"]+"/, `$1${outline(size)}"`);
const circle = rounded.replace('rx="224"', 'rx="512"'); // the round launcher variant

const render = (svg, size) =>
  sharp(Buffer.from(svg), { density: Math.max(72, (72 * size) / STORE) })
    .resize(size, size)
    .png()
    .toBuffer();

const write = (file, buf) => {
  writeFileSync(file, buf);
  return file;
};

// ---- the mark's footprint, so a rescale cannot slip through silently -----------
const bboxHeight = async (buf) => {
  const { data, info } = await sharp(buf).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const [r, g, b] = [data[0], data[1], data[2]]; // the tile corner
  let top = info.height, bottom = -1;
  for (let y = 0; y < info.height; y++)
    for (let x = 0; x < info.width; x++) {
      const o = (y * info.width + x) * 4;
      if (Math.abs(data[o] - r) + Math.abs(data[o + 1] - g) + Math.abs(data[o + 2] - b) > 12) {
        if (y < top) top = y;
        if (y > bottom) bottom = y;
      }
    }
  return (bottom - top + 1) / info.height;
};

// ---- store --------------------------------------------------------------------
const store = await render(master, STORE);
const fill = await bboxHeight(store);
if (Math.abs(fill - MARK_FILL) > 0.02)
  throw new Error(
    `pengu.png: the mark fills ${(fill * 100).toFixed(1)} % of the tile, expected ~${MARK_FILL * 100} % — ` +
      `re-fit the master's hull (see "Updating the mark" in branding/README.md)`,
  );
const written = [write(`${BRAND}pengu.png`, store), write(`${BRAND}pengu-rounded.png`, await render(rounded, STORE))];

// ---- Android legacy mipmaps ---------------------------------------------------
for (const [dir, size] of Object.entries(DPI)) {
  written.push(write(`${MD}-${dir}/ic_launcher.png`, await render(rounded, size)));
  written.push(write(`${MD}-${dir}/ic_launcher_round.png`, await render(circle, size)));
}

// ---- desktop ladder -----------------------------------------------------------
const ladder = new Map();
for (const size of DESKTOP_SIZES) {
  const png = await render(framelessAt(size), size);
  ladder.set(size, png);
  written.push(write(`${DESKTOP}/pengu-${size}.png`, png));
}

// ---- pengu.ico: 32-bit DIB up to 32 px, PNG above ------------------------------
const dib = async (buf) => {
  const { width: w, height: h } = await sharp(buf).metadata();
  const rgba = await sharp(buf).ensureAlpha().raw().toBuffer();
  const xor = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const s = ((h - 1 - y) * w + x) * 4, d = (y * w + x) * 4; // bottom-up BGRA
      xor[d] = rgba[s + 2]; xor[d + 1] = rgba[s + 1]; xor[d + 2] = rgba[s]; xor[d + 3] = rgba[s + 3];
    }
  const maskRow = Math.ceil(w / 32) * 4;
  const head = Buffer.alloc(40);
  head.writeUInt32LE(40, 0); head.writeInt32LE(w, 4); head.writeInt32LE(h * 2, 8);
  head.writeUInt16LE(1, 12); head.writeUInt16LE(32, 14); head.writeUInt32LE(xor.length + maskRow * h, 20);
  return Buffer.concat([head, xor, Buffer.alloc(maskRow * h)]);
};

const entries = [];
for (const size of ICO_SIZES) {
  const png = ladder.get(size) ?? (await render(framelessAt(size), size));
  entries.push({ size, data: size <= 32 ? await dib(png) : png });
}
const dir = Buffer.alloc(6 + entries.length * 16);
dir.writeUInt16LE(0, 0); dir.writeUInt16LE(1, 2); dir.writeUInt16LE(entries.length, 4);
let offset = dir.length;
entries.forEach(({ size, data }, i) => {
  const o = 6 + i * 16;
  dir[o] = size === 256 ? 0 : size; dir[o + 1] = size === 256 ? 0 : size;
  dir.writeUInt16LE(1, o + 4); dir.writeUInt16LE(32, o + 6);
  dir.writeUInt32LE(data.length, o + 8); dir.writeUInt32LE(offset, o + 12);
  offset += data.length;
});
written.push(write(`${BRAND}pengu.ico`, Buffer.concat([dir, ...entries.map((e) => e.data)])));

console.log(`wrote ${written.length} rasters (the mark fills ${(fill * 100).toFixed(1)} % of the store tile)`);
for (const f of written) console.log(`  ${f}`);
