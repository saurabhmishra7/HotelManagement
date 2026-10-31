#!/usr/bin/env bash
set -euo pipefail
# Fixed releases; verify downloaded archives against upstream checksums.
dest="${1:?Supply an install directory}"
mkdir -p "$dest"
case "$(uname -s)-$(uname -m)" in
 Linux-x86_64) gl_os=linux; gl_arch=x64; tv_os=Linux; tv_arch=64bit;;
 Darwin-arm64) gl_os=darwin; gl_arch=arm64; tv_os=macOS; tv_arch=ARM64;;
 *) echo 'Unsupported host; install the pinned versions from upstream releases.' >&2; exit 1;;
esac
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
for tool in gitleaks trivy; do
 if [[ "$tool" == gitleaks ]]; then
  version=8.30.1; repo=gitleaks/gitleaks; archive="gitleaks_${version}_${gl_os}_${gl_arch}.tar.gz"
 else
  version=0.76.1; repo=aquasecurity/trivy; archive="trivy_${version}_${tv_os}-${tv_arch}.tar.gz"
 fi
 base="https://github.com/${repo}/releases/download/v${version}"
 curl --fail --silent --show-error --location "$base/$archive" -o "$tmp/$archive"
 curl --fail --silent --show-error --location "$base/${tool}_${version}_checksums.txt" -o "$tmp/checksums"
 (cd "$tmp"; awk -v name="$archive" '$2 == name {print}' checksums > selected-checksum; test -s selected-checksum; shasum -a 256 -c selected-checksum)
 tar -xzf "$tmp/$archive" -C "$dest" "$tool"
done
