/**
 * Genera hero/login in JPEG (q80) e WebP in public/, con stesso obiettivo delle foto annuncio:
 * max 1920×1080, fit inside, senza upscale.
 * Sorgente: public/{basename}.png se presente, altrimenti {basename}.jpg (per rigenerare senza master PNG).
 * Uso: npm run optimize:public-images
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, '..', 'public');

function resolveInput(basename) {
  const png = path.join(publicDir, `${basename}.png`);
  const jpg = path.join(publicDir, `${basename}.jpg`);
  if (fs.existsSync(png)) return png;
  if (fs.existsSync(jpg)) return jpg;
  return null;
}

async function main() {
  const { default: sharp } = await import('sharp');

  async function optimize(basename) {
    const input = resolveInput(basename);
    if (!input) {
      console.warn(
        `[optimize-public-images] skip: ${basename}.png o ${basename}.jpg non trovato in public/`,
      );
      return;
    }

    const inputBuf = fs.readFileSync(input);
    const pipelineBase = sharp(inputBuf).resize(1920, 1080, {
      fit: 'inside',
      withoutEnlargement: true,
    });

    const jpegBuf = await pipelineBase
      .clone()
      .jpeg({ quality: 80, mozjpeg: true })
      .toBuffer();
    fs.writeFileSync(path.join(publicDir, `${basename}.jpg`), jpegBuf);

    await pipelineBase.clone().webp({ quality: 80 }).toFile(path.join(publicDir, `${basename}.webp`));

    console.log(`[optimize-public-images] ok: ${basename}.jpg, ${basename}.webp (da ${path.basename(input)})`);
  }

  await optimize('hero');
  await optimize('login');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
