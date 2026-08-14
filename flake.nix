{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    systems.url = "github:nix-systems/default";
  };
  outputs = { systems, nixpkgs, ... }:
    let
      eachSystem = f:
        nixpkgs.lib.genAttrs (import systems)
        (system: f nixpkgs.legacyPackages.${system});
    in {
      devShells = eachSystem (pkgs: {
        default = pkgs.mkShell {
          buildInputs = with pkgs; [
            ktfmt
            ktlint
            kotlin
            jdk25
            nil
            just
            yaml-language-server
          ];
          JAVA_HOME = "${pkgs.jdk25}";
          shellHook = ''
            export JAVA_HOME="${pkgs.jdk25}"
            export PATH="$JAVA_HOME/bin:$PATH"
          '';
        };
      });
    };
}
