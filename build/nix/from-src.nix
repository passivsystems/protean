#to test
#build:
# nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./build/nix/from-src.nix {}'
#remove:
# nix-store --delete `readlink result` --ignore-liveness
#
{ stdenv, /*fetchgit*/ fetchFromGitHub, makeWrapper, leiningen, jre }:

let
  version = "0.14.1";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
  /* src = fetchgit {
    url = "https://github.com/passivsystems/protean.git";
    rev = "1e1e43302021f7993e536a954d2b83c68bcb58dc";
    sha256 = "0xs8y61r13zqw29nk9hw49phih4vp3n401g3a981fsf009qz68a0";
  }; */

  # faster
  src = fetchFromGitHub {
    owner = "passivsystems";
    repo = "protean";
    rev = "${version}";
    sha256 = "0xs8y61r13zqw29nk9hw49phih4vp3n401g3a981fsf009qz68a0";
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
    cp ./defaults.edn $out/lib

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
