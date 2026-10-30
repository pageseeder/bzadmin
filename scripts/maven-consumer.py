"""Resolve the staged overlay with Maven, isolated from user settings and local artifacts."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
version = (root / "version.txt").read_text().strip()
repository = (root / "build/staging-repo").as_uri()
with tempfile.TemporaryDirectory(prefix="bzadmin-maven-") as temp:
    work = Path(temp)
    (work / "settings.xml").write_text("<settings/>")
    (work / "pom.xml").write_text(f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion><groupId>example</groupId>
      <artifactId>consumer</artifactId><version>1</version>
      <repositories><repository><id>staged</id><url>{repository}</url></repository></repositories>
      <dependencies><dependency><groupId>org.pageseeder.berlioz</groupId>
      <artifactId>pso-bzadmin</artifactId><version>{version}</version></dependency></dependencies>
    </project>""")
    subprocess.run(["mvn", "-B", "-s", str(work / "settings.xml"), "-gs", str(work / "settings.xml"),
                    f"-Dmaven.repo.local={work / 'repository'}", "-f", str(work / "pom.xml"),
                    "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies",
                    f"-DoutputDirectory={work / 'libraries'}"], check=True)
    actual = {file.name for file in (work / "libraries").glob("*.jar")}
    # Versions of transitive dependencies are read from the catalog/build resolution;
    # check module identities rather than imposing a second version policy here.
    modules = {name.rsplit("-", 1)[0] for name in actual}
    assert modules == {"pso-bzadmin", "pso-berlioz", "pso-xmlwriter", "slf4j-api"}, actual
    print("Clean Maven consumer resolved:", sorted(actual))
