# Web2App build runner

This repository only runs builds for the Web2App service. Each build is started by the
service, reads its settings from the service with a secret, and uploads its files
encrypted with a key made for that one build, so build files here cannot be opened.
