{
  description = "CPt205";

  inputs = {
    nixpkgs.url = "https://mirrors.ustc.edu.cn/nix-channels/nixos-unstable/nixexprs.tar.xz";
  };

  outputs = { self , nixpkgs ,... }: let
    # system should match the system you are running on
    system = "x86_64-linux";
  in {
    devShells."${system}".default = let
      pkgs = import nixpkgs { inherit system; };
    in pkgs.mkShell {
      # create an environment with nodejs, pnpm, and yarn
      packages = with pkgs; [
        xmake
        dotnet-sdk_10
        dotnet-repl
        dotnet-runtime_10
        jdk17
        kotlin
        gradle
      ];

      shellHook = ''
        echo '<====== Setup Develop Env ======>'
        cd csharp && dotnet build && cd ..
        mkdir -p home/resolver home/lib
        cp -r ./csharp/AssemblyResolver/bin/Debug/net9.0/* ./home/resolver/
        cp -r ./csharp/kotlin-stdlib/bin/Debug/netstandard2.0/kotlin-stdlib.dll ./home/lib/
        chmod a+x ./gradlew && ./gradlew --refresh-dependencies && ./gradlew :compiler:jvmRun
        echo '<====== Sample Build Complete ======>'
      '';
    };
  };
}
