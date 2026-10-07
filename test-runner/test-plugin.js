// test-runner/test-plugin.js
import fs from 'fs';
import path from 'path';

// Polyfills for Node.js test environment matching QuickJS runtime
if (!globalThis.fetch) {
  const nodeFetch = await import('node-fetch');
  globalThis.fetch = nodeFetch.default;
}

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
  }
};

export async function testPlugin(pluginDir) {
  const manifestPath = path.join(pluginDir, 'manifest.json');
  const pluginPath = path.join(pluginDir, 'plugin.js');

  if (!fs.existsSync(manifestPath)) {
    throw new Error(`Missing manifest.json at ${manifestPath}`);
  }
  if (!fs.existsSync(pluginPath)) {
    throw new Error(`Missing plugin.js at ${pluginPath}`);
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  console.log(`\n========================================`);
  console.log(`🧪 TESTING PLUGIN: [${manifest.id}] - ${manifest.name} (v${manifest.version})`);
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

  console.log(`\n🎉 RESULT: ALL TESTS PASSED FOR [${manifest.id}]!\n`);
  return true;
}

if (process.argv[2]) {
  testPlugin(process.argv[2]).catch(err => {
    console.error('❌ TEST FAILED:', err);
    process.exit(1);
  });
}
