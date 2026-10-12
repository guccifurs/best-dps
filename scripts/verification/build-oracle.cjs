// Bundles only into ignored build output. Reference implementation is not plugin code.
const path = require('node:path');
const [source, dependencies, output] = process.argv.slice(2).map(p => path.resolve(p));
const esbuild = require(path.join(dependencies, 'node_modules/esbuild'));
esbuild.buildSync({
  entryPoints: [path.join(__dirname, 'wiki-oracle.ts')], outfile: output,
  bundle:true, platform:'node', format:'esm',
  alias: {'@':path.join(source,'src'), '@data':path.join(source,'cdn/json')},
  nodePaths:[path.join(dependencies,'node_modules')], loader:{'.png':'dataurl'},
});
