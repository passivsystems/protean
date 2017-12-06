#to test
#build:
# nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./build/nix/from-src.nix {}'
#remove:
# nix-store --delete `readlink result` --ignore-liveness
#
{ stdenv, fetchFromGitHub, makeWrapper, leiningen, jre }:

let
  version = "0.11.0";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
  #src = fetchgit {
  #  url = "https://github.com/passivsystems/protean.git";
  #  rev = "fa0dfd10d0b2ce180c2b89cbeaab9f1c2c18ae6c";
  #  sha256 = "04jz5rfqcpw6zlnryqzkbv6bp3pxf763kq1p7lqvw5zfyqbn6vrc";
  #};

  # faster
  src = fetchFromGitHub {
    owner = "passivsystems";
    repo = "protean";
    rev = "${version}";
    sha256 = "04jz5rfqcpw6zlnryqzkbv6bp3pxf763kq1p7lqvw5zfyqbn6vrc";
  };

  buildInputs = [ leiningen makeWrapper ];

  buildPhase = ''
    # For leiningen
    export HOME=$PWD
    export LEIN_HOME=$HOME/.lein
    mkdir -p $LEIN_HOME
    echo "{:user {:local-repo \"$LEIN_HOME\"}}" > $LEIN_HOME/profiles.clj

    ${leiningen}/bin/lein uberjar
  '';

  installPhase = ''
    echo "installPhase"
    mkdir -p $out/{lib,bin}

    # populate lib
    cp ./target/protean-${version}-standalone.jar $out/lib/protean.jar
    rm -rf ./silk_templates/data/protean-api/*
    rm -rf ./silk_templates/site/*
    cp -r ./silk_templates $out/lib
    cp -r ./public $out/lib
    cp -r ./test-data $out/lib
    cp ./defaults.edn $out/lib
    cp ./sample-petstore.cod.edn $out/lib
    cp ./protean-utils.cod.edn $out/lib
    cp ./protean-utils.sim.edn $out/lib

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
