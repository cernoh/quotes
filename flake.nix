{
  description = "quotes - a serif Wayland desktop widget for lines from classic books";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;

      fontFiles = pkgs: [
        "${pkgs.eb-garamond}/share/fonts/opentype/EBGaramond12-Regular.otf"
        "${pkgs.eb-garamond}/share/fonts/opentype/EBGaramond12-Italic.otf"
        "${pkgs.eb-garamond}/share/fonts/opentype/EBGaramondSC12-Regular.otf"
      ];
    in
    {
      packages = forAllSystems (
        system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
          lib = pkgs.lib;
          pythonEnv = pkgs.python3.withPackages (ps: [ ps.pygobject3 ]);
          # Gtk pulls the whole GTK stack. Interpolating a package such as pango
          # gives its default output, which for pango is `bin` and holds no
          # typelib, so take the lib output of every entry on purpose.
          gtkStack = map lib.getLib ([
            pkgs.glib
            pkgs.pango
            pkgs.cairo
            pkgs.gdk-pixbuf
            pkgs.graphene
            pkgs.harfbuzz
            pkgs.gtk4
            pkgs.gtk4-layer-shell
            pkgs.gobject-introspection
          ] ++ pkgs.gtk4.buildInputs ++ pkgs.gtk4.propagatedBuildInputs);
          typelibPath = lib.makeSearchPath "lib/girepository-1.0" gtkStack;
          libraryPath = lib.makeLibraryPath gtkStack;
        in
        {
          default = self.packages.${system}.quotes;

          quotes = pkgs.stdenvNoCC.mkDerivation {
            pname = "quotes";
            version = "0.1.0";
            src = self;

            nativeBuildInputs = [ pkgs.makeWrapper ];
            dontConfigure = true;
            dontBuild = true;

            # gtk4-layer-shell reaches into libwayland, so it MUST load before
            # libwayland-client. GTK loads libwayland first, and linking order is
            # fixed by then, so preload the library. Without this,
            # gtk_layer_init_for_window silently fails and the widget becomes an
            # ordinary toplevel window, which a tiling compositor tiles.
            installPhase = ''
              runHook preInstall
              mkdir -p $out/share/quotes
              cp -r data quotes $out/share/quotes/
              rm -rf $out/share/quotes/quotes/__pycache__
              makeWrapper ${pythonEnv}/bin/python3 $out/bin/quotes \
                --add-flags "$out/share/quotes/quotes/widget.py" \
                --prefix GI_TYPELIB_PATH : "${typelibPath}" \
                --prefix LD_LIBRARY_PATH : "${libraryPath}" \
                --prefix LD_PRELOAD : "${pkgs.gtk4-layer-shell}/lib/libgtk4-layer-shell.so" \
                --prefix XDG_DATA_DIRS : "${pkgs.eb-garamond}/share:${pkgs.gtk4}/share" \
                --set QUOTES_DATA_DIR "$out/share/quotes/data" \
                --set QUOTES_FONT_FILE "${lib.concatStringsSep ":" (fontFiles pkgs)}"
              runHook postInstall
            '';

            meta = {
              description = "Desktop widget for lines from classic books";
              mainProgram = "quotes";
              platforms = lib.platforms.linux;
            };
          };
        }
      );

      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = "${self.packages.${system}.quotes}/bin/quotes";
        };
      });

      checks = forAllSystems (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          corpus = pkgs.runCommand "check-corpus" { } ''
            ${pkgs.python3}/bin/python3 ${./tools/check-corpus.py} ${./data/quotes.json}
            touch $out
          '';
        });

      devShells = forAllSystems (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          default = pkgs.mkShell {
            packages = [
              (pkgs.python3.withPackages (ps: [ ps.pygobject3 ]))
              pkgs.gtk4
              pkgs.gtk4-layer-shell
              pkgs.gobject-introspection
              pkgs.eb-garamond
              pkgs.grim
              pkgs.wlr-randr
            ];
            env = {
              GI_TYPELIB_PATH = pkgs.lib.makeSearchPath "lib/girepository-1.0" (
                map pkgs.lib.getLib (
                  [
                    pkgs.glib
                    pkgs.pango
                    pkgs.cairo
                    pkgs.gdk-pixbuf
                    pkgs.graphene
                    pkgs.harfbuzz
                    pkgs.gtk4
                    pkgs.gtk4-layer-shell
                    pkgs.gobject-introspection
                  ]
                  ++ pkgs.gtk4.buildInputs
                  ++ pkgs.gtk4.propagatedBuildInputs
                )
              );
              LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath (
                map pkgs.lib.getLib (
                  [
                    pkgs.glib
                    pkgs.pango
                    pkgs.cairo
                    pkgs.gdk-pixbuf
                    pkgs.graphene
                    pkgs.harfbuzz
                    pkgs.gtk4
                    pkgs.gtk4-layer-shell
                  ]
                  ++ pkgs.gtk4.buildInputs
                  ++ pkgs.gtk4.propagatedBuildInputs
                )
              );
              QUOTES_FONT_FILE = pkgs.lib.concatStringsSep ":" (fontFiles pkgs);
              # Layer shell MUST load before libwayland-client, or it cannot
              # interpose. See the installPhase comment in flake.nix.
              LD_PRELOAD = "${pkgs.gtk4-layer-shell}/lib/libgtk4-layer-shell.so";
            };
            shellHook = ''
              echo "quotes dev shell: python3 -m quotes.widget --print"
            '';
          };
        });

      formatter = forAllSystems (
        system: nixpkgs.legacyPackages.${system}.nixfmt-rfc-style
      );
    };
}
