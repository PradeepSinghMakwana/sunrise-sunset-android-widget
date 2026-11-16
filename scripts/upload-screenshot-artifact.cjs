const {DefaultArtifactClient} = require("@actions/artifact");
const path = require("node:path");

async function main() {
  const [name, ...relativeFiles] = process.argv.slice(2);
  if (!name || relativeFiles.length === 0) {
    throw new Error("Usage: upload-screenshot-artifact.cjs <checkpoint-name> <file...>");
  }
  const root = path.resolve("screenshots");
  const files = relativeFiles.map(file => path.resolve(file));
  const artifactName = `pixel5-${name.replace(/[^A-Za-z0-9._-]/g, "-")}`;
  const client = new DefaultArtifactClient();
  const result = await client.uploadArtifact(artifactName, files, root, {
    retentionDays: 7,
    compressionLevel: 0
  });
  console.log(`Live artifact available: ${artifactName} (id ${result.id}, ${result.size} bytes)`);
}

main().catch(error => {
  console.error("Live screenshot artifact upload failed:", error);
  process.exitCode = 1;
});
