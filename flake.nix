# symbanta

# Copyright © Josep Bigorra <jjbigorra@gmail.com>
#
# symbanta is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# symbanta is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
#

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
            darkhttpd
            watchexec
            maven
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
