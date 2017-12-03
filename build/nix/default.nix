{ stdenv, ant }:

let
  version = "0.12.0-pre.1";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
#  src = fetchurl { ... };
  src = "/home/colin/Workspaces/clojure/protean/target/nix/protean-${version}.tgz";

  installPhase = ''
    mkdir $out
    cp ./* $out/ -R
  '';

  meta = {
    description = "Evolve your RESTful API's and Web Services.";
    homepage = "https://github.com/passivsystems/protean";
    maintainers = [ stdenv.lib.maintainers.cmcdragonkai ];
    license = stdenv.lib.licenses.asl20;
    priority = 1;
    platforms = stdenv.lib.platforms.linux;
  };
}
