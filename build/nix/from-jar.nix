#to test
#build:
# nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./build/nix/from-jar.nix {}'
#remove:
# nix-store --delete `readlink result` --ignore-liveness
#
{ stdenv, fetchurl, makeWrapper, jre }:

let
  version = "0.11.0";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
  src = fetchurl {
    url = "https://github.com/passivsystems/protean/releases/download/${version}/protean.tgz";
    sha256 = "ac7d6bc830515e5a959f66535a5dd82a4d9391ba0cc7643bb17499b59b2cadc7";
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
    rm $out/lib/protean-server

    # create executables
    makeWrapper ${jre}/bin/java $out/bin/protean \
      --add-flags "-Xmx64m -jar $out/lib/protean.jar" \
      --set PROTEAN_HOME $out/lib \
      --set PROTEAN_CODEX_DIR $out/lib

    makeWrapper ${jre}/bin/java $out/bin/protean-server \
      --add-flags "-cp $out/lib/protean.jar -Xmx32m protean.server.main"
  '';

  fixupPhase = ''
    chmod +x $out/bin/protean
    chmod +x $out/bin/protean-server
  '';

  meta = {
    description = "Evolve your RESTful API's and Web Services.";
    homepage = "https://github.com/passivsystems/protean";
    license = stdenv.lib.licenses.asl20;
    platforms = stdenv.lib.platforms.all;
  };
}
