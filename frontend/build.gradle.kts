/*
 * Copyright 2026 Allette Systems
 * SPDX-License-Identifier: Apache-2.0
 */
tasks.create<Exec>("webpack") {
  group = "build"

  inputs.file("package-lock.json").withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.dir("src").withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir("../overlay/src/main/resources/META-INF/resources")
  outputs.cacheIf { true }

  // Invoke Webpack build
  commandLine("$projectDir/node_modules/.bin/webpack", "--mode production")
}
