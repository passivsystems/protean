{ stdenv, ant }:

let
  version = "0.12.0-pre.1";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
#  src = fetchurl {
#      url = "https://github.com/passivsystems/protean/releases/download/0.11.0/protean-nix-${version}.tgz";
#      sha256 = "1fr3sw06qncb6yygcf2lbnkxma4v1dbigpf39ajrm0isxbpyv944"; # TODO set value, once zip defined
#  };

  src = "/home/colin/Workspaces/clojure/protean/target/nix/protean-${version}.tgz";

  installPhase = ''
    mkdir $out
    cp ./* $out/ -R

    # create executables
    mkdir -p $out/bin

    echo "#!/bin/bash
    export PROTEAN_HOME=$out/lib/protean
    export PROTEAN_CODEX_DIR=$out/lib/protean
    java -Xmx64m -jar $out/lib/protean.jar \"\$@\"
    " > $out/bin/protean
    chmod +x $out/bin/protean

    echo "#!/bin/bash
    java -cp $out/lib/protean.jar -Xmx32m protean.server.main
    " > $out/bin/protean-server
    chmod +x $out/bin/protean-server
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
