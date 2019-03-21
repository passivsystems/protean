#to test
#build:
# nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./build/nix/from-jar.nix {}'
#remove:
# nix-store --delete `readlink result` --ignore-liveness
#
{ stdenv, fetchurl, makeWrapper, jre }:

let
  version = "0.14.0-SNAPSHOT";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
  src = fetchurl {
    url = "https://github.com/passivsystems/protean/releases/download/${version}/protean.tgz";
    sha256 = "CHANGE_ME";
  };

  # downloaded tarball doesn't have a root directory - move into one while unpacking.
  # we could use sourceRoot=".", but copying in installPhase will also copy nix files (e.g. env-var)
  unpackPhase = ''
    echo "unpackPhase"
    runHook preUnpack
    mkdir protean-${version}
    tar -C protean-${version} -xvzf $src
    export sourceRoot="protean-${version}"
    runHook postUnpack
  '';

  buildInputs = [ makeWrapper ];

  installPhase = ''
    echo "installPhase"
    mkdir -p $out/{lib,bin}
    cp ./* $out/lib -R

    # remove invalid executables
    rm $out/lib/protean

    # create executables
    makeWrapper ${jre}/bin/java $out/bin/protean \
      --add-flags "-Xmx64m -jar $out/lib/protean.jar" \
      --set PROTEAN_HOME $out/lib \
      --set PROTEAN_CODEX_DIR $out/lib
  '';

  fixupPhase = ''
    chmod +x $out/bin/protean
  '';

  meta = {
    description = "Evolve your RESTful API's and Web Services.";
    homepage = "https://github.com/passivsystems/protean";
    license = stdenv.lib.licenses.asl20;
    platforms = stdenv.lib.platforms.all;
  };
}
