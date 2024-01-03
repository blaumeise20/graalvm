arch -arch x86_64 zsh
export MX_PYTHON=/usr/bin/python3
function my {
    if [[ "$1" == "-d" ]]; then
        shift
        command=$1
        shift
        mx --env jvm-ce-llvm --native-images= $command --vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000 $@
    else
        mx --env jvm-ce-llvm --native-images= $@
    fi
}
# --experimental-options --llvm.llDebug=true --log.llvm.TraceIR.level=FINER
# my espresso --experimental-options --vm.Dgraal.Dump=:1 --vm.Dgraal.PrintGraph=Network --engine.BackgroundCompilation=false --engine.TraceCompilation -cp ../../java_hello_world _________IsPrime_________ 19259