// test-runner/test-plugin.js
import fs from 'fs';
import path from 'path';
import { execFileSync } from 'child_process';

// Polyfills for Node.js test environment matching QuickJS runtime
function curlFetch(url, options = {}) {
  const args = ["-s", "-i", "-L", "--http2"];
  if (options.method && options.method.toUpperCase() !== "GET") {
    args.push("-X", options.method.toUpperCase());
  }
  const headers = options.headers || {};
  for (const [k, v] of Object.entries(headers)) {
    args.push("-H", `${k}: ${v}`);
  }
  if (options.body) {
    args.push("--data", options.body);
  }
  args.push(url);

  try {
    const stdout = execFileSync("curl", args, { maxBuffer: 15 * 1024 * 1024, encoding: "utf8" });
    const headerEnd = stdout.indexOf("\r\n\r\n");
    const headerBlock = headerEnd >= 0 ? stdout.substring(0, headerEnd) : "";
    const body = headerEnd >= 0 ? stdout.substring(headerEnd + 4) : stdout;
    const statusMatch = headerBlock.match(/HTTP\/[\d.]+\s+(\d+)/);
    const status = statusMatch ? parseInt(statusMatch[1], 10) : 200;

    return {
      status,
      ok: status >= 200 && status < 300,
      statusText: status === 200 ? "OK" : "",
      text: async () => body,
      json: async () => JSON.parse(body),
      headers: {
        get: (name) => {
          const m = headerBlock.match(new RegExp(`^${name}:\\s*(.*)$`, "im"));
          return m ? m[1].trim() : null;
        }
      }
    };
  } catch (err) {
    return {
      status: 500,
      ok: false,
      statusText: err.message,
      text: async () => "",
      json: async () => ({}),
      headers: { get: () => null }
    };
  }
}

const originalFetch = globalThis.fetch;
globalThis.fetch = async function(url, options = {}) {
  if (originalFetch) {
    try {
      const res = await originalFetch(url, options);
      if (res.status === 403 || res.status === 503) {
        const cloned = res.clone();
        const text = await cloned.text();
        if (text.includes("Just a moment...") || text.includes("Cloudflare")) {
          return curlFetch(url, options);
        }
      }
      return res;
    } catch (_) {
      return curlFetch(url, options);
    }
  }
  return curlFetch(url, options);
};

globalThis.context = {
  storage: {
    get: () => null,
    set: () => {},
    remove: () => {}
  },
  log: {
    info: (...args) => console.log('[PLUGIN INFO]', ...args),
    warn: (...args) => console.warn('[PLUGIN WARN]', ...args),
    error: (...args) => console.error('[PLUGIN ERROR]', ...args)
  },
  webview: {
    sniff: async () => null
  },
  ui: {
    showToast: (msg, isLong = false) => console.log('[PLUGIN TOAST]', msg, isLong ? '(LONG)' : '')
  }
};

export async function testPlugin(pluginDir) {
  const pluginPath = path.join(pluginDir, 'plugin.js');
  if (!fs.existsSync(pluginPath)) {
    throw new Error(`Missing plugin.js at ${pluginPath}`);
  }

  const manifestPath = path.join(pluginDir, 'manifest.json');
  let manifest = null;
  if (fs.existsSync(manifestPath)) {
    manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  } else {
    // Tìm thông tin manifest từ file manifest.json của nhóm hoặc manifest tổng
    const dirName = path.basename(pluginDir);
    let searchDir = path.resolve(pluginDir, '..');
    while (searchDir !== path.dirname(searchDir)) {
      const candidate = path.join(searchDir, 'manifest.json');
      if (fs.existsSync(candidate)) {
        try {
          const parsed = JSON.parse(fs.readFileSync(candidate, 'utf8'));
          const found = (parsed.plugins || parsed.scrapers || []).find(
            p => p.id === dirName || (p.entry && (p.entry.includes(`/${dirName}/`) || p.entry.startsWith(`${dirName}/`)))
          );
          if (found) {
            manifest = found;
            break;
          }
        } catch (_) {}
      }
      searchDir = path.dirname(searchDir);
    }
  }
  if (!manifest) {
    manifest = { id: path.basename(pluginDir), name: path.basename(pluginDir), version: "1.0.0" };
  }

  const activeDomain = (manifest.customDomain && manifest.customDomain.trim()) 
    || (manifest.defaultDomain && manifest.defaultDomain.trim()) 
    || '';
  globalThis.CUSTOM_DOMAIN = activeDomain;
  globalThis.BASE_URL = activeDomain;
  globalThis.DEFAULT_DOMAIN = manifest.defaultDomain || '';
  globalThis.currentPluginId = manifest.id;
  globalThis.context.manifest = manifest;

  console.log(`\n========================================`);
  console.log(`🧪 TESTING PLUGIN: [${manifest.id}] - ${manifest.name} (v${manifest.version})`);
  console.log(`🌐 ACTIVE DOMAIN: ${activeDomain || 'N/A'}`);
  console.log(`========================================`);

  const moduleUrl = 'file://' + path.resolve(pluginPath);
  const pluginModule = await import(moduleUrl);
  const plugin = pluginModule.default;

  if (!plugin) {
    throw new Error('Plugin does not have export default!');
  }

  // 1. Test getHome
  console.log('1️⃣ Testing getHome()...');
  const home = await plugin.getHome();
  if (!home || !Array.isArray(home.sections)) {
    throw new Error('getHome() must return { sections: [] }');
  }
  console.log(`   ✅ getHome() returned ${home.sections.length} sections.`);
  let sampleSlug = null;
  for (const sec of home.sections) {
    console.log(`      - Section: "${sec.title}" (${sec.items.length} items)`);
    if (!sampleSlug && sec.items.length > 0) {
      sampleSlug = sec.items[0].slug;
    }
  }

  // 1.1 Test getMenu (if supported)
  if (typeof plugin.getMenu === 'function') {
    console.log('📑 Testing getMenu()...');
    const menu = await plugin.getMenu();
    if (!Array.isArray(menu)) {
      throw new Error('getMenu() must return an array of MenuItem');
    }
    console.log(`   ✅ getMenu() returned ${menu.length} menu items.`);
    menu.forEach(item => {
      if (item.isDropdown && Array.isArray(item.items)) {
        console.log(`      - Dropdown: "${item.title}" (${item.items.length} sub-items)`);
      } else {
        console.log(`      - Item: "${item.title}" (slug: ${item.slug || 'none'})`);
      }
    });
  }

  // 2. Test search
  console.log('2️⃣ Testing search()...');
  const searchResult = await plugin.search('a', 1);
  if (!searchResult || !Array.isArray(searchResult.items)) {
    throw new Error('search() must return { items: [], page, hasNext }');
  }
  console.log(`   ✅ search('a', 1) returned ${searchResult.items.length} items.`);
  if (!sampleSlug && searchResult.items.length > 0) {
    sampleSlug = searchResult.items[0].slug;
  }

  // 3. Test getDetail
  const testSlug = sampleSlug || 'test';
  console.log(`3️⃣ Testing getDetail("${testSlug}")...`);
  const detail = await plugin.getDetail(testSlug);
  if (!detail || !detail.slug || !detail.name) {
    throw new Error('getDetail() must return MovieDetail with slug and name');
  }
  if (!Array.isArray(detail.episodes) || detail.episodes.length === 0) {
    throw new Error('getDetail() must return episodes: EpisodeServer[]');
  }
  console.log(`   ✅ getDetail() returned "${detail.name}" with ${detail.episodes.length} servers.`);

  const firstServer = detail.episodes[0];
  if (!firstServer.episodes || firstServer.episodes.length === 0) {
    throw new Error(`Server ${firstServer.serverName} has no episodes!`);
  }
  const firstEpisode = firstServer.episodes[0];
  console.log(`      - Server: ${firstServer.serverName}, Episode: ${firstEpisode.name}`);

  // 4. Test getStream
  console.log(`4️⃣ Testing getStream(episode)...`);
  const stream = await plugin.getStream(firstEpisode);
  if (!stream || !stream.type || !stream.url) {
    throw new Error('getStream() must return EpisodeStream with type and url');
  }
  console.log(`   ✅ getStream() resolved stream:`);
  console.log(`      - Type: ${stream.type}`);
  console.log(`      - URL: ${stream.url.substring(0, 60)}...`);
  if (stream.headers && Object.keys(stream.headers).length > 0) {
    console.log(`      - Headers: ${JSON.stringify(stream.headers)}`);
  }
  if (stream.subtitles && stream.subtitles.length > 0) {
    console.log(`      - Subtitles: ${stream.subtitles.length} track(s)`);
  }

  // 5. Test Optional Methods (if present)
  if (typeof plugin.checkHealth === 'function') {
    console.log('🩺 Testing checkHealth()...');
    const health = await plugin.checkHealth();
    console.log(`   ✅ checkHealth() returned status: "${health?.status}" (${health?.message || 'No msg'})`);
  }

  if (typeof plugin.getSettings === 'function') {
    console.log('⚙️ Testing getSettings()...');
    const settings = await plugin.getSettings();
    console.log(`   ✅ getSettings() returned ${Array.isArray(settings) ? settings.length : 0} setting items.`);
  }

  console.log(`\n🎉 RESULT: ALL TESTS PASSED FOR [${manifest.id}]!\n`);
  return true;
}

if (process.argv[2]) {
  testPlugin(process.argv[2]).catch(err => {
    console.error('❌ TEST FAILED:', err);
    process.exit(1);
  });
}

