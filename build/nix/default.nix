{ stdenv, fetchurl, ant }:

let
  version = "0.11.0";
in
stdenv.mkDerivation rec {
  name = "protean-${version}";
  src = fetchurl {
    url = "https://github.com/passivsystems/protean/releases/download/${version}/protean.tgz";
    sha256 = "ac7d6bc830515e5a959f66535a5dd82a4d9391ba0cc7643bb17499b59b2cadc7"; # https://md5file.com/calculator
  };

  # required since tarball has multiple root directories, and cannot be assigned automatically
  # (even though we're not using it)
  sourceRoot = ".";

  # Phases: https://nixos.wiki/wiki/Create_and_debug_nix_packages#Using_nix-shell_for_package_development
  installPhase = ''
    echo "installPhase"
    mkdir -p $out/lib $out/bin
    cp ./* $out/lib -R

    # remove invalid executables
    rm $out/lib/protean
    rm $out/lib/protean-server

    # create executables
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
